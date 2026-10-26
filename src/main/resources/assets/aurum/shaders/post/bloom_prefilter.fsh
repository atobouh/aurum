#version 150

#include "common.glsl"

// Bloom, step 1: full resolution to 1/4 in one pass (four bilinear taps cover the 4x4 block), keeping only
// what is brighter than the threshold (the sun, lava, glowstone, sun glints). Each tap is weighted by
// 1 / (1 + brightness) so single hot pixels cannot flicker (Karis average).

uniform sampler2D u_Scene;
uniform vec2 u_Texel;      // full-resolution texel size
uniform vec2 u_Threshold;  // x threshold, y knee

in vec2 v_Uv;
out vec4 fragColor;

vec3 tap(vec2 o, out float w) {
    vec3 c = texture(u_Scene, v_Uv + o * u_Texel).rgb;
    w = 1.0 / (1.0 + max(c.r, max(c.g, c.b)));
    return c * w;
}

void main() {
    float w0, w1, w2, w3;
    vec3 c = tap(vec2(-1.0, -1.0), w0) + tap(vec2(1.0, -1.0), w1) + tap(vec2(-1.0, 1.0), w2) + tap(vec2(1.0, 1.0), w3);
    c /= w0 + w1 + w2 + w3;
    float l = max(c.r, max(c.g, c.b));
    if (u_Threshold.x < 0.0) {
        // Diffusion (0.7.9, Photon-style glow): no threshold. The display image is expanded back toward scene
        // brightness first (x / (1 - 0.80x): white sky and the sun weigh ~5x a mid-tone; 0.92 in 0.7.9 washed dark trees out), so the wide blur
        // glows strongly around bright areas and barely touches dark ones.
        float e = l / (1.0 - 0.80 * min(l, 0.999));
        fragColor = vec4(c * (e / max(l, 1e-4)), 1.0);
        return;
    }
    float t = u_Threshold.x, k = u_Threshold.y;
    float soft = clamp(l - t + k, 0.0, 2.0 * k);
    soft = soft * soft / (4.0 * k + 1e-4);
    float contrib = max(soft, l - t) / max(l, 1e-4);
    fragColor = vec4(c * contrib, 1.0);
}
