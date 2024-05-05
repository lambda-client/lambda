#version 330 core

#define pow2(x) (x * x)

uniform sampler2D u_Texture;
uniform vec2 u_Direction;
uniform int u_BlurLevel;
uniform float u_Alpha;

in vec2 v_TexCoord;
out vec4 color;

float gaussian(float x) {
    return exp(-pow2(x) / (2.0 * pow2(u_BlurLevel) / (2.50662 * u_BlurLevel)));
}

void main() {
    float totalWeight = 1.0;
    color = texture(u_Texture, v_TexCoord);

    for (int i = -u_BlurLevel; i <= u_BlurLevel; ++i) {
        float offset = float(i);
        float amount = gaussian(offset);
        vec2 texOffset = offset * u_Direction;

        color += texture(u_Texture, v_TexCoord + texOffset) * amount;
        totalWeight += amount;
    }

    color /= totalWeight;
    color *= vec4(1.0, 1.0, 1.0, u_Alpha);
}
