attributes {
    vec4 pos;
    vec2 uv;
};

uniforms {
    sampler2D u_Texture; # fragment
};

export {
    vec4 v_TexCoord; # uv
};

void fragment() {
    color = texture(u_Texture, v_TexCoord);
}#