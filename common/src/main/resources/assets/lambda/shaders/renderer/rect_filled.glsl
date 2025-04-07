attributes {
    vec2 uv;
};

#include "rect"

void fragment() {
    if (v_Color.a == 0.0) discard;

    float distance = signedDistance();
    float alpha = 1 - smoothstep(-SMOOTHING, SMOOTHING, distance);
    color = v_Color * vec4(1.0, 1.0, 1.0, alpha) * shade + noise;
}#
