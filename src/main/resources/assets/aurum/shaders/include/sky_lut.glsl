// Sky radiance from the baked lookup table (see SkyLut.java for the layout). No inverse trig: one sqrt per axis.

uniform sampler2D u_SkyLut;
// Daytime sky grade amount: 1 by day, 0 at golden hour and night (set per program; unset means 0).
uniform float u_SkyGrade;
// Look-preset sky tone: x saturation offset (0 = as is), y lavender (+) / golden (-) tint. Unset = neutral.
uniform vec4 u_SkyTone;
uniform vec4 u_SkyAppearance; // x daylight clarity, y daytime sky temperature; lighting stays unchanged

// The sky is viewed directly and through haze; with the linear tone curve it needs its own gain to read as the
// bright, open Minecraft sky (terrain exposure is anchored on lit surfaces, not on sky radiance).
const float SKY_GAIN = 3.0;

vec3 aurum_skyLut(float y, float cosA) {
    // Skip the table's first sky row (a sliver under 0.1 degrees of the deepest, longest-path red, which reads
    // as a painted stripe) and never filter into the below-horizon half.
    float v = 0.5 + 0.5 * sqrt(max(y, 0.0045));
    float u = sqrt(clamp(0.5 - 0.5 * cosA, 0.0, 1.0));
    // A small daylight lift leaves room for the blue-to-white gradient from the atmosphere model. The former
    // 32% desaturation and 12% lift made the open sky almost the same pale colour in every direction.
    // Leave the open sky bright, but give the sun-facing sunset more headroom for the disc and cloud light.
    // Compress the broad sun-facing sky, not just the disc's immediate pixels. The low sun should keep
    // its amber gradient while the open sky away from it retains the full daytime gain.
    float gain = mix(SKY_GAIN, 0.78, u_SkyTone.z * pow(max(cosA, 0.0), 1.5));
    // 0.7.7: at golden hour the whole low sky read near-white (220-255) where the references keep a darker
    // lavender-grey (130-170) and only the sun's surroundings glow. Dim the sky away from the sun.
    gain *= 1.0 - 0.42 * u_SkyTone.z * (1.0 - pow(max(cosA, 0.0), 6.0));
    vec3 c = texture(u_SkyLut, vec2(u, v)).rgb * gain;
    // Long atmospheric paths and the camera shoulder made the low noon sky almost neutral white. Shape
    // only displayed daylight radiance, over a broad elevation range, instead of warming/cooling blocks.
    float clarity = u_SkyGrade * u_SkyAppearance.x;
    float lowSky = 1.0 - smoothstep(0.0, 0.35, max(y, 0.0));
    c *= mix(vec3(1.0), vec3(0.60, 0.77, 1.0), clarity * lowSky);
    // Greying only toward the horizon (0.8.0): applied to the whole sky it left the zenith a flat, milky lavender.
    float grey = mix(0.12, 0.035, u_SkyAppearance.x) * lowSky;
    c = mix(c, mix(c, vec3(dot(c, vec3(0.2126, 0.7152, 0.0722))), grey)
                    * mix(1.035, 1.0, u_SkyAppearance.x), u_SkyGrade);
    c *= vec3(1.0) + vec3(0.15, 0.015, -0.12) * u_SkyAppearance.y * u_SkyGrade;
    float l = dot(c, vec3(0.2126, 0.7152, 0.0722));
    // The sky-saturation setting shapes the daytime sky; at golden hour it gave way to a +30% boost (0.7.9: the
    // 70% default had been muting every sunset to grey-peach, unlike Photon's orange-to-lavender skies).
    // Daytime gradient (0.8.0, user: "our sky transition is bad, washed out"): measured row by row, our clear sky
    // was one flat grey-blue from 80 degrees down to the horizon, (105,128,164) -> (114,140,177), where
    // Nostalgia runs from a deep (62,96,144) overhead to (101,118,158) low, and Photon from a clear blue to pale
    // grey at the horizon. Colour and depth now follow elevation: deep, saturated and a little darker overhead,
    // pale low down. The sky-saturation setting scales the whole curve (70 = the default curve).
    float elev = smoothstep(0.0, 0.65, max(y, 0.0));
    float userSat = (1.0 + u_SkyTone.x) / 0.7;
    float daySat = mix(0.62, 1.12, elev) * userSat;
    float sat = mix(1.0 + u_SkyTone.x, daySat, u_SkyGrade) * (1.0 - u_SkyTone.z) + 1.30 * u_SkyTone.z;
    c = max(mix(vec3(l), c, sat), vec3(0.0));
    // Brightness: our horizon was twice as bright as the zenith (a wide, flat white band), the references only a
    // little brighter. Calm the low sky by day and at golden hour (away from the sun's glow).
    float horizonCalm = max(u_SkyGrade, u_SkyTone.z * (1.0 - pow(max(cosA, 0.0), 4.0)));
    c *= mix(1.0, mix(0.58, 0.86, elev), horizonCalm);
    // Daytime hue path (0.8.2). Measured from the clear sky, top to horizon (R:G:B relative to blue):
    //   Nostalgia (0.41,0.65,1) overhead -> (0.81,0.81,1) lavender at the horizon; Photon -> (0.91,0.95,1)
    //   neutral grey-blue. Ours ran cyan in the middle and mint-white (0.88,0.98,1) at the horizon. The
    //   atmosphere model keeps the brightness; the colour follows the references' path, except near the sun,
    //   whose warm glow stays as the model computes it.
    // Display ratios converted to linear light (^2.2): the sky is still tone-mapped and graded after this.
    vec3 hue = mix(vec3(0.66, 0.69, 1.0), vec3(0.16, 0.40, 1.0), pow(elev, 0.8));
    float Lc = dot(c, vec3(0.2126, 0.7152, 0.0722));
    vec3 target = hue * (Lc / dot(hue, vec3(0.2126, 0.7152, 0.0722)));
    float nearSun = pow(max(cosA, 0.0), 8.0) * smoothstep(0.6, 0.0, y);
    c = mix(c, target, 0.80 * u_SkyGrade * (1.0 - nearSun));
    vec3 tone = u_SkyTone.y >= 0.0 ? u_SkyTone.y * vec3(0.07, -0.05, 0.09) : -u_SkyTone.y * vec3(0.12, 0.03, -0.38);
    // The tint matters most at sunrise and sunset (z = golden-hour amount), where the packs differ most.
    return c * max(vec3(1.0) + tone * (1.0 + 2.5 * u_SkyTone.z), vec3(0.2));
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
