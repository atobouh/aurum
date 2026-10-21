#version 150

#include "common.glsl"
#import <aurum:include/tonemap.glsl>
#import <aurum:include/sky_lut.glsl>

// Stylised clouds (Story Mode-like, see docs/research/SKY_EXTRAS.md), drawn at half resolution.
//
// Layer A: Minecraft's own cloud layout as rounded, puffy slabs. The cloud texture is baked into a distance
//          field (CloudBaker), extruded into a slab with fully rounded edges, and sphere-traced.
// Layer B: a higher, larger, softer and thinner sheet of the same shapes, drifting slower, for depth.
// Output: tonemapped colour premultiplied by coverage, and coverage in alpha. The composite lays it over the
// scene wherever the ray reaches the cloud before the terrain does (checked there at full resolution).

uniform sampler2D u_CloudSdf;

uniform mat4 u_InvProj;
uniform mat4 u_InvView;
uniform vec3 u_SunDir;
uniform vec3 u_LightDir;
uniform vec3 u_LightColor;
uniform vec3 u_SkyAmbient;
uniform vec4 u_SunGlow;      // rgb warm scattered sunlight, a = golden-hour amount
uniform vec4 u_Slab;         // x layer A centre height relative to camera, y half thickness, z rounding, w max distance
uniform vec4 u_Offset;       // xy layer A texel offset at the camera, zw layer B
uniform vec4 u_Shape;        // x blocks per texel (A), y coverage (texels), z rain, w layer B height relative to camera
uniform vec4 u_Look;         // x shading amount, y brightness, z layer B opacity, w haze density
uniform float u_PixelAngle;  // radians per half-resolution pixel
uniform float u_TexSize;     // cloud texels across the baked field (256 for vanilla)
uniform vec3 u_Tint;         // golden-hour push toward peach and mauve (references: Nostalgia at sunset)

in vec2 v_Uv;
out vec4 fragColor;

const int STEPS = 28;
const float B_SCALE = 2.6;   // layer B shapes are this much larger

float cloud2d(vec2 xz, vec2 offset, float scale) {
    vec2 uv = (offset + xz / scale) / u_TexSize;
    return (texture(u_CloudSdf, uv).r - u_Shape.y) * scale;
}

// Rounded extrusion of the 2D shape: a slab whose edges are fully round, like a pillow.
float slab(vec3 p, out float d2) {
    d2 = cloud2d(p.xz, u_Offset.xy, u_Shape.x);
    float r = u_Slab.z;
    vec2 w = vec2(d2 + r, abs(p.y - u_Slab.x) - (u_Slab.y - r));
    return min(max(w.x, w.y), 0.0) + length(max(w, 0.0)) - r;
}

vec3 slabNormal(vec3 p, float d2) {
    float e = u_Shape.x * 0.35;
    vec2 g = vec2(cloud2d(p.xz + vec2(e, 0.0), u_Offset.xy, u_Shape.x) - d2,
                  cloud2d(p.xz + vec2(0.0, e), u_Offset.xy, u_Shape.x) - d2);
    g = length(g) > 1e-5 ? normalize(g) : vec2(0.0);
    float r = u_Slab.z;
    float dy = p.y - u_Slab.x;
    vec2 w = vec2(d2 + r, abs(dy) - (u_Slab.y - r));
    vec2 q = max(w, 0.0);
    vec2 gw = length(q) > 1e-5 ? q / length(q) : (w.x > w.y ? vec2(1.0, 0.0) : vec2(0.0, 1.0));
    return normalize(vec3(g.x * gw.x, sign(dy) * gw.y, g.y * gw.x));
}

// Light on a cloud surface. Deliberately soft and bright: tops catch the sun, bellies stay a light lavender
// (references never show grey or black undersides in daylight), thin edges glow when backlit.
vec3 shade(vec3 n, vec3 dir, float inner, float rain) {
    float ndl = dot(n, u_LightDir) * 0.5 + 0.5;
    float lightUp = smoothstep(-0.05, 0.15, u_LightDir.y);
    vec3 sun = u_LightColor * (ndl * ndl) * lightUp;
    // Belly: sky light plus a little warm light scattered through the cloud, slightly cooler.
    vec3 belly = u_SkyAmbient * vec3(0.88, 0.88, 1.0) + u_LightColor * 0.06 * lightUp;
    vec3 top = u_SkyAmbient * 1.25;
    vec3 amb = mix(belly, top, n.y * 0.5 + 0.5);
    // Deep inside a big cloud the belly darkens a touch (fake thickness).
    amb *= mix(1.0, 0.82, saturate(inner) * saturate(-n.y));
    float shading = u_Look.x;
    vec3 c = mix(u_SkyAmbient * 1.1 + u_LightColor * 0.35 * lightUp, sun + amb, shading);
    // Silver lining: thin edges glow toward the sun, warm at golden hour.
    float toSun = saturate(dot(dir, u_SunDir));
    float thin = 1.0 - saturate(inner);
    c += u_SunGlow.rgb * (pow(toSun, 6.0) * thin * (0.4 + 0.8 * u_SunGlow.a));
    return c * u_Tint * u_Look.y * (1.0 - 0.45 * rain);
}

// Distant clouds sink into the sky colour behind them, like the haze in the references.
vec3 aerial(vec3 c, vec3 sky, float t, float maxD) {
    float f = smoothstep(0.2 * maxD, maxD, t) * 0.85;
    f = 1.0 - (1.0 - f) * exp(-t * u_Look.w);
    return mix(c, sky, f);
}

void main() {
    vec4 vp = u_InvProj * vec4(v_Uv * 2.0 - 1.0, 1.0, 1.0);
    vec3 dir = normalize(mat3(u_InvView) * (vp.xyz / vp.w));
    float maxD = u_Slab.w;
    float rain = u_Shape.z;
    vec3 sky = aurum_skyRadiance(dir, u_SunDir);

    vec4 a = vec4(0.0);
    float tA = 1e9;
    // ---- Layer A: sphere-traced rounded slabs.
    {
        float lo = u_Slab.x - u_Slab.y, hi = u_Slab.x + u_Slab.y;
        float tn = 0.0, tf = maxD;
        if (abs(dir.y) > 1e-4) {
            float ta = lo / dir.y, tb = hi / dir.y;
            tn = max(min(ta, tb), 0.0);
            tf = min(max(ta, tb), maxD);
        } else if (lo > 0.0 || hi < 0.0) {
            tf = -1.0;
        }
        if (tn < tf) {
            float t = tn;
            float d2 = 0.0;
            bool hit = false;
            for (int i = 0; i < STEPS; i++) {
                float d = slab(dir * t, d2);
                float eps = max(0.05, t * u_PixelAngle * 0.6);
                if (d < eps) { hit = true; break; }
                t += max(d * 0.9, eps);
                if (t > tf) break;
            }
            if (hit) {
                vec3 p = dir * t;
                vec3 n = slabNormal(p, d2);
                float inner = -d2 / (u_Shape.x * 1.5);
                vec3 c = shade(n, dir, inner, rain);
                // Soft, slightly see-through edges (Story Mode clouds are never cut out hard).
                float alpha = mix(0.85, 1.0, smoothstep(0.0, u_Shape.x * 0.8, -d2)) * (1.0 - smoothstep(0.75 * maxD, maxD, t));
                a = vec4(aurum_tonemap(aerial(c, sky, t, maxD)) * alpha, alpha);
                tA = t;
            }
        }
    }

    // ---- Layer B: one soft, flat, higher sheet.
    vec4 b = vec4(0.0);
    float tB = 1e9;
    if (u_Look.z > 0.0 && abs(dir.y) > 1e-4 && u_Shape.w / dir.y > 0.0) {
        float t = u_Shape.w / dir.y;
        float maxB = maxD * 2.0;
        if (t < maxB) {
            float scale = u_Shape.x * B_SCALE;
            vec2 xz = (dir * t).xz;
            float d2 = cloud2d(vec2(xz.y, -xz.x), u_Offset.zw, scale) / scale;
            float cover = smoothstep(0.1, -0.45, d2);
            if (cover > 0.0) {
                float toSun = saturate(dot(dir, u_SunDir));
                float lightUp = smoothstep(-0.05, 0.15, u_LightDir.y);
                vec3 c = (u_SkyAmbient * 1.15 + u_LightColor * 0.30 * lightUp) * u_Tint * u_Look.y * (1.0 - 0.45 * rain);
                c += u_SunGlow.rgb * pow(toSun, 6.0) * (1.0 - smoothstep(-1.5, 0.0, -d2) * 0.5) * (0.4 + 0.8 * u_SunGlow.a);
                float alpha = cover * u_Look.z * (1.0 - smoothstep(0.4 * maxB, maxB, t));
                b = vec4(aurum_tonemap(aerial(c, sky, t * 0.6, maxD)) * alpha, alpha);
                tB = t;
            }
        }
    }

    // Nearer layer over the farther one.
    vec4 nearL = tA <= tB ? a : b;
    vec4 farL = tA <= tB ? b : a;
    fragColor = vec4(nearL.rgb + farL.rgb * (1.0 - nearL.a), nearL.a + farL.a * (1.0 - nearL.a));
}
