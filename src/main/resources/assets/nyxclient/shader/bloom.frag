#version 330 core

in vec2 textureCoord;

uniform vec2 TextureSize;
uniform vec4 Rect;
uniform float Radius;
uniform float BlurRadius;

out vec4 fragColor;

const float BLOOM_STRENGTH = 0.42;

float roundedRectDistance(vec2 point, vec2 rectPosition, vec2 rectSize, float radius) {
    float safeRadius = min(radius, min(rectSize.x, rectSize.y) * 0.5);
    vec2 halfSize = rectSize * 0.5 - vec2(safeRadius);
    vec2 center = rectPosition + rectSize * 0.5;
    vec2 q = abs(point - center) - halfSize;
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - safeRadius;
}

void main() {
    vec2 point = textureCoord * TextureSize;
    float distance = roundedRectDistance(point, Rect.xy, Rect.zw, Radius);
    float sigma = max(BlurRadius * 0.35, 0.5);
    float outsideDistance = max(distance, 0.0);
    float alpha = exp(-(outsideDistance * outsideDistance) / (2.0 * sigma * sigma));

    fragColor = vec4(1.0, 1.0, 1.0, alpha * BLOOM_STRENGTH);
}
