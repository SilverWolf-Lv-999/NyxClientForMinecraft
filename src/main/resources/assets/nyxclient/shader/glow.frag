#version 330 core

in vec2 textureCoord;

uniform vec2 TextureSize;
uniform vec4 Rect;
uniform float Radius;
uniform float GlowRadius;

out vec4 fragColor;

float roundedRectDistance(vec2 point, vec2 rectPosition, vec2 rectSize, float radius) {
    float safeRadius = min(radius, min(rectSize.x, rectSize.y) * 0.5);
    vec2 halfSize = rectSize * 0.5 - vec2(safeRadius);
    vec2 center = rectPosition + rectSize * 0.5;
    vec2 q = abs(point - center) - halfSize;
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - safeRadius;
}

void main() {
    float distance = roundedRectDistance(textureCoord * TextureSize, Rect.xy, Rect.zw, Radius);
    float outsideDistance = max(distance, 0.0);
    float outsideMask = smoothstep(0.0, 1.0, outsideDistance);
    float falloff = 1.0 - smoothstep(0.0, max(GlowRadius, 0.0001), outsideDistance);

    fragColor = vec4(1.0, 1.0, 1.0, outsideMask * falloff * falloff);
}
