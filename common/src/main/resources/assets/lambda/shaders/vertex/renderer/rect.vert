#version 330 core

layout (location = 0) in vec4 pos;
layout (location = 1) in vec2 uv;
layout (location = 2) in vec3 data;
layout (location = 3) in vec4 color;

uniform mat4 u_Projection;
uniform mat4 u_ModelView;

out vec2 v_Position;
out vec2 v_TexCoord;
out vec4 v_Color;
out vec2 v_Size;
out float v_RoundRadius;

void main() {
    gl_Position = u_Projection * u_ModelView * pos;

    v_Position = pos.xy;
    v_TexCoord = uv;
    v_Color = color;

    v_Size = data.xy;
    v_RoundRadius = data.z;
}