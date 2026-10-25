#import <aurum:include/terrain_uniforms.glsl>
#import <aurum:include/tonemap.glsl>
#import <aurum:include/sky_lut.glsl>
#import <aurum:include/shadow.glsl>
#import <aurum:include/voxel_light.glsl>
#import <aurum:include/cloud_shadow.glsl>
#import <aurum:include/surface_light.glsl>

in vec3 aurum_Position;
in vec3 aurum_Normal;
in vec4 aurum_Color;
in vec2 aurum_LightCoord;
flat in int aurum_FullBright;

vec3 aurum_shadeModel(vec3 base) {
    vec3 ng = dot(aurum_Normal, aurum_Normal) > 1e-8 ? normalize(aurum_Normal) : normalize(cross(dFdx(aurum_Position), dFdy(aurum_Position)));
    vec3 n = ng;
    AurumLight illumination = aurum_evaluateLight(aurum_LightCoord, aurum_Position, ng, n, 1.0, 0, 1.0, 0.0);
    if (u_LightingDebug > 0) return aurum_lightDiagnostic(illumination, ng, 1.0);
    return aurum_tonemap(pow(max(base, vec3(0.0)), vec3(2.2)) * aurum_diffuseLight(illumination, 1.0));
}
