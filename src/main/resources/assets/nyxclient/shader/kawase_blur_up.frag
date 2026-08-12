#version 330 core

in vec2 uv;

uniform sampler2D uTexture;
uniform vec2 uHalfTexelSize;
uniform float uOffset;

out vec4 color;

void main() {
    color = (
        texture(uTexture, uv + vec2(-uHalfTexelSize.x * 2.0, 0.0) * uOffset) +
        texture(uTexture, uv + vec2(-uHalfTexelSize.x, uHalfTexelSize.y) * uOffset) * 2.0 +
        texture(uTexture, uv + vec2(0.0, uHalfTexelSize.y * 2.0) * uOffset) +
        texture(uTexture, uv + uHalfTexelSize * uOffset) * 2.0 +
        texture(uTexture, uv + vec2(uHalfTexelSize.x * 2.0, 0.0) * uOffset) +
        texture(uTexture, uv + vec2(uHalfTexelSize.x, -uHalfTexelSize.y) * uOffset) * 2.0 +
        texture(uTexture, uv + vec2(0.0, -uHalfTexelSize.y * 2.0) * uOffset) +
        texture(uTexture, uv - uHalfTexelSize * uOffset) * 2.0
    ) / 12.0;
    color.a = 1.0;
}
