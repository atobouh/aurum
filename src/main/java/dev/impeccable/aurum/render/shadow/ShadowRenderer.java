package dev.impeccable.aurum.render.shadow;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.impeccable.aurum.AurumMod;
import dev.impeccable.aurum.config.AurumConfig;
import dev.impeccable.aurum.director.Atmosphere;
import dev.impeccable.aurum.mixin.sodium.RenderSectionManagerAccessor;
import dev.impeccable.aurum.mixin.sodium.SodiumWorldRendererAccessor;
import dev.impeccable.aurum.render.GpuTimers;
import dev.impeccable.aurum.render.TimerSection;
import dev.impeccable.aurum.render.terrain.TerrainShaders;
import dev.impeccable.aurum.render.terrain.TerrainUniforms;
import it.unimi.dsi.fastutil.longs.Long2ReferenceMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectLinkedOpenHashMap;
import me.jellysquid.mods.sodium.client.gl.device.CommandList;
import me.jellysquid.mods.sodium.client.gl.device.RenderDevice;
import me.jellysquid.mods.sodium.client.render.SodiumWorldRenderer;
import me.jellysquid.mods.sodium.client.render.chunk.ChunkRenderMatrices;
import me.jellysquid.mods.sodium.client.render.chunk.ChunkRenderer;
import me.jellysquid.mods.sodium.client.render.chunk.RenderSection;
import me.jellysquid.mods.sodium.client.render.chunk.RenderSectionFlags;
import me.jellysquid.mods.sodium.client.render.chunk.RenderSectionManager;
import me.jellysquid.mods.sodium.client.render.chunk.lists.ChunkRenderList;
import me.jellysquid.mods.sodium.client.render.chunk.lists.ChunkRenderListIterable;
import me.jellysquid.mods.sodium.client.render.chunk.region.RenderRegion;
import me.jellysquid.mods.sodium.client.render.chunk.terrain.DefaultTerrainRenderPasses;
import me.jellysquid.mods.sodium.client.render.viewport.CameraTransform;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.ChunkSectionPos;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL14C;
import org.lwjgl.opengl.GL30C;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Sun (or moon) shadow map, drawn with Sodium's own chunk renderer from Sodium's already-built GPU meshes, so
 * nothing is re-meshed on the CPU.
 *
 * <p>Lazy and smooth (spec feature 4): the map is a cache. It is redrawn only when the light has moved, the player
 * has moved a few blocks, or a chunk mesh nearby changed. A redraw is spread over several frames into a back map
 * while the front map stays in use, then the two swap, so there is never a frame-time spike. After a swap the old
 * map is kept for {@value #BLEND_SECONDS} s and the terrain shader cross-fades from it, so shadows glide instead of
 * visibly stepping (user feedback: "I see it move every second").
 *
 * <p>Detail is concentrated near the player with the usual radial distortion (shaderLABS shadow tutorial).
 */
public final class ShadowRenderer {
    /** How far the virtual light camera sits from the player, in blocks. */
    private static final double LIGHT_DISTANCE = 400.0;
    private static final float DEPTH_HALF_RANGE = 400.0f;
    /** Radial distortion strength: higher puts more texels near the player. */
    public static final float DISTORTION = 0.82f;
    /** Rebuild when the light direction has turned this much (radians), or the player moved this far (blocks). */
    private static final float ANGLE_THRESHOLD = 0.0025f;
    /** Cross-fade time from the previous map to a freshly built one. */
    public static final float BLEND_SECONDS = 0.45f;
    private static final double MOVE_THRESHOLD = 4.0;
    private static final int SLICES = 3;
    private static final int MIN_FRAMES_BETWEEN_DIRTY_REBUILDS = 20;

    /** One shadow map and the light setup it was drawn with. */
    static final class Map {
        int texture, fbo;
        final Matrix4f projection = new Matrix4f();
        final Matrix4f view = new Matrix4f();
        double camX, camY, camZ;      // virtual light camera (positions are drawn relative to it)
        double originX, originY, originZ; // player position the map is centred on
        float lightX, lightY, lightZ;
        float halfExtent;
        boolean valid;
    }

    private static Map front = new Map(), back = new Map(), previous = new Map();
    private static long swapNanos;
    private static int resolution;
    private static int dummyDepth;
    private static boolean building;
    private static int slice;
    private static int sliceCount;
    private static boolean dirty;
    private static int frame, lastBuildStart;
    private static boolean failed;

    /** Camera position of the current frame (Sodium draws relative to it). */
    public static double cameraX, cameraY, cameraZ;

    private static final Matrix4f sampling = new Matrix4f();

    private ShadowRenderer() {}

    /** True when terrain should sample the shadow map this frame. */
    public static boolean usable() {
        return front.valid && !failed && AurumConfig.get().shadowsEnabled && TerrainUniforms.mode == 1;
    }

    public static int frontTexture() {
        return front.valid ? front.texture : dummyDepth();
    }

    /** World size of one shadow texel at the centre of the map, before distortion. */
    public static float centreTexelWorld() {
        return front.halfExtent * 2f / Math.max(1, resolution) * (1f - DISTORTION);
    }

    public static float inverseResolution() {
        return 1f / Math.max(1, resolution);
    }

    /** 0..1 progress of the cross-fade from the previous map to the front map (1 = done, sample front only). */
    public static float blend() {
        if (!previous.valid) return 1f;
        float t = (System.nanoTime() - swapNanos) / 1e9f / BLEND_SECONDS;
        return Math.max(0f, Math.min(1f, t));
    }

    public static int previousTexture() {
        return previous.valid ? previous.texture : frontTexture();
    }

    private static final Matrix4f samplingPrev = new Matrix4f();

    /** Same as {@link #samplingMatrix()} for the previous map. */
    public static Matrix4f previousSamplingMatrix() {
        Map m = previous.valid ? previous : front;
        samplingPrev.set(m.projection).mul(m.view)
                .translate((float) (cameraX - m.camX), (float) (cameraY - m.camY), (float) (cameraZ - m.camZ));
        return samplingPrev;
    }

    /** Light clip space from the current frame's camera-relative positions, for the front map. */
    public static Matrix4f samplingMatrix() {
        sampling.set(front.projection).mul(front.view)
                .translate((float) (cameraX - front.camX), (float) (cameraY - front.camY), (float) (cameraZ - front.camZ));
        return sampling;
    }

    /** Called from Sodium when chunk meshes are rebuilt. */
    public static void onSectionRebuilt(int cx, int cy, int cz) {
        if (!front.valid) return;
        int radius = AurumConfig.get().shadowDistance + 2;
        int ocx = (int) Math.floor(front.originX) >> 4, ocz = (int) Math.floor(front.originZ) >> 4;
        if (Math.abs(cx - ocx) <= radius && Math.abs(cz - ocz) <= radius) dirty = true;
    }

    /** Runs right before Sodium draws the opaque terrain. Draws (part of) the shadow map if needed. */
    public static void update() {
        frame++;
        EntityShadows.invalidate();
        AurumConfig c = AurumConfig.get();
        if (failed || !c.shadowsEnabled || TerrainUniforms.mode != 1 || !TerrainShaders.installed()) return;
        SodiumWorldRenderer swr = SodiumWorldRenderer.instanceNullable();
        if (swr == null) return;
        RenderSectionManager rsm = ((SodiumWorldRendererAccessor) swr).aurum$getRenderSectionManager();
        if (rsm == null) return;

        if (resolution != c.shadowResolution) {
            recreate(c.shadowResolution);
        }

        int t = GpuTimers.begin(TimerSection.SHADOW);
        try {
            if (!building && needsRebuild(c)) {
                startBuild(c);
            }
            if (building) {
                drawSlice(rsm, back, slice, sliceCount);
                slice++;
                if (slice >= sliceCount) {
                    building = false;
                    back.valid = true;
                    // Rotate: the old front becomes "previous" for the cross-fade, the old previous is reused.
                    Map recycled = previous;
                    previous = front;
                    front = back;
                    back = recycled;
                    swapNanos = System.nanoTime();
                }
            }
            EntityShadows.update(buildLightDir(), MinecraftClient.getInstance().getTickDelta());
        } catch (RuntimeException e) {
            failed = true;
            AurumMod.LOGGER.error("Shadow pass failed; shadows disabled for this session", e);
        } finally {
            MinecraftClient.getInstance().getFramebuffer().beginWrite(true);
            GpuTimers.end(t);
        }
    }

    private static boolean needsRebuild(AurumConfig c) {
        if (!front.valid) return true;
        float[] l = buildLightDir();
        float dot = l[0] * front.lightX + l[1] * front.lightY + l[2] * front.lightZ;
        if (dot < Math.cos(ANGLE_THRESHOLD)) return true;
        double dx = cameraX - front.originX, dy = cameraY - front.originY, dz = cameraZ - front.originZ;
        if (dx * dx + dy * dy + dz * dz > MOVE_THRESHOLD * MOVE_THRESHOLD) return true;
        if (front.halfExtent != c.shadowDistance * 16f) return true;
        return dirty && frame - lastBuildStart >= MIN_FRAMES_BETWEEN_DIRTY_REBUILDS;
    }

    /**
     * The light direction used for the map: never lower than ~10 degrees. Real shadows keep stretching toward the
     * horizon, but past ~6x an object's height they cross half the map and read as a glitch (user feedback), so
     * they stop lengthening there and fade out as the sun goes lower.
     */
    private static float[] buildLightDir() {
        float x = Atmosphere.lightDir[0], y = Math.max(Atmosphere.lightDir[1], 0.17f), z = Atmosphere.lightDir[2];
        float len = (float) Math.sqrt(x * x + y * y + z * z);
        return new float[]{x / len, y / len, z / len};
    }

    private static void startBuild(AurumConfig c) {
        Map m = back;
        float[] l = buildLightDir();
        m.lightX = l[0];
        m.lightY = l[1];
        m.lightZ = l[2];
        m.halfExtent = c.shadowDistance * 16f;

        // Light-space basis (the sun moves in the X/Y plane, so +Z is always a stable "up").
        double fx = -l[0], fy = -l[1], fz = -l[2];
        double sx = fy * 1 - fz * 0, sy = fz * 0 - fx * 1, sz = fx * 0 - fy * 0; // cross(f, (0,0,1))
        double slen = Math.sqrt(sx * sx + sy * sy + sz * sz);
        sx /= slen;
        sy /= slen;
        sz /= slen;
        double ux = sy * fz - sz * fy, uy = sz * fx - sx * fz, uz = sx * fy - sy * fx;

        // Snap the centre to whole texels in light space so the map does not shimmer when it is re-centred.
        double texel = m.halfExtent * 2.0 / resolution * (1.0 - DISTORTION);
        double px = cameraX * sx + cameraY * sy + cameraZ * sz;
        double py = cameraX * ux + cameraY * uy + cameraZ * uz;
        double snapX = Math.floor(px / texel) * texel - px, snapY = Math.floor(py / texel) * texel - py;
        m.originX = cameraX + sx * snapX + ux * snapY;
        m.originY = cameraY + sy * snapX + uy * snapY;
        m.originZ = cameraZ + sz * snapX + uz * snapY;

        m.camX = m.originX + l[0] * LIGHT_DISTANCE;
        m.camY = m.originY + l[1] * LIGHT_DISTANCE;
        m.camZ = m.originZ + l[2] * LIGHT_DISTANCE;
        m.view.identity().lookAt(0f, 0f, 0f, (float) fx, (float) fy, (float) fz, 0f, 0f, 1f);
        m.projection.identity().setOrtho(-m.halfExtent, m.halfExtent, -m.halfExtent, m.halfExtent,
                (float) LIGHT_DISTANCE - DEPTH_HALF_RANGE, (float) LIGHT_DISTANCE + DEPTH_HALF_RANGE);

        building = true;
        slice = 0;
        // The very first map is drawn in one go; later rebuilds are spread over frames behind the front map.
        sliceCount = front.valid ? SLICES : 1;
        dirty = false;
        lastBuildStart = frame;

        GlStateManager._glBindFramebuffer(GL30C.GL_FRAMEBUFFER, m.fbo);
        GlStateManager._viewport(0, 0, resolution, resolution);
        RenderSystem.depthMask(true);
        GlStateManager._clearDepth(1.0);
        GlStateManager._clear(GL11C.GL_DEPTH_BUFFER_BIT, false);
    }

    private static void drawSlice(RenderSectionManager rsm, Map m, int k, int n) {
        RenderSectionManagerAccessor acc = (RenderSectionManagerAccessor) rsm;
        ChunkRenderListIterable lists = collect(acc.aurum$getSectionByPosition(), m, k, n);

        GlStateManager._glBindFramebuffer(GL30C.GL_FRAMEBUFFER, m.fbo);
        GlStateManager._viewport(0, 0, resolution, resolution);

        int previousMode = TerrainUniforms.mode;
        TerrainUniforms.mode = 2;
        RenderSystem.enablePolygonOffset();
        RenderSystem.polygonOffset(1.1f, 4.0f);
        RenderDevice.enterManagedCode();
        try {
            ChunkRenderer renderer = acc.aurum$getChunkRenderer();
            CommandList commandList = RenderDevice.INSTANCE.createCommandList();
            ChunkRenderMatrices matrices = new ChunkRenderMatrices(m.projection, m.view);
            CameraTransform camera = new CameraTransform(m.camX, m.camY, m.camZ);
            renderer.render(matrices, commandList, lists, DefaultTerrainRenderPasses.SOLID, camera);
            renderer.render(matrices, commandList, lists, DefaultTerrainRenderPasses.CUTOUT, camera);
            commandList.flush();
        } finally {
            RenderDevice.exitManagedCode();
            RenderSystem.polygonOffset(0f, 0f);
            RenderSystem.disablePolygonOffset();
            TerrainUniforms.mode = previousMode;
        }
    }

    /** Sections with geometry around the map centre, in slice k of n (interleaved by chunk column). */
    private static ChunkRenderListIterable collect(Long2ReferenceMap<RenderSection> sections, Map m, int k, int n) {
        ClientWorld world = MinecraftClient.getInstance().world;
        int radius = (int) Math.ceil(m.halfExtent / 16f) + 1;
        int ocx = (int) Math.floor(m.originX) >> 4, ocz = (int) Math.floor(m.originZ) >> 4;
        int minY = world.getBottomSectionCoord(), maxY = world.getTopSectionCoord();
        Reference2ObjectLinkedOpenHashMap<RenderRegion, ChunkRenderList> lists = new Reference2ObjectLinkedOpenHashMap<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int cx = ocx + dx, cz = ocz + dz;
                if (Math.floorMod(cx + 2 * cz, n) != k) continue;
                for (int cy = minY; cy < maxY; cy++) {
                    RenderSection s = sections.get(ChunkSectionPos.asLong(cx, cy, cz));
                    if (s == null || s.isDisposed() || !s.isBuilt()) continue;
                    if (((s.getFlags() >>> RenderSectionFlags.HAS_BLOCK_GEOMETRY) & 1) == 0) continue;
                    ChunkRenderList list = lists.get(s.getRegion());
                    if (list == null) {
                        list = new ChunkRenderList(s.getRegion());
                        list.reset(0);
                        lists.put(s.getRegion(), list);
                    }
                    list.add(s);
                }
            }
        }
        List<ChunkRenderList> out = new ArrayList<>(lists.values());
        return reverse -> {
            Iterator<ChunkRenderList> it = out.iterator();
            return it;
        };
    }

    private static void recreate(int res) {
        building = false;
        for (Map m : new Map[]{front, back, previous}) {
            if (m.texture != 0) {
                GlStateManager._deleteTexture(m.texture);
                GlStateManager._glDeleteFramebuffers(m.fbo);
            }
            m.texture = depthTexture(res);
            m.fbo = GlStateManager.glGenFramebuffers();
            GlStateManager._glBindFramebuffer(GL30C.GL_FRAMEBUFFER, m.fbo);
            GL30C.glFramebufferTexture2D(GL30C.GL_FRAMEBUFFER, GL30C.GL_DEPTH_ATTACHMENT, GL11C.GL_TEXTURE_2D, m.texture, 0);
            GL11C.glDrawBuffer(GL11C.GL_NONE);
            GL11C.glReadBuffer(GL11C.GL_NONE);
            int status = GL30C.glCheckFramebufferStatus(GL30C.GL_FRAMEBUFFER);
            if (status != GL30C.GL_FRAMEBUFFER_COMPLETE) {
                failed = true;
                AurumMod.LOGGER.error("Shadow framebuffer incomplete (0x{}); shadows disabled", Integer.toHexString(status));
            }
            m.valid = false;
        }
        resolution = res;
        MinecraftClient.getInstance().getFramebuffer().beginWrite(true);
        AurumMod.LOGGER.info("Shadow maps: 3 x {}x{}", res, res);
    }

    static int depthTexture(int res) {
        int id = GlStateManager._genTexture();
        GlStateManager._bindTexture(id);
        GL11C.glTexImage2D(GL11C.GL_TEXTURE_2D, 0, GL14C.GL_DEPTH_COMPONENT24, res, res, 0, GL11C.GL_DEPTH_COMPONENT,
                GL11C.GL_UNSIGNED_INT, (java.nio.ByteBuffer) null);
        GlStateManager._texParameter(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MIN_FILTER, GL11C.GL_LINEAR);
        GlStateManager._texParameter(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MAG_FILTER, GL11C.GL_LINEAR);
        GlStateManager._texParameter(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_WRAP_S, 0x812F);
        GlStateManager._texParameter(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_WRAP_T, 0x812F);
        // Hardware depth comparison: every lookup is a filtered 2x2 test, which keeps soft edges cheap.
        GlStateManager._texParameter(GL11C.GL_TEXTURE_2D, GL14C.GL_TEXTURE_COMPARE_MODE, GL30C.GL_COMPARE_REF_TO_TEXTURE);
        GlStateManager._texParameter(GL11C.GL_TEXTURE_2D, GL14C.GL_TEXTURE_COMPARE_FUNC, GL11C.GL_LEQUAL);
        GlStateManager._bindTexture(0);
        return id;
    }

    private static int dummyDepth() {
        if (dummyDepth == 0) dummyDepth = depthTexture(1);
        return dummyDepth;
    }
}
