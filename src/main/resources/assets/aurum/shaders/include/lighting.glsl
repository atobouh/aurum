// Forward terrain lighting for the replacement Sodium fragment shader. Everything is linear HDR until the
// final tonemap. Inputs come from terrain_uniforms.glsl.

// uv drives derivatives (tangent frame, mips); suv is where to sample (moved by parallax, else equal to uv).
vec3 aurum_shadeTerrain(vec4 tex, vec4 vertexColor, vec2 lightCoord, vec3 pos, vec2 uv, vec2 suv, float mipBias, int material) {
    vec3 albedo = pow(tex.rgb * vertexColor.rgb, vec3(2.2));

    // Face normal from screen-space derivatives (Sodium has no vertex normals). Flip to face the camera.
    vec3 dpdx = dFdx(pos);
    vec3 dpdy = dFdy(pos);
    vec3 ng = normalize(cross(dpdx, dpdy));
    if (dot(ng, pos) > 0.0) ng = -ng;

    // Sodium bakes vanilla's per-face shade into the AO channel; divide it back out so the sun does the shading.
    float shade = ng.x * ng.x * 0.6 + ng.z * ng.z * 0.8 + ng.y * ng.y * (ng.y > 0.0 ? 1.0 : 0.5);
    float ao = clamp(vertexColor.a / max(shade, 0.45), 0.0, 1.0);
    ao = pow(ao, 1.0 + u_TerrainParams.y);

    bool foliage = aurum_isFoliage(material);
    vec3 n = ng;
    float coating = 0.0;
    bool packData = (aurum_atlasFlags(suv) & 0x40) != 0;
    if (g_pomWall != vec3(0.0)) {
        // A wall of a parallax pixel-cube: shade it as its own block face.
        n = g_pomWall;
    } else if ((u_TerrainParams.x > 0.0 || packData) && !foliage) {
        vec4 nt = suv == uv ? texture(u_NormalTex, uv, mipBias) : textureGrad(u_NormalTex, suv, dFdx(uv) * 0.84, dFdy(uv) * 0.84);
        // Resource-pack normals are authored detail: full strength (stored at half slope). Generated ones follow
        // the Normal strength setting.
        float strength = packData ? 2.0 : u_TerrainParams.x;
        vec3 ts = normalize(vec3((nt.rg * 2.0 - 1.0) * strength, 1.0));
        coating = nt.b - 0.5;
        if (packData) {
            // Resource-pack data: B is the pack's ambient occlusion, not a coating variation.
            ao *= mix(1.0, nt.b, 0.8);
            coating = 0.0;
        }
        // Cotangent frame from position and UV derivatives: works for any face orientation and texture rotation.
        vec2 duvx = dFdx(uv);
        vec2 duvy = dFdy(uv);
        vec3 dp2perp = cross(dpdy, ng);
        vec3 dp1perp = cross(ng, dpdx);
        vec3 t = dp2perp * duvx.x + dp1perp * duvy.x;
        vec3 b = dp2perp * duvx.y + dp1perp * duvy.y;
        float len2 = max(dot(t, t), dot(b, b));
        if (len2 > 1e-30) {
            float inv = inversesqrt(len2);
            n = normalize(t * (ts.x * inv) + b * (ts.y * inv) + ng * ts.z);
        }
    }

    AurumLight illumination = aurum_evaluateLight(lightCoord, pos, ng, n, ao, material, g_pomShadow, g_pomCavity);
    if (u_LightingDebug > 0) return aurum_lightDiagnostic(illumination, ng, ao);
    vec3 light = aurum_diffuseLight(illumination, ao);
    vec3 color = albedo * light * (1.0 + coating * 0.08);
    // Transmitted light picks up the leaf's colour twice (in and out of the leaf): albedo times its own hue.
    vec3 hue = albedo / max(max(albedo.r, max(albedo.g, albedo.b)), 1e-4);
    color += albedo * hue * hue * illumination.transmit * mix(1.0, ao, 0.5);
    if (u_PbrParams.x > 0.0 || u_PbrParams.w > 0.0) {
        vec4 pbr = suv == uv ? texture(u_PbrTex, uv, mipBias) : textureGrad(u_PbrTex, suv, dFdx(uv) * 0.84, dFdy(uv) * 0.84);
        if (pbr.r > 0.004 || pbr.g > 0.05) {
            float diffuseScale = 1.0;
            vec3 spec = aurum_pbrSpecular(albedo, pbr, n, pos, illumination.ndl > 0.0 ? illumination.directVis : 0.0, illumination.skyVis, illumination.blockBrightness, diffuseScale);
            color = color * diffuseScale + spec * ao;
        }
        // Emission (glowing ores, lamps): the texture glows in its own colour, picked up by bloom.
        color += albedo * pbr.b * u_PbrParams.w * u_BlockLightColor.r;
    }
    return aurum_tonemap(color);
}
