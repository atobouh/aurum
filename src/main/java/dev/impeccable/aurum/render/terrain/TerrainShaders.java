package dev.impeccable.aurum.render.terrain;

import dev.impeccable.aurum.AurumMod;
import dev.impeccable.aurum.ChatNotices;
import me.jellysquid.mods.sodium.client.gl.shader.ShaderConstants;
import me.jellysquid.mods.sodium.client.gl.shader.ShaderLoader;
import me.jellysquid.mods.sodium.client.gl.shader.ShaderParser;
import me.jellysquid.mods.sodium.client.render.chunk.shader.ChunkShaderBindingPoints;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.lwjgl.opengl.GL20C;
import org.lwjgl.opengl.GL30C;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/**
 * Decides whether Aurum's terrain shader replaces Sodium's, and supplies its source.
 *
 * <p>Two gates, both checked before Sodium compiles anything:
 * <ol>
 *   <li><b>Fingerprints.</b> Every Sodium shader file the replacement depends on must match the SHA-256 of
 *   Sodium 0.5.13's copy. Any Sodium build with identical shader code works; anything else keeps vanilla terrain.</li>
 *   <li><b>Test compile.</b> Both program variants Sodium will build are compiled and linked with this driver
 *   first. A driver that rejects them keeps vanilla terrain instead of crashing inside Sodium.</li>
 * </ol>
 */
public final class TerrainShaders {
    private static final Map<String, String> EXPECTED = Map.of(
            "blocks/block_layer_opaque.vsh", "6b905d4e9462325b4d5a8a244c3045de6dbe23f0bb316552c480cbfcc6ffba11",
            "blocks/block_layer_opaque.fsh", "b3c4882ce35a9715648ce71d870021ec5be7b44597af62c109bbdaad89045f11",
            "include/fog.glsl", "78937d50fa29af2f44c238c4086cf5cc6ec20c9c2893338d2f67dfe7e2b86798",
            "include/chunk_vertex.glsl", "812e06bbd4069e7879ff1be91843d38a38850fca37947090fde74e1844a256a3",
            "include/chunk_matrices.glsl", "84a67cab782c023f46c754cd50a6dc894cc9bafe689734f7a8e7cc2c9698cca4",
            "include/chunk_material.glsl", "18a5dc878965244d36adcc84fe59b6d446ac97f951a46a7326d38ba1075d3ab2");

    private static boolean installed;
    private static String reason = "not checked yet";

    private TerrainShaders() {}

    public static boolean installed() {
        return installed;
    }

    public static String reason() {
        return reason;
    }

    /** Called on the render thread at client start, before any world (and so any Sodium program) exists. */
    public static void decide() {
        installed = false;
        if (FabricLoader.getInstance().isModLoaded("windblown")) {
            reason = "Windblown edits the same shader";
            return;
        }
        for (Map.Entry<String, String> e : EXPECTED.entrySet()) {
            String actual = sha256(readSodium(e.getKey()));
            if (!e.getValue().equals(actual)) {
                reason = "Sodium's " + e.getKey() + " differs from 0.5.13";
                AurumMod.LOGGER.warn("Terrain shader not installed: {} (sha256 {})", reason, actual);
                ChatNotices.queue(Text.literal("[Aurum] ").formatted(Formatting.GOLD).append(Text.literal(
                        "This Sodium build's terrain shader differs from 0.5.13, so terrain lighting and waving plants"
                                + " stay off. Sodium 0.5.13 is recommended.").formatted(Formatting.YELLOW)));
                return;
            }
        }
        // Must be marked installed before the test compile: the test resolves #imports through ShaderLoader too.
        installed = true;
        String error = testCompile();
        if (error != null) {
            installed = false;
            reason = "driver rejected the terrain shader";
            AurumMod.LOGGER.error("Terrain shader test compile failed; keeping Sodium's shader.\n{}", error);
            ChatNotices.queue(Text.literal("[Aurum] ").formatted(Formatting.GOLD).append(Text.literal(
                    "Your graphics driver rejected Aurum's terrain shader; terrain stays vanilla. See latest.log.")
                    .formatted(Formatting.RED)));
            return;
        }
        reason = "installed";
        AurumMod.LOGGER.info("Aurum terrain shader installed over Sodium's");
    }

    /** Replacement source for Sodium's terrain shader files, or null to leave the original. */
    public static String replacement(Identifier id) {
        if (!installed || !"sodium".equals(id.getNamespace())) return null;
        String path = id.getPath();
        if (!path.equals("blocks/block_layer_opaque.vsh") && !path.equals("blocks/block_layer_opaque.fsh")) return null;
        String file = path.replace("blocks/", "terrain/");
        try (InputStream in = TerrainShaders.class.getResourceAsStream("/assets/aurum/shaders/" + file)) {
            if (in == null) return null;
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }

    private static String testCompile() {
        for (boolean discard : new boolean[]{false, true}) {
            ShaderConstants.Builder b = ShaderConstants.builder();
            b.add("USE_FOG");
            b.add("USE_FOG_SMOOTH");
            if (discard) b.add("USE_FRAGMENT_DISCARD");
            b.add("USE_VERTEX_COMPRESSION");
            ShaderConstants constants = b.build();
            String vsh = ShaderParser.parseShader(ShaderLoader.getShaderSource(new Identifier("sodium", "blocks/block_layer_opaque.vsh")), constants);
            String fsh = ShaderParser.parseShader(ShaderLoader.getShaderSource(new Identifier("sodium", "blocks/block_layer_opaque.fsh")), constants);

            int vs = compile(GL20C.GL_VERTEX_SHADER, vsh);
            if (vs < 0) return GL20C.glGetShaderInfoLog(-vs) + cleanup(-vs);
            int fs = compile(GL20C.GL_FRAGMENT_SHADER, fsh);
            if (fs < 0) return GL20C.glGetShaderInfoLog(-fs) + cleanup(-fs) + cleanup(vs);

            int program = GL20C.glCreateProgram();
            GL20C.glAttachShader(program, vs);
            GL20C.glAttachShader(program, fs);
            GL20C.glBindAttribLocation(program, ChunkShaderBindingPoints.ATTRIBUTE_POSITION, "a_Position");
            GL20C.glBindAttribLocation(program, ChunkShaderBindingPoints.ATTRIBUTE_COLOR, "a_Color");
            GL20C.glBindAttribLocation(program, ChunkShaderBindingPoints.ATTRIBUTE_TEXTURE, "a_TexCoord");
            GL20C.glBindAttribLocation(program, ChunkShaderBindingPoints.ATTRIBUTE_LIGHT_MATERIAL_INDEX, "a_LightAndData");
            GL30C.glBindFragDataLocation(program, ChunkShaderBindingPoints.FRAG_COLOR, "fragColor");
            GL20C.glLinkProgram(program);
            boolean ok = GL20C.glGetProgrami(program, GL20C.GL_LINK_STATUS) == GL20C.GL_TRUE;
            String log = ok ? null : GL20C.glGetProgramInfoLog(program);
            if (ok) {
                // Sodium binds these by name and throws if one is missing or optimised out.
                for (String u : List.of("u_ModelViewMatrix", "u_ProjectionMatrix", "u_RegionOffset", "u_TexCoordShrink",
                        "u_BlockTex", "u_LightTex", "u_FogColor", "u_FogShape", "u_FogStart", "u_FogEnd")) {
                    if (GL20C.glGetUniformLocation(program, u) < 0) {
                        log = "uniform " + u + " is not active";
                        break;
                    }
                }
            }
            GL20C.glDeleteProgram(program);
            GL20C.glDeleteShader(vs);
            GL20C.glDeleteShader(fs);
            if (log != null) return log;
        }
        return null;
    }

    /** Returns the shader id, or -id on compile failure (caller reads the log). */
    private static int compile(int type, String src) {
        int s = GL20C.glCreateShader(type);
        GL20C.glShaderSource(s, src);
        GL20C.glCompileShader(s);
        return GL20C.glGetShaderi(s, GL20C.GL_COMPILE_STATUS) == GL20C.GL_TRUE ? s : -s;
    }

    private static String cleanup(int shader) {
        GL20C.glDeleteShader(shader);
        return "";
    }

    private static String readSodium(String path) {
        try (InputStream in = ShaderLoader.class.getResourceAsStream("/assets/sodium/shaders/" + path)) {
            if (in == null) return "";
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("\r", "");
        } catch (IOException e) {
            return "";
        }
    }

    private static String sha256(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
