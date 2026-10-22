// LabPBR surface response and parallax ("3D blocks") for terrain. Data comes from the resource pack's _n / _s
// maps, baked into atlases parallel to the block atlas (AtlasBaker). Blocks without pack data get neutral values
// (rough dielectric, flat) and render exactly as before.

// Atlas flags (normal atlas alpha): bits 0-3 material, 4-5 sprite size (16 << n), 6 pack PBR, 7 aligned sprite.
int aurum_atlasFlags(vec2 uv) {
    return int(textureLod(u_NormalTex, uv, 0.0).a * 255.0 + 0.5);
}

// Parallax occlusion mapping: steps through the pack's height map along the view ray, inside the sprite (wrapping
// at its edges, so neighbouring textures never bleed in). Returns the uv to sample everything at. Pixel-art
// friendly: the height is read with nearest filtering, so depth steps keep crisp texel edges.
vec2 aurum_parallax(vec2 uv, vec3 pos) {
    if (u_PbrParams.y <= 0.0) return uv;
    float dist = length(pos);
    if (dist > u_PbrParams.z) return uv;
    int flags = aurum_atlasFlags(uv);
    if ((flags & 0xC0) != 0xC0) return uv;

    vec3 dpdx = dFdx(pos), dpdy = dFdy(pos);
    vec2 duvx = dFdx(uv), duvy = dFdy(uv);
    vec3 ng = normalize(cross(dpdx, dpdy));
    if (dot(ng, pos) > 0.0) ng = -ng;
    // Gradients of u and v over the surface (exact for a flat face): uv change for a world-space step.
    vec3 dp2perp = cross(dpdy, ng), dp1perp = cross(ng, dpdx);
    float det = dot(dpdx, dp2perp);
    if (abs(det) < 1e-12) return uv;
    vec3 gu = (dp2perp * duvx.x + dp1perp * duvy.x) / det;
    vec3 gv = (dp2perp * duvx.y + dp1perp * duvy.y) / det;

    vec3 d = pos / dist;
    float cosV = max(-dot(d, ng), 0.2);
    vec2 uvPerDepth = vec2(dot(gu, d), dot(gv, d)) / cosV;

    vec2 atlas = vec2(textureSize(u_NormalTex, 0));
    vec2 tile = (16.0 * float(1 << ((flags >> 4) & 3))) / atlas;
    vec2 tileMin = floor(uv / tile) * tile;

    // Fewer steps far away and at steep views; fade the depth out toward the distance limit.
    float depth = u_PbrParams.y * (1.0 - smoothstep(u_PbrParams.z * 0.6, u_PbrParams.z, dist));
    int steps = int(mix(24.0, 8.0, clamp(dist / u_PbrParams.z, 0.0, 1.0)));
    float layer = 1.0 / float(steps);
    vec2 prevUv = uv;
    float prevDiff = 0.0;
    float t = 0.0;
    for (int i = 0; i < 32; i++) {
        if (i >= steps) break;
        t += layer;
        vec2 suv = tileMin + mod(uv + uvPerDepth * depth * t - tileMin, tile);
        float h = textureLod(u_PbrTex, suv, 0.0).a;
        float diff = (1.0 - h) - t; // > 0: still above the surface
        if (diff <= 0.0) {
            // Linear refine between the last two layers.
            float w = prevDiff / max(prevDiff - diff, 1e-4);
            float tt = t - layer + w * layer;
            return tileMin + mod(uv + uvPerDepth * depth * tt - tileMin, tile);
        }
        prevDiff = diff;
        prevUv = suv;
    }
    return prevUv;
}

// Specular light from the sun (GGX) and the sky (reflected sky colour), for the pack's smoothness / F0 / metal.
// Returns the light to add, and scales the diffuse light for metals and Fresnel.
vec3 aurum_pbrSpecular(vec3 albedo, vec4 pbr, vec3 n, vec3 pos, float directVis, float skyVis, inout float diffuseScale) {
    float smoothness = pbr.r;
    bool metal = pbr.g > 229.5 / 255.0;
    vec3 f0 = metal ? albedo : vec3(pbr.g);
    float rough = max((1.0 - smoothness) * (1.0 - smoothness), 0.02);
    vec3 V = -normalize(pos);
    float nv = max(dot(n, V), 1e-3);

    // Sun / moon: GGX distribution, Schlick-Smith visibility, Schlick Fresnel.
    vec3 L = u_LightDir;
    vec3 H = normalize(L + V);
    float nl = max(dot(n, L), 0.0);
    float nh = max(dot(n, H), 0.0);
    float a2 = rough * rough;
    float dd = nh * nh * (a2 - 1.0) + 1.0;
    float D = a2 / (3.14159265 * dd * dd);
    float k = rough * 0.5;
    float vis = 0.25 / ((nl * (1.0 - k) + k) * (nv * (1.0 - k) + k));
    vec3 F = f0 + (1.0 - f0) * pow(1.0 - max(dot(H, V), 0.0), 5.0);
    vec3 spec = u_LightColor * (D * vis * nl * directVis) * F;

    // Sky reflection: the real sky in the mirrored direction; rough surfaces reflect less and duller.
    vec3 R = reflect(-V, n);
    R.y = max(R.y, 0.02);
    vec3 Fr = f0 + (max(vec3(1.0 - rough), f0) - f0) * pow(1.0 - nv, 5.0);
    float sharp = smoothness * smoothness;
    vec3 env = aurum_skyRadiance(R, u_SunDirW) * Fr * skyVis * mix(0.15, 1.0, sharp) * sharp;

    diffuseScale = metal ? 0.15 : (1.0 - max(Fr.r, max(Fr.g, Fr.b)) * sharp);
    return (spec + env) * u_PbrParams.x;
}
