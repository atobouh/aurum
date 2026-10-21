#version 150

#include "common.glsl"
#import <aurum:include/tonemap.glsl>
#import <aurum:include/sky_lut.glsl>

// Aurum clouds, two styles from one source (docs/research/SKY_EXTRAS.md, "Clouds, round two"):
//
// BLOCKY (default, Nostalgia / Story Mode): Minecraft's own cloud layout as dense rounded boxes, in two real 3D
//   layers at different heights and sizes for depth. The cloud texture is baked into a distance field
//   (CloudBaker), extruded into slabs with rounded edges, and sphere-traced. Edges are anti-aliased from the
//   closest approach of the ray, in screen pixels, so they stay crisp close up.
// SOFT (CLOUD_SOFT, Better Clouds-like): the same layout as soft, fluffy, translucent cumulus. A short
//   ray-march through a 2.5D density (layout distance field, warped and eroded by baked noise) with cheap
//   self-shadowing from the depth below each cloud's top, plus a high streaky cirrus layer.
//
// Output: tonemapped colour premultiplied by coverage, coverage in alpha. The composite lays it over the scene
// wherever the ray reaches the cloud layers before the terrain does (checked there at full resolution).

uniform sampler2D u_CloudSdf;
uniform sampler2D u_Noise;

uniform mat4 u_InvProj;
uniform mat4 u_InvView;
uniform vec3 u_SunDir;
uniform vec3 u_LightDir;
uniform vec3 u_LightColor;
uniform vec3 u_SkyAmbient;
uniform vec4 u_SunGlow;      // rgb warm scattered sunlight, a = golden-hour amount
uniform vec4 u_Layers;       // x cloud base height relative to camera, y max distance, z time (blocks, wrapped), w unused
uniform vec4 u_Offset;       // xy layer 1 texel offset at the camera, zw layer 2
uniform vec4 u_Shape;        // x blocks per texel, y coverage (texels), z rain, w unused
uniform vec4 u_Look;         // x shading amount, y brightness, z unused, w haze density
uniform vec2 u_NoiseCam;     // camera xz plus wind drift (x), wrapped to 4096 blocks on the CPU for precision
uniform float u_PixelAngle;  // radians per output pixel
uniform float u_TexSize;     // cloud texels across the baked field (256 for vanilla)
uniform vec3 u_Tint;         // golden-hour push toward peach and mauve (references: Nostalgia at sunset)

in vec2 v_Uv;
out vec4 fragColor;

// Baked tiling noise in world space, moving with the cloud layout. Tile sizes and stretch factors are chosen
// so 4096 blocks is a whole number of tiles: the CPU-side wrap never shows as a seam.
float noise(vec2 xz, vec2 stretch, float blocksPerTile, vec2 evolve) {
    return texture(u_Noise, ((xz + u_NoiseCam) * stretch + evolve) / blocksPerTile).r;
}

float cloud2d(vec2 xz, vec2 offset, float scale) {
    vec2 uv = (offset + xz / scale) / u_TexSize;
    // Shapes may only shrink in clear weather: growing a staircase outline rounds it into blobs (seen side by
    // side with Nostalgia's crisp boxes). Rain may thicken them.
    float shrink = max(-u_Shape.y, -0.8 * u_Shape.z);
    return (texture(u_CloudSdf, uv).r + shrink) * scale;
}

float lightUp() {
    return smoothstep(-0.05, 0.15, u_LightDir.y);
}

// Distant clouds sink into the sky colour behind them, like the haze in the references.
vec3 aerial(vec3 c, vec3 sky, float t) {
    float maxD = u_Layers.y;
    // Starts close so every step of distance reads as a step back in tone: depth through colour.
    float f = pow(smoothstep(0.04 * maxD, maxD, t), 0.75) * 0.88;
    f = 1.0 - (1.0 - f) * exp(-t * u_Look.w);
    return mix(c, sky, f);
}

#ifndef CLOUD_SOFT
// ------------------------------------------------------------------------------------------------ BLOCKY

const int STEPS = 40;

// Layer 1: the main sheet. Layer 2: larger, sparser boxes 20 blocks higher, rotated so its layout differs; seen
// through the gaps of layer 1, it gives the sky depth without cluttering it.
const float L1_CENTRE = 6.0, L1_HALF = 6.0, L1_ROUND = 2.5;
const float L2_CENTRE = 26.0, L2_HALF = 4.5, L2_ROUND = 2.2, L2_SCALE = 1.15, L2_SHRINK = 0.0;

vec2 l2coords(vec2 xz) {
    return vec2(xz.y, -xz.x);
}

float layer2d(vec2 xz, int layer) {
    if (layer == 0) return cloud2d(xz, u_Offset.xy, u_Shape.x);
    float s = u_Shape.x * L2_SCALE;
    return cloud2d(l2coords(xz), u_Offset.zw, s) + L2_SHRINK * s;
}

// Rounded extrusion of a 2D shape (Quilez): a box-like slab with flat faces and rounded edges.
float slab(float d2, float dy, float halfT, float r) {
    vec2 w = vec2(d2 + r, abs(dy) - (halfT - r));
    return min(max(w.x, w.y), 0.0) + length(max(w, 0.0)) - r;
}

// Distance to both layers. A layer is only fetched when it could be closer than the other (vertical bound).
float map(vec3 p, out int layer, out float d2out) {
    float base = u_Layers.x;
    float dy1 = p.y - (base + L1_CENTRE), dy2 = p.y - (base + L2_CENTRE);
    float b1 = abs(dy1) - L1_HALF, b2 = abs(dy2) - L2_HALF;
    float best = 1e9;
    layer = 0;
    d2out = 0.0;
    if (b1 < b2) {
        float d2 = layer2d(p.xz, 0);
        best = slab(d2, dy1, L1_HALF, L1_ROUND);
        d2out = d2;
        if (b2 < best) {
            float e2 = layer2d(p.xz, 1);
            float d = slab(e2, dy2, L2_HALF, L2_ROUND);
            if (d < best) { best = d; layer = 1; d2out = e2; }
        }
    } else {
        float e2 = layer2d(p.xz, 1);
        best = slab(e2, dy2, L2_HALF, L2_ROUND);
        layer = 1;
        d2out = e2;
        if (b1 < best) {
            float d2 = layer2d(p.xz, 0);
            float d = slab(d2, dy1, L1_HALF, L1_ROUND);
            if (d < best) { best = d; layer = 0; d2out = d2; }
        }
    }
    return best;
}

vec3 normalAt(vec3 p, int layer, float d2) {
    float e = u_Shape.x * 0.25;
    vec2 g = vec2(layer2d(p.xz + vec2(e, 0.0), layer) - d2, layer2d(p.xz + vec2(0.0, e), layer) - d2);
    g = length(g) > 1e-5 ? normalize(g) : vec2(0.0);
    float centre = u_Layers.x + (layer == 0 ? L1_CENTRE : L2_CENTRE);
    float halfT = layer == 0 ? L1_HALF : L2_HALF;
    float r = layer == 0 ? L1_ROUND : L2_ROUND;
    float dy = p.y - centre;
    vec2 w = vec2(d2 + r, abs(dy) - (halfT - r));
    vec2 q = max(w, 0.0);
    vec2 gw = length(q) > 1e-5 ? q / length(q) : (w.x > w.y ? vec2(1.0, 0.0) : vec2(0.0, 1.0));
    vec3 n = vec3(g.x * gw.x, sign(dy) * gw.y, g.y * gw.x);
    // Flat spots of the distance field have no horizontal gradient; never normalise a zero vector (NaN = black).
    return dot(n, n) > 1e-8 ? normalize(n) : vec3(0.0, sign(dy + 1e-4), 0.0);
}

// Story Mode shading with depth through tone, matched side by side with Nostalgia 0 and 6: the side faces are
// the brightest, near-white parts (lit, with a slight lift toward the top), bellies are a softer lavender with a
// slow tonal drift, tops bright; a warm rim on edges facing the sun; the higher layer slightly lighter and
// cooler, like it is further away.
vec3 shade(vec3 p, vec3 n, vec3 dir, int layer) {
    float up = lightUp();
    float ndl = saturate(dot(n, u_LightDir) * 0.6 + 0.4);
    float centre = u_Layers.x + (layer == 0 ? L1_CENTRE : L2_CENTRE);
    float halfT = layer == 0 ? L1_HALF : L2_HALF;
    float h = saturate((p.y - centre) / (2.0 * halfT) + 0.5);
    // Clouds scatter all colours alike: take the sky light's brightness with only part of its blue, or every
    // cloud turns blue-grey (the references are near-white with a lavender tint in shade).
    vec3 skyL = mix(u_SkyAmbient, vec3(luminance(u_SkyAmbient)), 0.55);

    vec3 top = skyL * 1.30 + u_LightColor * 0.55 * ndl * up;
    float drift = noise(p.xz, vec2(1.0), 256.0, vec2(0.0));
    vec3 belly = (skyL * vec3(0.98, 0.96, 1.08) + u_LightColor * 0.14 * up) * (0.93 + 0.14 * drift);
    vec3 side = (skyL * 1.20 + u_LightColor * 0.45 * ndl * up) * mix(0.90, 1.02, h);
    // Blend by facing: flat faces get their own tone, the rounded edges blend smoothly between them.
    float topW = smoothstep(0.35, 0.9, n.y), botW = smoothstep(0.35, 0.9, -n.y);
    vec3 c = side * (1.0 - topW - botW) + top * topW + belly * botW;
    c = mix(u_SkyAmbient * 1.1 + u_LightColor * 0.35 * up, c, u_Look.x);
    if (layer == 1) c *= vec3(1.03, 1.05, 1.10);
    float toSun = saturate(dot(dir, u_SunDir));
    float rim = (1.0 - abs(n.y)) * saturate(dot(n, u_LightDir));
    c += u_SunGlow.rgb * (pow(toSun, 6.0) * (1.0 - abs(n.y)) + rim * 0.25) * (0.3 + 0.7 * u_SunGlow.a);
    return c * u_Tint * u_Look.y * (1.0 - 0.45 * u_Shape.z);
}

vec4 clouds(vec3 dir, vec3 sky) {
    float base = u_Layers.x;
    float lo = base + L1_CENTRE - L1_HALF, hi = base + L2_CENTRE + L2_HALF;
    float tn = 0.0, tf = u_Layers.y;
    if (abs(dir.y) > 1e-4) {
        float ta = lo / dir.y, tb = hi / dir.y;
        tn = max(min(ta, tb), 0.0);
        tf = min(max(ta, tb), u_Layers.y);
    } else if (lo > 0.0 || hi < 0.0) {
        return vec4(0.0);
    }
    if (tn >= tf) return vec4(0.0);

    float t = tn;
    int layer;
    float d2;
    // Closest approach in pixels, for anti-aliased edges; and where it happened, for its colour.
    float bestRatio = 1e9, bestT = t;
    int bestLayer = 0;
    bool hit = false;
    for (int i = 0; i < STEPS; i++) {
        float d = map(dir * t, layer, d2);
        float pix = max(t * u_PixelAngle, 0.02);
        float ratio = d / pix;
        if (ratio < bestRatio) { bestRatio = ratio; bestT = t; bestLayer = layer; }
        if (ratio < 0.35) { hit = true; break; }
        t += max(d * 0.9, pix * 0.5);
        if (t > tf) break;
    }
    // Inside: opaque. A ray that only grazed an edge gets partial coverage (one pixel of soft edge).
    float alpha = hit ? 1.0 : saturate(1.2 - bestRatio);
    if (alpha <= 0.0) return vec4(0.0);
    vec3 p = dir * bestT;
    vec3 n = normalAt(p, bestLayer, layer2d(p.xz, bestLayer));
    vec3 c = shade(p, n, dir, bestLayer);
    alpha *= 1.0 - smoothstep(0.75 * u_Layers.y, u_Layers.y, bestT);
    return vec4(aurum_tonemap(aerial(c, sky, bestT)) * alpha, alpha);
}

#else
// ------------------------------------------------------------------------------------------------ SOFT

const int STEPS = 18;
const float BOTTOM = -2.0, TOP = 30.0;   // volume, relative to the cloud base
// Density at a point, plus how deep below the local cloud top it is (for self-shadowing).
float density(vec3 p, out float belowTop, out float height) {
    belowTop = 0.0;
    height = 0.0;
    float y = p.y - (u_Layers.x + BOTTOM);
    float s = u_Shape.x;
    // Far from any cloud cell: empty, one fetch.
    float raw = cloud2d(p.xz, u_Offset.xy, s) / s;
    if (raw > 2.2) return 0.0;
    float warp = noise(p.xz, vec2(1.0), 256.0, vec2(0.0)) - 0.5;
    float d2 = cloud2d(p.xz + vec2(warp, -warp) * 26.0, u_Offset.xy, s) / s;
    float cover = smoothstep(1.6, -0.7, d2);
    if (cover <= 0.0) return 0.0;
    float n = noise(p.xz, vec2(1.0, 1.5), 64.0, vec2(y * 2.5, u_Layers.z * 0.25));
    float top = mix(5.0, TOP - 2.0, cover * cover) * (0.65 + 0.7 * n);
    float bottom = 2.0 + 3.0 * (1.0 - n) * (1.0 - cover);
    float dens = smoothstep(bottom, bottom + 3.0, y) * smoothstep(top, top - max(top * 0.45, 2.0), y) * cover;
    dens = saturate(dens * 1.9 - (1.0 - n) * 0.4);
    belowTop = max(top - y, 0.0);
    height = saturate((y - bottom) / max(top - bottom, 1.0));
    return dens;
}

vec4 clouds(vec3 dir, vec3 sky) {
    float lo = u_Layers.x + BOTTOM, hi = u_Layers.x + TOP;
    float tn = 0.0, tf = u_Layers.y;
    if (abs(dir.y) > 1e-4) {
        float ta = lo / dir.y, tb = hi / dir.y;
        tn = max(min(ta, tb), 0.0);
        tf = min(max(ta, tb), u_Layers.y);
    } else if (lo > 0.0 || hi < 0.0) {
        tf = -1.0;
    }
    vec3 col = vec3(0.0);
    float trans = 1.0;
    float tSum = 0.0, wSum = 0.0;
    if (tn < tf) {
        // Grazing rays cross a lot of slab; cap the segment and let the haze hide the rest.
        tf = min(tf, tn + 420.0);
        float dt = (tf - tn) / float(STEPS);
        float jitter = fract(52.9829189 * fract(dot(gl_FragCoord.xy, vec2(0.06711056, 0.00583715))));
        float t = tn + dt * jitter;
        float up = lightUp();
        float toSun = saturate(dot(dir, u_SunDir));
        // Mostly even scattering plus a forward lobe: silver linings toward the sun.
        float phase = 0.8 + 1.6 * pow(toSun, 8.0) * (0.4 + 0.6 * u_SunGlow.a);
        vec3 belly = u_SkyAmbient * vec3(0.92, 0.92, 1.02) + u_LightColor * 0.10 * up;
        vec3 topAmb = u_SkyAmbient * 1.2;
        for (int i = 0; i < STEPS; i++) {
            float belowTop, height;
            float d = density(dir * t, belowTop, height);
            if (d > 0.0) {
                float sigma = d * 0.16;
                float a = 1.0 - exp(-sigma * dt);
                // Light reaching this point through the cloud above it (Beer), with a multiple-scattering floor.
                // Depth below the top stands in for the path to the sun; it is not stretched for a low sun,
                // because then the light enters through the cloud's side, not its top.
                float od = belowTop * 0.05 + u_Shape.z * 0.6;
                float sunT = exp(-od) * 0.75 + 0.25 * exp(-od * 0.2);
                vec3 light = u_LightColor * sunT * phase * up + mix(belly, topAmb, height);
                col += trans * a * light;
                tSum += t * trans * a;
                wSum += trans * a;
                trans *= 1.0 - a;
                if (trans < 0.03) break;
            }
            t += dt;
        }
    }
    float alpha = 1.0 - trans;
    vec4 result = vec4(0.0);
    if (alpha > 0.002) {
        float tAvg = tSum / max(wSum, 1e-4);
        vec3 c = col / alpha * u_Tint * u_Look.y * (1.0 - 0.4 * u_Shape.z);
        c = mix(u_SkyAmbient * 1.1 + u_LightColor * 0.35 * lightUp(), c, u_Look.x);
        alpha *= 1.0 - smoothstep(0.75 * u_Layers.y, u_Layers.y, tAvg);
        result = vec4(aurum_tonemap(aerial(c, sky, tAvg)) * alpha, alpha);
    }

    // High cirrus streaks: smooth, so they never look blocky. Behind the cumulus.
    float hc = u_Layers.x + 230.0;
    if (dir.y > 1e-3 && hc / dir.y > 0.0) {
        float t = hc / dir.y;
        if (t < u_Layers.y * 2.5) {
            vec2 xz = (dir * t).xz;
            float n = noise(xz, vec2(0.5, 1.5), 512.0, vec2(u_Layers.z * 0.5, 0.0));
            float n2 = noise(xz, vec2(0.75, 2.0), 128.0, vec2(u_Layers.z, 31.0));
            float cover = smoothstep(0.52, 0.85, n * 0.75 + n2 * 0.35) * 0.45 * (1.0 - 0.7 * u_Shape.z);
            cover *= 1.0 - smoothstep(u_Layers.y, u_Layers.y * 2.5, t);
            if (cover > 0.002) {
                vec3 c = (u_SkyAmbient * 1.25 + u_LightColor * 0.30 * lightUp()) * u_Tint * u_Look.y;
                c += u_SunGlow.rgb * pow(saturate(dot(dir, u_SunDir)), 6.0) * (0.3 + 0.7 * u_SunGlow.a);
                vec3 cc = aurum_tonemap(aerial(c, sky, t * 0.5));
                result = vec4(result.rgb + cc * cover * (1.0 - result.a), result.a + cover * (1.0 - result.a));
            }
        }
    }
    return result;
}
#endif

void main() {
    vec4 vp = u_InvProj * vec4(v_Uv * 2.0 - 1.0, 1.0, 1.0);
    vec3 dir = normalize(mat3(u_InvView) * (vp.xyz / vp.w));
    vec3 sky = aurum_skyRadiance(dir, u_SunDir);
    fragColor = clouds(dir, sky);
}
