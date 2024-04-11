#version 330 core

layout (location = 0) in vec4 pos;
layout (location = 1) in vec2 uv;
layout (location = 2) in vec4 color;

uniform mat4 u_Projection;
uniform mat4 u_ModelView;

out vec2 v_TexCoord;
out vec4 v_Color;

void main() {
    gl_Position = u_Projection * u_ModelView * pos;

    v_TexCoord = uv;
    v_Color = color;
}