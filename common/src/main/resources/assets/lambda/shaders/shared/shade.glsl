uniforms {
    float u_Shade;      # fragment
    float u_ShadeTime;  # fragment
    vec4 u_ShadeColor1; # fragment
    vec4 u_ShadeColor2; # fragment
    vec2 u_ShadeSize;   # fragment
};

export {
    vec2 v_Position; # gl_Position.xy * 0.5 + 0.5
};

#define shade getShadeColor()

vec4 getShadeColor() {
    if (u_Shade != 1.0) return vec4(1.0);

    vec2 pos = v_Position * u_ShadeSize;
    float p = sin(pos.x - pos.y - u_ShadeTime) * 0.5 + 0.5;

    return mix(u_ShadeColor1, u_ShadeColor2, p);
}#