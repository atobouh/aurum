// Scene-space visibility, shared by all receivers. Eight RGBA bands hold up to thirty-two source visibilities;
// band eight holds local bounced radiance and independent probe validity. Evaluate Lambert/falloff at the
// actual receiver, not at an offset probe (which incorrectly darkened floors beside a torch).
uniform sampler2D u_VoxelLight;
uniform vec4 u_VoxelParams; // x field world side, y probe side, z reserved, w effective blend
uniform vec3 u_VoxelOrigin;
uniform vec4 u_LocalLightPosLevel[32]; // xyz relative to the published field origin, w emission level
uniform vec3 u_LocalLightColor[32];
uniform int u_LocalLightCount;

vec4 aurum_probeBand(vec3 p, float band) {
    float side = u_VoxelParams.y;
    p = clamp(p, vec3(0.0), vec3(side - 1.0));
    float z0 = floor(p.z), z1 = min(z0 + 1.0, side - 1.0);
    vec2 local = p.xy + 0.5;
    vec2 size = vec2(textureSize(u_VoxelLight, 0));
    vec4 a = texture(u_VoxelLight, vec2(z0 * side + local.x, band * side + local.y) / size);
    vec4 b = texture(u_VoxelLight, vec2(z1 * side + local.x, band * side + local.y) / size);
    return mix(a, b, fract(p.z));
}

vec4 aurum_voxelLight(vec3 pos, vec3 normal) {
    if (u_VoxelParams.w <= 0.0) return vec4(0.0);
    float spacing = u_VoxelParams.x / u_VoxelParams.y;
    vec3 receiver = pos - u_VoxelOrigin;
    vec3 query = receiver + normal * 0.015;
    if (u_PixelLight.x > 0.5) {
        vec3 snap = (floor(receiver * u_PixelLight.y) + 0.5) / u_PixelLight.y - receiver;
        query += snap - normal * dot(snap, normal);
    }
    if (any(lessThan(query, vec3(0.0))) || any(greaterThanEqual(query, vec3(u_VoxelParams.x)))) return vec4(0.0);
    float reliable = max(u_VoxelParams.x * 0.5 - 2.0, 1.0);
    float cameraCoverage = 1.0 - smoothstep(max(reliable - 2.0, 0.0), reliable, length(pos));
    // A complete, source-free snapshot is known darkness, even while an old mesh lightmap catches up.
    if (u_LocalLightCount == 0) return vec4(vec3(0.0), cameraCoverage * u_VoxelParams.w);
    vec3 p = query / spacing - 0.5;
    vec4 indirect = aurum_probeBand(p, 8.0);
    if (indirect.a < 0.001) return vec4(0.0);
    vec3 direct = vec3(0.0);
    int cachedBand = -1;
    vec4 visibility = vec4(0.0);
    for (int i=0; i<32; i++) {
        if (i >= u_LocalLightCount) break;
        vec3 delta = u_LocalLightPosLevel[i].xyz - receiver;
        float distance = length(delta);
        float level = max((u_LocalLightPosLevel[i].w - distance) / 15.0, 0.0);
        if (level <= 0.0) continue;
        float cosine = distance < 0.0001 ? 1.0 : max(dot(normal, delta) / distance, 0.0);
        if (cosine <= 0.0) continue;
        int band = i / 4;
        if (band != cachedBand) { visibility = aurum_probeBand(p, float(band)); cachedBand = band; }
        float visible = clamp(visibility[i % 4] / indirect.a, 0.0, 1.0);
        if (u_PixelLight.x > 0.5) visible = step(0.5, visible);
        direct += u_LocalLightColor[i] * (level / (4.0 - 3.0 * level) * cosine * visible);
    }
    vec3 radiance = direct + indirect.rgb / indirect.a * u_TerrainParams.w;
    // Keep the transition centred on the camera continuously, not on a four-block cache-origin jump.
    // The two-block safety margin fits the reliable sphere inside either adjacent 16-block field.
    float coverage = cameraCoverage * smoothstep(0.0, 0.1, indirect.a);
    return vec4(radiance, coverage * u_VoxelParams.w);
}
