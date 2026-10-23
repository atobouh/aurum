// Aurum water, style B "Crystal Clear" with Photon as the reference (docs/research/WATER_PROPOSAL.md).
// Runs in the translucent terrain pass for water sprites. Everything the water shows is computed here:
//   - what lies under it: the opaque scene copied before this pass, refracted, tinted by the water column
//     (Beer-Lambert absorption: clear turquoise shallows, deep teal)
//   - what it reflects: the real sky (our sky table) and, on Medium/High, terrain by screen-space reflection
//   - the sun: a sharp glint on fine ripples (glitter), hidden by shadows
//   - a soft bright edge along shores
// The result is written opaque (alpha 1) over the copy, so the blend stage does not tint it again.

uniform int u_AurumPass;           // 1 in the translucent pass when the opaque copy is valid
uniform sampler2D u_OpaqueDepth;
uniform sampler2D u_OpaqueColor;
uniform sampler2D u_WaterNormalTex; // tiling ripples: rg slope, b height
uniform vec4 u_WaterParams;        // xy 1 / framebuffer size, z screen-space reflections on, w wave strength
uniform vec4 u_WaterScroll;        // ripple layer offsets (tile units, wrapped)
uniform mat4 u_ProjectionMatrix;
uniform mat4 u_ModelViewMatrix;
uniform sampler2D u_CloudReflection;
uniform mat4 u_PrevCloudViewProj;
uniform float u_CloudReflectionOn;

// View-space depth (positive, blocks) from a depth-buffer value.
float aurum_viewDepth(float d) {
    float ndc = d * 2.0 - 1.0;
    return -u_ProjectionMatrix[3][2] / (-ndc - u_ProjectionMatrix[2][2]);
}

vec3 aurum_toLinear(vec3 c) { return pow(max(c, vec3(0.0)), vec3(2.2)); }
vec3 aurum_toDisplay(vec3 c) { return pow(max(c, vec3(0.0)), vec3(1.0 / 2.2)); }

// Ripple slope at a world position: three layers (8, 4 and 2 blocks per tile), all periodic across the
// 256-block camera modulo. Fine layers fade with distance so far water stays calm instead of shimmering.
vec2 aurum_waterSlope(vec2 xz, float dist) {
    vec2 a = texture(u_WaterNormalTex, xz / 8.0 + u_WaterScroll.xy).rg * 2.0 - 1.0;
    vec2 b = texture(u_WaterNormalTex, vec2(xz.y, -xz.x) / 4.0 + u_WaterScroll.zw).rg * 2.0 - 1.0;
    vec2 c = texture(u_WaterNormalTex, xz / 2.0 + u_WaterScroll.yx * 1.7).rg * 2.0 - 1.0;
    float fine = 1.0 / (1.0 + dist * 0.05);
    b = vec2(-b.y, b.x); // undo the layer's rotation
    // Far water settles (Photon: calm, clean reflections toward the horizon).
    float calm = 1.0 / (1.0 + dist * 0.028);
    return (a * 0.052 + b * 0.040 * fine + c * 0.038 * fine * fine) * calm * u_WaterParams.w;
}

// Screen-space reflection of the opaque scene. Returns rgb (display) and a = hit confidence.
vec4 aurum_waterSSR(vec3 posWorld, vec3 dirWorld, float dist) {
    vec3 p0 = (u_ModelViewMatrix * vec4(posWorld, 1.0)).xyz;
    vec3 r = normalize(mat3(u_ModelViewMatrix) * dirWorld);
    float stepLen = 0.6 + dist * 0.02;
    float t = stepLen;
    float jitter = fract(52.9829189 * fract(dot(gl_FragCoord.xy, vec2(0.06711056, 0.00583715))));
    t *= 0.5 + jitter;
    vec2 hitUv = vec2(0.0);
    float prevT = 0.0;
    for (int i = 0; i < 18; i++) {
        vec3 p = p0 + r * t;
        if (p.z > -0.05) break; // behind the camera
        vec4 c = u_ProjectionMatrix * vec4(p, 1.0);
        vec2 uv = c.xy / c.w * 0.5 + 0.5;
        if (any(lessThan(uv, vec2(0.0))) || any(greaterThan(uv, vec2(1.0)))) break;
        float sceneZ = aurum_viewDepth(texture(u_OpaqueDepth, uv).r);
        float rayZ = -p.z;
        if (rayZ > sceneZ && rayZ - sceneZ < max(stepLen * 1.5, 0.6)) {
            // Refine between the last miss and this hit.
            float lo = prevT, hi = t;
            for (int j = 0; j < 4; j++) {
                float mid = 0.5 * (lo + hi);
                vec3 pm = p0 + r * mid;
                vec4 cm = u_ProjectionMatrix * vec4(pm, 1.0);
                vec2 um = cm.xy / cm.w * 0.5 + 0.5;
                if (-pm.z > aurum_viewDepth(texture(u_OpaqueDepth, um).r)) { hi = mid; uv = um; } else { lo = mid; }
            }
            vec2 edge = smoothstep(vec2(0.0), vec2(0.08), uv) * (1.0 - smoothstep(vec2(0.92), vec2(1.0), uv));
            return vec4(texture(u_OpaqueColor, uv).rgb, edge.x * edge.y * (1.0 - float(i) / 18.0 * 0.5));
        }
        prevT = t;
        stepLen *= 1.35;
        t += stepLen;
    }
    return vec4(0.0);
}

vec3 aurum_shadeWater(vec4 tex, vec4 vertexColor, vec2 lightCoord, vec3 pos) {
    vec3 dpdx = dFdx(pos), dpdy = dFdy(pos);
    vec3 ng = normalize(cross(dpdx, dpdy));
    if (dot(ng, pos) > 0.0) ng = -ng;
    float dist = length(pos);
    vec3 V = pos / dist;            // camera -> surface
    vec3 wp = pos + u_CameraMod;    // world position modulo 256

    // Surface normal: ripples on top faces (and gently on slanted flowing water), flat on sides.
    vec3 n = ng;
    if (ng.y > 0.3) {
        vec2 s = aurum_waterSlope(wp.xz, dist);
        n = normalize(vec3(-s.x, 1.0, -s.y));
    }

    float skyL = aurum_lightLevel(lightCoord.y);
    float skyVis = skyL * skyL;

    // ---- What lies under the surface (refracted).
    vec2 screenUv = gl_FragCoord.xy * u_WaterParams.xy;
    float waterZ = aurum_viewDepth(gl_FragCoord.z);
    vec2 refrUv = screenUv + (n.xz - ng.xz) * (0.35 / (1.0 + waterZ * 0.08));
    float behindZ = aurum_viewDepth(texture(u_OpaqueDepth, refrUv).r);
    if (behindZ < waterZ) { refrUv = screenUv; behindZ = aurum_viewDepth(texture(u_OpaqueDepth, screenUv).r); }
    vec3 behind = aurum_toLinear(texture(u_OpaqueColor, refrUv).rgb);
    // Length of the ray inside the water.
    float column = max(behindZ - waterZ, 0.0) * dist / max(waterZ, 1e-3);
    bool openBottom = texture(u_OpaqueDepth, refrUv).r >= 1.0;
    if (openBottom) column = 1e4;

    // Absorption per block: keep the bed visible farther into clear shallows, while red still falls off first.
    // The biome water colour nudges it (swamps murkier, warm oceans bluer) without painting the water.
    vec3 tint = aurum_toLinear(vertexColor.rgb);
    vec3 tintN = tint / max(max(tint.r, max(tint.g, tint.b)), 1e-3);
    vec3 sigma = vec3(0.60, 0.20, 0.13) * mix(vec3(1.0), 1.6 - tintN * 0.8, 0.5);
    vec3 trans = exp(-sigma * column);
    // u_LightDir follows the moon at night; sunny caustics and glitter must use the actual sun direction.
    float sunUp = smoothstep(-0.05, 0.2, u_SunDirW.y);
    float sunVisible = 0.0;
    if (ng.y > 0.3 && sunUp > 0.0) {
        vec2 sh = aurum_shadow(pos, ng);
        sunVisible = mix(smoothstep(0.55, 0.95, skyL), sh.x, sh.y);
    }
    // Caustics: the bright, wobbling light network sunlight draws on a shallow bed (Photon's shallows). Two
    // ripple layers crossing; lines where their heights match. Strongest in the shallows, gone with depth.
    if (!(texture(u_OpaqueDepth, refrUv).r >= 1.0) && column < 8.0) {
        vec3 bed = wp + V * column;
        float h1 = texture(u_WaterNormalTex, bed.xz / 4.0 + u_WaterScroll.xy * 1.3).b;
        float h2 = texture(u_WaterNormalTex, vec2(bed.z, -bed.x) / 2.0 + u_WaterScroll.zw * 1.6).b;
        float caust = pow(1.0 - abs(h1 - h2), 10.0);
        behind *= 1.0 + caust * 1.4 * sunUp * sunVisible * exp(-column * 0.35) * smoothstep(0.0, 0.3, column);
    }
    // Light scattered back out of the water body (what deep water looks like), lit by sky and sun.
    vec3 deepAlbedo = vec3(0.008, 0.036, 0.052) * mix(vec3(1.0), tintN * 1.4, 0.35);
    vec3 deepHdr = (u_SkyAmbient * skyVis * 1.6 + u_LightColor * sunUp * 0.45 * skyVis) * deepAlbedo
                   + u_BlockLightColor * deepAlbedo * aurum_lightLevel(lightCoord.x) * 0.5;
    vec3 deep = aurum_toLinear(aurum_tonemap(deepHdr));
    // A small surface tint keeps water present without hiding sand and stone just beneath it.
    vec3 under = behind * trans * vec3(0.91, 0.97, 0.99) + deep * (1.0 - trans);

    // ---- Reflection.
    vec3 R = reflect(V, n);
    R.y = abs(R.y);
    vec3 skyRefl = aurum_toLinear(aurum_tonemap(aurum_skyRadiance(R, u_SunDirW))) * mix(0.25, 1.0, skyVis);
    // Clouds are rendered after translucent terrain. Reproject last frame's premultiplied cloud colour into
    // this reflected direction; a feathered screen edge falls back to the sky LUT when the ray was off screen.
    // Distant cloud parallax is small, and this costs one filtered sample rather than a second cloud march.
    if (u_CloudReflectionOn > 0.5 && ng.y > 0.3 && R.y > 0.02) {
        vec4 clip = u_PrevCloudViewProj * vec4(R, 0.0);
        if (clip.w > 0.001) {
            vec2 cuv = clip.xy / clip.w * 0.5 + 0.5;
            if (all(greaterThan(cuv, vec2(0.0))) && all(lessThan(cuv, vec2(1.0)))) {
                vec2 edge = smoothstep(vec2(0.02), vec2(0.12), cuv)
                          * (1.0 - smoothstep(vec2(0.88), vec2(0.98), cuv));
                vec4 cl = texture(u_CloudReflection, cuv);
                float coverage = cl.a * edge.x * edge.y;
                vec3 cloud = aurum_toLinear(cl.rgb / max(cl.a, 0.001));
                skyRefl = mix(skyRefl, cloud * mix(0.25, 1.0, skyVis), coverage);
            }
        }
    }
    vec3 refl = skyRefl;
    if (u_WaterParams.z > 0.5 && ng.y > 0.3) {
        vec4 ssr = aurum_waterSSR(pos, R, dist);
        refl = mix(skyRefl, aurum_toLinear(ssr.rgb), ssr.a);
    }
    float cosT = clamp(dot(-V, n), 0.0, 1.0);
    float fresnel = 0.02 + 0.98 * pow(1.0 - cosT, 5.0);
    // A little extra reflection at mid angles: real water looks glassier than pure Schlick on a monitor.
    fresnel = min(fresnel + 0.15 * (1.0 - cosT) * (1.0 - cosT), 0.95);

    vec3 color = mix(under, refl, fresnel);

    // ---- Sun glint: a hot, very tight lobe on the fine ripples (Photon's glitter) plus a soft sheen.
    if (ng.y > 0.3 && sunUp > 0.0) {
        vec3 L = u_LightDir;
        vec3 H = normalize(L - V);
        // Glitter (Photon's signature): the sun catches thousands of tiny, steep wave facets. Two very fine
        // layers (1 and 0.5 blocks per tile) tilt a separate glint normal much more than the visible ripples,
        // so the highlight shatters into bright points spread over a wide path. Faded with distance (the
        // texture's mips smooth them anyway) so far water keeps a clean path instead of noise.
        // Sampled on skewed grids (integer matrices: still exactly periodic over 256 blocks) so the facets never
        // line up into a visible grid of dots.
        float sparkleFade = 1.0 / (1.0 + dist * 0.03);
        vec2 s1 = vec2(3.0 * wp.x + wp.z, 3.0 * wp.z - wp.x) / 4.0;
        vec2 s2 = vec2(2.0 * wp.x - 3.0 * wp.z, 3.0 * wp.x + 2.0 * wp.z) / 4.0;
        vec2 g1 = texture(u_WaterNormalTex, s1 + u_WaterScroll.wz * 2.3).rg * 2.0 - 1.0;
        vec2 g2 = texture(u_WaterNormalTex, s2 + u_WaterScroll.xy * 3.1).rg * 2.0 - 1.0;
        // Facets steep enough to catch the sun across a wide field around its reflection, as in Photon.
        vec2 gs = (g1 * 0.32 + vec2(g2.y, -g2.x) * 0.26) * sparkleFade * u_WaterParams.w;
        vec3 ns = normalize(n + vec3(-gs.x, 0.0, -gs.y));
        float nh = max(dot(n, H), 0.0);
        float nhs = max(dot(ns, H), 0.0);
        // Broaden and temper the highlight: the previous high-power lobes were added after their own tone map,
        // leaving clipped white dots instead of a continuous sun path. Bound the added light by what remains
        // below display white, so the reflected sky and shoreline keep their colour under the sparkle.
        float spec = pow(nhs, 1200.0) * 45.0 * sparkleFade + pow(nh, 500.0) * 8.0 + pow(nh, 90.0) * 0.5;
        vec3 glint = aurum_toLinear(aurum_tonemap(u_LightColor * spec * sunVisible * fresnel * 2.0));
        color += glint * max(vec3(1.0) - color, vec3(0.0)) * 0.85;
    }

    // ---- Shore edge: a thin, soft, broken bright line where the water is very shallow.
    if (!openBottom) {
        float h = texture(u_WaterNormalTex, wp.xz / 4.0 + u_WaterScroll.zw * 0.5).b;
        float edge = (1.0 - smoothstep(0.0, 0.35 + 0.25 * h, column)) * smoothstep(0.35, 0.65, h + 0.25);
        vec3 foamHdr = (u_SkyAmbient * skyVis + u_LightColor * sunUp * 0.6 * skyVis) * 0.55;
        color = mix(color, aurum_toLinear(aurum_tonemap(foamHdr)), edge * 0.45);
    }

    return aurum_toDisplay(color);
}
