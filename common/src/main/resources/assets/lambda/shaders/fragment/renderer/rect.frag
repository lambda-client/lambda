#version 330 core

in vec2 v_TexCoord;
in vec4 v_Color;
in vec2 v_Size;
in float v_RoundRadius;

out vec4 color;

#define SMOOTHING 0.5

void main() {
    vec2 halfSize = v_Size * 0.5;

    float radius = max(v_RoundRadius, SMOOTHING);

    vec2 smoothVec = vec2(SMOOTHING);
    vec2 coord = mix(-smoothVec, v_Size + smoothVec, v_TexCoord);

    vec2 center = halfSize - coord;
    float distance = length(max(abs(center) - halfSize + radius, 0.0)) - radius;

    float alpha = 1.0 - smoothstep(-SMOOTHING, SMOOTHING, distance);
    alpha = clamp(alpha, 0.0, 1.0);

    color = v_Color * vec4(1.0, 1.0, 1.0, alpha);
}