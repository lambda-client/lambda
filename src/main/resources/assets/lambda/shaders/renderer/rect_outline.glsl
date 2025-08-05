attributes {
    vec4 pos;
    vec2 uv;
    vec4 color;
};

uniforms {
    float u_RectWidth; # fragment
};

#include "rect"

void fragment() {
    float distance = signedDistance();
    float innerAlpha = smoothstep(-u_RectWidth - SMOOTHING, -u_RectWidth + SMOOTHING, distance);
    float outerAlpha = 1 - smoothstep(u_RectWidth - SMOOTHING, u_RectWidth + SMOOTHING, distance);
    color = v_Color * vec4(1.0, 1.0, 1.0, min(innerAlpha, outerAlpha)) * shade + noise;
}#