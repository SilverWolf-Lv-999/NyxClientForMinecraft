#version 330 core

in vec2 textureCoord;

uniform sampler2D InputTexture;
uniform vec2 TexelSize;
uniform vec2 Direction;
uniform float Radius;
uniform float Sigma;

out vec4 fragColor;

const int MAX_RADIUS = 32;

float gaussianWeight(float offset, float sigma) {
    return exp(-(offset * offset) / (2.0 * sigma * sigma));
}

void main() {
    int radius = int(clamp(ceil(Radius), 0.0, float(MAX_RADIUS)));
    float sigma = max(Sigma, 0.5);
    vec2 stepVector = Direction * TexelSize;

    vec4 color = texture(InputTexture, textureCoord) * gaussianWeight(0.0, sigma);
    float totalWeight = gaussianWeight(0.0, sigma);

    for (int i = 1; i <= MAX_RADIUS; i++) {
        if (i > radius) {
            break;
        }

        float weight = gaussianWeight(float(i), sigma);
        vec2 offset = stepVector * float(i);
        color += texture(InputTexture, textureCoord + offset) * weight;
        color += texture(InputTexture, textureCoord - offset) * weight;
        totalWeight += weight * 2.0;
    }

    fragColor = color / totalWeight;
}
