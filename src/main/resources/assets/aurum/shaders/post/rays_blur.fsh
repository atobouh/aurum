#version 150

// God rays, step 2 (1/4 resolution): radial integration of the sky mask toward the sun (GPU Gems 3, chapter 13).
// Sixteen samples with a per-pixel jitter; the result is the share of open sky along the line to the sun.

uniform sampler2D u_Mask;
uniform vec2 u_SunUv;

in vec2 v_Uv;
out vec4 fragColor;

const int SAMPLES = 16;

void main() {
    // Interleaved gradient noise (Jimenez 2014): hides the sample steps without visible patterns.
    float jitter = fract(52.9829189 * fract(dot(gl_FragCoord.xy, vec2(0.06711056, 0.00583715))));
    vec2 step = (u_SunUv - v_Uv) / float(SAMPLES);
    vec2 uv = v_Uv + step * jitter;
    float sum = 0.0, weight = 0.0, w = 1.0;
    for (int i = 0; i < SAMPLES; i++) {
        sum += texture(u_Mask, clamp(uv, 0.0, 1.0)).r * w;
        weight += w;
        w *= 0.94;
        uv += step;
    }
    fragColor = vec4(sum / weight);
}
