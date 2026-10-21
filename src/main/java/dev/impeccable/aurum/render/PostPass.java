package dev.impeccable.aurum.render;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.impeccable.aurum.config.AurumConfig;
import dev.impeccable.aurum.director.Atmosphere;
import dev.impeccable.aurum.director.FogDirector;
import dev.impeccable.aurum.render.terrain.TerrainUniforms;
import dev.impeccable.aurum.sky.SkyState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL30C;

/**
 * The combined post pass and its helpers.
 *
 * <p>Per frame, after the hand is drawn:
 * <ol>
 *   <li>Copy the scene colour (the pass cannot read and write the same texture).</li>
 *   <li>Bounce gather at 32x16 and a 1x1 temporal blend (the Badlands glow colour, used next frame by terrain).</li>
 *   <li>Sky extras at reduced resolution: clouds, god rays, flare occlusion, bloom ({@link SkyExtras}).</li>
 *   <li>The composite: fog, edge fade, clouds, rays, bloom, flare and grade in one full-screen draw into the
 *       main framebuffer.</li>
 * </ol>
 * The hand is drawn into its own depth texture (swapped in around renderHand) so world depth survives for fog
 * without a depth copy, and the hand is never fogged.
 */
public final class PostPass {
    private static final int GATHER_W = 32, GATHER_H = 16;
    private static final int UNIT_A = 6, UNIT_B = 7, UNIT_C = 8, UNIT_D = 9;

    private static int width, height;
    private static int sceneTex, sceneFbo;
    private static int handDepthTex;
    private static int depthAttachmentPoint = GL30C.GL_DEPTH_ATTACHMENT;
    private static int gatherTex, gatherFbo, gatherLevels;
    private static final int[] bounceTex = new int[2], bounceFbo = new int[2];
    private static int bounceIndex;

    private static boolean handSwapped, handValid;
    private static boolean checkedHandFbo, handSwapBroken;
    private static int handDepthFormat;

    /** World matrices captured when WorldRenderer.render starts (the hand later changes the projection). */
    private static final Matrix4f worldProjection = new Matrix4f();
    private static final Matrix4f worldView = new Matrix4f();
    private static final Matrix4f invProj = new Matrix4f();
    private static final Matrix4f invView = new Matrix4f();

    private PostPass() {}

    public static void captureWorldMatrices(Matrix4f view, Matrix4f projection) {
        worldView.set(view);
        worldProjection.set(projection);
        handValid = false;
    }

    private static boolean enabled() {
        return Pipeline.isActive() && MinecraftClient.getInstance().world != null && ShaderManager.get("post/composite") != null;
    }

    /** Called right before renderHand: give the hand its own depth buffer. */
    public static void beforeHand(Framebuffer main) {
        if (!enabled() || handSwapBroken) return;
        ensureTargets(main);
        GlStateManager._glBindFramebuffer(GL30C.GL_FRAMEBUFFER, main.fbo);
        GL30C.glFramebufferTexture2D(GL30C.GL_FRAMEBUFFER, depthAttachmentPoint, GL11C.GL_TEXTURE_2D, handDepthTex, 0);
        if (!checkedHandFbo) {
            checkedHandFbo = true;
            int status = GL30C.glCheckFramebufferStatus(GL30C.GL_FRAMEBUFFER);
            if (status != GL30C.GL_FRAMEBUFFER_COMPLETE) {
                dev.impeccable.aurum.AurumMod.LOGGER.error("Hand depth framebuffer incomplete (0x{}); hand depth swap disabled", Integer.toHexString(status));
                GL30C.glFramebufferTexture2D(GL30C.GL_FRAMEBUFFER, depthAttachmentPoint, GL11C.GL_TEXTURE_2D, main.getDepthAttachment(), 0);
                handSwapBroken = true;
                return;
            }
            dev.impeccable.aurum.AurumMod.LOGGER.info("Hand depth framebuffer OK (depth format 0x{})", Integer.toHexString(handDepthFormat));
        }
        handSwapped = true;
    }

    /** Called right after renderHand: put the world depth back. */
    public static void afterHand(Framebuffer main) {
        if (!handSwapped) return;
        handSwapped = false;
        GlStateManager._glBindFramebuffer(GL30C.GL_FRAMEBUFFER, main.fbo);
        GL30C.glFramebufferTexture2D(GL30C.GL_FRAMEBUFFER, depthAttachmentPoint, GL11C.GL_TEXTURE_2D, main.getDepthAttachment(), 0);
        handValid = true;
    }

    /** Runs the bounce and composite passes. Called at the end of GameRenderer.renderWorld. */
    public static void run(Framebuffer main) {
        if (!enabled()) return;
        ensureTargets(main);
        AurumConfig c = AurumConfig.get();
        int t = GpuTimers.begin(TimerSection.AURUM_POST);

        worldProjection.invert(invProj);
        worldView.invert(invView);

        // 1. Copy scene colour.
        GlStateManager._glBindFramebuffer(GL30C.GL_READ_FRAMEBUFFER, main.fbo);
        GlStateManager._glBindFramebuffer(GL30C.GL_DRAW_FRAMEBUFFER, sceneFbo);
        GL30C.glBlitFramebuffer(0, 0, width, height, 0, 0, width, height, GL11C.GL_COLOR_BUFFER_BIT, GL11C.GL_NEAREST);

        RenderSystem.disableBlend();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);

        boolean aurumWorld = TerrainUniforms.mode == 1;

        // 2. Bounce: gather, mip down to one texel, ease toward it.
        if (aurumWorld && c.bounceEnabled) {
            ShaderProgram gather = ShaderManager.get("post/bounce_gather");
            GlStateManager._glBindFramebuffer(GL30C.GL_FRAMEBUFFER, gatherFbo);
            GlStateManager._viewport(0, 0, GATHER_W, GATHER_H);
            gather.bind();
            gather.set1i("u_Scene", UNIT_A);
            gather.set1i("u_Depth", UNIT_B);
            gather.setMatrix("u_InvProj", invProj);
            org.lwjgl.opengl.GL20C.glUniform2f(gather.uniform("u_TexelStep"), 0.5f / GATHER_W, 0.5f / GATHER_H);
            GlUtil.bindTexture(UNIT_A, sceneTex);
            GlUtil.bindTexture(UNIT_B, main.getDepthAttachment());
            GlUtil.drawFullscreenTriangle();
            GlUtil.bindTexture(UNIT_A, gatherTex);
            GlStateManager._activeTexture(0x84C0 + UNIT_A);
            GL30C.glGenerateMipmap(GL11C.GL_TEXTURE_2D);
            GlStateManager._activeTexture(0x84C0);

            int prev = bounceIndex, next = 1 - bounceIndex;
            ShaderProgram blend = ShaderManager.get("post/bounce_blend");
            GlStateManager._glBindFramebuffer(GL30C.GL_FRAMEBUFFER, bounceFbo[next]);
            GlStateManager._viewport(0, 0, 1, 1);
            blend.bind();
            blend.set1i("u_Gather", UNIT_A);
            blend.set1i("u_Previous", UNIT_C);
            blend.set1f("u_GatherLod", gatherLevels);
            blend.set1f("u_Blend", 0.04f);
            GlUtil.bindTexture(UNIT_C, bounceTex[prev]);
            GlUtil.drawFullscreenTriangle();
            bounceIndex = next;
            AurumTextures.setBounce(bounceTex[next]);
        }

        // 3. Sky extras: clouds, god rays, flare occlusion, bloom.
        if (aurumWorld) {
            SkyExtras.run(sceneTex, main.getDepthAttachment(), worldView, worldProjection, invProj, invView);
        } else {
            SkyExtras.cloudsDrawn = SkyExtras.raysDrawn = SkyExtras.flareOn = SkyExtras.bloomDrawn = false;
        }

        // 4. Composite into the main framebuffer. Depth is detached so sampling it is not a feedback loop.
        ShaderProgram comp = ShaderManager.get("post/composite");
        GlStateManager._glBindFramebuffer(GL30C.GL_FRAMEBUFFER, main.fbo);
        GL30C.glFramebufferTexture2D(GL30C.GL_FRAMEBUFFER, depthAttachmentPoint, GL11C.GL_TEXTURE_2D, 0, 0);
        GlStateManager._viewport(0, 0, width, height);
        comp.bind();
        comp.set1i("u_Scene", UNIT_A);
        comp.set1i("u_Depth", UNIT_B);
        comp.set1i("u_HandDepth", UNIT_C);
        comp.set1i("u_Bounce", UNIT_D);
        comp.set1i("u_SkyLut", 10);
        // GlStateManager tracks only 12 units; 2-5 are free here (vanilla and terrain rebind theirs before drawing).
        comp.set1i("u_Clouds", 11);
        comp.set1i("u_Rays", 3);
        comp.set1i("u_Bloom", 4);
        comp.set1i("u_Flare", 5);
        comp.set1i("u_Volumetric", 2);
        float[] cs = SkyExtras.cloudSlab;
        comp.set4f("u_CloudSlab", cs[0], cs[1], cs[2], SkyExtras.cloudsDrawn ? cs[3] : 0f);
        float[] ss = SkyExtras.sunScreen;
        comp.set4f("u_SunScreen", ss[0], ss[1], ss[2], ss[3]);
        comp.set4f("u_Extras", SkyExtras.rayStrength, SkyExtras.bloomStrength, SkyExtras.flareStrength, SkyExtras.volumetricStrength);
        comp.set4f("u_Volumetric2", dev.impeccable.aurum.render.shadow.ShadowRenderer.range() * 0.95f, FogDirector.enclosed, 0f, 0f);
        comp.setMatrix("u_InvProj", invProj);
        comp.setMatrix("u_InvView", invView);
        float[] sun = Atmosphere.sunDir;
        comp.set3f("u_SunDir", sun[0], sun[1], sun[2]);
        dev.impeccable.aurum.sky.SkyLut glut = SkyState.current();
        if (glut != null) comp.set4f("u_SunGlow", glut.sunGlow[0], glut.sunGlow[1], glut.sunGlow[2], Atmosphere.golden);
        float cameraHeight = (float) Math.max(-8.0, MinecraftClient.getInstance().gameRenderer.getCamera().getPos().y - 63.0);
        comp.set4f("u_Fog", FogDirector.distanceDensity, FogDirector.heightDensity, 0.07f, cameraHeight);
        float far = MinecraftClient.getInstance().gameRenderer.getViewDistance();
        float bounceFog = c.bounceEnabled ? 0.25f * c.bounceStrength / 100f : 0f;
        comp.set4f("u_Fog2", FogDirector.cave, far * 0.80f, far * 0.98f, bounceFog);
        boolean fog = aurumWorld && c.fogEnabled && SkyState.current() != null;
        boolean edge = aurumWorld && c.edgeFade && SkyState.current() != null;
        comp.set4f("u_Flags", fog ? 1f : 0f, edge ? 1f : 0f, handValid ? 1f : 0f, 1f);
        comp.set4f("u_Grade", c.saturation / 100f, c.vibrance / 100f, c.contrast / 100f, 0f);
        comp.set4f("u_ToneParams", c.tonemap.ordinal(), Atmosphere.exposure, 0f, 0f);
        comp.set3f("u_WhiteBalance", Atmosphere.whiteBalance[0], Atmosphere.whiteBalance[1], Atmosphere.whiteBalance[2]);
        GlUtil.bindTexture(UNIT_A, sceneTex);
        GlUtil.bindTexture(UNIT_B, main.getDepthAttachment());
        GlUtil.bindTexture(UNIT_C, handDepthTex);
        GlUtil.bindTexture(UNIT_D, AurumTextures.bounce());
        GlUtil.bindTexture(10, SkyState.texture());
        int clear = AurumTextures.clear();
        GlUtil.bindTexture(11, SkyExtras.cloudsDrawn ? SkyExtras.cloudTexture() : clear);
        GlUtil.bindTexture(3, SkyExtras.raysDrawn ? SkyExtras.raysTexture() : clear);
        GlUtil.bindTexture(4, SkyExtras.bloomDrawn ? SkyExtras.bloomTexture() : clear);
        GlUtil.bindTexture(5, SkyExtras.flareOn ? SkyExtras.flareTexture() : clear);
        GlUtil.bindTexture(2, SkyExtras.volumetricDrawn ? SkyExtras.volumetricTexture() : clear);
        GlUtil.drawFullscreenTriangle();
        ShaderProgram.unbind();
        GL30C.glFramebufferTexture2D(GL30C.GL_FRAMEBUFFER, depthAttachmentPoint, GL11C.GL_TEXTURE_2D, main.getDepthAttachment(), 0);

        // Unbind our textures from the high units and restore the state vanilla expects next.
        for (int unit : new int[]{UNIT_A, UNIT_B, UNIT_C, UNIT_D, 10, 11, 2, 3, 4, 5}) GlUtil.bindTexture(unit, 0);
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        main.beginWrite(true);
        GpuTimers.end(t);
    }

    private static void ensureTargets(Framebuffer main) {
        if (sceneTex != 0 && width == main.textureWidth && height == main.textureHeight) return;
        delete();
        width = main.textureWidth;
        height = main.textureHeight;

        // Linear so the bloom prefilter can average 2x2 pixels per tap; full-resolution reads hit texel centres.
        sceneTex = GlUtil.createTexture(GL11C.GL_RGBA8, width, height, GL11C.GL_RGBA, GL11C.GL_UNSIGNED_BYTE, GL11C.GL_LINEAR);
        sceneFbo = fbo(sceneTex);

        // Hand depth matches the main depth format (some mods enable a stencil buffer).
        GlStateManager._bindTexture(main.getDepthAttachment());
        int format = GL11C.glGetTexLevelParameteri(GL11C.GL_TEXTURE_2D, 0, GL11C.GL_TEXTURE_INTERNAL_FORMAT);
        handDepthFormat = format;
        boolean stencil = format == GL30C.GL_DEPTH24_STENCIL8 || format == GL30C.GL_DEPTH32F_STENCIL8;
        depthAttachmentPoint = stencil ? GL30C.GL_DEPTH_STENCIL_ATTACHMENT : GL30C.GL_DEPTH_ATTACHMENT;
        handDepthTex = GlUtil.createTexture(format, width, height,
                stencil ? GL30C.GL_DEPTH_STENCIL : GL11C.GL_DEPTH_COMPONENT,
                stencil ? GL30C.GL_UNSIGNED_INT_24_8 : GL11C.GL_FLOAT, GL11C.GL_NEAREST);

        gatherTex = GlUtil.createTexture(GL30C.GL_RGBA16F, GATHER_W, GATHER_H, GL11C.GL_RGBA, GL11C.GL_FLOAT, GL11C.GL_LINEAR);
        GlStateManager._texParameter(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MIN_FILTER, GL11C.GL_LINEAR_MIPMAP_LINEAR);
        GL30C.glGenerateMipmap(GL11C.GL_TEXTURE_2D);
        gatherLevels = (int) Math.floor(Math.log(Math.max(GATHER_W, GATHER_H)) / Math.log(2));
        gatherFbo = fbo(gatherTex);
        for (int i = 0; i < 2; i++) {
            bounceTex[i] = GlUtil.createTexture(GL30C.GL_RGBA16F, 1, 1, GL11C.GL_RGBA, GL11C.GL_FLOAT, GL11C.GL_NEAREST);
            bounceFbo[i] = fbo(bounceTex[i]);
            GL11C.glClearColor(0.2f, 0.2f, 0.2f, 1f);
            GL11C.glClear(GL11C.GL_COLOR_BUFFER_BIT);
        }
        GlStateManager._bindTexture(0);
        main.beginWrite(false);
    }

    private static int fbo(int colorTex) {
        int fbo = GlStateManager.glGenFramebuffers();
        GlStateManager._glBindFramebuffer(GL30C.GL_FRAMEBUFFER, fbo);
        GL30C.glFramebufferTexture2D(GL30C.GL_FRAMEBUFFER, GL30C.GL_COLOR_ATTACHMENT0, GL11C.GL_TEXTURE_2D, colorTex, 0);
        return fbo;
    }

    private static void delete() {
        if (sceneTex == 0) return;
        GlStateManager._deleteTexture(sceneTex);
        GlStateManager._deleteTexture(handDepthTex);
        GlStateManager._deleteTexture(gatherTex);
        GlStateManager._glDeleteFramebuffers(sceneFbo);
        GlStateManager._glDeleteFramebuffers(gatherFbo);
        for (int i = 0; i < 2; i++) {
            GlStateManager._deleteTexture(bounceTex[i]);
            GlStateManager._glDeleteFramebuffers(bounceFbo[i]);
        }
        AurumTextures.setBounce(0);
        sceneTex = 0;
    }
}
