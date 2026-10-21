#version 150

// Bloom, step 2: halve the resolution (dual filter, Bjorge 2015): five bilinear taps.

uniform sampler2D u_Src;
uniform vec2 u_Texel;  // source texel size

in vec2 v_Uv;
out vec4 fragColor;

void main() {
    vec2 h = u_Texel;
    vec3 c = texture(u_Src, v_Uv).rgb * 4.0;
    c += texture(u_Src, v_Uv + vec2(-h.x, -h.y)).rgb;
    c += texture(u_Src, v_Uv + vec2(h.x, -h.y)).rgb;
    c += texture(u_Src, v_Uv + vec2(-h.x, h.y)).rgb;
    c += texture(u_Src, v_Uv + vec2(h.x, h.y)).rgb;
    fragColor = vec4(c / 8.0, 1.0);
}
