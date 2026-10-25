// One surface illumination contract used by Sodium terrain and supported core model shaders.
// Inputs and components are linear relative radiance; the display transform is applied by the caller.
uniform float u_FoliageDepth;
float aurum_lightLevel(float coord) { return clamp((coord * 256.0 - 8.0) / 240.0, 0.0, 1.0); }
bool aurum_isFoliage(int material) {
    return material == 1 || material == 2 || material == 3 || material == 6 || material == 7 || material == 8;
}
// The same local source accounting is used by solid/model receivers and the water scattering response.
vec3 aurum_localBlockLight(vec2 lightCoord, vec3 pos, vec3 normal, out vec4 voxel) {
    float level = aurum_lightLevel(lightCoord.x);
    vec3 fallback = u_BlockLightColor * (level / (4.0 - 3.0 * level));
    voxel = u_VoxelParams.w > 0.0 ? aurum_voxelLight(pos, normal) : vec4(0.0);
    return mix(fallback, voxel.rgb * u_BlockLightColor.r, voxel.a);
}
struct AurumLight {
    vec3 direct; vec3 ambient; vec3 block; vec3 fallback; vec3 voxel;
    vec3 transmit; // light scattered through leaves and plants; the caller tints it by the albedo's hue
    float coverage; float ndl; float directVis; float skyVis; float blockBrightness;
};
AurumLight aurum_evaluateLight(vec2 lightCoord, vec3 pos, vec3 ng, inout vec3 n,
        float ao, int material, float pomShadow, float pomCavity) {
    bool foliage = aurum_isFoliage(material);
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
        // 0.7.2: less lift and wrap, so a crown has a bright sun side and a dark far side (lighting audit);
        // the glow inside the canopy comes from the coloured transmission below, not from flat wrap.
        n = normalize(n + vec3(0.0, mix(0.30, 0.12, u_FoliageDepth), 0.0));
        ndl = clamp(dot(n, u_LightDir) * 0.90 + 0.10, 0.0, 1.0) * smoothstep(-0.05, 0.2, u_LightDir.y);
    } else {
        // A restrained low-sun diffuse wrap keeps rough horizontal blocks lit while the shadow map still
        // cuts dark paths through them. Pure Lambert at grazing incidence made both sides read as ambient
        // fill, unlike the gold-lit ground and distinct silhouettes in the matched reference captures.
        float wrap = 0.14 * u_SkyTone.z;
        ndl = max((dot(n, u_LightDir) + wrap) / (1.0 + wrap), 0.0);
    }

    float skyVis = sky * sky;
    // Minecraft's skylight already falls through a canopy. Squaring it again for diffuse daylight,
    // then applying leaf and vertex AO, crushed shaded crowns in dense modded forests. Retain the
    // stronger night occlusion and the original specular visibility, but recover diffuse sky access
    // during the day. Zero skylight still means zero sky fill (including sealed interiors).
    float daylightFill = smoothstep(0.05, 0.35, u_SunDirW.y);
    float diffuseSkyVis = mix(skyVis, sky, 0.75 * daylightFill);
    // Without a shadow map, sky light is the only hint of cover. Inside the shadow range the real shadow takes
    // over; a weak sky-light gate stays so sealed caves never catch sunlight through thin ceilings.
    float directVis = smoothstep(0.55, 0.95, sky);
    bool thin = material == 4 || foliage;
    float leafSun = directVis;   // sunlight reaching this leaf, without the backlight floor
    float leafBack = 0.0;
    if ((ndl > 0.0 || thin) && sky > 0.05) {
        vec2 sh = aurum_shadow(pos, ng);
        // Thin leaves pass a little backlight, while front-facing leaves in shadow stay dark.
        // The former fixed 22% sunlight floor made whole crowns glow evenly.
        leafBack = 1.0 - max(dot(ng, u_LightDir), 0.0);
        float leafTransmit = 0.03 + 0.06 * leafBack * leafBack;
        float lit = material == 4 ? max(sh.x, leafTransmit) : sh.x;
        leafSun = mix(leafSun, sh.x * smoothstep(0.05, 0.3, sky), sh.y);
        directVis = mix(directVis, lit * smoothstep(0.05, 0.3, sky), sh.y);
    }
    float cloudVisibility = aurum_cloudShadow(pos);
    directVis *= pomShadow * cloudVisibility;
    vec3 direct = u_LightColor * (ndl * directVis);
    // Leaves and plants scatter sunlight through themselves: what comes out is their own colour, roughly
    // squared (yellow-green, saturated), strongest when lit from behind. This is the references' glowing
    // canopy (Photon). Shaded interior leaves keep a faint green glow from light scattered between leaves.
    vec3 transmit = vec3(0.0);
    if (thin) {
        float sunUpT = smoothstep(-0.05, 0.2, u_LightDir.y);
        float through = material == 4 ? 0.30 + 0.70 * leafBack * leafBack : 0.35;
        transmit = u_LightColor * sunUpT * (leafSun * through * aurum_cloudShadow(pos) * pomShadow
                 + 0.03 * smoothstep(0.3, 0.9, sky));
    }

    // Vertical faces sample the sky in their own direction. The sky table is integrated on the CPU,
    // so terrain pays only a few multiplies, without tracing or another texture lookup.
    // At noon the sun is vertical and its horizontal direction is zero.
    float sunSide = dot(n.xz, u_SunDirW.xz) * inversesqrt(max(dot(u_SunDirW.xz, u_SunDirW.xz), 1e-4));
    vec3 sideSky = mix(u_SkySideAway, u_SkySideToward, sunSide * 0.5 + 0.5);
    vec3 ambient = (u_SkyAmbient * max(n.y, 0.0)
                  + (sideSky + u_GroundAmbient * 0.5) * (1.0 - abs(n.y))
                  + u_GroundAmbient * max(-n.y, 0.0)) * diffuseSkyVis;
    // A cloud redistributes part of the intercepted sun into broad diffuse light. Previously it
    // only removed sunlight, leaving large patches of land much darker than the sky and water.
    // Keep the footprint and hard block shadows: this small hemisphere fill is sky-occluded and
    // still receives local AO below. It adds no texture lookup or full-screen pass.
    ambient += u_LightColor * ((1.0 - cloudVisibility) * 0.16 * max(u_SunDirW.y, 0.0)
               * diffuseSkyVis * (0.45 + 0.55 * max(n.y, 0.0)));
    if (material == 4) {
        // Vanilla leaf AO distinguishes the interior of a crown. Keep that local occlusion in the sky fill
        // instead of letting the broad ambient term turn every cube the same shade of green.
        ambient *= mix(mix(0.60, 0.94, ao), mix(0.75, 1.0, ao), daylightFill);
        float canopyFloor = mix(0.62, 0.82, daylightFill);
        ambient *= mix(1.0, mix(canopyFloor, 1.0, smoothstep(0.60, 1.0, sky)), u_FoliageDepth);
    }
    ambient *= 1.0 - 0.18 * pomCavity;
    vec4 voxel;
    vec3 blockLight = aurum_localBlockLight(lightCoord, pos, ng, voxel);
    float blockBrightness;
    blockBrightness = max(blockLight.r, max(blockLight.g, blockLight.b)) / max(u_BlockLightColor.r, 0.001);
    AurumLight result;
    result.direct = direct; result.ambient = ambient; result.block = blockLight; result.transmit = transmit;
    result.fallback = u_BlockLightColor * (blk / (4.0 - 3.0 * blk));
    result.voxel = voxel.rgb * u_BlockLightColor.r; result.coverage = voxel.a;
    result.ndl = ndl; result.directVis = directVis;
    result.skyVis = skyVis; result.blockBrightness = blockBrightness;
    return result;
}

vec3 aurum_diffuseLight(AurumLight light, float ao) {
    return light.direct * mix(1.0, ao, 0.35) + light.ambient * ao
         + light.block * mix(1.0, ao, 0.6) + vec3(u_TerrainParams.z);
}
vec3 aurum_lightDiagnostic(AurumLight light, vec3 ng, float ao) {
    if (u_LightingDebug == 1) return aurum_tonemap(light.direct * mix(1.0, ao, 0.35));
    if (u_LightingDebug == 2) return aurum_tonemap(light.ambient * ao);
    if (u_LightingDebug == 3) return aurum_tonemap(light.block * mix(1.0, ao, 0.6));
    if (u_LightingDebug == 4) return aurum_tonemap(light.fallback * mix(1.0, ao, 0.6));
    if (u_LightingDebug == 5) return aurum_tonemap(light.voxel * mix(1.0, ao, 0.6));
    if (u_LightingDebug == 6) return vec3(1.0 - light.coverage, light.coverage, 0.0);
    if (u_LightingDebug == 7) return ng * 0.5 + 0.5;
    if (u_LightingDebug == 8) return vec3(ao);
    return vec3(0.0, 0.8, 0.8);
}
