#version 330 core

uniform sampler2D u_Texture;
uniform vec2 u_TexelSize;

in vec2 v_TexCoord;
out vec4 color;

#define SPHREAD 4

void main() {
    float alpha = 0.0;
    float blurWeight = 0.0;

    for (int x = -SPHREAD; x <= SPHREAD; ++x) {
        for (int y = -SPHREAD; y <= SPHREAD; ++y) {
            vec2 offset = vec2(x, y) * u_TexelSize;

            float color = texture(u_Texture, v_TexCoord + offset).r;
            float weight = exp(-color * color);

            alpha += color * weight;
            blurWeight += weight;
        }
    }

    alpha /= blurWeight;
    color = vec4(alpha, 1.0, 1.0, 1.0);
}
