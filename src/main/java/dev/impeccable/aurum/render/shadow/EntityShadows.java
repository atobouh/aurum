package dev.impeccable.aurum.render.shadow;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.VertexSorter;
import dev.impeccable.aurum.AurumMod;
import dev.impeccable.aurum.config.AurumConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL30C;

/**
 * The dynamic shadow layer (spec feature 4): players and mobs within {@value #RANGE} blocks, redrawn every frame
 * into a small undistorted map centred on the camera. Terrain stays in the cached map; vanilla entity shaders
 * cannot apply the terrain map's distortion, which is why moving things get their own map.
 */
public final class EntityShadows {
    public static final float RANGE = 16f;
    private static final float HALF_EXTENT = RANGE + 2f;
    private static final float LIGHT_DISTANCE = 128f;
    private static final float DEPTH_HALF_RANGE = 96f;

    private static int texture, fbo, resolution;
    private static boolean valid;
    private static boolean failed;
    private static double camX, camY, camZ;
    private static final Matrix4f projection = new Matrix4f();
    private static final Matrix4f view = new Matrix4f();
    private static final Matrix4f sampling = new Matrix4f();

    private EntityShadows() {}

    /** Called whenever the shadow pass does not run this frame, so stale entity shadows are never used. */
    public static void invalidate() {
        valid = false;
    }

    public static boolean usable() {
        return valid && !failed;
    }

    public static int texture() {
        return texture;
    }

    /** True while entities near the camera get real shadows (vanilla's blob is then skipped for them). */
    public static boolean replacesBlob(Entity entity) {
        if (!valid) return false;
        MinecraftClient client = MinecraftClient.getInstance();
        return entity.squaredDistanceTo(client.gameRenderer.getCamera().getPos()) < RANGE * RANGE;
    }

    public static Matrix4f samplingMatrix() {
        sampling.set(projection).mul(view).translate((float) (ShadowRenderer.cameraX - camX),
                (float) (ShadowRenderer.cameraY - camY), (float) (ShadowRenderer.cameraZ - camZ));
        return sampling;
    }

    /** Draws nearby entities into the dynamic map. Runs right after the terrain shadow update. */
    public static void update(float[] lightDir, float tickDelta) {
        AurumConfig c = AurumConfig.get();
        valid = false;
        if (failed || !c.entityShadows) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) return;

        int res = c.shadowResolution >= 2048 ? 1024 : 512;
        if (res != resolution) create(res);
        if (failed) return;

        camX = ShadowRenderer.cameraX + lightDir[0] * LIGHT_DISTANCE;
        camY = ShadowRenderer.cameraY + lightDir[1] * LIGHT_DISTANCE;
        camZ = ShadowRenderer.cameraZ + lightDir[2] * LIGHT_DISTANCE;
        view.identity().lookAt(0f, 0f, 0f, -lightDir[0], -lightDir[1], -lightDir[2], 0f, 0f, 1f);
        projection.identity().setOrtho(-HALF_EXTENT, HALF_EXTENT, -HALF_EXTENT, HALF_EXTENT,
                LIGHT_DISTANCE - DEPTH_HALF_RANGE, LIGHT_DISTANCE + DEPTH_HALF_RANGE);

        GlStateManager._glBindFramebuffer(GL30C.GL_FRAMEBUFFER, fbo);
        GlStateManager._viewport(0, 0, resolution, resolution);
        RenderSystem.depthMask(true);
        GlStateManager._clearDepth(1.0);
        GlStateManager._clear(GL11C.GL_DEPTH_BUFFER_BIT, false);

        EntityRenderDispatcher dispatcher = client.getEntityRenderDispatcher();
        VertexConsumerProvider.Immediate immediate = client.getBufferBuilders().getEntityVertexConsumers();
        RenderSystem.backupProjectionMatrix();
        RenderSystem.setProjectionMatrix(projection, VertexSorter.BY_Z);
        MatrixStack modelView = RenderSystem.getModelViewStack();
        modelView.push();
        modelView.loadIdentity();
        modelView.multiplyPositionMatrix(view);
        RenderSystem.applyModelViewMatrix();
        dispatcher.setRenderShadows(false);
        RenderSystem.enablePolygonOffset();
        RenderSystem.polygonOffset(1.5f, 4.0f);
        int drawn = 0;
        dev.impeccable.aurum.render.surface.SurfaceLighting.depthPass = true;
        try {
            MatrixStack matrices = new MatrixStack();
            for (Entity e : client.world.getEntities()) {
                if (e.squaredDistanceTo(ShadowRenderer.cameraX, ShadowRenderer.cameraY, ShadowRenderer.cameraZ) > RANGE * RANGE) continue;
                if (e.isInvisible()) continue;
                double x = MathHelper.lerp(tickDelta, e.lastRenderX, e.getX()) - camX;
                double y = MathHelper.lerp(tickDelta, e.lastRenderY, e.getY()) - camY;
                double z = MathHelper.lerp(tickDelta, e.lastRenderZ, e.getZ()) - camZ;
                try {
                    dispatcher.render(e, x, y, z, e.getYaw(tickDelta), tickDelta, matrices, immediate, 0xF000F0);
                    drawn++;
                } catch (RuntimeException ex) {
                    // A modded renderer that cannot run outside the normal pass: skip it, keep the rest.
                }
            }
            immediate.draw();
        } catch (RuntimeException ex) {
            failed = true;
            AurumMod.LOGGER.error("Entity shadow pass failed; entity shadows disabled for this session", ex);
        } finally {
            dev.impeccable.aurum.render.surface.SurfaceLighting.depthPass = false;
            RenderSystem.polygonOffset(0f, 0f);
            RenderSystem.disablePolygonOffset();
            dispatcher.setRenderShadows(client.options.getEntityShadows().getValue());
            modelView.pop();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.restoreProjectionMatrix();
            client.getFramebuffer().beginWrite(true);
        }
        valid = !failed;
    }

    private static void create(int res) {
        if (texture != 0) {
            GlStateManager._deleteTexture(texture);
            GlStateManager._glDeleteFramebuffers(fbo);
        }
        texture = ShadowRenderer.depthTexture(res);
        fbo = GlStateManager.glGenFramebuffers();
        GlStateManager._glBindFramebuffer(GL30C.GL_FRAMEBUFFER, fbo);
        GL30C.glFramebufferTexture2D(GL30C.GL_FRAMEBUFFER, GL30C.GL_DEPTH_ATTACHMENT, GL11C.GL_TEXTURE_2D, texture, 0);
        GL11C.glDrawBuffer(GL11C.GL_NONE);
        GL11C.glReadBuffer(GL11C.GL_NONE);
        if (GL30C.glCheckFramebufferStatus(GL30C.GL_FRAMEBUFFER) != GL30C.GL_FRAMEBUFFER_COMPLETE) {
            failed = true;
            AurumMod.LOGGER.error("Entity shadow framebuffer incomplete; entity shadows disabled");
        }
        resolution = res;
        MinecraftClient.getInstance().getFramebuffer().beginWrite(true);
    }

    public static float inverseResolution() {
        return 1f / Math.max(1, resolution);
    }
}
