package dev.impeccable.aurum.render.surface;

import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.Properties;

/** Executable adapter contract; uses production fingerprinting, patching and include preprocessing. */
public final class CoreShaderContractCheck {
    private static int checks;
    private static void require(boolean pass,String name) { checks++;if(!pass)throw new AssertionError(name); }
    private static byte[] resource(String path) throws Exception {
        try(var in=CoreShaderContractCheck.class.getResourceAsStream("/assets/"+path)) {
            if(in==null)throw new AssertionError("Missing classpath asset: "+path);
            return in.readAllBytes();
        }
    }
    private static String expandMinecraftImports(String source) throws Exception {
        var pattern=java.util.regex.Pattern.compile("#moj_import <([^>]+)>");
        var seen=new java.util.HashSet<String>();
        while(true) {
            var match=pattern.matcher(source);if(!match.find())return source;
            String path=match.group(1);
            String replacement=seen.add(path)?new String(resource("minecraft/shaders/include/"+path),StandardCharsets.UTF_8):"";
            source=source.substring(0,match.start())+replacement+source.substring(match.end());
        }
    }
    public static void main(String[] args) throws Exception {
        Properties fingerprints=new Properties();
        try(var in=CoreShaderContractCheck.class.getResourceAsStream("/assets/aurum/core-shader-fingerprints.properties")){fingerprints.load(in);}
        ResourceFactory original=id->{
            try { byte[] bytes=resource(id.getNamespace()+"/"+id.getPath());return Optional.of(new Resource(null,()->new ByteArrayInputStream(bytes))); }
            catch(Exception e){return Optional.empty();}
        };
        int variants=0;
        for(String key:fingerprints.stringPropertyNames())if(key.endsWith(".vsh")) {
            String name=key.substring(0,key.length()-4);variants++;
            require(CoreShaderSources.eligible(original,name),"vanilla stage pair accepted: "+name);
            for(boolean vertex:new boolean[]{true,false}) {
                String source=new String(resource("minecraft/shaders/core/"+name+(vertex?".vsh":".fsh")),StandardCharsets.UTF_8);
                String patched=CoreShaderSources.patch(source,vertex);
                var export=java.nio.file.Path.of("build/shared-shader-check");java.nio.file.Files.createDirectories(export);
                java.nio.file.Files.writeString(export.resolve(name+(vertex?".vsh":".fsh")),expandMinecraftImports(patched));
                require(patched.contains(source.replace("void main()","void aurum_vanillaMain()").substring(source.indexOf("void main()"))),"original main body retained");
                require(!patched.contains("#import <aurum:"),"Aurum includes fully expanded");
                if(vertex) {
                    require(patched.contains("flat out int aurum_FullBright") && patched.contains("greaterThanEqual(UV2, ivec2(240))"),"full-bright flag uses integer vertex coordinates");
                    require(patched.contains("aurum_LightCoord = (vec2(UV2) + 8.0) / 256.0"),"packed model light matches terrain decode");
                    require(patched.contains("u_AurumInvView * ModelViewMat * vec4(Position, 1.0)"),"receiver world-position adapter present");
                } else {
                    require(patched.contains("if (u_AurumMode != 1) { aurum_vanillaMain(); return; }"),"UI/special path retains vanilla");
                    require(patched.contains("flat in int aurum_FullBright") && patched.contains("aurum_FullBright != 0"),"full-bright material flag is flat, not interpolated");
                    require(patched.contains("aurum_evaluateLight(aurum_LightCoord"),"model uses production shared evaluator");
                    if(source.contains("color.a < 0.1")) {
                        String body=patched.substring(patched.lastIndexOf("void main()"));
                        require(body.contains("if (color.a < 0.1) discard;"),"new path preserves cutout");
                        require((body.indexOf("color *= aurum_Color")<body.indexOf("if (color.a < 0.1)"))
                                ==source.contains("texture(Sampler0, texCoord0) * vertexColor"),"alpha test ordering preserved");
                    }
                    if(source.contains("mix(overlayColor.rgb"))require(patched.lastIndexOf("mix(overlayColor.rgb")>patched.indexOf("void main()"),"new path preserves overlay");
                }
            }
            ResourceFactory modified=id->{
                var found=original.getResource(id);
                if(!id.getPath().equals("shaders/core/"+name+".fsh"))return found;
                return Optional.of(new Resource(null,()->new ByteArrayInputStream("modified shader".getBytes(StandardCharsets.UTF_8))));
            };
            require(!CoreShaderSources.eligible(modified,name),"modified fragment rejects both adapters");
        }
        require(!CoreShaderSources.eligible(original,"rendertype_eyes"),"authored emissive eye shader excluded");
        require(!CoreShaderSources.eligible(original,"rendertype_entity_shadow"),"blob shadow shader excluded");
        require(variants>=8,"all required model variants covered");
        System.out.println("Surface adapter contract passed: "+variants+" variants, "+checks+" assertions; original branches, paired fingerprints, core light decode, shared evaluator, cutouts, overlays and exclusions.");
    }
}
