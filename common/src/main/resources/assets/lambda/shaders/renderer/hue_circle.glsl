attributes {
    vec4 pos;
    vec2 uv;
};

export {
    vec2 v_TexCoord; # uv
};

#include "hsb"
#include "sdf"

void fragment() {
    float dst = 0.5 - length(v_TexCoord - 0.5);
    float sdf = sdf(dst, 0.98, 1.0);

    float hue255 = hue(vec2(v_TexCoord.y, 1.0 - v_TexCoord.x));
    vec3 rgb = hsb2rgb(vec3(hue255, 1.0 - dst * 2.0, 1.0));

    color = vec4(rgb, sdf);
}#
