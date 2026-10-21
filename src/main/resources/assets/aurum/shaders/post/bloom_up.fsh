#version 150

// Bloom, step 3: double the resolution with a tent filter (dual filter, eight bilinear taps) and add the
// level of that size, so every level contributes.

uniform sampler2D u_Src;
uniform sampler2D u_Add;
uniform vec2 u_Texel;  // source texel size

in vec2 v_Uv;
out vec4 fragColor;

void main() {
    vec2 h = u_Texel;
    vec3 c = texture(u_Src, v_Uv + vec2(-2.0 * h.x, 0.0)).rgb;
    c += texture(u_Src, v_Uv + vec2(2.0 * h.x, 0.0)).rgb;
    c += texture(u_Src, v_Uv + vec2(0.0, -2.0 * h.y)).rgb;
    c += texture(u_Src, v_Uv + vec2(0.0, 2.0 * h.y)).rgb;
    c += texture(u_Src, v_Uv + vec2(-h.x, -h.y)).rgb * 2.0;
    c += texture(u_Src, v_Uv + vec2(h.x, -h.y)).rgb * 2.0;
    c += texture(u_Src, v_Uv + vec2(-h.x, h.y)).rgb * 2.0;
    c += texture(u_Src, v_Uv + vec2(h.x, h.y)).rgb * 2.0;
    fragColor = vec4(c / 12.0 + texture(u_Add, v_Uv).rgb, 1.0);
}
