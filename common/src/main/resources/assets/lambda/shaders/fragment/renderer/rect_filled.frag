#version 330 core

uniform float u_Time;
uniform vec4 u_Color1;
uniform vec4 u_Color2;
uniform vec2 u_Size;

in vec2 v_Position;
in vec2 v_TexCoord;
in vec4 v_Color;
in vec2 v_Size;
in vec2 v_RoundRadiusL;
in vec2 v_RoundRadiusR;
in float v_Shade;

out vec4 color;

#define SMOOTHING 0.25
#define NOISE_GRANULARITY 0.005

vec4 noise() {
    // https://shader-tutorial.dev/advanced/color-banding-dithering/
    float random = fract(sin(dot(v_TexCoord, vec2(12.9898, 78.233))) * 43758.5453);
    float ofs = mix(-NOISE_GRANULARITY, NOISE_GRANULARITY, random);
    return vec4(ofs, ofs, ofs, 0.0);
}

vec4 shade() {
    if (v_Shade != 1.0) return v_Color;

    vec2 pos = v_Position * u_Size;
    float p = sin(pos.x - pos.y - u_Time) * 0.5 + 0.5;

    return mix(u_Color1, u_Color2, p) * v_Color;
}

float getRoundRadius() {
    bool xcmp = v_TexCoord.x > 0.5;
    bool ycmp = v_TexCoord.y > 0.5;

    float r = 0.0;

    if (xcmp) {
        if (ycmp) {
            // Right bottom
            r = v_RoundRadiusR.y;
        } else {
            // Right top
            r = v_RoundRadiusR.x;
        }
    } else {
        if (ycmp) {
            // Left bottom
            r = v_RoundRadiusL.y;
        } else {
            // Left top
            r = v_RoundRadiusL.x;
        }
    }

    return r;
}

vec4 round() {
    vec2 halfSize = v_Size * 0.5;

    float radius = max(getRoundRadius(), SMOOTHING);

    vec2 smoothVec = vec2(SMOOTHING);
    vec2 coord = mix(-smoothVec, v_Size + smoothVec, v_TexCoord);

    vec2 center = halfSize - coord;
    float distance = length(max(abs(center) - halfSize + radius, 0.0)) - radius;

    float alpha = 1.0 - smoothstep(-SMOOTHING, SMOOTHING, distance);
    return vec4(1.0, 1.0, 1.0, clamp(alpha, 0.0, 1.0));
}

void main() {
    color = shade() * round() + noise();
}