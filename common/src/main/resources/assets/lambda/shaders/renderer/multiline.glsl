attributes {
    vec4 pos;
    float distance;
    float width;
    float alpha;
    vec4 color;
};

uniforms {
    float u_Length;     # fragment
    float u_Dashiness;  # fragment
    float u_DashPeriod; # fragment
};

export {
    vec4 v_Color;     # color
    float v_Distance; # distance
    float v_Width;    # width
    float v_Alpha;    # alpha
};

#define PI 3.1415926535
#define SMOOTHING 100

void fragment() {
    float smoothing = min(v_Width, SMOOTHING);
    float dist = abs(v_Alpha - 0.5) * 2 * v_Width;
    float alpha = smoothstep(v_Width, v_Width - smoothing, dist);

    if (u_Length > 0.0) {
        float startFade = smoothstep(0.0, smoothing * 0.5, v_Distance);
        float endFade = smoothstep(u_Length, u_Length - smoothing * 0.5, v_Distance);
        alpha *= min(startFade, endFade);
    }

    if (u_Dashiness < 0.98) {
        float dashSmoothing = smoothing / u_DashPeriod;
        float dashPos = fract(v_Distance / u_DashPeriod);
        float dashed = smoothstep(0.0, dashSmoothing, dashPos) * (1.0 - smoothstep(u_Dashiness, u_Dashiness + dashSmoothing, dashPos));
        alpha *= mix(dashed, 1.0, max(min((u_Dashiness - 0.9) * 10.0, 1.0), 0.0));
    }

    color = v_Color * vec4(1.0, 1.0, 1.0, alpha);
}#
