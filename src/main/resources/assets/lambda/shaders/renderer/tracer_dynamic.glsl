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
    core gl_Position;  # vec4(0.0, 0.0, 0.0, 1.0)
    vec4 v_Color;      # color
};

void vertex() {
    if (gl_VertexID % 2 == 0) {
        vec3 VERTEX_POSITION = mix(pos1, pos2, u_TickDelta) - u_CameraPosition;
        gl_Position = u_ProjModel * vec4(VERTEX_POSITION, 1.0);
    }
}#

void fragment() {
    color = v_Color;
}#