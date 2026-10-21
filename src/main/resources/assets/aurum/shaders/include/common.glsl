// Shared constants and helpers. Conservative GLSL 1.50 only (spec rule 9): no extensions, plain float maths.
#ifndef AURUM_COMMON
#define AURUM_COMMON

const float PI = 3.14159265;
const float TAU = 6.28318531;

float saturate(float x) { return clamp(x, 0.0, 1.0); }
vec3 saturate(vec3 x) { return clamp(x, 0.0, 1.0); }

float luminance(vec3 c) { return dot(c, vec3(0.2126, 0.7152, 0.0722)); }

#endif
