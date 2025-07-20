attributes {
    vec4 pos;
    vec2 uv;
    vec4 color;
};

uniforms {
    sampler2D u_FontTexture;  # fragment
    sampler2D u_EmojiTexture; # fragment
    float u_SDFMin;           # fragment
    float u_SDFMax;           # fragment
};

export {
    vec2 v_TexCoord; # uv
    vec4 v_Color;    # color
};

#include "sdf"

void fragment() {
    bool isEmoji = v_TexCoord.x < 0.0;

    if (isEmoji) {
        vec4 c = texture(u_EmojiTexture, -v_TexCoord);
        color = vec4(c.rgb, sdf(c.a, u_SDFMin, u_SDFMax)) * v_Color;
        return;
    }

    float sdf = sdf(texture(u_FontTexture, v_TexCoord).r, u_SDFMin, u_SDFMax);
    color = vec4(1.0, 1.0, 1.0, sdf) * v_Color;
}#