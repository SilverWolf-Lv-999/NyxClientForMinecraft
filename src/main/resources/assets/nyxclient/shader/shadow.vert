#version 330 core

uniform vec2 u_Size;

out vec2 v_TexCoord;
out vec2 v_OneTexel;

void main() {
    vec2 position;
    if (gl_VertexID == 0) {
        position = vec2(-1.0, -1.0);
    } else if (gl_VertexID == 1) {
        position = vec2(-1.0, 3.0);
    } else {
        position = vec2(3.0, -1.0);
    }

    v_TexCoord = (position + 1.0) * 0.5;
    v_OneTexel = 1.0 / u_Size;
    gl_Position = vec4(position + v_OneTexel * 0.5, 0.0, 1.0);
}
