#version 330 core

uniform float u_Time;
uniform vec4 u_Color1;
uniform vec4 u_Color2;
uniform vec2 u_Size;

in vec2 v_Position;
in vec2 v_TexCoord;
in vec4 v_Color;
in vec2 v_Size;
in float v_RoundRadius;
in float v_Shade;

out vec4 color;

#define SMOOTHING 0.5

vec4 shade() {
    if (v_Shade != 1.0) return v_Color;

    vec2 pos = v_Position * u_Size;
    float p = sin(pos.x - pos.y - u_Time) * 0.5 + 0.5;

    return mix(u_Color1, u_Color2, p) * v_Color;
}

vec4 round() {
    vec2 halfSize = v_Size * 0.5;

    float radius = max(v_RoundRadius, SMOOTHING);

    vec2 smoothVec = vec2(SMOOTHING);
    vec2 coord = mix(-smoothVec, v_Size + smoothVec, v_TexCoord);

    vec2 center = halfSize - coord;
    float distance = length(max(abs(center) - halfSize + radius, 0.0)) - radius;

    float alpha = 1.0 - smoothstep(-SMOOTHING, SMOOTHING, distance);
    return vec4(1.0, 1.0, 1.0, clamp(alpha, 0.0, 1.0));
}

void main() {
    color = shade() * round();
}