attributes {
    vec3 pos;
    vec2 uv;
    vec4 color;
};

uniforms {
    vec3 u_CameraPosition; # vertex
};

export {
    core gl_Position; # u_ProjModel * vec4(pos - u_CameraPosition, 1.0)
    vec2 v_TexCoord;  # uv
    vec4 v_Color;     # color
};

void fragment() {
    float a = 1.0 - length(v_TexCoord - 0.5) * 2.0;
    color = v_Color * vec4(1.0, 1.0, 1.0, a);
}#