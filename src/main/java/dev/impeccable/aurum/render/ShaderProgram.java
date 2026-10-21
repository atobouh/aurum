package dev.impeccable.aurum.render;

import com.mojang.blaze3d.platform.GlStateManager;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import org.lwjgl.opengl.GL20C;

/** A linked GL program with a cached uniform lookup. Binding goes through GlStateManager to keep its cache honest. */
public final class ShaderProgram {
    private final String name;
    private int handle;
    private final Object2IntOpenHashMap<String> uniforms = new Object2IntOpenHashMap<>();

    ShaderProgram(String name, int handle) {
        this.name = name;
        this.handle = handle;
        this.uniforms.defaultReturnValue(Integer.MIN_VALUE);
    }

    public String name() {
        return this.name;
    }

    public void bind() {
        GlStateManager._glUseProgram(this.handle);
    }

    /** Unbinds and tells vanilla its program is no longer bound, so its next draw rebinds it. */
    public static void unbind() {
        GlStateManager._glUseProgram(0);
        dev.impeccable.aurum.mixin.ShaderProgramAccessor.aurum$setActiveProgramGlRef(-1);
    }

    public void set1i(String name, int v) {
        org.lwjgl.opengl.GL20C.glUniform1i(this.uniform(name), v);
    }

    public void set1f(String name, float v) {
        org.lwjgl.opengl.GL20C.glUniform1f(this.uniform(name), v);
    }

    public void set3f(String name, float x, float y, float z) {
        org.lwjgl.opengl.GL20C.glUniform3f(this.uniform(name), x, y, z);
    }

    public void set4f(String name, float x, float y, float z, float w) {
        org.lwjgl.opengl.GL20C.glUniform4f(this.uniform(name), x, y, z, w);
    }

    private final float[] matrixScratch = new float[16];

    public void setMatrix(String name, org.joml.Matrix4f m) {
        m.get(this.matrixScratch);
        org.lwjgl.opengl.GL20C.glUniformMatrix4fv(this.uniform(name), false, this.matrixScratch);
    }

    /** Returns -1 when the uniform is absent or optimised out; GL ignores writes to -1. */
    public int uniform(String uniformName) {
        int loc = this.uniforms.getInt(uniformName);
        if (loc == Integer.MIN_VALUE) {
            loc = GL20C.glGetUniformLocation(this.handle, uniformName);
            this.uniforms.put(uniformName, loc);
        }
        return loc;
    }

    public void delete() {
        if (this.handle != 0) {
            GL20C.glDeleteProgram(this.handle);
            this.handle = 0;
        }
    }
}
