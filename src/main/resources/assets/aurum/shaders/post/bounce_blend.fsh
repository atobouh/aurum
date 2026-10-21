#version 150

// Badlands glow, step 2: turn the averaged gather into one colour and ease toward it over a couple of
// seconds, so turning around never flickers the bounce tint.

uniform sampler2D u_Gather;    // mipmapped gather target; the last level is the average
uniform sampler2D u_Previous;  // last frame's result (1x1)
uniform float u_GatherLod;
uniform float u_Blend;

out vec4 fragColor;

void main() {
    vec4 g = textureLod(u_Gather, vec2(0.5), u_GatherLod);
    vec3 prev = texelFetch(u_Previous, ivec2(0), 0).rgb;
    vec3 target = g.a > 1e-4 ? g.rgb / g.a : prev;
    fragColor = vec4(mix(prev, target, u_Blend), 1.0);
}
