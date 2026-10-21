#version 150

// Lens-flare occlusion into one texel: how much of the sun disc is visible (terrain and clouds), eased over a
// few frames. The composite reads this texel directly, so the CPU never waits for the GPU.

uniform sampler2D u_Depth;
uniform sampler2D u_Clouds;
uniform sampler2D u_Previous;
uniform vec4 u_Sun;    // xy sun position (uv), z disc radius (uv, vertical), w width / height
uniform float u_Blend;

out vec4 fragColor;

void main() {
    float vis = 0.0;
    for (int y = 0; y < 4; y++) {
        for (int x = 0; x < 4; x++) {
            vec2 o = (vec2(x, y) - 1.5) / 1.5 * u_Sun.z;
            vec2 uv = u_Sun.xy + vec2(o.x / u_Sun.w, o.y);
            bool inside = all(greaterThanEqual(uv, vec2(0.0))) && all(lessThanEqual(uv, vec2(1.0)));
            vis += inside && texture(u_Depth, uv).r >= 1.0 ? 1.0 : 0.0;
        }
    }
    vis /= 16.0;
    vis *= 1.0 - 0.85 * texture(u_Clouds, clamp(u_Sun.xy, 0.0, 1.0)).a;
    float prev = texelFetch(u_Previous, ivec2(0), 0).r;
    fragColor = vec4(mix(prev, vis, u_Blend));
}
