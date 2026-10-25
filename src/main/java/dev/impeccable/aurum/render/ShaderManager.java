package dev.impeccable.aurum.render;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.impeccable.aurum.AurumMod;
import org.lwjgl.opengl.GL20C;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Loads Aurum's GLSL from {@code assets/aurum/shaders/} on the classpath (not from resource packs, so a pack can
 * never break the pipeline), resolves {@code #include "file.glsl"} from {@code include/}, and compiles every
 * program at startup. One failure disables the whole pipeline with a clear message instead of a black screen.
 */
public final class ShaderManager {
    private static final String ROOT = "/assets/aurum/shaders/";

    /** Programs compiled at startup: name -> {vertex, fragment} paths, then optional defines. */
    private static final String[][] PROGRAMS = {
            {"sky/sky", "post/fullscreen.vsh", "sky/sky.fsh"},
            {"post/composite", "post/fullscreen.vsh", "post/composite.fsh"},
            {"post/clouds_blocky", "post/fullscreen.vsh", "post/clouds.fsh"},
            {"post/clouds_soft", "post/fullscreen.vsh", "post/clouds.fsh", "CLOUD_SOFT"},
            {"post/rays_mask", "post/fullscreen.vsh", "post/rays_mask.fsh"},
            {"post/rays_blur", "post/fullscreen.vsh", "post/rays_blur.fsh"},
            {"post/volumetric", "post/fullscreen.vsh", "post/volumetric.fsh"},
            {"post/ssao", "post/fullscreen.vsh", "post/ssao.fsh"},
            {"post/flare_occlusion", "post/fullscreen.vsh", "post/flare_occlusion.fsh"},
            {"post/bloom_prefilter", "post/fullscreen.vsh", "post/bloom_prefilter.fsh"},
            {"post/bloom_down", "post/fullscreen.vsh", "post/bloom_down.fsh"},
            {"post/bloom_up", "post/fullscreen.vsh", "post/bloom_up.fsh"},
    };

    private static final Map<String, ShaderProgram> programs = new LinkedHashMap<>();

    private ShaderManager() {}

    /** Compiles everything. Returns false (and disables the pipeline) if any program fails. */
    public static boolean compileAll() {
        RenderSystem.assertOnRenderThread();
        deleteAll();
        for (String[] p : PROGRAMS) {
            try {
                Map<String, String> defines = new LinkedHashMap<>();
                for (int i = 3; i < p.length; i++) defines.put(p[i], "1");
                programs.put(p[0], compile(p[0], p[1], p[2], defines));
            } catch (ShaderException e) {
                AurumMod.LOGGER.error("Shader '{}' failed:\n{}", p[0], e.getMessage());
                deleteAll();
                Pipeline.disable("Shader '" + p[0] + "' failed to compile on this driver.");
                return false;
            }
        }
        AurumMod.LOGGER.info("Compiled {} Aurum shader program(s)", programs.size());
        return true;
    }

    public static ShaderProgram get(String name) {
        return programs.get(name);
    }

    public static int programCount() {
        return programs.size();
    }

    private static void deleteAll() {
        programs.values().forEach(ShaderProgram::delete);
        programs.clear();
    }

    static ShaderProgram compile(String name, String vshPath, String fshPath, Map<String, String> defines) throws ShaderException {
        int vsh = compileStage(GL20C.GL_VERTEX_SHADER, vshPath, defines);
        int fsh;
        try {
            fsh = compileStage(GL20C.GL_FRAGMENT_SHADER, fshPath, defines);
        } catch (ShaderException e) {
            GL20C.glDeleteShader(vsh);
            throw e;
        }

        int program = GL20C.glCreateProgram();
        GL20C.glAttachShader(program, vsh);
        GL20C.glAttachShader(program, fsh);
        GL20C.glLinkProgram(program);
        GL20C.glDetachShader(program, vsh);
        GL20C.glDetachShader(program, fsh);
        GL20C.glDeleteShader(vsh);
        GL20C.glDeleteShader(fsh);

        if (GL20C.glGetProgrami(program, GL20C.GL_LINK_STATUS) == GL20C.GL_FALSE) {
            String log = GL20C.glGetProgramInfoLog(program);
            GL20C.glDeleteProgram(program);
            throw new ShaderException("Link failed for " + name + ":\n" + log);
        }
        return new ShaderProgram(name, program);
    }

    private static int compileStage(int type, String path, Map<String, String> defines) throws ShaderException {
        String source = preprocess(path, defines);
        int shader = GL20C.glCreateShader(type);
        GL20C.glShaderSource(shader, source);
        GL20C.glCompileShader(shader);
        if (GL20C.glGetShaderi(shader, GL20C.GL_COMPILE_STATUS) == GL20C.GL_FALSE) {
            String log = GL20C.glGetShaderInfoLog(shader);
            GL20C.glDeleteShader(shader);
            throw new ShaderException("Compile failed for " + path + ":\n" + log + "\n--- source ---\n" + numbered(source));
        }
        return shader;
    }

    /** Inlines includes and inserts defines right after the {@code #version} line. */
    public static String preprocess(String path, Map<String, String> defines) throws ShaderException {
        String body = expandIncludes(path, new HashSet<>());
        int versionEnd = body.startsWith("#version") ? body.indexOf('\n') + 1 : 0;
        StringBuilder header = new StringBuilder();
        defines.forEach((k, v) -> header.append("#define ").append(k).append(' ').append(v).append('\n'));
        return body.substring(0, versionEnd) + header + body.substring(versionEnd);
    }

    private static String expandIncludes(String path, Set<String> seen) throws ShaderException {
        StringBuilder out = new StringBuilder();
        for (String line : read(path).split("\r?\n", -1)) {
            String t = line.trim();
            if (t.startsWith("#include")) {
                int a = t.indexOf('"'), b = t.lastIndexOf('"');
                if (a < 0 || b <= a) throw new ShaderException("Malformed include in " + path + ": " + line);
                String inc = "include/" + t.substring(a + 1, b);
                if (seen.add(inc)) out.append(expandIncludes(inc, seen));
            } else if (t.startsWith("#import <aurum:")) {
                // Same syntax as Sodium's parser, so include files can be shared with the terrain shader.
                String inc = t.substring("#import <aurum:".length(), t.lastIndexOf('>'));
                if (seen.add(inc)) out.append(expandIncludes(inc, seen));
            } else {
                out.append(line).append('\n');
            }
        }
        return out.toString();
    }

    private static String read(String path) throws ShaderException {
        try (InputStream in = ShaderManager.class.getResourceAsStream(ROOT + path)) {
            if (in == null) throw new ShaderException("Shader file not found: " + ROOT + path);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new ShaderException("Could not read " + path + ": " + e);
        }
    }

    private static String numbered(String source) {
        StringBuilder sb = new StringBuilder();
        String[] lines = source.split("\n", -1);
        for (int i = 0; i < lines.length; i++) sb.append(i + 1).append(": ").append(lines[i]).append('\n');
        return sb.toString();
    }

    public static final class ShaderException extends Exception {
        ShaderException(String message) {
            super(message);
        }
    }
}
