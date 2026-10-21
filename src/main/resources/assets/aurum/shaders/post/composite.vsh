#version 150

// Full-screen triangle generated from gl_VertexID; no vertex buffer needed.
out vec2 v_Uv;

void main() {
    vec2 p = vec2(float((gl_VertexID << 1) & 2), float(gl_VertexID & 2));
    v_Uv = p;
    gl_Position = vec4(p * 2.0 - 1.0, 0.0, 1.0);
}
