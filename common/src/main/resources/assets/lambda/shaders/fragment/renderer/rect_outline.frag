#version 330 core

uniform float u_Time;
uniform vec4 u_Color1;
uniform vec4 u_Color2;
uniform vec2 u_Size;

in vec2 v_Position;
in float v_Alpha;
in vec4 v_Color;
in float v_Shade;

out vec4 color;

vec4 shade() {
    if (v_Shade != 1.0) return v_Color;

    vec2 pos = v_Position * u_Size;
    float p = sin(pos.x - pos.y - u_Time) * 0.5 + 0.5;

    return mix(u_Color1, u_Color2, p) * v_Color;
}

vec4 glow() {
    float newAlpha = min(1.0, v_Alpha * v_Alpha * v_Alpha);
    return vec4(1.0, 1.0, 1.0, newAlpha);
}

void main() {
    color = shade() * glow();
}