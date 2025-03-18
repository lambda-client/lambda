attributes {
    vec4 pos;
    vec2 uv;
    vec4 color;
};

uniforms {
    vec2 u_Size;              # fragment
    float u_RoundLeftTop;     # fragment
    float u_RoundLeftBottom;  # fragment
    float u_RoundRightBottom; # fragment
    float u_RoundRightTop;    # fragment
};

#include "rect"
#define SMOOTHING 0.2
#define round getRoundAlpha()

float getRoundRadius() {
    bool xcmp = v_TexCoord.x > 0.5;
    bool ycmp = v_TexCoord.y > 0.5;

    float r = 0.0;

    if (xcmp) {
        if (ycmp) { r = u_RoundRightBottom; }
        else { r = u_RoundRightTop; }
    } else {
        if (ycmp) { r = u_RoundLeftBottom; }
        else { r = u_RoundLeftTop; }
    }

    return r;
}#

vec4 getRoundAlpha() {
    vec2 halfSize = u_Size * 0.5;

    float radius = max(getRoundRadius(), SMOOTHING);

    vec2 smoothVec = vec2(SMOOTHING);
    vec2 coord = mix(-smoothVec, u_Size + smoothVec, v_TexCoord);

    vec2 center = halfSize - coord;
    float distance = length(max(abs(center) - halfSize + radius, 0.0)) - radius;

    float alpha = 1.0 - smoothstep(-SMOOTHING, SMOOTHING, distance);
    return vec4(1.0, 1.0, 1.0, clamp(alpha, 0.0, 1.0));
}#

void fragment() {
    color = v_Color * shade * round + noise;
}#