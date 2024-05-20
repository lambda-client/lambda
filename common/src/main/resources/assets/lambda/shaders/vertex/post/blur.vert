#version 330 core

layout (location = 0) in vec4 pos;
layout (location = 1) in vec2 uv;

uniform mat4 u_Projection;
uniform mat4 u_ModelView;

out vec2 v_TexCoord;

void main() {
    gl_Position = u_Projection * u_ModelView * pos;
    v_TexCoord = uv;
}