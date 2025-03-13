#version 330 core

uniform float u_Shade;
uniform float u_ShadeTime;
uniform vec4 u_ShadeColor1;
uniform vec4 u_ShadeColor2;
uniform vec2 u_ShadeSize;

in vec2 v_Position;
in float v_Alpha;
in vec4 v_Color;

out vec4 color;

vec4 shade() {
    if (u_Shade != 1.0) return v_Color;

    vec2 pos = v_Position * u_ShadeSize;
    float p = sin(pos.x - pos.y - u_ShadeTime) * 0.5 + 0.5;

    return mix(u_ShadeColor1, u_ShadeColor2, p) * v_Color;
}

vec4 glow() {
    float newAlpha = min(1.0, v_Alpha * v_Alpha * v_Alpha);
    return vec4(1.0, 1.0, 1.0, newAlpha);
}

void main() {
    color = shade() * glow();
}