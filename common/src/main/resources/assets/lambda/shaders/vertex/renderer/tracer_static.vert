#version 330 core

layout (location = 0) in vec3 pos;
layout (location = 1) in vec4 color;

uniform mat4 u_ProjModel;

uniform vec3 u_CameraPosition;

out vec4 v_Color;

#define VERTEX_POSITION pos - u_CameraPosition

#define SCREEN_CENTER vec4(0.0, 0.0, 0.0, 1.0)
#define PROJECTED u_ProjModel * vec4(VERTEX_POSITION, 1.0)

void main() {
    gl_Position = gl_VertexID % 2 == 0 ? SCREEN_CENTER : PROJECTED;
    v_Color = color;
}
