package dev.impeccable.aurum.render.surface;

import dev.impeccable.aurum.render.ShaderManager;
import net.minecraft.resource.ResourceFactory;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Properties;
import java.util.Set;

/** Small fingerprinted adapters around original core shader branches; no resource-pack shader overwrite. */
public final class CoreShaderSources {
    private static final Properties HASHES = new Properties();
    private static final Set<String> failed = new HashSet<>();
    static {
        try (var in = CoreShaderSources.class.getResourceAsStream("/assets/aurum/core-shader-fingerprints.properties")) {
            if (in != null) HASHES.load(in);
        } catch (IOException e) { throw new ExceptionInInitializerError(e); }
    }
    private CoreShaderSources() {}
    public static void failed(String name) { failed.add(name); }
    public static boolean eligible(ResourceFactory resources, String name) throws IOException {
        if (failed.contains(name) || !HASHES.containsKey(name + ".vsh")) return false;
        for (String ext : new String[]{"vsh", "fsh"}) {
            try (var in = resources.getResourceOrThrow(new Identifier("minecraft", "shaders/core/" + name + "." + ext)).getInputStream()) {
                if (!hash(new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("\r", "")).equals(HASHES.getProperty(name + "." + ext))) return false;
            }
        }
        return true;
    }
    public static String patch(String source, boolean vertex) throws IOException {
        String original = source.replace("void main()", "void aurum_vanillaMain()");
        if (original.equals(source)) throw new IOException("Core shader main signature differs");
        if (vertex) {
            String declarations = "\nuniform mat4 u_AurumInvView;\nout vec3 aurum_Position;\nout vec3 aurum_Normal;\nout vec4 aurum_Color;\nout vec2 aurum_LightCoord;\nflat out int aurum_FullBright;\n";
            original = original.replace("void aurum_vanillaMain()", declarations + "void aurum_vanillaMain()");
            return original + "\nvoid main() {\n aurum_vanillaMain();\n aurum_Position = (u_AurumInvView * ModelViewMat * vec4(Position, 1.0)).xyz;\n aurum_Normal = mat3(u_AurumInvView) * mat3(ModelViewMat) * Normal;\n aurum_Color = Color;\n aurum_LightCoord = (vec2(UV2) + 8.0) / 256.0;\n aurum_FullBright = all(greaterThanEqual(UV2, ivec2(240))) ? 1 : 0;\n}\n";
        }
        String common;
        try { common = ShaderManager.preprocess("include/model_light.glsl", java.util.Map.of()); }
        catch (ShaderManager.ShaderException e) { throw new IOException(e); }
        original = original.replace("void aurum_vanillaMain()", common + "\nvoid aurum_vanillaMain()");
        boolean cutout = source.contains("color.a < 0.1");
        boolean alphaAfterColor = source.contains("texture(Sampler0, texCoord0) * vertexColor");
        boolean overlay = source.contains("mix(overlayColor.rgb");
        return original + "\nvoid main() {\n if (u_AurumMode != 1) { aurum_vanillaMain(); return; }\n if (aurum_FullBright != 0) { if (u_LightingDebug > 0) fragColor = vec4(1.0, 0.5, 0.0, 1.0); else aurum_vanillaMain(); return; }\n vec4 color = texture(Sampler0, texCoord0);\n"
                + (cutout && !alphaAfterColor ? " if (color.a < 0.1) discard;\n" : "")
                + " color *= aurum_Color * ColorModulator;\n"
                + (cutout && alphaAfterColor ? " if (color.a < 0.1) discard;\n" : "")
                + (overlay ? " color.rgb = mix(overlayColor.rgb, color.rgb, overlayColor.a);\n" : "")
                + " fragColor = vec4(aurum_shadeModel(color.rgb), color.a);\n}\n";
    }
    private static String hash(String s) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException e) { throw new AssertionError(e); }
    }
}
