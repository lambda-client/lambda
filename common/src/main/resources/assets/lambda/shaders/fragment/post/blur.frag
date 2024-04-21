#version 330 core

#define pow2(x) (x * x)

uniform sampler2D u_Texture;
uniform vec2 u_Direction;
uniform int u_BlurLevel;

in vec2 v_TexCoord;
out vec4 color;

float gaussian(float x) {
    return exp(-pow2(x) / (2.0 * pow2(u_BlurLevel) / (2.50662 * u_BlurLevel)));
}

void main() {
    vec4 col = texture(u_Texture, v_TexCoord);
    float totalWeight = 1.0;

    for (int i = -u_BlurLevel; i <= u_BlurLevel; ++i) {
        float offset = float(i);
        vec2 texOffset = offset * u_Direction;
        col += texture(u_Texture, v_TexCoord + texOffset) * gaussian(offset);
        totalWeight += gaussian(offset);
    }

    // Normalize the color
    col /= totalWeight;

    // Vertical blur
    totalWeight = 0.0;
    for (int i = -u_BlurLevel; i <= u_BlurLevel; ++i) {
        float offset = float(i);
        vec2 texOffset = offset * u_Direction;
        col += texture(u_Texture, v_TexCoord + texOffset) * gaussian(offset);
        totalWeight += gaussian(offset);
    }

    color = col / totalWeight;
}
