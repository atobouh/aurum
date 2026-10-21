package dev.impeccable.aurum.render;

import com.mojang.blaze3d.platform.GlStateManager;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL12C;
import org.lwjgl.opengl.GL30C;

/** Small GL helpers that keep Minecraft's GlStateManager cache consistent with what we change. */
public final class GlUtil {
    private static int emptyVao;

    private GlUtil() {}

    /** Minecraft's NativeImage uploads leave row length / skip values set; reset before our own uploads. */
    public static void resetUnpack() {
        GlStateManager._pixelStore(GL11C.GL_UNPACK_ROW_LENGTH, 0);
        GlStateManager._pixelStore(GL11C.GL_UNPACK_SKIP_PIXELS, 0);
        GlStateManager._pixelStore(GL11C.GL_UNPACK_SKIP_ROWS, 0);
        GlStateManager._pixelStore(GL11C.GL_UNPACK_ALIGNMENT, 4);
    }

    /** Creates a 2D texture with the given filtering and clamp-to-edge wrapping. Leaves it bound on the active unit. */
    public static int createTexture(int internalFormat, int width, int height, int format, int type, int filter) {
        int id = GlStateManager._genTexture();
        GlStateManager._bindTexture(id);
        GlStateManager._texParameter(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MIN_FILTER, filter);
        GlStateManager._texParameter(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MAG_FILTER, filter == GL11C.GL_LINEAR ? GL11C.GL_LINEAR : GL11C.GL_NEAREST);
        GlStateManager._texParameter(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_WRAP_S, GL12C.GL_CLAMP_TO_EDGE);
        GlStateManager._texParameter(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_WRAP_T, GL12C.GL_CLAMP_TO_EDGE);
        GL11C.glTexImage2D(GL11C.GL_TEXTURE_2D, 0, internalFormat, width, height, 0, format, type, (java.nio.ByteBuffer) null);
        return id;
    }

    /** Binds a texture to a unit and leaves unit 0 active, as vanilla code expects. */
    public static void bindTexture(int unit, int texture) {
        GlStateManager._activeTexture(GL13C_TEXTURE0 + unit);
        GlStateManager._bindTexture(texture);
        GlStateManager._activeTexture(GL13C_TEXTURE0);
    }

    private static final int GL13C_TEXTURE0 = 0x84C0;

    /** Draws a full-screen triangle generated from gl_VertexID. A VAO must be bound in core profile. */
    public static void drawFullscreenTriangle() {
        if (emptyVao == 0) emptyVao = GL30C.glGenVertexArrays();
        int prev = GL11C.glGetInteger(GL30C.GL_VERTEX_ARRAY_BINDING);
        GL30C.glBindVertexArray(emptyVao);
        GL11C.glDrawArrays(GL11C.GL_TRIANGLES, 0, 3);
        GL30C.glBindVertexArray(prev);
    }
}
