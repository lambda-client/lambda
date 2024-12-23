#version 330 core

layout (location = 0) in vec4 pos;
layout (location = 1) in vec2 uv;
layout (location = 2) in float alpha;
layout (location = 3) in float shade;
layout (location = 4) in vec2 sc1;
layout (location = 5) in vec2 sc2;
layout (location = 6) in vec4 color;

uniform mat4 u_ProjModel;

out vec2 v_Position;
out vec2 v_TexCoord;
out vec2 v_Scissor1;
out vec2 v_Scissor2;
out float v_Alpha;
out vec4 v_Color;
out float v_Shade;

void main() {
    gl_Position = u_ProjModel * pos;

    v_Position = gl_Position.xy * 0.5 + 0.5;
    v_TexCoord = uv;
    v_Scissor1 = sc1;
    v_Scissor2 = sc2;

    v_Alpha = alpha;
    v_Color = color;
    v_Shade = shade;
}