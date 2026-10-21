// Forward terrain lighting for the replacement Sodium fragment shader. Everything is linear HDR until the
// final tonemap. Inputs come from terrain_uniforms.glsl.

float aurum_lightLevel(float coord) {
    // Sodium packs light as level * 16 + 8 out of 256.
    return clamp((coord * 256.0 - 8.0) / 240.0, 0.0, 1.0);
}

bool aurum_isFoliage(int material) {
    return material == 1 || material == 2 || material == 3 || material == 6 || material == 7 || material == 8;
}

vec3 aurum_shadeTerrain(vec4 tex, vec4 vertexColor, vec2 lightCoord, vec3 pos, vec2 uv, float mipBias, int material) {
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
    if (u_TerrainParams.x > 0.0 && !foliage) {
        vec4 nt = texture(u_NormalTex, uv, mipBias);
        vec3 ts = normalize(vec3((nt.rg * 2.0 - 1.0) * u_TerrainParams.x, 1.0));
        coating = nt.b - 0.5;
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

    float sky = aurum_lightLevel(lightCoord.y);
    float blk = aurum_lightLevel(lightCoord.x);

    float ndl;
    if (foliage) {
        // Thin plants: light them as soft, upward-facing, slightly translucent cards.
        n = vec3(0.0, 1.0, 0.0);
        ndl = (clamp(u_LightDir.y, 0.0, 1.0) * 0.45 + 0.35) * smoothstep(-0.05, 0.25, u_LightDir.y);
    } else if (material == 4) {
        n = normalize(n + vec3(0.0, 0.6, 0.0));
        ndl = clamp(dot(n, u_LightDir) * 0.6 + 0.4, 0.0, 1.0) * smoothstep(-0.05, 0.2, u_LightDir.y);
    } else {
        ndl = max(dot(n, u_LightDir), 0.0);
    }

    float skyVis = sky * sky;
    // Without a shadow map, sky light is the only hint of cover. Inside the shadow range the real shadow takes
    // over; a weak sky-light gate stays so sealed caves never catch sunlight through thin ceilings.
    float directVis = smoothstep(0.55, 0.95, sky);
    if (ndl > 0.0) {
        vec2 sh = aurum_shadow(pos, ng);
        // Leaves let light through: a shaded leaf still glows a little (cheap subsurface stand-in).
        float lit = material == 4 ? max(sh.x, 0.35) : sh.x;
        directVis = mix(directVis, lit * smoothstep(0.05, 0.3, sky), sh.y);
    }
    vec3 direct = u_LightColor * (ndl * directVis);

    vec3 ambient = mix(u_GroundAmbient, u_SkyAmbient, n.y * 0.5 + 0.5) * skyVis;
    // Bounce glow: tint the light that is not direct sun toward the colour of the surroundings.
    vec3 bounce = texelFetch(u_BounceTex, ivec2(0), 0).rgb;
    float bl = max(dot(bounce, vec3(0.2126, 0.7152, 0.0722)), 1e-4);
    vec3 bounceTint = clamp(bounce / bl, 0.0, 3.0);
    ambient *= mix(vec3(1.0), bounceTint, u_TerrainParams.w * (1.0 - clamp(ndl * directVis, 0.0, 1.0)));

    float blockBrightness = blk / (4.0 - 3.0 * blk); // vanilla's light curve
    vec3 light = (direct + ambient) * ao + u_BlockLightColor * (blockBrightness * ao) + vec3(u_TerrainParams.z);

    vec3 color = albedo * light * (1.0 + coating * 0.08);
    return aurum_tonemap(color);
}
