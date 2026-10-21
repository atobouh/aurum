#version 330 core

// Aurum replacement for Sodium 0.5.13's sodium:blocks/block_layer_opaque.vsh. Only installed when Sodium's
// original files match the fingerprints in TerrainShaders. Keeps every Sodium uniform in use, because Sodium
// throws on a missing uniform.

#import <sodium:include/fog.glsl>
#import <sodium:include/chunk_vertex.glsl>
#import <sodium:include/chunk_matrices.glsl>
#import <sodium:include/chunk_material.glsl>
#import <aurum:include/terrain_uniforms.glsl>
#import <aurum:include/wind.glsl>

out vec4 v_Color;
out vec2 v_TexCoord;

out float v_MaterialMipBias;
#ifdef USE_FRAGMENT_DISCARD
out float v_MaterialAlphaCutoff;
#endif

#ifdef USE_FOG
out float v_FragDistance;
#endif

out vec4 v_VanillaLight;
out vec2 v_LightCoord;
out vec3 v_Position;
flat out int v_Material;

uniform int u_FogShape;
uniform vec3 u_RegionOffset;
uniform vec2 u_TexCoordShrink;

uniform sampler2D u_LightTex; // The light map texture sampler

uvec3 _get_relative_chunk_coord(uint pos) {
    // Packing scheme is defined by LocalSectionIndex
    return uvec3(pos) >> uvec3(5u, 0u, 2u) & uvec3(7u, 3u, 7u);
}

vec3 _get_draw_translation(uint pos) {
    return _get_relative_chunk_coord(pos) * vec3(16.0);
}

void main() {
    _vert_init();

    vec3 translation = u_RegionOffset + _get_draw_translation(_draw_id);
    vec3 position = _vert_position + translation;

    v_TexCoord = (_vert_tex_diffuse_coord_bias * u_TexCoordShrink) + _vert_tex_diffuse_coord; // FMA for precision
    v_Material = 0;

#ifdef USE_FRAGMENT_DISCARD
    // Plants and leaves live in the cutout pass; solid terrain never pays for the material lookup.
    if (u_AurumMode == 1) {
        int id = int(textureLod(u_NormalTex, v_TexCoord, 0.0).a * 255.0 + 0.5);
        v_Material = id;
        if (id != 0 && u_WindParams.z > 0.0) {
            float dist = length(position);
            if (dist < WIND_MAX_DIST) {
                // A negative V bias means this vertex sits on the top edge of its sprite.
                position += aurum_windOffset(id, position, _vert_tex_diffuse_coord_bias.y < 0.0, dist);
            }
        }
    }
#endif

#ifdef USE_FOG
    v_FragDistance = getFragDistance(u_FogShape, position);
#endif

    gl_Position = u_ProjectionMatrix * u_ModelViewMatrix * vec4(position, 1.0);

    v_Color = _vert_color;
    v_VanillaLight = texture(u_LightTex, _vert_tex_light_coord);
    v_LightCoord = _vert_tex_light_coord;
    v_Position = position;

    v_MaterialMipBias = _material_mip_bias(_material_params);
#ifdef USE_FRAGMENT_DISCARD
    v_MaterialAlphaCutoff = _material_alpha_cutoff(_material_params);
#endif
}
