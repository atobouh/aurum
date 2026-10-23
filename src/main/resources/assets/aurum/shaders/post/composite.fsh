#version 150

#include "common.glsl"
#import <aurum:include/tonemap.glsl>
#import <aurum:include/sky_lut.glsl>

// The one extra full-screen pass (spec rule 1): fog, edge fade, bounce tint, clouds, god rays, bloom, lens flare
// and colour grade together. Clouds, rays and bloom arrive pre-computed at reduced resolution.
// Runs after the hand is drawn; the hand has its own depth texture so it never gets fogged.

uniform sampler2D u_Scene;
uniform sampler2D u_Depth;
uniform sampler2D u_HandDepth;
uniform sampler2D u_Bounce;
uniform sampler2D u_Clouds;   // half resolution, premultiplied
uniform sampler2D u_Rays;     // quarter resolution, open sky along the line to the sun
uniform sampler2D u_Bloom;    // quarter resolution
uniform sampler2D u_Flare;    // 1x1, visible share of the sun disc
uniform sampler2D u_Volumetric; // one-third resolution: r sunlit share of the air, g distance covered / range
uniform sampler2D u_Ao;     // half resolution: r ambient occlusion, g distance / range
uniform vec4 u_AoParams;    // x strength, y range (blocks)
uniform vec4 u_Volumetric2;    // x range (blocks), y enclosed (0 open sky .. 1 cave or room), z haze (0..1)

uniform mat4 u_InvProj;
uniform mat4 u_InvView;     // rotation only, view -> world
uniform vec3 u_SunDir;
uniform vec4 u_SunGlow;     // rgb warm scattered sunlight, a = golden-hour amount
uniform vec4 u_Fog;         // x distance density, y height density, z height falloff, w camera height above sea level
uniform vec4 u_Fog3;        // x clear zone (blocks), y most the fog may cover, z fog desaturation
uniform vec4 u_Fog2;        // x cave amount, y edge fade start, z edge fade end, w bounce tint amount
uniform vec4 u_Flags;       // x fog on, y edge fade on, z hand depth valid, w grade on
uniform vec4 u_Grade;       // x saturation, y vibrance, z contrast
uniform vec4 u_CloudSlab;   // x cloud volume bottom, y top, z high layer height (relative to camera),
                            // w 0 off, 1 on, 2 on + sharpen edges, 3 on + smooth
uniform vec4 u_SunScreen;   // xy sun position (uv), z width / height, w sun in front of the camera (0..1)
uniform vec4 u_Extras;      // x god rays, y bloom, z flare, w volumetric light strength

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

// Distance along the ray to the nearest cloud layer (the cloud volume's entry, or the high layer's plane).
float cloudEntry(vec3 dir) {
    float t = 1e9;
    if (abs(dir.y) > 1e-4) {
        float ta = u_CloudSlab.x / dir.y, tb = u_CloudSlab.y / dir.y;
        float tn = max(min(ta, tb), 0.0), tf = max(ta, tb);
        if (tf > 0.0) t = tn;
        float t2 = u_CloudSlab.z / dir.y;
        if (t2 > 0.0) t = min(t, t2);
    } else if (u_CloudSlab.x <= 0.0 && u_CloudSlab.y >= 0.0) {
        t = 0.0;
    }
    return t;
}

// Volumetric light, upscaled edge-aware. Nearby beams get a compact spatial kernel; distant haze still uses
// the broad 3x3 kernel to hide march jitter. Depth agreement keeps tree silhouettes from borrowing bright
// open-air values behind them.
vec2 volumetricLit(vec2 uv, float dist) {
    vec2 size = vec2(textureSize(u_Volumetric, 0));
    vec2 base = floor(uv * size) + 0.5;
    float g = min(dist, u_Volumetric2.x) / u_Volumetric2.x;
    vec2 sum = vec2(0.0);
    float wsum = 0.0;
    for (int y = -1; y <= 1; y++) {
        for (int x = -1; x <= 1; x++) {
            vec2 tc = base + vec2(x, y);
            vec2 v = texelFetch(u_Volumetric, ivec2(clamp(tc, vec2(0.0), size - 1.0)), 0).rg;
            vec2 d = abs(tc - uv * size);
            float broad = max(0.0, 1.6 - d.x) * max(0.0, 1.6 - d.y);
            float close = exp(-dot(d, d) * 3.5);
            float spatial = mix(close, broad, smoothstep(8.0, 28.0, dist));
            float depthGate = mix(220.0, 60.0, smoothstep(8.0, 28.0, dist));
            float w = spatial * exp(-abs(v.g - g) * depthGate) + 1e-5;
            sum += v * w;
            wsum += w;
        }
    }
    return sum / wsum;
}

// Contact shadows: one 4x4 block of the half-resolution result (exactly cancels the 4x4 rotation pattern),
// edge-aware on distance so silhouettes never get dark halos.
float contactShadow(vec2 uv, float dist) {
    vec2 size = vec2(textureSize(u_Ao, 0));
    vec2 base = floor(uv * size) - 1.5;
    float g = min(dist / u_AoParams.y, 1.0);
    float sum = 0.0, wsum = 0.0;
    for (int y = 0; y < 4; y++) {
        for (int x = 0; x < 4; x++) {
            vec2 tc = base + vec2(x, y) + 0.5;
            vec2 v = texelFetch(u_Ao, ivec2(clamp(tc, vec2(0.0), size - 1.0)), 0).rg;
            float w = exp(-abs(v.g - g) * 200.0 * max(1.0 - g, 0.05)) + 1e-4;
            sum += v.r * w;
            wsum += w;
        }
    }
    return sum / wsum;
}

// A subtle lens flare: warm halo, a short horizontal streak, and faint ghosts mirrored through the centre.
vec3 flare(vec2 uv, float vis) {
    vec2 aspect = vec2(u_SunScreen.z, 1.0);
    vec2 s = u_SunScreen.xy;
    vec2 d = (uv - s) * aspect;
    vec3 c = vec3(1.0, 0.72, 0.42) * exp(-length(d) * 9.0) * 0.18;
    c += vec3(1.0, 0.78, 0.55) * exp(-abs(d.y) * 260.0) * exp(-abs(d.x) * 5.0) * 0.10;
    vec2 m = vec2(1.0) - s;
    const vec3 GHOST_COLOR[4] = vec3[](vec3(1.0, 0.55, 0.25), vec3(0.45, 0.85, 0.65), vec3(0.65, 0.45, 1.0), vec3(1.0, 0.75, 0.40));
    const float GHOST_POS[4] = float[](0.35, 0.70, 1.05, 1.40);
    const float GHOST_SIZE[4] = float[](0.045, 0.025, 0.07, 0.11);
    const float GHOST_GAIN[4] = float[](0.05, 0.05, 0.035, 0.025);
    for (int i = 0; i < 4; i++) {
        vec2 g = (uv - mix(s, m, GHOST_POS[i])) * aspect;
        float r = length(g) / GHOST_SIZE[i];
        // Soft disc with a slightly brighter rim, like an aperture ghost.
        float disc = (1.0 - smoothstep(0.75, 1.0, r)) * (0.6 + 0.4 * smoothstep(0.3, 0.95, r));
        c += GHOST_COLOR[i] * disc * GHOST_GAIN[i];
    }
    // Fade toward the screen edges so the ghosts never pop.
    float edge = smoothstep(0.0, 0.12, min(min(s.x, 1.0 - s.x), min(s.y, 1.0 - s.y)) + 0.06);
    return c * vis * edge;
}

void main() {
    vec3 scene = texture(u_Scene, v_Uv).rgb;
    float depth = texture(u_Depth, v_Uv).r;
    bool hand = u_Flags.z > 0.5 && texture(u_HandDepth, v_Uv).r < 1.0;

    vec4 vp = u_InvProj * vec4(v_Uv * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    vec3 view = vp.xyz / vp.w;
    float dist = depth < 1.0 ? length(view) : 1e9;
    vec3 dir = mat3(u_InvView) * normalize(view);
    // Sunlit share of the air in front of this pixel (volumetric light), shared by the fog and the beams.
    vec2 vl = u_Extras.w > 0.0 && depth < 1.0 && !hand ? volumetricLit(v_Uv, dist) : vec2(1.0, 0.0);

    if (depth < 1.0 && !hand && u_AoParams.x > 0.0 && dist < u_AoParams.y) {
        // Darken where surfaces meet and under cover, before the haze goes over it.
        scene *= mix(1.0, contactShadow(v_Uv, dist), u_AoParams.x);
    }

    if (depth < 1.0 && !hand && (u_Flags.x > 0.5 || u_Flags.y > 0.5)) {
        // One sky lookup serves both the fog colour and the edge fade.
        vec3 sky = aurum_tonemap(aurum_skyRadiance(dir, u_SunDir));

        if (u_Flags.x > 0.5) {
            // Exponential distance fog (Quilez) plus exact height fog for valleys (analytic integral). Fog starts
            // after a clear zone, as in the references (Complementary, Bliss): nearby terrain keeps its full
            // contrast and colour, the haze lives in the distance (0.3.3 fogged a fence 30 blocks away by ~40%).
            float fogDist = max(dist - u_Fog3.x, 0.0);
            float fd = 1.0 - exp(-fogDist * u_Fog.x);
            float b = u_Fog.z;
            float ry = dir.y * dist;
            float hInt = abs(ry) > 1e-3 ? (1.0 - exp(-ry * b)) / (dir.y * b) : dist;
            hInt *= fogDist / max(dist, 1e-3);
            float fh = 1.0 - exp(-u_Fog.y * exp(-u_Fog.w * b) * max(hInt, 0.0));
            // Never a solid wall: distant shapes still read through the haze (caves excepted).
            float f = (1.0 - (1.0 - fd) * (1.0 - fh)) * mix(u_Fog3.y, 1.0, u_Fog2.x);
            if (f > 0.002) {
                // Fog takes the sky's colour in that direction, so it is warmer toward the sun at golden hour.
                // Haze lit by the sun: looking toward a low sun, the air itself glows gold (Mie forward scattering).
                float toSun = max(dot(dir, u_SunDir), 0.0);
                vec3 inscatter = u_SunGlow.rgb * (pow(toSun, 4.0) * 0.45 + pow(toSun, 30.0) * 0.8) * (0.15 + 0.85 * u_SunGlow.a);
                // Haze in shadow does not glow: with volumetric light on, shadowed lanes of air stay darker.
                if (u_Extras.w > 0.0) inscatter *= 0.35 + 0.65 * vl.r;
                // The haze itself is near-neutral (the sky's pinks would turn it into a flat pink soup); its warmth
                // comes from the sunlight scattered toward the sun.
                vec3 fogRad = aurum_fogRadiance(dir, u_SunDir);
                fogRad = mix(fogRad, vec3(luminance(fogRad)), u_Fog3.z);
                vec3 fogColor = aurum_tonemap(fogRad + inscatter);
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

    if (!hand) {
        // Clouds go over whatever the ray meets after reaching a cloud layer (sky, or terrain seen from above).
        if (u_CloudSlab.w > 0.5 && dist > cloudEntry(dir)) {
            vec4 cl = texture(u_Clouds, v_Uv);
            if (u_CloudSlab.w > 2.5) {
                // Soft clouds: four extra bilinear taps (a 4x4 texel tent) smooth out the ray-march jitter.
                vec2 h = 1.0 / vec2(textureSize(u_Clouds, 0));
                cl = cl * 0.36 + (texture(u_Clouds, v_Uv + vec2(h.x, h.y) * 0.75) + texture(u_Clouds, v_Uv + vec2(-h.x, h.y) * 0.75)
                     + texture(u_Clouds, v_Uv + vec2(h.x, -h.y) * 0.75) + texture(u_Clouds, v_Uv - h * 0.75)) * 0.16;
            } else if (u_CloudSlab.w > 1.5 && cl.a > 0.0) {
                // Half-resolution blocky clouds: bilinear filtering smears a crisp edge over two pixels. Its
                // coverage is a ramp across the edge, so re-steepening it gives a one-pixel, anti-aliased edge.
                float a = smoothstep(0.12, 0.88, cl.a);
                cl = vec4(cl.rgb * (a / cl.a), a);
            }
            scene = scene * (1.0 - cl.a) + cl.rgb;
        }
        vec3 hue = u_SunGlow.rgb / max(max(u_SunGlow.r, max(u_SunGlow.g, u_SunGlow.b)), 1e-4);
        vec3 rayColor = mix(vec3(1.0), hue, 0.65);
        float toSun = max(dot(dir, u_SunDir), 0.0);
        if (u_Extras.w > 0.0 && depth < 1.0) {
            // Volumetric light: glow from the sunlit air in front of terrain. Shadowed air adds nothing, so tree
            // and terrain shadows cut dark lanes into the glow: beams. Not over the sky, whose colour already
            // contains this light (adding it again washed the sky out around the sun).
            // Single scattering with a realistic air density (0.4.1): clear air scatters ~1.5% per lit block,
            // hazier air (golden hour, rain, fog) more, caves and rooms much more. 0.4.0 used ~10x too much, so the
            // shadow lanes of hills and houses turned into heavy, blurry stripes over everything near the player
            // (user screenshots). Now open land gets a faint glow and the beams only stand out where they should:
            // toward a low sun, in haze, and from openings into dark spaces.
            vec2 v = vl;
            float cave = u_Volumetric2.y;
            float density = 0.015 * (1.0 + 3.0 * u_Volumetric2.z) * (1.0 + 7.0 * cave);
            float litBlocks = v.r * v.g * u_Volumetric2.x;
            float glow = 1.0 - exp(-litBlocks * density);
            // Mie-like phase: mostly toward the sun; a small all-round part, larger in caves (beam seen from the side).
            float phase = 0.25 + 1.1 * pow(toSun, 8.0) + 0.5 * pow(toSun, 40.0) + 1.5 * cave;
            scene += rayColor * min(glow * phase * u_Extras.w, 0.25);
        }
        if (u_Extras.x > 0.0) {
            // Screen-space shafts over open sky. A radial average of an unobstructed sky is 1 everywhere;
            // adding that directly drew a wide, blurry wedge instead of beams. Compare neighbouring angular
            // paths so only actual cloud/terrain gaps produce a shaft. The shadow-map pass handles beams in
            // front of nearby terrain.
            float rays = texture(u_Rays, v_Uv).r;
            float r = length((v_Uv - u_SunScreen.xy) * vec2(u_SunScreen.z, 1.0));
            vec2 tangent = normalize(vec2(-(v_Uv.y - u_SunScreen.y),
                                          (v_Uv.x - u_SunScreen.x) * u_SunScreen.z) + vec2(1e-5));
            vec2 offset = tangent * (0.018 + 0.045 * r) / vec2(u_SunScreen.z, 1.0);
            float surround = 0.5 * (texture(u_Rays, clamp(v_Uv + offset, 0.0, 1.0)).r
                                  + texture(u_Rays, clamp(v_Uv - offset, 0.0, 1.0)).r);
            float shaft = max(rays - surround - 0.015, 0.0);
            float openSky = (depth >= 1.0 ? 1.0 : 0.0) * (1.0 - texture(u_Clouds, v_Uv).a);
            // Rays start a little away from the sun: piled up on its face they only blow it out to white.
            scene += rayColor * pow(shaft, 1.35) * 0.85 * exp(-r * 2.2)
                   * smoothstep(0.02, 0.14, r) * openSky * u_Extras.x;
        }
    }
    if (u_Extras.y > 0.0) scene += texture(u_Bloom, v_Uv).rgb * u_Extras.y;
    if (u_Extras.z > 0.0) {
        float vis = texelFetch(u_Flare, ivec2(0), 0).r;
        if (vis > 0.002) scene += flare(v_Uv, vis) * u_Extras.z;
    }

    if (u_Flags.w > 0.5) scene = grade(scene);
    fragColor = vec4(scene, 1.0);
}
