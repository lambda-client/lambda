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
#define SMOOTHING 0.5

float roundedRectSDF() {
    vec4 r = vec4(u_RoundRightBottom, u_RoundRightTop, u_RoundLeftBottom, u_RoundLeftTop);
    r.xy = (v_TexCoord.x > 0.5) ? r.xy : r.zw;
    r.x  = (v_TexCoord.y > 0.5) ? r.x  : r.y;

    vec2 q = u_Size * (abs(v_TexCoord - 0.5) - 0.5) + r.x;
    return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - r.x;
}#

void fragment() {
    float a = 1.0 - smoothstep(-SMOOTHING, 0.0, roundedRectSDF());
    color = v_Color * vec4(shade.rgb, shade.a * a) + noise;
}#