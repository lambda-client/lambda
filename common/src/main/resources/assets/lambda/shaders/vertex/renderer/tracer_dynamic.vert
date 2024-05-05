#version 330 core

layout (location = 0) in vec3 pos1;
layout (location = 1) in vec3 pos2;
layout (location = 2) in vec4 color;

uniform mat4 u_Projection;
uniform mat4 u_ModelView;

uniform float u_TickDelta;
uniform vec3 u_CameraPosition;

out vec4 v_Color;

#define VERTEX_POSITION mix(pos1, pos2, u_TickDelta) - u_CameraPosition

#define SCREEN_CENTER vec4(0.0, 0.0, 0.0, 1.0)
#define PROJECTED u_Projection * u_ModelView * vec4(VERTEX_POSITION, 1.0)

void main() {
    gl_Position = gl_VertexID % 2 == 0 ? SCREEN_CENTER : PROJECTED;
    v_Color = color;
}
