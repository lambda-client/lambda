#version 330 core

uniform sampler2D u_Texture;
uniform float u_Time;
uniform vec4 u_Color1;
uniform vec4 u_Color2;
uniform vec2 u_Size;

in vec2 v_Position;
in vec2 v_TexCoord;

out vec4 color;

vec4 shade() {
    vec2 pos = v_Position * u_Size;
    float p = sin(pos.x - pos.y - u_Time) * 0.5 + 0.5;

    return mix(u_Color1, u_Color2, p);
}

void main() {
    color = texture(u_Texture, v_TexCoord) * shade();
}