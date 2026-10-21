#version 150

#include "common.glsl"

// Badlands glow, step 1: gather the colour of what is near the player into a tiny target (4 taps per texel).
// Sky, hand and far terrain are weighted out; mipmapping this target then gives the average in one texel.

uniform sampler2D u_Scene;
uniform sampler2D u_Depth;
uniform mat4 u_InvProj;
uniform vec2 u_TexelStep;   // offset between taps, in scene UV

in vec2 v_Uv;
out vec4 fragColor;

void main() {
    vec3 sum = vec3(0.0);
    float weight = 0.0;
    for (int i = 0; i < 4; i++) {
        vec2 uv = v_Uv + u_TexelStep * vec2(float(i & 1) - 0.5, float(i >> 1) - 0.5);
        float d = texture(u_Depth, uv).r;
        if (d >= 1.0) continue;
        vec4 vp = u_InvProj * vec4(uv * 2.0 - 1.0, d * 2.0 - 1.0, 1.0);
        float dist = length(vp.xyz / vp.w);
        float w = exp(-dist / 24.0);
        sum += pow(texture(u_Scene, uv).rgb, vec3(2.2)) * w;
        weight += w;
    }
    fragColor = vec4(sum, weight) * 0.25;
}
