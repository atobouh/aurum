package dev.impeccable.aurum.bake;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.impeccable.aurum.AurumMod;
import dev.impeccable.aurum.mixin.SpriteAtlasTextureAccessor;
import dev.impeccable.aurum.render.AurumTextures;
import dev.impeccable.aurum.render.GlUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.util.Identifier;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL12C;
import org.lwjgl.opengl.GL30C;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Builds two atlases once per resource reload, parallel to the block atlas (spec feature 2, LabPBR 1.3):
 * <ul>
 *   <li>Normal atlas RG: tangent-space slope, from the resource pack's {@code _n} normal map when it has one
 *       (LabPBR), otherwise generated from the texture's luminance so pixel-art edges stay crisp.</li>
 *   <li>Normal atlas B: the pack's ambient occlusion, or a faint per-texel "coating" variation.</li>
 *   <li>Normal atlas A: bits 0-3 motion material id ({@link MaterialClassifier}), bits 4-5 sprite size
 *       (16 &lt;&lt; n texels), bit 6 "pack PBR data", bit 7 "aligned square sprite" (parallax can wrap in it).</li>
 *   <li>PBR atlas: R smoothness, G F0 / metal, B emission, A height (LabPBR {@code _s} and {@code _n} alpha);
 *       neutral values where the pack has none.</li>
 * </ul>
 * In game this costs two texture reads per terrain pixel. Nothing is recomputed per frame.
 */
public final class AtlasBaker {
    /** Luminance difference to slope. Strength is scaled again in the shader. */
    private static final float SLOPE = 2.5f;

    private static int texture, pbrTexture;
    private static int width, height;
    /** True when the loaded resource packs ship LabPBR data for at least one block texture. */
    public static boolean packHasPbr;

    /** One sprite's LabPBR maps, resampled to the sprite's size. Null maps mean "not provided". */
    private record PackMaps(int[] normal, int[] specular) {}

    private AtlasBaker() {}

    public static void bake() {
        RenderSystem.assertOnRenderThread();
        long start = System.nanoTime();
        MinecraftClient client = MinecraftClient.getInstance();
        SpriteAtlasTexture atlas = client.getBakedModelManager().getAtlas(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE);

        GlStateManager._bindTexture(atlas.getGlId());
        int w = GL11C.glGetTexLevelParameteri(GL11C.GL_TEXTURE_2D, 0, GL11C.GL_TEXTURE_WIDTH);
        int h = GL11C.glGetTexLevelParameteri(GL11C.GL_TEXTURE_2D, 0, GL11C.GL_TEXTURE_HEIGHT);
        int maxLevel = GL11C.glGetTexParameteri(GL11C.GL_TEXTURE_2D, GL12C.GL_TEXTURE_MAX_LEVEL);
        if (w <= 0 || h <= 0) {
            AurumMod.LOGGER.warn("Block atlas has no size yet; normal atlas not built");
            return;
        }
        ByteBuffer src = MemoryUtil.memAlloc(w * h * 4);
        GlStateManager._pixelStore(GL11C.GL_PACK_ALIGNMENT, 4);
        GL11C.glGetTexImage(GL11C.GL_TEXTURE_2D, 0, GL11C.GL_RGBA, GL11C.GL_UNSIGNED_BYTE, src);

        Map<Identifier, Sprite> sprites = ((SpriteAtlasTextureAccessor) atlas).aurum$getSprites();
        Map<Identifier, Integer> materials = MaterialClassifier.classify();
        // Water surfaces get Aurum's water shading (translucent pass).
        materials.put(new Identifier("minecraft", "block/water_still"), MaterialClassifier.WATER);
        materials.put(new Identifier("minecraft", "block/water_flow"), MaterialClassifier.WATER);
        ByteBuffer dst = MemoryUtil.memCalloc(w * h * 4);
        // Untouched texels (gaps between sprites): flat normal, no material.
        for (int i = 0; i < w * h; i++) {
            dst.put(i * 4, (byte) 128);
            dst.put(i * 4 + 1, (byte) 128);
            dst.put(i * 4 + 2, (byte) 128);
        }

        List<Sprite> list = new ArrayList<>(sprites.values());
        final int fw = w;
        // LabPBR maps from the resource packs, read once per sprite.
        Map<Sprite, PackMaps> pack = new java.util.concurrent.ConcurrentHashMap<>();
        if (dev.impeccable.aurum.config.AurumConfig.get().labPbr) {
            net.minecraft.resource.ResourceManager rm = client.getResourceManager();
            list.parallelStream().forEach(sprite -> {
                PackMaps m = loadPack(rm, sprite);
                if (m != null) pack.put(sprite, m);
            });
        }
        packHasPbr = !pack.isEmpty();
        // Packs disagree on the normal map's green direction (OpenGL vs DirectX); pick the one that agrees with
        // the luminance slopes on this pack's own textures.
        final float flipY = packHasPbr ? normalYSign(src, fw, pack) : 1f;
        ByteBuffer pbr = MemoryUtil.memAlloc(w * h * 4);
        for (int i = 0; i < w * h; i++) {
            // Neutral: rough, dielectric (F0 10/255 = 4 %), no emission, flat (height at the top).
            pbr.put(i * 4, (byte) 0).put(i * 4 + 1, (byte) 10).put(i * 4 + 2, (byte) 0).put(i * 4 + 3, (byte) 255);
        }
        java.util.concurrent.atomic.AtomicInteger aligned = new java.util.concurrent.atomic.AtomicInteger();
        list.parallelStream().forEach(sprite -> {
            int material = materials.getOrDefault(sprite.getContents().getId(), 0);
            int sw = sprite.getContents().getWidth(), sh = sprite.getContents().getHeight();
            int flags = material & 15;
            int code = Integer.numberOfTrailingZeros(sw) - 4;
            if (sw == sh && Integer.bitCount(sw) == 1 && code >= 0 && code <= 3
                    && sprite.getX() % sw == 0 && sprite.getY() % sh == 0) {
                flags |= code << 4 | 0x80;
                aligned.incrementAndGet();
            }
            PackMaps m = pack.get(sprite);
            if (m != null) flags |= 0x40;
            bakeSprite(src, dst, fw, sprite.getX(), sprite.getY(), sw, sh, flags, m, flipY);
            if (m != null) bakePbr(pbr, fw, sprite.getX(), sprite.getY(), sw, sh, m);
        });

        if (texture == 0 || width != w || height != h) {
            if (texture != 0) GlStateManager._deleteTexture(texture);
            texture = GlUtil.createTexture(GL11C.GL_RGBA8, w, h, GL11C.GL_RGBA, GL11C.GL_UNSIGNED_BYTE, GL11C.GL_NEAREST);
            width = w;
            height = h;
        }
        GlStateManager._bindTexture(texture);
        GlUtil.resetUnpack();
        GL11C.glTexSubImage2D(GL11C.GL_TEXTURE_2D, 0, 0, 0, w, h, GL11C.GL_RGBA, GL11C.GL_UNSIGNED_BYTE, dst);
        // Same mip setup as the block atlas so normals line up with what the terrain shader samples.
        int levels = Math.max(0, maxLevel);
        GlStateManager._texParameter(GL11C.GL_TEXTURE_2D, GL12C.GL_TEXTURE_MAX_LEVEL, levels);
        GlStateManager._texParameter(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MIN_FILTER,
                levels > 0 ? GL11C.GL_NEAREST_MIPMAP_LINEAR : GL11C.GL_NEAREST);
        if (levels > 0) GL30C.glGenerateMipmap(GL11C.GL_TEXTURE_2D);
        GlStateManager._bindTexture(0);

        MemoryUtil.memFree(src);
        MemoryUtil.memFree(dst);
        AurumTextures.setNormalAtlas(texture);

        // PBR atlas: same layout and mips as the block atlas (smoothness and height filter well).
        if (pbrTexture == 0 || width != w || height != h) {
            if (pbrTexture != 0) GlStateManager._deleteTexture(pbrTexture);
            pbrTexture = GlUtil.createTexture(GL11C.GL_RGBA8, w, h, GL11C.GL_RGBA, GL11C.GL_UNSIGNED_BYTE, GL11C.GL_NEAREST);
        }
        GlStateManager._bindTexture(pbrTexture);
        GlUtil.resetUnpack();
        GL11C.glTexSubImage2D(GL11C.GL_TEXTURE_2D, 0, 0, 0, w, h, GL11C.GL_RGBA, GL11C.GL_UNSIGNED_BYTE, pbr);
        GlStateManager._texParameter(GL11C.GL_TEXTURE_2D, GL12C.GL_TEXTURE_MAX_LEVEL, levels);
        GlStateManager._texParameter(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MIN_FILTER,
                levels > 0 ? GL11C.GL_NEAREST_MIPMAP_LINEAR : GL11C.GL_NEAREST);
        if (levels > 0) GL30C.glGenerateMipmap(GL11C.GL_TEXTURE_2D);
        GlStateManager._bindTexture(0);
        MemoryUtil.memFree(pbr);
        AurumTextures.setPbrAtlas(pbrTexture);
        AurumMod.LOGGER.info("LabPBR: {} of {} block textures have pack PBR data{}; {} sprites can use parallax",
                pack.size(), list.size(), packHasPbr ? (flipY < 0 ? " (DirectX normals)" : " (OpenGL normals)") : "",
                aligned.get());
        AurumMod.LOGGER.info("Baked normal atlas {}x{} ({} sprites, {} mip levels) in {} ms", w, h, list.size(), levels,
                (System.nanoTime() - start) / 1_000_000);
    }

    private static void bakeSprite(ByteBuffer src, ByteBuffer dst, int atlasWidth, int x0, int y0, int sw, int sh, int flags,
                                   PackMaps pack, float flipY) {
        float[] lum = new float[sw * sh];
        boolean[] solid = new boolean[sw * sh];
        for (int y = 0; y < sh; y++) {
            for (int x = 0; x < sw; x++) {
                int i = ((y0 + y) * atlasWidth + (x0 + x)) * 4;
                float r = (src.get(i) & 0xFF) / 255f, g = (src.get(i + 1) & 0xFF) / 255f, b = (src.get(i + 2) & 0xFF) / 255f;
                int a = src.get(i + 3) & 0xFF;
                lum[y * sw + x] = 0.2126f * r + 0.7152f * g + 0.0722f * b;
                solid[y * sw + x] = a > 25;
            }
        }
        for (int y = 0; y < sh; y++) {
            for (int x = 0; x < sw; x++) {
                int k = y * sw + x;
                float c = lum[k];
                // Block textures tile, so wrap inside the sprite. Transparent neighbours count as flat.
                float l = sample(lum, solid, sw, sh, x - 1, y, c), r = sample(lum, solid, sw, sh, x + 1, y, c);
                float u = sample(lum, solid, sw, sh, x, y - 1, c), d = sample(lum, solid, sw, sh, x, y + 1, c);
                float sx = clamp(-(r - l) * 0.5f * SLOPE);
                float sy = clamp(-(d - u) * 0.5f * SLOPE);
                int hash = (x0 + x) * 73856093 ^ (y0 + y) * 19349663;
                int coat = 128 + ((hash >>> 8) & 31) - 16;
                if (pack != null && pack.normal != null) {
                    // LabPBR normal: RG = xy in [0,1], z reconstructed; slope = xy / z. B = ambient occlusion.
                    int n = pack.normal[k];
                    float nx = ((n >>> 16) & 0xFF) / 127.5f - 1f, ny = (((n >>> 8) & 0xFF) / 127.5f - 1f) * flipY;
                    float nz = (float) Math.sqrt(Math.max(0.05f, 1f - nx * nx - ny * ny));
                    sx = clamp(nx / nz * 0.5f);
                    sy = clamp(ny / nz * 0.5f);
                    coat = n & 0xFF;
                }
                int o = ((y0 + y) * atlasWidth + (x0 + x)) * 4;
                dst.put(o, (byte) Math.round((sx * 0.5f + 0.5f) * 255f));
                dst.put(o + 1, (byte) Math.round((sy * 0.5f + 0.5f) * 255f));
                dst.put(o + 2, (byte) coat);
                dst.put(o + 3, (byte) flags);
            }
        }
    }

    private static void bakePbr(ByteBuffer pbr, int atlasWidth, int x0, int y0, int sw, int sh, PackMaps m) {
        for (int y = 0; y < sh; y++) {
            for (int x = 0; x < sw; x++) {
                int k = y * sw + x;
                int o = ((y0 + y) * atlasWidth + (x0 + x)) * 4;
                int smooth = 0, f0 = 10, emission = 0, height = 255;
                if (m.specular != null) {
                    int sp = m.specular[k];
                    smooth = (sp >>> 16) & 0xFF;
                    f0 = (sp >>> 8) & 0xFF;
                    int a = (sp >>> 24) & 0xFF;
                    emission = a == 255 ? 0 : a; // LabPBR: alpha 0-254 is emission, 255 means none
                }
                if (m.normal != null) height = (m.normal[k] >>> 24) & 0xFF;
                pbr.put(o, (byte) smooth);
                pbr.put(o + 1, (byte) f0);
                pbr.put(o + 2, (byte) emission);
                pbr.put(o + 3, (byte) height);
            }
        }
    }

    /** Reads {@code <texture>_n.png} and {@code <texture>_s.png} for a sprite, first frame, at the sprite's size. */
    private static PackMaps loadPack(net.minecraft.resource.ResourceManager rm, Sprite sprite) {
        Identifier id = sprite.getContents().getId();
        int sw = sprite.getContents().getWidth(), sh = sprite.getContents().getHeight();
        int[] n = readMap(rm, new Identifier(id.getNamespace(), "textures/" + id.getPath() + "_n.png"), sw, sh);
        int[] s = readMap(rm, new Identifier(id.getNamespace(), "textures/" + id.getPath() + "_s.png"), sw, sh);
        return n == null && s == null ? null : new PackMaps(n, s);
    }

    /** ARGB pixels of the map's first frame, nearest-resampled to sw x sh, or null if the pack has no such map. */
    private static int[] readMap(net.minecraft.resource.ResourceManager rm, Identifier path, int sw, int sh) {
        java.util.Optional<net.minecraft.resource.Resource> res = rm.getResource(path);
        if (res.isEmpty()) return null;
        try (java.io.InputStream in = res.get().getInputStream();
             net.minecraft.client.texture.NativeImage img = net.minecraft.client.texture.NativeImage.read(in)) {
            int iw = img.getWidth();
            int ih = Math.min(img.getHeight(), iw * sh / Math.max(sw, 1)); // animated maps: first frame
            int[] out = new int[sw * sh];
            for (int y = 0; y < sh; y++) {
                for (int x = 0; x < sw; x++) {
                    int abgr = img.getColor(x * iw / sw, y * ih / sh);
                    // NativeImage is ABGR; return ARGB.
                    out[y * sw + x] = (abgr & 0xFF00FF00) | ((abgr & 0xFF) << 16) | ((abgr >>> 16) & 0xFF);
                }
            }
            return out;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * +1 if the pack's normal maps use OpenGL's green direction (matching our luminance slopes), -1 for DirectX.
     * Correlates the pack's green channel with the luminance-derived vertical slope over many textures.
     */
    private static float normalYSign(ByteBuffer src, int atlasWidth, Map<Sprite, PackMaps> pack) {
        double sum = 0;
        int count = 0;
        for (Map.Entry<Sprite, PackMaps> e : pack.entrySet()) {
            if (e.getValue().normal == null || count > 400_000) continue;
            Sprite sp = e.getKey();
            int sw = sp.getContents().getWidth(), sh = sp.getContents().getHeight();
            for (int y = 1; y < sh - 1; y++) {
                for (int x = 0; x < sw; x++) {
                    float u = lumAt(src, atlasWidth, sp.getX() + x, sp.getY() + y - 1);
                    float d = lumAt(src, atlasWidth, sp.getX() + x, sp.getY() + y + 1);
                    float ny = ((e.getValue().normal[y * sw + x] >>> 8) & 0xFF) / 127.5f - 1f;
                    sum += -(d - u) * ny;
                    count++;
                }
            }
        }
        return sum >= 0 ? 1f : -1f;
    }

    private static float lumAt(ByteBuffer src, int atlasWidth, int x, int y) {
        int i = (y * atlasWidth + x) * 4;
        return 0.2126f * (src.get(i) & 0xFF) + 0.7152f * (src.get(i + 1) & 0xFF) + 0.0722f * (src.get(i + 2) & 0xFF);
    }

    private static float sample(float[] lum, boolean[] solid, int w, int h, int x, int y, float fallback) {
        x = Math.floorMod(x, w);
        y = Math.floorMod(y, h);
        int k = y * w + x;
        return solid[k] ? lum[k] : fallback;
    }

    private static float clamp(float v) {
        return Math.max(-1f, Math.min(1f, v));
    }
}
