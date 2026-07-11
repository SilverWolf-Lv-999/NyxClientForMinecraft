#version 330 core

layout(location = 0) in vec3 Position;
layout(location = 1) in vec4 Color;

uniform mat4 ModelViewProjection;
uniform float AlphaMultiplier;

out vec4 vertexColor;

void main() {
    vertexColor = vec4(Color.rgb, Color.a * AlphaMultiplier);
    gl_Position = ModelViewProjection * vec4(Position, 1.0);
}
