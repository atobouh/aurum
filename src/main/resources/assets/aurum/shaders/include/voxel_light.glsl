// Visibility is baked through a block-shape voxel snapshot on the CPU. One surface-atlas lookup supplies
// nearby coloured block light, with vanilla fallback for entities/cards, unbaked cells and volume edges.
uniform sampler2D u_VoxelLight;
uniform vec4 u_VoxelParams; // x field side, y samples per face, z atlas tiles per row, w enabled strength
uniform vec3 u_VoxelOrigin; // field origin relative to this frame's camera

vec4 aurum_voxelLight(vec3 pos, vec3 normal) {
    if (u_VoxelParams.w <= 0.0) return vec4(0.0);
    vec3 p = pos - normal * 0.003 - u_VoxelOrigin;
    vec3 cell = floor(p);
    if (any(lessThan(cell, vec3(0.0))) || any(greaterThanEqual(cell, vec3(u_VoxelParams.x)))) return vec4(0.0);
    vec3 local = fract(p);
    vec3 an = abs(normal);
    float face;
    vec2 uv;
    if (an.x > an.y && an.x > an.z) { face = normal.x > 0.0 ? 0.0 : 1.0; uv = local.zy; }
    else if (an.y > an.z) { face = normal.y > 0.0 ? 2.0 : 3.0; uv = local.xz; }
    else { face = normal.z > 0.0 ? 4.0 : 5.0; uv = local.xy; }
    float block = (cell.y * u_VoxelParams.x + cell.z) * u_VoxelParams.x + cell.x;
    float tile = block * 6.0 + face;
    vec2 base = vec2(mod(tile, u_VoxelParams.z), floor(tile / u_VoxelParams.z)) * u_VoxelParams.y;
    vec2 coord = base + clamp(uv * u_VoxelParams.y, vec2(0.5), vec2(u_VoxelParams.y - 0.5));
    vec4 light = u_PixelLight.x > 0.5
            ? texelFetch(u_VoxelLight, ivec2(base + clamp(floor(uv * u_VoxelParams.y), vec2(0.0), vec2(u_VoxelParams.y - 1.0))), 0)
            : texture(u_VoxelLight, coord / vec2(textureSize(u_VoxelLight, 0)));
    // Keep shadows through the centre of the volume; soften only its boundary into vanilla lighting.
    vec3 edge = min(p, vec3(u_VoxelParams.x) - p);
    light.a *= smoothstep(0.0, 2.0, min(edge.x, min(edge.y, edge.z))) * u_VoxelParams.w;
    return light;
}
