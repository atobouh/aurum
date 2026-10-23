#version 150

// God rays, step 1 (1/2 resolution): where is open sky? Terrain blocks the sun fully, clouds mostly, so rays
// also fall through gaps between clouds.

uniform sampler2D u_Depth;
uniform sampler2D u_Clouds;

in vec2 v_Uv;
out vec4 fragColor;

void main() {
    float sky = texture(u_Depth, v_Uv).r >= 1.0 ? 1.0 : 0.0;
    float cloud = texture(u_Clouds, v_Uv).a;
    fragColor = vec4(sky * (1.0 - 0.9 * cloud));
}
