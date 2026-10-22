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
uniform float u_SunShape;   // 0 round disc, 1 square (Minecraft-style)

in vec2 v_Uv;
out vec4 fragColor;

void main() {
    vec4 p = u_InvViewProj * vec4(v_Uv * 2.0 - 1.0, 1.0, 1.0);
    vec3 dir = normalize(p.xyz / p.w);
    vec3 color = aurum_skyRadiance(dir, u_SunDir);

    float c = dot(dir, u_SunDir);
    if (u_SunShape < 0.5) {
        // Disc of about 1.5 degrees with a soft limb, plus a tight bright halo.
        float disc = smoothstep(0.99962, 0.99972, c);
        float limb = 0.6 + 0.4 * smoothstep(0.99962, 0.99990, c);
        float halo = pow(max(c, 0.0), 2200.0) * 0.06 + pow(max(c, 0.0), 300.0) * 0.012;
        color += u_SunDisc * (disc * limb + halo);
    } else if (c > 0.95) {
        // Square sun, aligned like vanilla's: one edge along the sun's path, the other along world Z. Gnomonic
        // coordinates around the sun, Chebyshev distance gives the square. A crisp anti-aliased core, slightly
        // hotter in the middle, inside a faint larger square glow (the look of vanilla-style shader skies).
        vec3 e1 = vec3(0.0, 0.0, 1.0);
        vec3 e2 = normalize(cross(u_SunDir, e1));
        vec2 q = vec2(dot(dir, e1), dot(dir, e2)) / c;
        float m = max(abs(q.x), abs(q.y));
        // Size matched to the square-sun look the user referenced (about 6 degrees across).
        const float S = 0.085;
        // Edge width: one pixel, capped. Unbounded, it exploded where the projection does (90 degrees from the
        // sun) and drew a dotted white line across the daytime sky (0.4.1).
        float px = clamp(fwidth(m), 1e-5, 0.01);
        float core = 1.0 - smoothstep(S - px, S + px, m);
        float centre = 0.85 + 0.15 * (1.0 - smoothstep(0.0, S, length(q)));
        float rim = (1.0 - smoothstep(1.3 * S - px, 1.45 * S, m)) * 0.14 + exp(-max(m - S, 0.0) * 22.0) * 0.06;
        color += u_SunDisc * (core * centre + rim * (1.0 - core));
    }
    // The wide, warm glow every good golden-hour shot has around the sun (stronger when the sun is low).
    float cg = max(c, 0.0);
    // The square sun keeps only the wide glow: a tight round glow as bright as the square would swallow its edges.
    float tight = u_SunShape < 0.5 ? 1.0 : 0.12;
    color += u_SunGlow.rgb * (pow(cg, 6.0) * 0.7 + (pow(cg, 40.0) * 2.0 + pow(cg, 400.0) * 5.0) * tight) * (0.15 + 0.85 * u_SunGlow.a);

    fragColor = vec4(aurum_tonemap(color), 1.0);
}
