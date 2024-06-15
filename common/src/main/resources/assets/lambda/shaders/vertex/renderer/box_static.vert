#version 330 core

layout (location = 0) in vec3 pos;
layout (location = 1) in vec4 color;

uniform mat4 u_ProjModel;
uniform vec3 u_CameraPosition;

out vec4 v_Color;

#define VERTEX_POSITION pos - u_CameraPosition

void main() {
    gl_Position = u_ProjModel * vec4(VERTEX_POSITION, 1.0);
    v_Color = color;
}
