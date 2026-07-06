#version 330 core

in vec4 vertexColor;
in vec2 textureCoord;

uniform sampler2D FontTexture;

out vec4 fragColor;

void main() {
    vec4 sampled = texture(FontTexture, textureCoord);
    fragColor = vec4(vertexColor.rgb, vertexColor.a * sampled.a);
}
