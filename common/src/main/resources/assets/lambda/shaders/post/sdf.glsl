attributes {
    vec4 pos;
    vec2 uv;
};

uniforms {
    sampler2D u_Texture; # fragment
    vec2 u_TexelSize;    # fragment
};

export {
    vec2 v_TexCoord; # uv
};

#define SPREAD 4

void fragment() {
    vec4 colors = vec4(0.0);
    vec4 blurWeight = vec4(0.0);

    for (int x = -SPREAD; x <= SPREAD; ++x) {
        for (int y = -SPREAD; y <= SPREAD; ++y) {
            vec2 offset = vec2(x, y) * u_TexelSize;

            vec4 color = texture(u_Texture, v_TexCoord + offset);
            vec4 weight = exp(-color * color);

            colors += color * weight;
            blurWeight += weight;
        }
    }

    color = colors / blurWeight;
}#