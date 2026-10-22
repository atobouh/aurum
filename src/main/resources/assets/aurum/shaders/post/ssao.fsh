#version 150

// Contact shadows (screen-space ambient occlusion, 1/2 resolution): darkening where surfaces meet and under
// cover (under trees, along walls, in crevices), the "shadow embracing the world" of the reference packs
// (docs/research/LOOK_FOG_LIGHT.md). Eight samples in a normal-oriented hemisphere about a block across,
// rotated per pixel; the composite upscales it edge-aware and smooths the noise.

uniform sampler2D u_Depth;
uniform mat4 u_Proj;
uniform mat4 u_InvProj;
uniform vec4 u_Params;   // x radius (blocks), y max distance (blocks)

in vec2 v_Uv;
out vec4 fragColor;

const vec3 KERNEL[8] = vec3[](
    vec3(0.36, 0.12, 0.30), vec3(-0.42, 0.20, 0.25), vec3(0.15, -0.40, 0.45), vec3(-0.20, -0.30, 0.20),
    vec3(0.60, -0.15, 0.55), vec3(-0.55, 0.45, 0.62), vec3(0.10, 0.70, 0.50), vec3(-0.15, -0.75, 0.70));

vec3 viewPos(vec2 uv) {
    float d = texture(u_Depth, uv).r;
    vec4 p = u_InvProj * vec4(uv * 2.0 - 1.0, d * 2.0 - 1.0, 1.0);
    return p.xyz / p.w;
}

void main() {
    float depth = texture(u_Depth, v_Uv).r;
    if (depth >= 1.0) { fragColor = vec4(1.0, 1.0, 0.0, 1.0); return; }
    vec3 p = viewPos(v_Uv);
    float dist = length(p);
    if (dist > u_Params.y) { fragColor = vec4(1.0, min(dist / u_Params.y, 1.0), 0.0, 1.0); return; }
    vec3 n = normalize(cross(dFdx(p), dFdy(p)));
    if (dot(n, p) > 0.0) n = -n;
    // Kernel rotation from a fixed 4x4 pattern (16 angles). The composite averages exactly one 4x4 block, so
    // the noise cancels out and nothing crawls when the camera moves (0.3.4 used per-pixel random noise, which
    // the user saw as dark, smearing blotches on grass and mobs).
    ivec2 cell = ivec2(gl_FragCoord.xy) & 3;
    const float BAYER[16] = float[](0.0, 8.0, 2.0, 10.0, 12.0, 4.0, 14.0, 6.0, 3.0, 11.0, 1.0, 9.0, 15.0, 7.0, 13.0, 5.0);
    float a = 6.2831853 * (BAYER[cell.y * 4 + cell.x] + 0.5) / 16.0;
    vec3 helper = abs(n.y) < 0.99 ? vec3(0.0, 1.0, 0.0) : vec3(1.0, 0.0, 0.0);
    vec3 t = normalize(cross(helper, n));
    vec3 b = cross(n, t);
    float ca = cos(a), sa = sin(a);
    vec3 tr = t * ca + b * sa, br = b * ca - t * sa;
    float radius = u_Params.x;
    float occ = 0.0;
    for (int i = 0; i < 8; i++) {
        vec3 k = KERNEL[i];
        vec3 s = p + (tr * k.x + br * k.y + n * k.z) * radius;
        vec4 c = u_Proj * vec4(s, 1.0);
        vec2 uv = c.xy / c.w * 0.5 + 0.5;
        if (any(lessThan(uv, vec2(0.0))) || any(greaterThan(uv, vec2(1.0)))) continue;
        vec3 q = viewPos(uv);
        // Occluded when the surface seen there is in front of the sample point, and close enough to matter.
        // Thin, close occluders (grass blades, a mob's edge in front of the ground) count only partly.
        float diff = q.z - s.z;
        float range = smoothstep(0.0, 1.0, radius / max(abs(p.z - q.z), 1e-3));
        occ += smoothstep(0.05, 0.3, diff) * range;
    }
    float ao = 1.0 - occ / 8.0;
    ao = mix(ao, 1.0, smoothstep(u_Params.y * 0.6, u_Params.y, dist));
    fragColor = vec4(ao, dist / u_Params.y, 0.0, 1.0);
}
