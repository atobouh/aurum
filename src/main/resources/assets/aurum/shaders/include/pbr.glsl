// LabPBR surface response and parallax ("3D blocks") for terrain. Data comes from the resource pack's _n / _s
// maps, baked into atlases parallel to the block atlas (AtlasBaker). Blocks without pack data get neutral values
// (rough dielectric, flat) and render exactly as before.

// Atlas flags (normal atlas alpha): bits 0-3 material, 4-5 sprite size (16 << n), 6 pack PBR, 7 aligned sprite.
int aurum_atlasFlags(vec2 uv) {
    return int(textureLod(u_NormalTex, uv, 0.0).a * 255.0 + 0.5);
}

// Voxel parallax ("3D blocks"), the look of LabPBR packs like Seraphic and SPBR: every texel of the pack's height
// map is a tiny column, so each texture pixel stands up as a small cube. The view ray walks the texel grid (2D DDA)
// inside the sprite, wrapping at its edges. Where it enters a column from the side it hit a wall: the wall gets its
// own normal (lit or shaded by the sun like a real block face). A short march toward the sun from the hit point
// gives each little cube its shadow. Results for the lighting: g_pomWall (world normal of a hit wall, or zero) and
// g_pomShadow (1 lit, 0 shadowed by the relief).
vec3 g_pomWall = vec3(0.0);
float g_pomShadow = 1.0;
float g_pomCavity = 0.0;

float aurum_pomDepthAt(ivec2 cell, ivec2 tmin, ivec2 tsize) {
    // GLSL's % is undefined for negative operands, which broke the relief on faces whose texture runs the other
    // way (0.5.1). Wrap with floor division instead.
    ivec2 r = cell - tmin;
    ivec2 c = tmin + r - tsize * ivec2(floor(vec2(r) / vec2(tsize)));
    return 1.0 - texelFetch(u_PbrTex, c, 0).a;
}

vec2 aurum_parallax(vec2 uv, vec3 pos) {
    // Derivatives first, in uniform control flow.
    vec3 dpdx = dFdx(pos), dpdy = dFdy(pos);
    vec2 duvx = dFdx(uv), duvy = dFdy(uv);
    g_pomWall = vec3(0.0);
    g_pomShadow = 1.0;
    g_pomCavity = 0.0;
    if (u_PbrParams.y <= 0.0) return uv;
    float dist = length(pos);
    if (dist > u_PbrParams.z) return uv;
    // Relief smaller than a screen pixel only adds aliasing and ray-march cost. Keep it for blocks whose texels
    // can actually be seen; this naturally reaches farther at 1080p than in a small test window.
    vec2 footprint = (abs(duvx) + abs(duvy)) * vec2(textureSize(u_PbrTex, 0));
    if (max(footprint.x, footprint.y) > 0.90) return uv;
    int flags = aurum_atlasFlags(uv);
    // Aligned sprite with a height map (from the pack, or generated from the texture).
    if ((flags & 0x80) == 0) return uv;

    vec3 ng = normalize(cross(dpdx, dpdy));
    if (dot(ng, pos) > 0.0) ng = -ng;
    // Gradients of u and v over the surface (exact for a flat face): uv change per world-space step.
    vec3 dp2perp = cross(dpdy, ng), dp1perp = cross(ng, dpdx);
    float det = dot(dpdx, dp2perp);
    if (abs(det) < 1e-12) return uv;
    vec3 gu = (dp2perp * duvx.x + dp1perp * duvy.x) / det;
    vec3 gv = (dp2perp * duvx.y + dp1perp * duvy.y) / det;

    vec2 atlas = vec2(textureSize(u_PbrTex, 0));
    ivec2 tsize = ivec2(16 * (1 << ((flags >> 4) & 3))) * int(max(u_PbrScale, 1.0));
    vec2 p0 = uv * atlas;                                   // texel coordinates
    ivec2 tmin = ivec2(floor(p0 / vec2(tsize))) * tsize;

    // Fade the relief out toward the distance limit.
    float depth = u_PbrParams.y * (1.0 - smoothstep(u_PbrParams.z * 0.6, u_PbrParams.z, dist));
    vec3 d = pos / dist;
    float cosV = max(-dot(d, ng), 0.12);
    // Texels travelled per unit of s, where s runs from 0 (top of the relief) to 1 (its full depth).
    vec2 dp = vec2(dot(gu, d), dot(gv, d)) * atlas * depth / cosV;

    // 2D DDA over texel cells along p(s) = p0 + dp * s.
    ivec2 cell = ivec2(floor(p0));
    ivec2 stepDir = ivec2(sign(dp));
    vec2 inv = vec2(abs(dp.x) > 1e-6 ? 1.0 / abs(dp.x) : 1e9, abs(dp.y) > 1e-6 ? 1.0 / abs(dp.y) : 1e9);
    vec2 frac = p0 - vec2(cell);
    vec2 sNext = vec2(dp.x > 0.0 ? (1.0 - frac.x) * inv.x : frac.x * inv.x,
                      dp.y > 0.0 ? (1.0 - frac.y) * inv.y : frac.y * inv.y);
    float sEnter = 0.0;
    int wallAxis = -1;
    float sHit = -1.0;
    for (int i = 0; i < 48; i++) {
        float surf = aurum_pomDepthAt(cell, tmin, tsize);
        if (surf <= sEnter) { sHit = sEnter; break; }           // entered through a wall (or the top at s = 0)
        float sExit = min(sNext.x, sNext.y);
        if (surf <= sExit) { sHit = surf; wallAxis = -1; break; } // landed on this column's top
        if (sExit >= 1.0) break;
        if (sNext.x < sNext.y) { cell.x += stepDir.x; sEnter = sNext.x; sNext.x += inv.x; wallAxis = 0; }
        else { cell.y += stepDir.y; sEnter = sNext.y; sNext.y += inv.y; wallAxis = 1; }
    }
    if (sHit < 0.0) return uv;
    if (sHit > 0.0 && wallAxis >= 0 && sHit == sEnter) {
        // A wall faces back along the step: -u or -v (world direction of increasing u is along gu).
        vec3 axis = wallAxis == 0 ? gu * float(stepDir.x) : gv * float(stepDir.y);
        // Authored height maps can support crisp vertical walls. Luminance-derived heights cannot;
        // soften their wall normal to retain a raised-pixel impression without black/white striping.
        g_pomWall = (flags & 0x40) != 0 ? -normalize(axis) : normalize(mix(ng, -normalize(axis), 0.55));
        // A deeper exposed pixel wall receives less open-sky light than the face at the top of the relief.
        g_pomCavity = clamp(sHit, 0.0, 1.0);
    }
    vec2 pHit = p0 + dp * sHit;
    // Keep the lookup inside the hit cell (texel centre on the crossed axis) so walls show the cell's colour.
    if (g_pomWall != vec3(0.0)) {
        if (wallAxis == 0) pHit.x = float(cell.x) + 0.5; else pHit.y = float(cell.y) + 0.5;
    }

    // Self-shadow: from the hit point, walk toward the light over the height field.
    vec3 L = u_LightDir;
    float lz = dot(L, ng);
    if (lz > 0.02) {
        vec2 lp = vec2(dot(gu, L), dot(gv, L)) * atlas;     // texels per world unit along L
        float stepW = 0.6 / max(max(abs(lp.x), abs(lp.y)), 1e-4);
        float ds = stepW * lz / max(depth, 1e-4);           // s gained per step (rising toward the top)
        float s = sHit - 0.001;
        vec2 q = pHit;
        for (int i = 0; i < 12; i++) {
            q += lp * stepW;
            s -= ds;
            if (s <= 0.0) break;
            if (aurum_pomDepthAt(ivec2(floor(q)), tmin, tsize) < s - 0.002) {
                g_pomShadow = (flags & 0x40) != 0 ? 0.0 : 0.68;
                break;
            }
        }
    } else {
        g_pomShadow = 1.0;
    }

    vec2 wrapped = vec2(tmin) + mod(pHit - vec2(tmin), vec2(tsize));
    return wrapped / atlas;
}

// Specular light from the sun (GGX) and the sky (reflected sky colour), for the pack's smoothness / F0 / metal.
// Returns the light to add, and scales the diffuse light for metals and Fresnel.
vec3 aurum_pbrSpecular(vec3 albedo, vec4 pbr, vec3 n, vec3 pos, float directVis, float skyVis, float blockLight,
                       inout float diffuseScale) {
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

    // Reflections: the real sky in the mirrored direction outdoors, and the warm block light indoors (a lantern-lit
    // room shows the same sheen on polished floors). Fresnel with a roughness-aware ceiling: smooth surfaces turn
    // mirror-like at grazing angles, rough ones keep a soft sheen. 0.5.0 weighted this by smoothness squared twice
    // and the gloss was invisible (user).
    vec3 R = reflect(-V, n);
    R.y = max(R.y, 0.02);
    // 0.7.4 (user: grass and stone bleached white with a PBR pack): the grazing ceiling follows smoothness squared,
    // and a rough surface reflects the sky's average light (as bright as the sky fill on the ground), not the
    // displayed sky in one direction. Only near-mirror surfaces show the sharp, bright sky. No extra x2.
    float gloss2 = smoothness * smoothness;
    vec3 Fr = f0 + (max(vec3(gloss2), f0) - f0) * pow(1.0 - nv, 5.0);
    vec3 sky = mix(u_SkyAmbient, aurum_skyRadiance(R, u_SunDirW), gloss2 * gloss2) * skyVis;
    vec3 room = u_BlockLightColor * blockLight * 0.35;
    float clarity = mix(0.35, 1.0, smoothness);
    // u_PbrParams.x carries the Gloss setting (Sheen slider): 1 at 100 %.
    vec3 env = (sky + room) * Fr * clarity;

    diffuseScale = metal ? 0.15 : (1.0 - max(Fr.r, max(Fr.g, Fr.b)) * smoothness);
    return (spec + env) * u_PbrParams.x;
}
