#version 150

#include "common.glsl"

// Temporal anti-aliasing resolve (see TemporalAA.java). The world was drawn with a sub-pixel jitter; each pixel
// blends the new frame with the history reprojected through the camera's motion, after clamping the history to
// the colours around the pixel in the new frame (so moving things and disocclusions do not ghost).

uniform sampler2D u_Current;    // this frame, fully composited (display)
uniform sampler2D u_History;    // last resolved frame
uniform sampler2D u_Depth;      // world depth of this frame
uniform sampler2D u_HandDepth;  // hand depth (< 1 where the hand is)
uniform sampler2D u_OpaqueDepth; // depth before translucent terrain (1 everywhere when not captured)
uniform mat4 u_InvProj;         // this frame's (jittered) inverse projection
uniform mat4 u_InvView;         // rotation only, view -> world
uniform mat4 u_PrevViewProj;    // last frame's unjittered projection * rotation-only view
uniform vec3 u_CamDelta;        // camera position now minus last frame (blocks)
uniform vec4 u_Params;          // x history valid, y new-frame weight, z sharpen, w hand depth valid
uniform vec4 u_Jitter;          // xy jitter in uv, zw texel size

in vec2 v_Uv;
out vec4 fragColor;

vec3 toYCoCg(vec3 c) {
    return vec3(dot(c, vec3(0.25, 0.5, 0.25)), dot(c, vec3(0.5, 0.0, -0.5)), dot(c, vec3(-0.25, 0.5, -0.25)));
}
vec3 fromYCoCg(vec3 c) {
    return vec3(c.x + c.y - c.z, c.x + c.z, c.x - c.y - c.z);
}

// Catmull-Rom history fetch from five bilinear taps (after M. Pettineo): keeps the history sharp while it is
// resampled every frame, where plain bilinear would soften the image a little more with each move.
vec3 historyCatmullRom(vec2 uv) {
    vec2 size = vec2(textureSize(u_History, 0));
    vec2 samplePos = uv * size;
    vec2 tc1 = floor(samplePos - 0.5) + 0.5;
    vec2 f = samplePos - tc1;
    vec2 w0 = f * (-0.5 + f * (1.0 - 0.5 * f));
    vec2 w1 = 1.0 + f * f * (-2.5 + 1.5 * f);
    vec2 w2 = f * (0.5 + f * (2.0 - 1.5 * f));
    vec2 w3 = f * f * (-0.5 + 0.5 * f);
    vec2 w12 = w1 + w2;
    vec2 tc0 = (tc1 - 1.0) / size;
    vec2 tc3 = (tc1 + 2.0) / size;
    vec2 tc12 = (tc1 + w2 / w12) / size;
    vec3 r = texture(u_History, vec2(tc12.x, tc0.y)).rgb * (w12.x * w0.y)
           + texture(u_History, vec2(tc0.x, tc12.y)).rgb * (w0.x * w12.y)
           + texture(u_History, vec2(tc12.x, tc12.y)).rgb * (w12.x * w12.y)
           + texture(u_History, vec2(tc3.x, tc12.y)).rgb * (w3.x * w12.y)
           + texture(u_History, vec2(tc12.x, tc3.y)).rgb * (w12.x * w3.y);
    float wsum = w12.x * w0.y + w0.x * w12.y + w12.x * w12.y + w3.x * w12.y + w12.x * w3.y;
    return max(r / wsum, vec3(0.0));
}

void main() {
    vec2 texel = u_Jitter.zw;
    vec3 cur = texture(u_Current, v_Uv).rgb;
    bool hand = u_Params.w > 0.5 && texture(u_HandDepth, v_Uv).r < 1.0;
    if (u_Params.x < 0.5 || hand) { fragColor = vec4(cur, 1.0); return; }

    // Neighbourhood of the new frame (3x3), in YCoCg: mean and spread for variance clipping, plus the closest
    // depth so a thin foreground edge reprojects with the foreground's motion.
    vec3 m1 = vec3(0.0), m2 = vec3(0.0), lo = vec3(1e9), hi = vec3(-1e9);
    float closest = 1.0;
    vec2 closestUv = v_Uv;
    vec3 blur = vec3(0.0), rgbLo = vec3(1e9), rgbHi = vec3(-1e9);
    for (int y = -1; y <= 1; y++) {
        for (int x = -1; x <= 1; x++) {
            vec2 o = vec2(x, y) * texel;
            vec3 c = texture(u_Current, v_Uv + o).rgb;
            if (x != 0 || y != 0) blur += c;
            rgbLo = min(rgbLo, c); rgbHi = max(rgbHi, c);
            vec3 q = toYCoCg(c);
            m1 += q; m2 += q * q;
            lo = min(lo, q); hi = max(hi, q);
            float d = texture(u_Depth, v_Uv + o).r;
            if (d < closest) { closest = d; closestUv = v_Uv + o; }
        }
    }
    blur /= 8.0;
    vec3 mean = m1 / 9.0;
    vec3 sigma = sqrt(max(m2 / 9.0 - mean * mean, vec3(0.0)));

    // Reproject: where was this surface last frame?
    vec4 vp = u_InvProj * vec4(closestUv * 2.0 - 1.0, closest * 2.0 - 1.0, 1.0);
    vec3 view = vp.xyz / vp.w;
    vec4 prevClip;
    if (closest >= 1.0) {
        // Sky: only the camera's rotation moves it.
        prevClip = u_PrevViewProj * vec4(mat3(u_InvView) * normalize(view), 0.0);
    } else {
        vec3 world = mat3(u_InvView) * view + u_CamDelta;
        prevClip = u_PrevViewProj * vec4(world, 1.0);
    }
    vec2 prevUv = prevClip.xy / max(prevClip.w, 1e-5) * 0.5 + 0.5;
    if (prevClip.w <= 0.0 || any(lessThan(prevUv, vec2(0.0))) || any(greaterThan(prevUv, vec2(1.0)))) {
        fragColor = vec4(cur, 1.0);
        return;
    }
    vec2 motionPx = (v_Uv - prevUv) / texel;

    vec3 hist = toYCoCg(historyCatmullRom(prevUv));
    // Variance clip, bounded by the min/max box: history that no longer fits here is pulled toward this frame.
    // Tight (0.9 sigma): waving leaves have no motion vectors, so a loose box lets them smear.
    vec3 boxLo = max(lo, mean - sigma * 0.9), boxHi = min(hi, mean + sigma * 0.9);
    vec3 centre = 0.5 * (boxHi + boxLo), extent = max(0.5 * (boxHi - boxLo), vec3(1e-4));
    vec3 offs = hist - centre;
    vec3 ratio = abs(offs / extent);
    float maxRatio = max(ratio.x, max(ratio.y, ratio.z));
    if (maxRatio > 1.0) hist = centre + offs / maxRatio;
    vec3 histRgb = fromYCoCg(hist);

    // New-frame weight: 10% when still (eight frames of jitter accumulate), more while moving fast so motion
    // stays crisp. Luminance weighting keeps bright specks (sun glints) from flickering.
    float alpha = mix(u_Params.y, 0.35, smoothstep(2.0, 24.0, length(motionPx)));
    // Water (in front of the opaque scene) animates its own texture: keep most of the new frame so its pixel
    // lines stay crisp instead of averaging away.
    if (texture(u_Depth, v_Uv).r < texture(u_OpaqueDepth, v_Uv).r - 1e-6) alpha = max(alpha, 0.5);
    float wc = alpha / (1.0 + luminance(cur));
    float wh = (1.0 - alpha) / (1.0 + luminance(histRgb));
    vec3 res = (cur * wc + histRgb * wh) / (wc + wh);

    // Light sharpening against the soft 3x3 average, limited to the neighbourhood so it cannot ring.
    if (u_Params.z > 0.0) {
        res = clamp(res + (res - blur) * u_Params.z, rgbLo, rgbHi);
    }
    fragColor = vec4(max(res, vec3(0.0)), 1.0);
}
