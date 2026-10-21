#version 150

#include "common.glsl"
#import <aurum:include/tonemap.glsl>
#import <aurum:include/sky_lut.glsl>

// Aurum sky: one lookup-table read per pixel plus an analytic sun disc. Drawn in place of vanilla's sky dome;
// vanilla still draws the stars and moon on top.

uniform mat4 u_InvViewProj;
uniform vec3 u_SunDir;
uniform vec3 u_SunDisc;     // disc radiance (zero below the horizon and in rain)
uniform vec4 u_SunGlow;     // rgb warm scattered sunlight, a = golden-hour amount

in vec2 v_Uv;
out vec4 fragColor;

void main() {
    vec4 p = u_InvViewProj * vec4(v_Uv * 2.0 - 1.0, 1.0, 1.0);
    vec3 dir = normalize(p.xyz / p.w);
    vec3 color = aurum_skyRadiance(dir, u_SunDir);

    float c = dot(dir, u_SunDir);
    // Disc of about 1.5 degrees with a soft limb, plus a tight bright halo.
    float disc = smoothstep(0.99962, 0.99972, c);
    float limb = 0.6 + 0.4 * smoothstep(0.99962, 0.99990, c);
    float halo = pow(max(c, 0.0), 2200.0) * 0.06 + pow(max(c, 0.0), 300.0) * 0.012;
    color += u_SunDisc * (disc * limb + halo);
    // The wide, warm glow every good golden-hour shot has around the sun (stronger when the sun is low).
    float cg = max(c, 0.0);
    color += u_SunGlow.rgb * (pow(cg, 6.0) * 0.7 + pow(cg, 40.0) * 2.0 + pow(cg, 400.0) * 5.0) * (0.15 + 0.85 * u_SunGlow.a);

    fragColor = vec4(aurum_tonemap(color), 1.0);
}
