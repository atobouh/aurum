package dev.impeccable.aurum.render;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.impeccable.aurum.config.AurumConfig;
import dev.impeccable.aurum.director.Atmosphere;
import dev.impeccable.aurum.sky.SkyLut;
import dev.impeccable.aurum.sky.SkyState;
import org.joml.Matrix4f;

/**
 * Draws Aurum's sky in place of vanilla's sky dome: one full-screen triangle, one LUT read per pixel plus an
 * analytic sun disc. Vanilla keeps drawing the stars and moon afterwards.
 */
public final class SkyRenderer {
    private static final int UNIT_LUT = 6;
    private static final Matrix4f invViewProj = new Matrix4f();

    private SkyRenderer() {}

    /** True when Aurum draws the sky this frame (set by the frame director). */
    public static boolean active;

    /** Returns false if nothing was drawn, so the caller can fall back to vanilla. */
    public static boolean draw(Matrix4f view, Matrix4f projection) {
        ShaderProgram program = ShaderManager.get("sky/sky");
        SkyLut lut = SkyState.current();
        if (!active || program == null || lut == null) return false;

        invViewProj.set(projection).mul(view).invert();
        AurumConfig c = AurumConfig.get();

        RenderSystem.disableBlend();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        program.bind();
        program.setMatrix("u_InvViewProj", invViewProj);
        float[] sun = Atmosphere.sunDir;
        program.set3f("u_SunDir", sun[0], sun[1], sun[2]);
        program.set3f("u_SunDisc", lut.sunDisc[0], lut.sunDisc[1], lut.sunDisc[2]);
        program.set1f("u_SunShape", c.sunShape == dev.impeccable.aurum.config.SunShape.SQUARE ? 1f : 0f);
        program.set4f("u_SunGlow", lut.sunGlow[0], lut.sunGlow[1], lut.sunGlow[2], Atmosphere.golden);
        program.set4f("u_ToneParams", c.tonemap.ordinal(), Atmosphere.exposure, 0f, 0f);
        program.set3f("u_WhiteBalance", Atmosphere.whiteBalance[0], Atmosphere.whiteBalance[1], Atmosphere.whiteBalance[2]);
        program.set1i("u_SkyLut", UNIT_LUT);
        program.set1f("u_SkyGrade", dev.impeccable.aurum.director.Atmosphere.skyGrade());
        program.set4f("u_SkyAppearance", Atmosphere.skyAppearance[0], Atmosphere.skyAppearance[1], 0f, 0f);
        program.set4f("u_SkyTone", dev.impeccable.aurum.director.Atmosphere.skyTone[0], dev.impeccable.aurum.director.Atmosphere.skyTone[1], dev.impeccable.aurum.director.Atmosphere.golden, 0f);
        GlUtil.bindTexture(UNIT_LUT, SkyState.texture());
        GlUtil.drawFullscreenTriangle();
        ShaderProgram.unbind();
        RenderSystem.enableDepthTest();
        return true;
    }
}
