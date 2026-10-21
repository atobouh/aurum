// AgX-style display transform, written for Aurum. Works on linear HDR input, returns display (sRGB-ish) output.
// Inset/outset matrices are the published AgX working-space matrices; the curve is our own smooth sigmoid
// fitted around mid grey, with a steeper slope for the "Punchy" look.
//
// u_ToneParams: x = mode (0 AgX Punchy, 1 AgX, 2 off), y = exposure multiplier

uniform vec4 u_ToneParams;

const mat3 AGX_INSET = mat3(0.842479062, 0.042328242, 0.042375655,
                            0.078433600, 0.878468636, 0.078433600,
                            0.079223745, 0.079166127, 0.879142974);
const mat3 AGX_OUTSET = mat3(1.196879005, -0.052896852, -0.052971636,
                             -0.098020881, 1.151903130, -0.098043450,
                             -0.099029744, -0.098961177, 1.151073673);
const float AGX_MIN_EV = -12.47393;
const float AGX_MAX_EV = 4.026069;
const float AGX_MID = 0.6060605; // log-encoded position of 0.18 grey

float aurum_sigmoid(float x, float slope) {
    // Smooth S curve through (AGX_MID, 0.5) with the given slope there, normalised to hit 0 and 1 at the ends.
    float u0 = -AGX_MID * slope * 2.0;
    float u1 = (1.0 - AGX_MID) * slope * 2.0;
    float u = (x - AGX_MID) * slope * 2.0;
    float s = u / sqrt(1.0 + u * u);
    float s0 = u0 / sqrt(1.0 + u0 * u0);
    float s1 = u1 / sqrt(1.0 + u1 * u1);
    return (s - s0) / (s1 - s0);
}

vec3 aurum_agx(vec3 color, float slope) {
    color = AGX_INSET * max(color, vec3(0.0));
    color = clamp(log2(max(color, vec3(1e-10))), AGX_MIN_EV, AGX_MAX_EV);
    color = (color - AGX_MIN_EV) / (AGX_MAX_EV - AGX_MIN_EV);
    color = vec3(aurum_sigmoid(color.r, slope), aurum_sigmoid(color.g, slope), aurum_sigmoid(color.b, slope));
    // The curve output is display-encoded; go back to linear for the outset, then re-encode.
    color = AGX_OUTSET * pow(color, vec3(2.2));
    return pow(clamp(color, 0.0, 1.0), vec3(1.0 / 2.2));
}

vec3 aurum_punch(vec3 c, float amount) {
    // The "Punchy" look: AgX greys colours on purpose; give some saturation back after the curve.
    float l = dot(c, vec3(0.2126, 0.7152, 0.0722));
    return clamp(mix(vec3(l), c, amount), 0.0, 1.0);
}

vec3 aurum_tonemap(vec3 hdr) {
    hdr *= u_ToneParams.y;
    if (u_ToneParams.x < 0.5) return aurum_punch(aurum_agx(hdr, 1.32), 1.3);
    if (u_ToneParams.x < 1.5) return aurum_agx(hdr, 1.12);
    return pow(clamp(hdr, 0.0, 1.0), vec3(1.0 / 2.2));
}
