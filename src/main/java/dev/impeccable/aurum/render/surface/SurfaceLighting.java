package dev.impeccable.aurum.render.surface;

import dev.impeccable.aurum.render.terrain.TerrainUniforms;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL20C;

/** Shared world uniforms for model receivers; inventory/UI draws and shadow depth draws stay vanilla. */
public final class SurfaceLighting {
    private static final Matrix4f inverseView = new Matrix4f();
    private static final float[] matrix = new float[16];
    private static boolean world;
    private static int adapters,draws;
    private static long frame;
    public static long frame() { return frame; }
    public static void registerAdapter() { adapters++; }
    public static java.util.Map<String,Object> debugState() { return java.util.Map.of("adapters",adapters,"modelBindsThisFrame",draws,"world",world,"depthPass",depthPass); }
    public static boolean depthPass;
    private SurfaceLighting() {}

    public static void beginWorld(Matrix4f view) {
        inverseView.set(view).m30(0).m31(0).m32(0).invert();
        world = true; draws = 0; frame++;
    }
    public static void endWorld() { world = false; }
    public static boolean active() { return world && !depthPass && TerrainUniforms.mode == 1; }
    public static void upload(int[] locations, int inverseLocation, boolean cached) {
        if (!active()) {
            GL20C.glUniform1i(locations[0], 0);
            return;
        }
        draws++;
        if (cached) {
            GL20C.glUniform1i(locations[0], 1);
            TerrainUniforms.bindModelTextures();
            return;
        }
        // Keep vanilla's lightmap intact for explicit full-bright branches. Models use unit 5 for cloud SDF.
        org.lwjgl.opengl.GL13C.glActiveTexture(0x84C0 + 2);
        int lightmap = org.lwjgl.opengl.GL11C.glGetInteger(org.lwjgl.opengl.GL11C.GL_TEXTURE_BINDING_2D);
        org.lwjgl.opengl.GL13C.glActiveTexture(0x84C0);
        TerrainUniforms.upload(locations, false);
        dev.impeccable.aurum.render.GlUtil.bindTexture(2, lightmap);
        dev.impeccable.aurum.render.GlUtil.bindTexture(5, dev.impeccable.aurum.bake.CloudBaker.texture());
        GL20C.glUniform1i(locations[48], 5);
        inverseView.get(matrix);
        GL20C.glUniformMatrix4fv(inverseLocation, false, matrix);
    }
}
