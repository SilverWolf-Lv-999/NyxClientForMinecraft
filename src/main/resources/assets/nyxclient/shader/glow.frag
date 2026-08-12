#version 330 core

in vec2 textureCoord;

uniform sampler2D InputTexture;
uniform vec2 TextureSize;
uniform vec4 Rect;
uniform float Radius;
uniform bool UseTextureMask;

out vec4 fragColor;

float roundedRectDistance(vec2 point, vec2 rectPosition, vec2 rectSize, float radius) {
    float safeRadius = min(radius, min(rectSize.x, rectSize.y) * 0.5);
    vec2 halfSize = rectSize * 0.5 - vec2(safeRadius);
    vec2 center = rectPosition + rectSize * 0.5;
    vec2 q = abs(point - center) - halfSize;
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - safeRadius;
}

void main() {
    vec2 point = textureCoord * TextureSize;
    float alpha;
    if (UseTextureMask) {
        vec2 uv = (point - Rect.xy) / Rect.zw;
        if (uv.x < 0.0 || uv.y < 0.0 || uv.x > 1.0 || uv.y > 1.0) {
            fragColor = vec4(0.0);
            return;
        }
        alpha = texture(InputTexture, uv).a;
    } else {
        float distance = roundedRectDistance(point, Rect.xy, Rect.zw, Radius);
        alpha = 1.0 - smoothstep(0.0, 1.0, distance);
    }

    fragColor = vec4(1.0, 1.0, 1.0, alpha);
}
