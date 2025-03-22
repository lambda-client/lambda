attributes {
    vec4 pos;
    vec2 uv;
    vec4 color;
};

uniforms {
    sampler2D u_Texture; # fragment
};

export {
    vec2 v_TexCoord; # uv
    vec4 v_Color;    # color
};

void fragment() {
    color = texture(u_Texture, v_TexCoord) * v_Color;
}#