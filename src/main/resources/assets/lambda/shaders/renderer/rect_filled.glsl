attributes {
    vec4 pos;
    vec2 uv;
    vec4 color;
};

#include "rect"

void fragment() {
    float distance = signedDistance();
    float alpha = 1 - smoothstep(-SMOOTHING, SMOOTHING, distance);
    color = v_Color * vec4(1.0, 1.0, 1.0, alpha) * shade + noise;
}#