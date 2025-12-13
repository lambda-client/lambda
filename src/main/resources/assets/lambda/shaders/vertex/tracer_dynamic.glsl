#version 330 core

layout (location = 0) in vec3 pos1;
layout (location = 1) in vec2 pos1;
layout (location = 2) in vec4 color;

out vec4 v_Color;

uniform mat4 u_ProjModel;
uniform mat4 u_View;
uniform float u_TickDelta;

void main()
{
    if (l_VertexID % 2 != 0)
            return;

    vec3 VERTEX_POSITION = mix(pos1, pos2, u_TickDelta);
    gl_Position = u_ProjModel * u_View * vec4(VERTEX_POSITION, 1.0);
}