#version 330 core

layout (location = 0) in vec3 pos;
layout (location = 1) in vec2 uv;
layout (location = 2) in vec4 color;

out vec2 v_TexCoord;
out vec4 v_Color;

uniform mat4 u_ProjModel;
uniform mat4 u_View;

void main()
{
    gl_Position = u_ProjModel * u_View * vec4(pos, 1.0);

    v_TexCoord = uv;
    v_Color = color;
}