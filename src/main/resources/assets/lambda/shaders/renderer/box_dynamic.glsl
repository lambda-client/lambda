attributes {
    vec3 pos1;
    vec3 pos2;
    vec4 color;
};

uniforms {
    mat4 u_ProjModel;      # vertex
    float u_TickDelta;     # vertex
    vec3 u_CameraPosition; # vertex
};

export {
    core gl_Position;  # u_ProjModel * vec4(mix(pos1, pos2, u_TickDelta) - u_CameraPosition, 1.0)
    vec4 v_Color;      # color
};

void fragment() {
    color = v_Color;
}#