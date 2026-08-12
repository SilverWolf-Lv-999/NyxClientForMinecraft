#version 330 core

in vec2 uv;

uniform sampler2D uTexture;
uniform vec2 uHalfTexelSize;
uniform float uOffset;

out vec4 color;

void main() {
    color = (
        texture(uTexture, uv) * 4.0 +
        texture(uTexture, uv - uHalfTexelSize * uOffset) +
        texture(uTexture, uv + uHalfTexelSize * uOffset) +
        texture(uTexture, uv + vec2(uHalfTexelSize.x, -uHalfTexelSize.y) * uOffset) +
        texture(uTexture, uv - vec2(uHalfTexelSize.x, -uHalfTexelSize.y) * uOffset)
    ) / 8.0;
    color.a = 1.0;
}
