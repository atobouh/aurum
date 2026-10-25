#version 330 core

// Aurum replacement for Sodium 0.5.13's sodium:blocks/block_layer_opaque.fsh. The vanilla branch reproduces
// Sodium's original output exactly; the Aurum branch does the lighting in include/lighting.glsl.

// Declared before the includes: the water shader reads the live (animated) block texture.
uniform sampler2D u_BlockTex;

#import <sodium:include/fog.glsl>
#import <aurum:include/terrain_uniforms.glsl>
#import <aurum:include/tonemap.glsl>
#import <aurum:include/shadow.glsl>
#import <aurum:include/sky_lut.glsl>
#import <aurum:include/pbr.glsl>
#import <aurum:include/lighting.glsl>
#import <aurum:include/water.glsl>

in vec4 v_Color;
in vec2 v_TexCoord;
in float v_FragDistance;

in float v_MaterialMipBias;
in float v_MaterialAlphaCutoff;

in vec4 v_VanillaLight;
in vec2 v_LightCoord;
in vec3 v_Position;
flat in int v_Material;

uniform vec4 u_FogColor;
uniform float u_FogStart;
uniform float u_FogEnd;

out vec4 fragColor;

void main() {
    // 3D blocks: move the texture lookup along the pack's height map (Aurum lighting only, opaque passes).
    vec2 uv = v_TexCoord;
    if (u_AurumMode == 1 && u_AurumPass == 0) uv = aurum_parallax(v_TexCoord, v_Position);
    vec4 diffuseColor = uv == v_TexCoord ? texture(u_BlockTex, v_TexCoord, v_MaterialMipBias)
                                         : textureGrad(u_BlockTex, uv, dFdx(v_TexCoord), dFdy(v_TexCoord));

#ifdef USE_FRAGMENT_DISCARD
    if (diffuseColor.a < v_MaterialAlphaCutoff) {
        discard;
    }
#endif

    if (u_AurumMode == 2) {
        // Shadow pass: depth only.
        fragColor = vec4(0.0);
        return;
    }

    if (u_AurumMode == 0) {
        diffuseColor.rgb *= v_Color.rgb * v_VanillaLight.rgb;
        diffuseColor.rgb *= v_Color.a * v_VanillaLight.a;
        fragColor = _linearFog(diffuseColor, v_FragDistance, u_FogColor, u_FogStart, u_FogEnd);
        return;
    }

    if (u_AurumPass == 1) {
        // Translucent pass: water sprites get Aurum water; glass, ice and the rest keep the normal lighting.
        int id = int(textureLod(u_NormalTex, v_TexCoord, 0.0).a * 255.0 + 0.5) & 15;
        if (id == 9) {
            fragColor = vec4(aurum_shadeWater(diffuseColor, v_Color, v_LightCoord, v_Position, v_TexCoord), 1.0);
            return;
        }
    }

    vec3 lit = aurum_shadeTerrain(diffuseColor, v_Color, v_LightCoord, v_Position, v_TexCoord, uv, v_MaterialMipBias, v_Material);
    fragColor = vec4(lit, diffuseColor.a);
}
