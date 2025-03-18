attributes {
    vec4 pos;
    vec2 uv;
    float alpha;
    vec4 color;
};

export {
    float v_Alpha; # alpha
};

#include "rect"
#define glow glowAlpha()

vec4 glowAlpha() {
    float newAlpha = min(1.0, v_Alpha * v_Alpha * v_Alpha);
    return vec4(1.0, 1.0, 1.0, newAlpha);
}#

void fragment() {
    color = v_Color * shade * glow;
}#