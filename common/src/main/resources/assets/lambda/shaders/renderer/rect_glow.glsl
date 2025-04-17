attributes {
    vec4 pos;
    vec2 uv;
    vec4 color;
};

uniforms {
    float u_RectWidth; # fragment
    float u_InnerRectWidth; # fragment

    float u_InnerRoundLeftTop;     # fragment
    float u_InnerRoundLeftBottom;  # fragment
    float u_InnerRoundRightBottom; # fragment
    float u_InnerRoundRightTop;    # fragment
};

#include "rect"

void fragment() {
    float distance = signedDistance();
    float innerDistance = signedDistance(vec4(
        u_InnerRoundRightBottom,
        u_InnerRoundRightTop,
        u_InnerRoundLeftBottom,
        u_InnerRoundLeftTop
    ));

    float bloomDistance = distance;
    if (innerDistance > 0.0 && distance < 0.0) bloomDistance = 0.0;
    if (innerDistance < 0.0) bloomDistance = innerDistance;

    float bloomSpread = ((bloomDistance > 0.0) ? u_RectWidth : -u_InnerRectWidth);
    float bloomAlpha = 1.0 / (1.0 + bloomDistance / bloomSpread);

    float glowAlpha =
        smoothstep(-u_InnerRectWidth, 0.0, bloomDistance) -
        smoothstep(0.0, u_RectWidth, bloomDistance);

    color = v_Color * vec4(1.0, 1.0, 1.0, bloomAlpha * glowAlpha) * shade + noise;
}#