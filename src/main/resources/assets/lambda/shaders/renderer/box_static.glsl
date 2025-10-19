attributes {
    vec3 pos;
    vec4 color;
};

uniforms {
    mat4 u_ProjModel;      # vertex
    vec3 u_CameraLerp; # vertex
};

export {
    core gl_Position;  # u_ProjModel * vec4(pos + u_CameraLerp, 1.0)
    vec4 v_Color;      # color
};

void fragment() {
    color = v_Color;
}#