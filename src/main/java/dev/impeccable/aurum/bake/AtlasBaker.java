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
 * Builds the normal atlas once per resource reload, parallel to the block atlas (spec feature 2):
 * <ul>
 *   <li>RG: tangent-space slope from the texture's luminance, per texel, so pixel-art edges stay crisp.</li>
 *   <li>B: a faint per-texel "coating" variation.</li>
 *   <li>A: motion material id for waving plants (see {@link MaterialClassifier}).</li>
 * </ul>
 * In game this costs one texture read per terrain pixel. Nothing is recomputed per frame.
 */
public final class AtlasBaker {
    /** Luminance difference to slope. Strength is scaled again in the shader. */
    private static final float SLOPE = 2.5f;

    private static int texture;
    private static int width, height;

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
        list.parallelStream().forEach(sprite -> {
            int material = materials.getOrDefault(sprite.getContents().getId(), 0);
            bakeSprite(src, dst, fw, sprite.getX(), sprite.getY(),
                    sprite.getContents().getWidth(), sprite.getContents().getHeight(), material);
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
        AurumMod.LOGGER.info("Baked normal atlas {}x{} ({} sprites, {} mip levels) in {} ms", w, h, list.size(), levels,
                (System.nanoTime() - start) / 1_000_000);
    }

    private static void bakeSprite(ByteBuffer src, ByteBuffer dst, int atlasWidth, int x0, int y0, int sw, int sh, int material) {
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
                int o = ((y0 + y) * atlasWidth + (x0 + x)) * 4;
                dst.put(o, (byte) Math.round((sx * 0.5f + 0.5f) * 255f));
                dst.put(o + 1, (byte) Math.round((sy * 0.5f + 0.5f) * 255f));
                dst.put(o + 2, (byte) coat);
                dst.put(o + 3, (byte) material);
            }
        }
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
