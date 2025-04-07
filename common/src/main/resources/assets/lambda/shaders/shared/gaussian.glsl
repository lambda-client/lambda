attributes {
    vec2 uv;
};

uniforms {
    sampler2D u_Texture; # fragment
    vec2 u_TexelSize;    # global
    vec2 u_Extend;       # vertex

    vec2 u_Position1; # vertex
    vec2 u_Position2; # vertex
};

export {
    core gl_Position; # u_ProjModel * vec4(mix(u_Position1, u_Position2, uv), 0.0, 1.0) + vec4(u_TexelSize * u_Extend * vec2(uv.x - 0.5, 0.5 - uv.y) * 2 * 12, 0.0, 0.0)
    vec2 v_TexCoord;  # gl_Position.xy * 0.5 + 0.5
};

#define WEIGHT_BASE 0.09950225481157

#define WEIGHT_0 0.18446050802858
#define WEIGHT_1 0.13614942259252
#define WEIGHT_2 0.07862968075756
#define WEIGHT_3 0.03538335634090
#define WEIGHT_4 0.01232869558916
#define WEIGHT_5 0.00329720928547

#define OFFSET_0 1.47692307692307
#define OFFSET_1 3.44615384615384
#define OFFSET_2 5.41538461538461
#define OFFSET_3 7.38461538461538
#define OFFSET_4 9.35384615384615
#define OFFSET_5 11.3230769230769
