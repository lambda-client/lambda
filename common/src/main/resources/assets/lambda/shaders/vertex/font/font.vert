#version 330 core

layout (location = 0) in vec4 pos;
layout (location = 1) in vec2 uv;
layout (location = 2) in vec2 sc1;
layout (location = 3) in vec2 sc2;
layout (location = 4) in vec4 color;

uniform mat4 u_ProjModel;

out vec2 v_TexCoord;
out vec2 v_Scissor1;
out vec2 v_Scissor2;

out vec4 v_Color;

void main() {
    vec4 proj = u_ProjModel * pos;
    vec4 div = proj / proj.w;
    gl_Position = vec4(div.x, div.y, 0.0, 1.0);

    v_TexCoord = uv;
    v_Scissor1 = sc1;
    v_Scissor2 = sc2;

    v_Color = color;
}