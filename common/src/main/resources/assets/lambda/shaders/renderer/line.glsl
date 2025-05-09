attributes {
    vec4 pos;
    float alpha;
    vec4 color;
};

uniforms {
    float u_Width;      # fragment
    float u_Dashiness;  # fragment
    float u_DashPeriod; # fragment
};

export {
    vec4 v_Color;     # color
    float v_Alpha;    # alpha
};

#define PI 3.1415926535
#define SMOOTHING 100

void fragment() {
    float smoothing = min(u_Width, SMOOTHING);
    float dist = abs(v_Alpha - 0.5) * 2 * u_Width;
    float alpha = smoothstep(u_Width, u_Width - smoothing, dist);

    color = v_Color * vec4(1.0, 1.0, 1.0, alpha);
}#
