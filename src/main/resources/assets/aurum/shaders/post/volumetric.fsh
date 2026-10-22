#version 150

// Volumetric light (1/2 resolution): how much of the air along each view ray is in sunlight, from the shadow
// map. Shadowed air (behind trees, under islands, inside caves) stays dark, sunlit air glows in the composite:
// that contrast is what draws light beams, even with the sun off-screen. Sixteen jittered steps, one shadow
// lookup each; the composite smooths the jitter with an edge-aware upscale.

uniform sampler2D u_Depth;
uniform sampler2DShadow u_ShadowMap;
uniform mat4 u_ShadowMatrix;  // camera-relative world position -> light clip space
uniform mat4 u_InvProj;
uniform mat4 u_InvView;
uniform vec4 u_Params;        // x distortion, y range (blocks), z depth bias

in vec2 v_Uv;
out vec4 fragColor;

const int STEPS = 16;

float lit(vec3 pos) {
    vec4 c = u_ShadowMatrix * vec4(pos, 1.0);
    float r = length(c.xy);
    if (r > 0.97) return 1.0;
    vec2 d = c.xy / (r * u_Params.x + (1.0 - u_Params.x));
    return texture(u_ShadowMap, vec3(d, c.z) * 0.5 + 0.5 - vec3(0.0, 0.0, u_Params.z));
}

void main() {
    float depth = texture(u_Depth, v_Uv).r;
    vec4 vp = u_InvProj * vec4(v_Uv * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    vec3 view = vp.xyz / vp.w;
    float dist = depth < 1.0 ? length(view) : 1e9;
    vec3 dir = mat3(u_InvView) * normalize(view);
    float range = min(dist, u_Params.y);
    float jitter = fract(52.9829189 * fract(dot(gl_FragCoord.xy, vec2(0.06711056, 0.00583715))));
    // Steps packed closer near the camera, where beams are largest on screen.
    float sum = 0.0;
    for (int i = 0; i < STEPS; i++) {
        float f = (float(i) + jitter) / float(STEPS);
        sum += lit(dir * (range * f * f));
    }
    // r: share of the ray (inside the shadow range) that is sunlit; g: that range as a fraction of the maximum.
    fragColor = vec4(sum / float(STEPS), range / u_Params.y, 0.0, 1.0);
}
