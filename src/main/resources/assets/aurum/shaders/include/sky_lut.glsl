// Sky radiance from the baked lookup table (see SkyLut.java for the layout). No inverse trig: one sqrt per axis.

uniform sampler2D u_SkyLut;

vec3 aurum_skyLut(float y, float cosA) {
    // Skip the table's first sky row (a sliver under 0.1 degrees of the deepest, longest-path red, which reads
    // as a painted stripe) and never filter into the below-horizon half.
    float v = 0.5 + 0.5 * sqrt(max(y, 0.0045));
    float u = sqrt(clamp(0.5 - 0.5 * cosA, 0.0, 1.0));
    return texture(u_SkyLut, vec2(u, v)).rgb;
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
    vec3 ground = mix(haze, vec3(dot(haze, vec3(0.2126, 0.7152, 0.0722))), 0.45) * 0.55;
    return mix(horizon, ground, smoothstep(0.0, 0.08, below));
}

// Fog colour: the sky a little above the horizon in that direction, without the bright glow right around
// the sun (which would wash terrain out to pink-white), slightly desaturated like real haze.
vec3 aurum_fogRadiance(vec3 dir, vec3 sunDir) {
    float cosA = min(aurum_cosToSun(dir, sunDir), 0.85);
    vec3 c = aurum_skyLut(clamp(dir.y, 0.04, 1.0), cosA);
    return mix(c, vec3(dot(c, vec3(0.2126, 0.7152, 0.0722))), 0.25);
}
