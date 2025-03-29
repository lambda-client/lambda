attributes {
    vec4 pos;
    vec2 uv;
    vec4 color;
};

uniforms {
    vec2 u_Size; # fragment

    float u_RoundLeftTop;     # fragment
    float u_RoundLeftBottom;  # fragment
    float u_RoundRightBottom; # fragment
    float u_RoundRightTop;    # fragment
};

export {
    vec2 v_TexCoord; # uv
    vec4 v_Color;    # color
};

#include "shade"

#define NOISE_GRANULARITY 0.004
#define SMOOTHING 0.3

#define noise getNoise()

vec4 getNoise() {
    // https://shader-tutorial.dev/advanced/color-banding-dithering/
    float random = fract(sin(dot(v_TexCoord, vec2(12.9898, 78.233))) * 43758.5453);
    float ofs = mix(-NOISE_GRANULARITY, NOISE_GRANULARITY, random);
    return vec4(ofs, ofs, ofs, 0.0);
}#

float signedDistance(in vec4 r) {
    r.xy = (v_TexCoord.x > 0.5) ? r.xy : r.zw;
    r.x  = (v_TexCoord.y > 0.5) ? r.x  : r.y;

    vec2 q = u_Size * (abs(v_TexCoord - 0.5) - 0.5) + r.x;
    return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - r.x;
}#

float signedDistance() {
    return signedDistance(vec4(u_RoundRightBottom, u_RoundRightTop, u_RoundLeftBottom, u_RoundLeftTop));
}#
