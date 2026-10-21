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
uniform vec4 u_SunGlow;     // rgb warm scattered sunlight, a = golden-hour amount
uniform vec4 u_Fog;         // x distance density, y height density, z height falloff, w camera height above sea level
uniform vec4 u_Fog2;        // x cave amount, y edge fade start, z edge fade end, w bounce tint amount
uniform vec4 u_Flags;       // x fog on, y edge fade on, z hand depth valid, w grade on
uniform vec4 u_Grade;       // x saturation, y vibrance, z contrast

in vec2 v_Uv;
out vec4 fragColor;

// Oklab (Björn Ottosson, public domain): perceptual space where scaling chroma never shifts hue.
vec3 toOklab(vec3 c) {
    vec3 lms = mat3(0.4122214708, 0.2119034982, 0.0883024619,
                    0.5363325363, 0.6806995451, 0.2817188376,
                    0.0514459929, 0.1073969566, 0.6299787005) * c;
    lms = pow(max(lms, vec3(0.0)), vec3(1.0 / 3.0));
    return mat3(0.2104542553, 1.9779984951, 0.0259040371,
                0.7936177850, -2.4285922050, 0.7827717662,
                -0.0040720468, 0.4505937099, -0.8086757660) * lms;
}

vec3 fromOklab(vec3 lab) {
    vec3 lms = mat3(1.0, 1.0, 1.0,
                    0.3963377774, -0.1055613458, -0.0894841775,
                    0.2158037573, -0.0638541728, -1.2914855480) * lab;
    lms = lms * lms * lms;
    return mat3(4.0767416621, -1.2684380046, -0.0041960863,
                -3.3077115913, 2.6097574011, -0.7034186147,
                0.2309699292, -0.3413193965, 1.7076147010) * lms;
}

vec3 grade(vec3 c) {
    vec3 lab = toOklab(pow(saturate(c), vec3(2.2)));
    float chroma = length(lab.yz);
    // Vibrance lifts muted colours more than ones that are already strong, so nothing turns neon.
    float vib = 1.0 + (u_Grade.y - 1.0) * (1.0 - smoothstep(0.02, 0.16, chroma));
    lab.yz *= u_Grade.x * vib;
    // Contrast as a gentle S on perceptual lightness around the middle.
    float l = saturate(lab.x);
    lab.x = mix(l, l * l * (3.0 - 2.0 * l), u_Grade.z - 1.0);
    return pow(saturate(fromOklab(lab)), vec3(1.0 / 2.2));
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
        // One sky lookup serves both the fog colour and the edge fade.
        vec3 sky = aurum_tonemap(aurum_skyRadiance(dir, u_SunDir));

        if (u_Flags.x > 0.5) {
            // Exponential distance fog (Quilez) plus exact height fog for valleys (analytic integral).
            float fd = 1.0 - exp(-dist * u_Fog.x);
            float b = u_Fog.z;
            float ry = dir.y * dist;
            float hInt = abs(ry) > 1e-3 ? (1.0 - exp(-ry * b)) / (dir.y * b) : dist;
            float fh = 1.0 - exp(-u_Fog.y * exp(-u_Fog.w * b) * max(hInt, 0.0));
            float f = 1.0 - (1.0 - fd) * (1.0 - fh);
            if (f > 0.002) {
                // Fog takes the sky's colour in that direction, so it is warmer toward the sun at golden hour.
                // Haze lit by the sun: looking toward a low sun, the air itself glows gold (Mie forward scattering).
                float toSun = max(dot(dir, u_SunDir), 0.0);
                vec3 inscatter = u_SunGlow.rgb * (pow(toSun, 4.0) * 1.4 + pow(toSun, 30.0) * 2.0) * (0.25 + 0.75 * u_SunGlow.a);
                vec3 fogColor = aurum_tonemap(aurum_fogRadiance(dir, u_SunDir) + inscatter);
                vec3 bounce = texelFetch(u_Bounce, ivec2(0), 0).rgb;
                float bl = max(luminance(bounce), 1e-4);
                fogColor *= mix(vec3(1.0), clamp(bounce / bl, 0.0, 2.0), u_Fog2.w);
                // In caves the haze is dark, not sky-coloured.
                fogColor = mix(fogColor, fogColor * 0.08 + vec3(0.012, 0.013, 0.016), u_Fog2.x);
                scene = mix(scene, fogColor, saturate(f));
            }
        }

        if (u_Flags.y > 0.5) {
            // Hide the render-distance edge by fading into the sky behind it. Chunks load in a cylinder
            // around the player, so the edge is measured horizontally (vanilla's terrain fog does the same).
            float edge = smoothstep(u_Fog2.y, u_Fog2.z, length(dir.xz) * dist);
            scene = mix(scene, sky, edge);
        }
    }

    if (u_Flags.w > 0.5) scene = grade(scene);
    fragColor = vec4(scene, 1.0);
}
