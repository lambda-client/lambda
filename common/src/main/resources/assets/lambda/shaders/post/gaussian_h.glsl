attributes {
    vec2 uv;
};

#include "gaussian"

void fragment() {
    vec4 sum = texture(u_Texture, v_TexCoord) * WEIGHT_BASE;

    sum += texture(u_Texture, v_TexCoord + vec2(OFFSET_0 * u_TexelSize.x, 0.0)) * WEIGHT_0;
    sum += texture(u_Texture, v_TexCoord - vec2(OFFSET_0 * u_TexelSize.x, 0.0)) * WEIGHT_0;

    sum += texture(u_Texture, v_TexCoord + vec2(OFFSET_1 * u_TexelSize.x, 0.0)) * WEIGHT_1;
    sum += texture(u_Texture, v_TexCoord - vec2(OFFSET_1 * u_TexelSize.x, 0.0)) * WEIGHT_1;

    sum += texture(u_Texture, v_TexCoord + vec2(OFFSET_2 * u_TexelSize.x, 0.0)) * WEIGHT_2;
    sum += texture(u_Texture, v_TexCoord - vec2(OFFSET_2 * u_TexelSize.x, 0.0)) * WEIGHT_2;

    sum += texture(u_Texture, v_TexCoord + vec2(OFFSET_3 * u_TexelSize.x, 0.0)) * WEIGHT_3;
    sum += texture(u_Texture, v_TexCoord - vec2(OFFSET_3 * u_TexelSize.x, 0.0)) * WEIGHT_3;

    sum += texture(u_Texture, v_TexCoord + vec2(OFFSET_4 * u_TexelSize.x, 0.0)) * WEIGHT_4;
    sum += texture(u_Texture, v_TexCoord - vec2(OFFSET_4 * u_TexelSize.x, 0.0)) * WEIGHT_4;

    sum += texture(u_Texture, v_TexCoord + vec2(OFFSET_5 * u_TexelSize.x, 0.0)) * WEIGHT_5;
    sum += texture(u_Texture, v_TexCoord - vec2(OFFSET_5 * u_TexelSize.x, 0.0)) * WEIGHT_5;

    color = sum;
}#
