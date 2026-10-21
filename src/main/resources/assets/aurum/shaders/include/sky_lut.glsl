// Sky radiance from the baked lookup table (see SkyLut.java for the layout). No inverse trig: one sqrt per axis.

uniform sampler2D u_SkyLut;

// The sky is viewed directly and through haze; with the linear tone curve it needs its own gain to read as the
// bright, open Minecraft sky (terrain exposure is anchored on lit surfaces, not on sky radiance).
const float SKY_GAIN = 3.0;

vec3 aurum_skyLut(float y, float cosA) {
    // Skip the table's first sky row (a sliver under 0.1 degrees of the deepest, longest-path red, which reads
    // as a painted stripe) and never filter into the below-horizon half.
    float v = 0.5 + 0.5 * sqrt(max(y, 0.0045));
    float u = sqrt(clamp(0.5 - 0.5 * cosA, 0.0, 1.0));
    return texture(u_SkyLut, vec2(u, v)).rgb * SKY_GAIN;
}

float aurum_cosToSun(vec3 dir, vec3 sunDir) {
    float lh = dot(dir.xz, dir.xz);
    float ls = dot(sunDir.xz, sunDir.xz);
    return (lh > 1e-8 && ls > 1e-8) ? dot(dir.xz, sunDir.xz) * inversesqrt(lh * ls) : 1.0;
}

vec3 aurum_skyRadiance(vec3 dir, vec3 sunDir) {
    float cosA = aurum_cosToSun(dir, sunDir);
    if (dir.y >= 0.0) return aurum_skyLut(dir.y, cosA);
    // Below the horizon: distant ground under haze. The table's very first row is the deepest, longest-path red,
    // which is only right in a sliver at the horizon; below it, use the sky a couple of degrees up instead,
    // desaturated and darker, and blend over a few degrees so there is no hard line.
    float below = -dir.y;
    vec3 horizon = aurum_skyLut(0.0, cosA);
    vec3 haze = aurum_skyLut(0.035, cosA);
    // Mix horizon and haze so a sunset horizon fades into warm dusk, not a navy band (user feedback).
    vec3 base = mix(horizon, haze, 0.5);
    vec3 ground = mix(base, vec3(dot(base, vec3(0.2126, 0.7152, 0.0722))), 0.35) * 0.6;
    return mix(horizon, ground, smoothstep(0.0, 0.15, below));
}

// Fog colour: the sky a little above the horizon in that direction, without the bright glow right around
// the sun (which would wash terrain out to pink-white), slightly desaturated like real haze.
vec3 aurum_fogRadiance(vec3 dir, vec3 sunDir) {
    float cosA = min(aurum_cosToSun(dir, sunDir), 0.85);
    vec3 c = aurum_skyLut(clamp(dir.y, 0.04, 1.0), cosA);
    // Haze is a little darker than the open sky behind it, so it veils terrain without greying it out.
    return mix(c, vec3(dot(c, vec3(0.2126, 0.7152, 0.0722))), 0.12) * 0.75;
}
