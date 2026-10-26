#version 150

#include "common.glsl"
#import <aurum:include/tonemap.glsl>
#import <aurum:include/sky_lut.glsl>

// The one extra full-screen pass (spec rule 1): fog, edge fade, bounce tint, clouds, god rays, bloom, lens flare
// and colour grade together. Clouds, rays and bloom arrive pre-computed at reduced resolution.
// Runs after the hand is drawn; the hand has its own depth texture so it never gets fogged.

uniform int u_LightingDebug;
uniform sampler2D u_Scene;
uniform sampler2D u_Depth;
uniform sampler2D u_BeforeTranslucentDepth;
uniform vec4 u_SurfaceEdge; // x valid copied depth, yz original opaque edge fade
uniform sampler2D u_HandDepth;
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
uniform vec4 u_Fog3;        // x clear zone (blocks), y most the fog may cover, z fog desaturation, w aerial haze density
uniform vec4 u_Fog2;        // x cave amount, y edge fade start, z edge fade end, w bounce tint amount
uniform vec4 u_PixelLight; // x crisp light reconstruction, y world grid (used by the producer)
uniform vec4 u_Flags;       // x fog on, y edge fade on, z hand depth valid, w grade on
uniform vec4 u_Grade;       // x saturation, y vibrance, z contrast, w detail sharpening
uniform vec4 u_Grade2;      // x golden-hour grade strength
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
    // Golden-hour grade (0.7.9, the film look of Photon's sunsets): the whole frame warms toward orange-gold,
    // colours deepen and shadows sink into silhouettes, instead of a thin orange band over a grey-blue world.
    float g = u_SunGlow.a * u_Grade2.x;
    if (g > 0.0) {
        lab.yz *= 1.0 + 0.30 * g;
        lab.yz += vec2(0.030, 0.045) * g * smoothstep(0.15, 0.75, lab.x);
        float l2 = saturate(lab.x);
        lab.x = mix(l2, l2 * l2 * (3.0 - 2.0 * l2), 0.35 * g);
    }
    return pow(saturate(fromOklab(lab)), vec3(1.0 / 2.2));
}

// Four neighbouring scene reads, fused into this composite. Use the original scene's luminance detail,
// not sharpened bloom/fog/rays, and preserve chromaticity when applying it to the composed image.
vec3 sharpenDetail(vec3 composed, vec3 original, vec2 uv) {
    vec2 px = 1.0 / vec2(textureSize(u_Scene, 0));
    vec4 n = vec4(luminance(texture(u_Scene, uv + vec2(px.x, 0.0)).rgb),
                  luminance(texture(u_Scene, uv - vec2(px.x, 0.0)).rgb),
                  luminance(texture(u_Scene, uv + vec2(0.0, px.y)).rgb),
                  luminance(texture(u_Scene, uv - vec2(0.0, px.y)).rgb));
    float y = luminance(original);
    float lo = min(y, min(min(n.x, n.y), min(n.z, n.w)));
    float hi = max(y, max(max(n.x, n.y), max(n.z, n.w)));
    float contrast = hi - lo;
    // Reject nearly flat/noisy areas and damp high-contrast silhouettes to avoid ringing and crawling edges.
    float adapt = smoothstep(0.008, 0.045, contrast) * (1.0 - smoothstep(0.22, 0.6, contrast));
    float detail = (y - dot(n, vec4(0.25))) * 0.85 * u_Grade.w * adapt;
    detail = clamp(detail, -min(0.035, contrast * 0.15), min(0.035, contrast * 0.15));
    float cy = max(luminance(composed), 1e-4);
    return saturate(composed * max((cy + detail) / cy, 0.0));
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
    if (u_PixelLight.x > 0.5) {
        // Keep the march's native pixel cells. No bilinear upscale or spatial blur across a shaft boundary.
        vec2 v = texelFetch(u_Volumetric, ivec2(clamp(floor(uv * size), vec2(0.0), size - 1.0)), 0).rg;
        v.r = floor(v.r * 32.0 + 0.5) / 32.0;
        return v;
    }
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
            float spatial = mix(close, broad, smoothstep(8.0, 28.0, dist) * (1.0 - 0.85 * u_Volumetric2.y));
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

// Forward scattering, normalized around a broad aerosol lobe. This is shared by fog and shadow-map shafts
// so they describe one volume of air rather than independent overlays with different colour and falloff.
float airPhase(float cosTheta) {
    const float g = 0.65;
    float d = max(1.0 + g * g - 2.0 * g * cosTheta, 0.02);
    return 0.08 + 0.22 * (1.0 - g * g) / (d * sqrt(d));
}

float lightRayMask(vec2 uv) {
    if (u_PixelLight.x > 0.5) {
        ivec2 size = textureSize(u_Rays, 0);
        return texelFetch(u_Rays, ivec2(clamp(floor(uv * vec2(size)), vec2(0.0), vec2(size - 1))), 0).r;
    }
    return texture(u_Rays, uv).r;
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
    // Preserve contribution views: no fog, AO, grade, bloom, rays or sharpening over diagnostic colours.
    if (u_LightingDebug > 0) { fragColor = vec4(scene, 1.0); return; }
    vec3 original = scene;
    bool cloudCovered = false;
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

    if (depth < 1.0 && !hand && (u_Flags.x > 0.5 || u_Flags.y > 0.5 || u_Extras.w > 0.0)) {
        // One sky lookup serves both the fog colour and the edge fade.
        vec3 sky = aurum_tonemap(aurum_skyRadiance(dir, u_SunDir));

        if (u_Flags.x > 0.5 || u_Extras.w > 0.0) {
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
            if (u_Flags.x < 0.5) f = 0.0;
            // The same air supplies fog and shafts. A clear foreground stays transparent; under cover the
            // shorter path can still reveal a beam from an opening. Taking the larger opacity avoids counting
            // the same scattering medium twice.
            float airDensity = 0.00020 + 0.0009 * u_Volumetric2.z;
            float airRange = vl.g * u_Volumetric2.x;
            // Being under a tree must not turn the entire distant forest into dense indoor haze.
            float opticalDepth = airRange * airDensity
                    + 0.0045 * u_Volumetric2.y * 32.0 * (1.0 - exp(-airRange * 0.03125));
            float beamOpacity = 1.0 - exp(-opticalDepth * u_Extras.w);
            float atmosphereFog = f;
            f = max(f, beamOpacity);
            if (f > 0.002) {
                // Fog takes the sky's colour in that direction, so it is warmer toward the sun at golden hour.
                // Haze lit by the sun: looking toward a low sun, the air itself glows gold (Mie forward scattering).
                float toSun = dot(dir, u_SunDir);
                vec3 inscatter = u_SunGlow.rgb * airPhase(toSun) * (0.15 + 0.85 * u_SunGlow.a);
                // Haze in shadow does not glow: with volumetric light on, shadowed lanes of air stay darker.
                if (u_Extras.w > 0.0) inscatter *= vl.r * mix(1.0, 2.0, u_Volumetric2.y);
                // The haze itself is near-neutral (the sky's pinks would turn it into a flat pink soup); its warmth
                // comes from the sunlight scattered toward the sun.
                vec3 fogRad = aurum_fogRadiance(dir, u_SunDir);
                fogRad = mix(fogRad, vec3(luminance(fogRad)), u_Fog3.z);
                // Cover removes ambient skylight, while a sunlit opening can still illuminate the air.
                // Darkening the entire fog source here would also erase the beam inside a cave or room.
                fogRad *= mix(1.0, 0.002, max(u_Fog2.x, u_Volumetric2.y));
                // Extra shaft opacity carries sunlight; do not also inject a sky-coloured ambient veil.
                fogRad *= min(atmosphereFog / max(f, 0.0001), 1.0);
                vec3 fogColor = aurum_tonemap(fogRad + inscatter);
                // The scene is already display-mapped for vanilla compatibility. Compose air in display-linear
                // light; blending gamma values raised shadows and erased colour before the final grade.
                scene = pow(mix(pow(max(scene, vec3(0.0)), vec3(2.2)),
                                pow(max(fogColor, vec3(0.0)), vec3(2.2)), saturate(f)), vec3(1.0 / 2.2));
            }
        }

        if (u_Extras.w > 0.0 && u_Flags.x > 0.5) {
            // Light shafts (0.8.5). Sunlit air along the view adds the sun's forward-scattered light: beams between
            // trees and through gaps, a luminous glow toward the sun, nothing at all in shadowed air. Added as light
            // (in-scattering), never mixed in as grey air, so it cannot veil the scene (0.8.3's lesson).
            float sunlitLen = vl.r * vl.g * u_Volumetric2.x;
            float toSunB = dot(dir, u_SunDir);
            vec3 beamCol = pow(max(aurum_tonemap(u_SunGlow.rgb * airPhase(toSunB) * 1.6), vec3(0.0)), vec3(2.2));
            float amount = (1.0 - exp(-sunlitLen * 0.0022 * u_Extras.w)) * (1.0 - u_Fog2.x);
            vec3 sl = pow(max(scene, vec3(0.0)), vec3(2.2));
            scene = pow(sl + beamCol * amount * (1.0 - sl), vec3(1.0 / 2.2));
        }

        if (u_Fog3.w > 0.0) {
            // Distance mist (0.8.1, replaces 0.7.1's uniform pale-blue tint, which the user rightly called a washed
            // filter). Two layers of air averaged along the ray: a dense ground mist (16-block height scale) that
            // pools in valleys, between tree layers and over water, and a thin upper haze (60 blocks). Hilltops and
            // treetops rise out of the mist, so distance reads as layers of volume, as in Photon's forest shot.
            // The mist is a sunlit, near-neutral white (not the sky's blue), glowing warm toward the sun, and darker
            // in shadowed air where the volumetric light knows it.
            float h0 = max(u_Fog.w, 0.0), h1 = max(u_Fog.w + dir.y * dist, 0.0);
            float dh = h1 - h0;
            float mist = abs(dh) > 0.5 ? 16.0 * (exp(-h0 / 16.0) - exp(-h1 / 16.0)) / dh : exp(-h0 / 16.0);
            float thin = abs(dh) > 0.5 ? 60.0 * (exp(-h0 / 60.0) - exp(-h1 / 60.0)) / dh : exp(-h0 / 60.0);
            // 0.8.3: from 64 blocks and much thinner. Starting at 16 with a dense ground layer laid a milky veil
            // over the whole scene (A/B in the user's view: saturation 0.19 with it, 0.28 without; darks 0.31 vs
            // 0.18). Near and middle distance now stay crisp; only far land hazes.
            // 0.8.7, Photon's aerial perspective (user's same-scene shots): the foreground stays crisp, haze
            // grows with distance faster than linearly - about 11 % at 64 blocks, 27 % at 100, 50 % at 150 at
            // ground level - so tree lines and villages step back into bright air in layers.
            float far = max(dist - 24.0, 0.0);
            // 0.8.8: much lighter - at the user's 23-chunk distance 0.8.7 turned trees at 60 blocks into grey ghosts.
            float od = far * sqrt(far) * u_Fog3.w * 0.075 * (0.80 * mist + 0.45 * thin);
            float aerial = (1.0 - exp(-od)) * (1.0 - u_Fog2.x) * (1.0 - 0.8 * u_Volumetric2.y);
            if (aerial > 0.002) {
                vec3 airRad = aurum_fogRadiance(dir, u_SunDir) / 0.75;
                float lum = luminance(airRad);
                // Near-neutral, slightly warm white: 85 % grey of the horizon's brightness, 15 % of its colour.
                // Photon's haze: bright, a soft grey-blue of the low sky (30 % of its colour).
                airRad = mix(vec3(lum) * vec3(1.0, 1.0, 1.02), airRad, 0.30);
                float toSunA = dot(dir, u_SunDir);
                vec3 glow = u_SunGlow.rgb * airPhase(toSunA) * (0.25 + 0.75 * u_SunGlow.a) * 0.45;
                vec3 airLit = airRad + glow;
                // Shadowed air (behind hills and trees, per the volumetric pass) is visibly darker: volume.
                if (u_Extras.w > 0.0) airLit *= mix(0.62, 1.0, vl.r);
                vec3 airColor = aurum_tonemap(airLit);
                scene = pow(mix(pow(max(scene, vec3(0.0)), vec3(2.2)),
                                pow(max(airColor, vec3(0.0)), vec3(2.2)), min(aerial, mix(0.55, 0.40, u_SunGlow.a))), vec3(1.0 / 2.2));
            }
        }

        if (u_Flags.y > 0.5) {
            // Hide the render-distance edge by fading into the sky behind it. Chunks load in a cylinder
            // around the player, so the edge is measured horizontally (vanilla's terrain fog does the same).
            float horizontal = length(dir.xz) * dist;
            vec2 edgeRange = u_SurfaceEdge.yz;
            if (u_SurfaceEdge.x > 0.5 && horizontal > u_Fog2.y
                    && depth < texture(u_BeforeTranslucentDepth, v_Uv).r - 1e-7) {
                // Water (or another nearer transparent surface) lies in front of the copied opaque scene.
                // Keep the stronger horizon fade off opaque foliage and blocks.
                edgeRange = u_Fog2.yz;
            }
            float edge = smoothstep(edgeRange.x, edgeRange.y, horizontal);
            scene = mix(scene, sky, edge);
        }
    }

    if (!hand) {
        // Clouds go over whatever the ray meets after reaching a cloud layer (sky, or terrain seen from above).
        if (u_CloudSlab.w > 0.5 && dist > cloudEntry(dir) + 0.25) {
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
            cloudCovered = cl.a > 0.01;
        }
        vec3 hue = u_SunGlow.rgb / max(max(u_SunGlow.r, max(u_SunGlow.g, u_SunGlow.b)), 1e-4);
        vec3 rayColor = mix(vec3(1.0), hue, 0.65);
        float toSun = max(dot(dir, u_SunDir), 0.0);
        if (u_Extras.x > 0.0) {
            // Screen-space shafts over open sky. A radial average of an unobstructed sky is 1 everywhere;
            // adding that directly drew a wide, blurry wedge instead of beams. Compare neighbouring angular
            // paths so only actual cloud/terrain gaps produce a shaft. The shadow-map pass handles beams in
            // front of nearby terrain.
            float rays = lightRayMask(v_Uv);
            float r = length((v_Uv - u_SunScreen.xy) * vec2(u_SunScreen.z, 1.0));
            vec2 tangent = normalize(vec2(-(v_Uv.y - u_SunScreen.y),
                                          (v_Uv.x - u_SunScreen.x) * u_SunScreen.z) + vec2(1e-5));
            vec2 offset = tangent * (0.018 + 0.045 * r) / vec2(u_SunScreen.z, 1.0);
            float surround = 0.5 * (lightRayMask(clamp(v_Uv + offset, 0.0, 1.0))
                                  + lightRayMask(clamp(v_Uv - offset, 0.0, 1.0)));
            float shaft = max(rays - surround - 0.015, 0.0);
            if (u_PixelLight.x > 0.5) shaft = floor(shaft * 32.0 + 0.5) / 32.0;
            float openSky = (depth >= 1.0 ? 1.0 : 0.0) * (1.0 - texture(u_Clouds, v_Uv).a);
            // Rays start a little away from the sun: piled up on its face they only blow it out to white.
            scene += rayColor * pow(shaft, 1.35) * 0.85 * exp(-r * 2.2)
                   * smoothstep(0.02, 0.14, r) * openSky * u_Extras.x;
        }
    }
    if (u_Extras.y > 0.0) {
        // Photon-style glow: blend a wide blur of the expanded image back in (energy-conserving), then compress.
        // Bright sky and the sun spread a soft haze of light over what is around them; dark areas stay dark.
        vec3 b = texture(u_Bloom, v_Uv).rgb * 0.25; // four summed levels
        float l = max(scene.r, max(scene.g, scene.b));
        // 0.8.0: 0.7.9 blended 11 % of a 12x-expanded blur and lifted every dark silhouette next to the sky into
        // milky grey (user screenshots). Now about 3 % of a 5x-expanded blur: a glow around bright areas only.
        vec3 e = scene * ((1.0 / (1.0 - 0.80 * min(l, 0.999))));
        e = mix(e, b, saturate(u_Extras.y * 0.12));
        float le = max(e.r, max(e.g, e.b));
        scene = e * (1.0 / (1.0 + 0.80 * le));
    }
    if (u_Extras.z > 0.0) {
        float vis = texelFetch(u_Flare, ivec2(0), 0).r;
        if (vis > 0.002) scene += flare(v_Uv, vis) * u_Extras.z;
    }

    if (u_Flags.w > 0.5) scene = grade(scene);
    if (u_Grade.w > 0.0 && !hand && !cloudCovered && depth < 1.0) scene = sharpenDetail(scene, original, v_Uv);
    fragColor = vec4(scene, 1.0);
}
