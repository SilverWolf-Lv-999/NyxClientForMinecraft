#version 330 core

layout(location = 0) in vec2 Position;
layout(location = 1) in vec4 Color;
layout(location = 2) in vec2 TexCoord;

uniform vec2 ScreenSize;

out vec4 vertexColor;
out vec2 textureCoord;

void main() {
    vec2 normalized = vec2(Position.x / ScreenSize.x * 2.0 - 1.0, 1.0 - Position.y / ScreenSize.y * 2.0);
    gl_Position = vec4(normalized, 0.0, 1.0);
    vertexColor = Color;
    textureCoord = TexCoord;
}
