// Forward terrain lighting for the replacement Sodium fragment shader. Everything is linear HDR until the
// final tonemap. Inputs come from terrain_uniforms.glsl.

float aurum_lightLevel(float coord) {
    // Sodium packs light as level * 16 + 8 out of 256.
    return clamp((coord * 256.0 - 8.0) / 240.0, 0.0, 1.0);
}

bool aurum_isFoliage(int material) {
    return material == 1 || material == 2 || material == 3 || material == 6 || material == 7 || material == 8;
}

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

    float sky = aurum_lightLevel(lightCoord.y);
    float blk = aurum_lightLevel(lightCoord.x);

    float ndl;
    if (foliage) {
        // Thin plants: light them as soft, upward-facing, slightly translucent cards.
        n = vec3(0.0, 1.0, 0.0);
        ndl = (clamp(u_LightDir.y, 0.0, 1.0) * 0.45 + 0.35) * smoothstep(-0.05, 0.25, u_LightDir.y);
    } else if (material == 4) {
        // Leaf cubes retain their face direction. A smaller light floor gives the canopy distinct sunlit
        // tops, side faces and shaded interiors instead of nearly uniform lime-green lighting.
        n = normalize(n + vec3(0.0, 0.45, 0.0));
        ndl = clamp(dot(n, u_LightDir) * 0.82 + 0.18, 0.0, 1.0) * smoothstep(-0.05, 0.2, u_LightDir.y);
    } else {
        // A restrained low-sun diffuse wrap keeps rough horizontal blocks lit while the shadow map still
        // cuts dark paths through them. Pure Lambert at grazing incidence made both sides read as ambient
        // fill, unlike the gold-lit ground and distinct silhouettes in the matched reference captures.
        float wrap = 0.14 * u_SkyTone.z;
        ndl = max((dot(n, u_LightDir) + wrap) / (1.0 + wrap), 0.0);
    }

    float skyVis = sky * sky;
    // Without a shadow map, sky light is the only hint of cover. Inside the shadow range the real shadow takes
    // over; a weak sky-light gate stays so sealed caves never catch sunlight through thin ceilings.
    float directVis = smoothstep(0.55, 0.95, sky);
    if (ndl > 0.0) {
        vec2 sh = aurum_shadow(pos, ng);
        // Thin leaves pass a little backlight, while front-facing leaves in shadow stay dark.
        // The former fixed 22% sunlight floor made whole crowns glow evenly.
        float leafBack = 1.0 - max(dot(ng, u_LightDir), 0.0);
        float leafTransmit = 0.04 + 0.10 * leafBack * leafBack;
        float lit = material == 4 ? max(sh.x, leafTransmit) : sh.x;
        directVis = mix(directVis, lit * smoothstep(0.05, 0.3, sky), sh.y);
    }
    directVis *= g_pomShadow;
    vec3 direct = u_LightColor * (ndl * directVis);

    // Vertical faces sample the sky in their own direction. The sky table is integrated on the CPU,
    // so terrain pays only a few multiplies, without tracing or another texture lookup.
    // At noon the sun is vertical and its horizontal direction is zero.
    float sunSide = dot(n.xz, u_SunDirW.xz) * inversesqrt(max(dot(u_SunDirW.xz, u_SunDirW.xz), 1e-4));
    vec3 sideSky = mix(u_SkySideAway, u_SkySideToward, sunSide * 0.5 + 0.5);
    vec3 ambient = (u_SkyAmbient * max(n.y, 0.0)
                  + (sideSky + u_GroundAmbient * 0.5) * (1.0 - abs(n.y))
                  + u_GroundAmbient * max(-n.y, 0.0)) * skyVis;
    if (material == 4) {
        // Vanilla leaf AO distinguishes the interior of a crown. Keep that local occlusion in the sky fill
        // instead of letting the broad ambient term turn every cube the same shade of green.
        ambient *= mix(0.60, 0.94, ao);
    }
    ambient *= 1.0 - 0.18 * g_pomCavity;
    // Bounce glow: tint the light that is not direct sun toward the colour of the surroundings.
    vec3 bounce = texelFetch(u_BounceTex, ivec2(0), 0).rgb;
    float bl = max(dot(bounce, vec3(0.2126, 0.7152, 0.0722)), 1e-4);
    vec3 bounceTint = clamp(bounce / bl, 0.0, 3.0);
    ambient *= mix(vec3(1.0), bounceTint, u_TerrainParams.w * (1.0 - clamp(ndl * directVis, 0.0, 1.0)));

    float blockBrightness = blk / (4.0 - 3.0 * blk); // vanilla's light curve
    // Ambient receives the full local AO. Sun and nearby blocklight keep their lit face/detail visible.
    vec3 light = direct * mix(1.0, ao, 0.35) + ambient * ao
               + u_BlockLightColor * (blockBrightness * mix(1.0, ao, 0.6)) + vec3(u_TerrainParams.z);

    vec3 color = albedo * light * (1.0 + coating * 0.08);
    if (u_PbrParams.x > 0.0 || u_PbrParams.w > 0.0) {
        vec4 pbr = suv == uv ? texture(u_PbrTex, uv, mipBias) : textureGrad(u_PbrTex, suv, dFdx(uv) * 0.84, dFdy(uv) * 0.84);
        if (pbr.r > 0.004 || pbr.g > 0.05) {
            float diffuseScale = 1.0;
            vec3 spec = aurum_pbrSpecular(albedo, pbr, n, pos, ndl > 0.0 ? directVis : 0.0, skyVis, blockBrightness, diffuseScale);
            color = color * diffuseScale + spec * ao;
        }
        // Emission (glowing ores, lamps): the texture glows in its own colour, picked up by bloom.
        color += albedo * pbr.b * u_PbrParams.w * u_BlockLightColor.r;
    }
    return aurum_tonemap(color);
}
