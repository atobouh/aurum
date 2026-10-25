// Aurum water: one look, after Complementary Reimagined's (user decision 2026-10-25, docs/research/WATER_ONE.md).
// The vanilla water texture stays: its live animation frame shows as a faint surface pattern and drives the surface
// normals, so reflections break along the same pixel lines. Over it: mirror-like reflections of terrain (screen
// space) and of the real sky and clouds, a clean sun reflection, moderate clarity (shallows show the bed, depth goes
// dark), and soft shore edges. Runs in the translucent terrain pass for water sprites and writes the final colour
// opaque over the scene copied before the pass, so the blend stage does not tint it again.

uniform int u_AurumPass;           // 1 in the translucent pass when the opaque copy is valid
uniform sampler2D u_OpaqueDepth;
uniform sampler2D u_OpaqueColor;
uniform sampler2D u_WaterNormalTex; // tiling ripples: rg slope, b height
uniform vec4 u_WaterParams;        // xy 1 / framebuffer size, z screen-space reflections on, w wave strength
uniform vec4 u_WaterScroll;        // ripple layer offsets (tile units, wrapped)
uniform mat4 u_ProjectionMatrix;
uniform mat4 u_ModelViewMatrix;

// View-space depth (positive, blocks) from a depth-buffer value.
float aurum_viewDepth(float d) {
    float ndc = d * 2.0 - 1.0;
    return -u_ProjectionMatrix[3][2] / (-ndc - u_ProjectionMatrix[2][2]);
}

vec3 aurum_toLinear(vec3 c) { return pow(max(c, vec3(0.0)), vec3(2.2)); }
vec3 aurum_toDisplay(vec3 c) { return pow(max(c, vec3(0.0)), vec3(1.0 / 2.2)); }

// Ripple slope at a world position: three layers (16, 6 and 2 blocks per tile; all divide 256 so the camera
// modulo never shows). Fine layers fade with distance so far water stays calm instead of shimmering.
vec2 aurum_waterSlope(vec2 xz, float dist) {
    vec2 a = texture(u_WaterNormalTex, xz / 16.0 + u_WaterScroll.xy).rg * 2.0 - 1.0;
    vec2 b = texture(u_WaterNormalTex, vec2(xz.y, -xz.x) / 6.0 + u_WaterScroll.zw).rg * 2.0 - 1.0;
    vec2 c = texture(u_WaterNormalTex, xz / 2.0 + u_WaterScroll.yx * 1.7).rg * 2.0 - 1.0;
    float fine = 1.0 / (1.0 + dist * 0.05);
    b = vec2(-b.y, b.x); // undo the layer's rotation
    // Far water settles (Photon: calm, clean reflections toward the horizon).
    float calm = 1.0 / (1.0 + dist * 0.012);
    return (a * 0.09 + b * 0.06 * fine + c * 0.05 * fine * fine) * calm * u_WaterParams.w;
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

// Luminance of the live water texture at a texel offset, wrapped inside the sprite (no bleeding into neighbours).
float aurum_waterLum(vec2 uv, vec2 off, vec2 tileMin, vec2 tile) {
    vec2 q = tileMin + mod(uv + off - tileMin, tile);
    return dot(textureLod(u_BlockTex, q, 0.0).rgb, vec3(0.2126, 0.7152, 0.0722));
}

vec3 aurum_shadeWater(vec4 tex, vec4 vertexColor, vec2 lightCoord, vec3 pos, vec2 uv) {
    vec3 dpdx = dFdx(pos), dpdy = dFdy(pos);
    vec2 duvx = dFdx(uv), duvy = dFdy(uv);
    vec3 ng = normalize(cross(dpdx, dpdy));
    if (dot(ng, pos) > 0.0) ng = -ng;
    float dist = length(pos);
    vec3 V = pos / dist;            // camera -> surface
    vec3 wp = pos + u_CameraMod;    // world position modulo 256

    // ---- Surface normal from the vanilla texture's pixel pattern (current animation frame), plus a slow, broad
    // swell so open water still breathes. Crisp per texel: reflections break along the texture's lines.
    vec2 atlas = vec2(textureSize(u_BlockTex, 0));
    vec2 texel = 1.0 / atlas;
    int flags = int(textureLod(u_NormalTex, uv, 0.0).a * 255.0 + 0.5);
    vec2 tile = (flags & 0x80) != 0 ? vec2(16.0 * float(1 << ((flags >> 4) & 3))) * texel : vec2(1.0);
    vec2 tileMin = floor(uv / tile) * tile;
    vec2 cuv = (floor(uv * atlas) + 0.5) * texel;
    float lc = aurum_waterLum(cuv, vec2(0.0), tileMin, tile);
    float gx = aurum_waterLum(cuv, vec2(texel.x, 0.0), tileMin, tile) - aurum_waterLum(cuv, vec2(-texel.x, 0.0), tileMin, tile);
    float gy = aurum_waterLum(cuv, vec2(0.0, texel.y), tileMin, tile) - aurum_waterLum(cuv, vec2(0.0, -texel.y), tileMin, tile);
    // Cotangent frame (works for any texture rotation, so flowing water ripples along its flow).
    vec3 dp2perp = cross(dpdy, ng), dp1perp = cross(ng, dpdx);
    vec3 T = dp2perp * duvx.x + dp1perp * duvy.x;
    vec3 B = dp2perp * duvx.y + dp1perp * duvy.y;
    float invmax = inversesqrt(max(max(dot(T, T), dot(B, B)), 1e-30));
    T *= invmax;
    B *= invmax;
    // The pattern fades with distance (sub-pixel lines only shimmer) and on side faces it stays gentle.
    float patternFade = (1.0 - smoothstep(12.0, 48.0, dist)) * (ng.y > 0.3 ? 1.0 : 0.4);
    // Gentle: the reference keeps a near-mirror that only wavers along the lines.
    float k = 0.45 * patternFade * u_WaterParams.w;
    vec3 n = normalize(ng - (T * gx + B * gy) * k);
    if (ng.y > 0.3) {
        vec2 swell = texture(u_WaterNormalTex, wp.xz / 16.0 + u_WaterScroll.xy * 0.6).rg * 2.0 - 1.0;
        n = normalize(n + vec3(-swell.x, 0.0, -swell.y) * 0.035 * u_WaterParams.w / (1.0 + dist * 0.02));
    }

    float skyL = aurum_lightLevel(lightCoord.y);
    float skyVis = skyL * skyL;
    float blockL = aurum_lightLevel(lightCoord.x);
    float sunUp = smoothstep(-0.05, 0.2, u_LightDir.y);
    float sunVisible = 0.0;
    if (sunUp > 0.0) {
        vec2 sh = aurum_shadow(pos, ng);
        sunVisible = mix(smoothstep(0.55, 0.95, skyL), sh.x, sh.y);
    }

    // ---- What lies under the surface (refracted a little along the pattern).
    vec2 screenUv = gl_FragCoord.xy * u_WaterParams.xy;
    float waterZ = aurum_viewDepth(gl_FragCoord.z);
    vec2 refrUv = screenUv + (n.xz - ng.xz) * (0.25 / (1.0 + waterZ * 0.08));
    float behindZ = aurum_viewDepth(texture(u_OpaqueDepth, refrUv).r);
    if (behindZ < waterZ) { refrUv = screenUv; behindZ = aurum_viewDepth(texture(u_OpaqueDepth, screenUv).r); }
    vec3 behind = aurum_toLinear(texture(u_OpaqueColor, refrUv).rgb);
    float column = max(behindZ - waterZ, 0.0) * dist / max(waterZ, 1e-3);
    bool openBottom = texture(u_OpaqueDepth, refrUv).r >= 1.0;
    if (openBottom) column = 1e4;

    // Moderate clarity: the bed shows for a block or two, then the water's own colour takes over. The biome
    // water colour sets that colour (swamps murky green, warm oceans bright teal), as in vanilla.
    // The biome colour is kept, but pulled toward a dark green-teal: vanilla's saturated blue reads as plastic
    // next to real reflections (the reference's water is dark and natural, the sky supplies the blue).
    vec3 tint = aurum_toLinear(vertexColor.rgb);
    vec3 tintN = tint / max(max(tint.r, max(tint.g, tint.b)), 1e-3);
    tintN = mix(vec3(0.42, 0.66, 0.62), tintN, 0.35);
    vec3 sigma = vec3(0.85, 0.42, 0.36) * mix(vec3(1.0), 1.6 - tintN * 0.8, 0.5);
    vec3 trans = exp(-sigma * column);
    vec3 deepAlbedo = vec3(0.006, 0.016, 0.018) * tintN * 1.6;
    vec3 deepHdr = (u_SkyAmbient * skyVis * 1.6 + u_LightColor * sunUp * 0.4 * sunVisible) * deepAlbedo
                   + u_BlockLightColor * deepAlbedo * blockL * 0.5;
    vec3 deep = aurum_toLinear(aurum_tonemap(deepHdr));
    vec3 under = behind * trans + deep * (1.0 - trans);

    // ---- The vanilla water skin: the texture itself, lit like terrain, laid thinly over what is underneath.
    // Its bright pixel lines read through clearly; its dark base only slightly darkens the view.
    // Only the texture's bright lines are kept (texels brighter than their neighbours), plus a thin veil of its
    // colour, so the water reads as Minecraft water without the flat vanilla blue.
    float around = 0.25 * (aurum_waterLum(cuv, vec2(texel.x, 0.0), tileMin, tile) + aurum_waterLum(cuv, vec2(-texel.x, 0.0), tileMin, tile)
                         + aurum_waterLum(cuv, vec2(0.0, texel.y), tileMin, tile) + aurum_waterLum(cuv, vec2(0.0, -texel.y), tileMin, tile));
    float line = smoothstep(0.02, 0.10, lc - around) * patternFade;
    vec3 skin = aurum_toLinear(tex.rgb) * tintN;
    float ndl = max(dot(n, u_LightDir), 0.0);
    vec3 skinHdr = skin * (u_SkyAmbient * skyVis * 1.2 + u_LightColor * ndl * sunVisible
                           + u_BlockLightColor * (blockL / (4.0 - 3.0 * blockL)));
    vec3 skinLit = aurum_toLinear(aurum_tonemap(skinHdr));
    under = mix(under, skinLit, 0.10);

    // ---- Reflection: terrain (screen space) where found, otherwise the real sky.
    vec3 R = reflect(V, n);
    R.y = abs(R.y);
    vec3 refl = aurum_toLinear(aurum_tonemap(aurum_skyRadiance(R, u_SunDirW))) * mix(0.2, 1.0, skyVis);
    if (u_WaterParams.z > 0.5 && ng.y > 0.3) {
        vec4 ssr = aurum_waterSSR(pos, R, dist);
        refl = mix(refl, aurum_toLinear(ssr.rgb), ssr.a);
    }
    float cosT = clamp(dot(-V, n), 0.0, 1.0);
    // Glassy: Schlick plus a lift at middle angles, so the far half of a lake mirrors the shore like the reference.
    float fresnel = 0.02 + 0.98 * pow(1.0 - cosT, 5.0);
    fresnel = min(fresnel + 0.35 * (1.0 - cosT) * (1.0 - cosT), 0.97);
    vec3 color = mix(under, refl, fresnel);
    // The texture's light streaks sit on top of the reflection, faint, like the reference's pixel lines.
    color += skinLit * line * 0.35 * (0.4 + 0.6 * fresnel);

    // ---- Sun: one clean, bright reflection that breaks up only where the pixel pattern tilts the surface.
    if (ng.y > 0.3 && sunUp > 0.0) {
        vec3 H = normalize(u_LightDir - V);
        float nh = max(dot(n, H), 0.0);
        float spec = pow(nh, 900.0) * 30.0 + pow(nh, 120.0) * 0.6;
        vec3 glint = aurum_toLinear(aurum_tonemap(u_LightColor * spec * sunVisible * fresnel * 2.0));
        color += glint * max(vec3(1.0) - color, vec3(0.0)) * 0.9;
    }

    // ---- Shore edge: a soft, slightly brighter rim where the water thins out against blocks.
    if (!openBottom) {
        float edge = 1.0 - smoothstep(0.0, 0.45, column);
        vec3 rimHdr = (u_SkyAmbient * skyVis + u_LightColor * sunUp * 0.5 * sunVisible) * 0.45;
        color = mix(color, aurum_toLinear(aurum_tonemap(rimHdr)), edge * 0.18);
    }

    return aurum_toDisplay(color);
}
