#version 330 core

layout (location = 0) in vec3 pos1;
layout (location = 1) in vec3 pos2;
layout (location = 2) in vec4 color;

out vec4 v_Color;

uniform mat4 u_ProjModel;
uniform mat4 u_View;
uniform float u_TickDelta;

void main()
{
    gl_Position = u_ProjModel * u_View * vec4(mix(pos1, pos2, u_TickDelta), 1.0);
    v_Color = color;
}