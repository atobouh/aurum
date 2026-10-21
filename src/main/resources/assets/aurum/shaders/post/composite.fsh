#version 150

#include "common.glsl"
#import <aurum:include/tonemap.glsl>
#import <aurum:include/sky_lut.glsl>

// The one extra full-screen pass (spec rule 1): fog, edge fade, bounce tint and colour grade together.
// Runs after the hand is drawn; the hand has its own depth texture so it never gets fogged.

uniform sampler2D u_Scene;
uniform sampler2D u_Depth;
uniform sampler2D u_HandDepth;
uniform sampler2D u_Bounce;

uniform mat4 u_InvProj;
uniform mat4 u_InvView;     // rotation only, view -> world
uniform vec3 u_SunDir;
uniform vec4 u_Fog;         // x distance density, y height density, z height falloff, w camera height above sea level
uniform vec4 u_Fog2;        // x cave amount, y edge fade start, z edge fade end, w bounce tint amount
uniform vec4 u_Flags;       // x fog on, y edge fade on, z hand depth valid, w grade on
uniform vec4 u_Grade;       // x saturation, y vibrance, z contrast

in vec2 v_Uv;
out vec4 fragColor;

vec3 grade(vec3 c) {
    float l = luminance(c);
    c = mix(vec3(l), c, u_Grade.x);
    float sat = max(c.r, max(c.g, c.b)) - min(c.r, min(c.g, c.b));
    c = mix(vec3(l), c, 1.0 + (u_Grade.y - 1.0) * (1.0 - saturate(sat * 1.6)));
    // Contrast as a gentle S around mid grey in display space.
    c = saturate(c);
    vec3 s = c * c * (3.0 - 2.0 * c);
    c = mix(c, s, u_Grade.z - 1.0) ;
    return saturate(c);
}

void main() {
    vec3 scene = texture(u_Scene, v_Uv).rgb;
    float depth = texture(u_Depth, v_Uv).r;
    bool hand = u_Flags.z > 0.5 && texture(u_HandDepth, v_Uv).r < 1.0;

    if (depth < 1.0 && !hand && (u_Flags.x > 0.5 || u_Flags.y > 0.5)) {
        vec4 vp = u_InvProj * vec4(v_Uv * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
        vec3 view = vp.xyz / vp.w;
        float dist = length(view);
        vec3 dir = mat3(u_InvView) * (view / dist);

        if (u_Flags.x > 0.5) {
            // Exponential distance fog (Quilez) plus exact height fog for valleys (analytic integral).
            float fd = 1.0 - exp(-dist * u_Fog.x);
            float b = u_Fog.z;
            float ry = dir.y * dist;
            float hInt = abs(ry) > 1e-3 ? (1.0 - exp(-ry * b)) / (dir.y * b) : dist;
            float fh = 1.0 - exp(-u_Fog.y * exp(-u_Fog.w * b) * max(hInt, 0.0));
            float f = 1.0 - (1.0 - fd) * (1.0 - fh);

            // Fog takes the sky's colour in that direction, so it is warmer toward the sun at golden hour.
            vec3 fogDir = normalize(vec3(dir.x, max(dir.y, 0.03), dir.z));
            vec3 fogColor = aurum_tonemap(aurum_skyRadiance(fogDir, u_SunDir) * 1.25);
            vec3 bounce = texelFetch(u_Bounce, ivec2(0), 0).rgb;
            float bl = max(luminance(bounce), 1e-4);
            fogColor *= mix(vec3(1.0), clamp(bounce / bl, 0.0, 2.0), u_Fog2.w);
            // In caves the haze is dark, not sky-coloured.
            fogColor = mix(fogColor, fogColor * 0.08 + vec3(0.012, 0.013, 0.016), u_Fog2.x);
            scene = mix(scene, fogColor, saturate(f));
        }

        if (u_Flags.y > 0.5) {
            // Hide the render-distance edge by fading into the real sky behind it. Chunks load in a cylinder
            // around the player, so the edge is measured horizontally (vanilla's terrain fog does the same).
            float edge = smoothstep(u_Fog2.y, u_Fog2.z, length(dir.xz) * dist);
            if (edge > 0.0) scene = mix(scene, aurum_tonemap(aurum_skyRadiance(dir, u_SunDir) * 1.25), edge);
        }
    }

    if (u_Flags.w > 0.5) scene = grade(scene);
    fragColor = vec4(scene, 1.0);
}
