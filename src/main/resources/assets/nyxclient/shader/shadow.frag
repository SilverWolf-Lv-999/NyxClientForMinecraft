#version 330 core

in vec2 v_TexCoord;
in vec2 v_OneTexel;

uniform sampler2D u_Texture;
uniform vec2 u_Direction;

out vec4 color;

const int U_RADIUS = 3;
const float U_WEIGHTS[7] = float[](
    0.05199096,
    0.054712396,
    0.056413162,
    0.056991756,
    0.056413162,
    0.054712396,
    0.05199096
);

void main() {
    vec4 finalColor = vec4(0.0);
    float totalWeight = 0.0;

    for (int i = -U_RADIUS; i <= U_RADIUS; ++i) {
        float weight = U_WEIGHTS[i + U_RADIUS];
        finalColor += texture(u_Texture, v_TexCoord + v_OneTexel * float(i) * u_Direction) * weight;
        totalWeight += weight;
    }

    color = finalColor / totalWeight;
}
