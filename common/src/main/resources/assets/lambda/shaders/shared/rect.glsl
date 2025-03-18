attributes {
    vec2 uv;
};

export {
    vec2 v_TexCoord; # uv
    vec4 v_Color;    # color
};

#include "shade"

#define NOISE_GRANULARITY 0.004
#define noise getNoise()

vec4 getNoise() {
    // https://shader-tutorial.dev/advanced/color-banding-dithering/
    float random = fract(sin(dot(v_TexCoord, vec2(12.9898, 78.233))) * 43758.5453);
    float ofs = mix(-NOISE_GRANULARITY, NOISE_GRANULARITY, random);
    return vec4(ofs, ofs, ofs, 0.0);
}#