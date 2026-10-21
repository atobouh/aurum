#version 150

#include "common.glsl"

// Phase 0: the combined post pass is compiled at startup to prove the loader and the driver, but not drawn.
// Phase 1 fills it with fog, bounce tint, tonemap and grade.
uniform sampler2D u_Scene;

in vec2 v_Uv;
out vec4 fragColor;

void main() {
    vec3 color = texture(u_Scene, v_Uv).rgb;
    fragColor = vec4(saturate(color), 1.0);
}
