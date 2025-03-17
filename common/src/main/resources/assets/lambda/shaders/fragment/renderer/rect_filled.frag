#version 330 core

uniform vec2 u_Size;
uniform float u_RoundLeftTop;
uniform float u_RoundLeftBottom;
uniform float u_RoundRightBottom;
uniform float u_RoundRightTop;

uniform float u_Shade;
uniform float u_ShadeTime;
uniform vec4 u_ShadeColor1;
uniform vec4 u_ShadeColor2;
uniform vec2 u_ShadeSize;

in vec2 v_Position;
in vec2 v_TexCoord;
in vec4 v_Color;

out vec4 color;

#define SMOOTHING 0.2
#define NOISE_GRANULARITY 0.004

vec4 noise() {
    // https://shader-tutorial.dev/advanced/color-banding-dithering/
    float random = fract(sin(dot(v_TexCoord, vec2(12.9898, 78.233))) * 43758.5453);
    float ofs = mix(-NOISE_GRANULARITY, NOISE_GRANULARITY, random);
    return vec4(ofs, ofs, ofs, 0.0);
}

vec4 shade() {
    if (u_Shade != 1.0) return v_Color;

    vec2 pos = v_Position * u_ShadeSize;
    float p = sin(pos.x - pos.y - u_ShadeTime) * 0.5 + 0.5;

    return mix(u_ShadeColor1, u_ShadeColor2, p) * v_Color;
}

float getRoundRadius() {
    // ToDo: use step
    bool xcmp = v_TexCoord.x > 0.5;
    bool ycmp = v_TexCoord.y > 0.5;

    float r = 0.0;

    if (xcmp) {
        if (ycmp) {
            r = u_RoundRightBottom;
        } else {
            r = u_RoundRightTop;
        }
    } else {
        if (ycmp) {
            r = u_RoundLeftBottom;
        } else {
            r = u_RoundLeftTop;
        }
    }

    return r;
}

vec4 round() {
    vec2 halfSize = u_Size * 0.5;

    float radius = max(getRoundRadius(), SMOOTHING);

    vec2 smoothVec = vec2(SMOOTHING);
    vec2 coord = mix(-smoothVec, u_Size + smoothVec, v_TexCoord);

    vec2 center = halfSize - coord;
    float distance = length(max(abs(center) - halfSize + radius, 0.0)) - radius;

    float alpha = 1.0 - smoothstep(-SMOOTHING, SMOOTHING, distance);
    return vec4(1.0, 1.0, 1.0, clamp(alpha, 0.0, 1.0));
}

void main() {
    color = shade() * round() + noise();
}