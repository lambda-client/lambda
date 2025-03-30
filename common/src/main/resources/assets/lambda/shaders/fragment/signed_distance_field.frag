#version 330 core

uniform sampler2D u_Texture;
uniform vec2 u_TexelSize;

in vec2 v_TexCoord;
out vec4 color;

#define SPHREAD 4

void main() {
    vec4 colors = vec4(0.0);
    vec4 blurWeight = vec4(0.0);

    for (int x = -SPHREAD; x <= SPHREAD; ++x) {
        for (int y = -SPHREAD; y <= SPHREAD; ++y) {
            vec2 offset = vec2(x, y) * u_TexelSize;

            vec4 color = texture(u_Texture, v_TexCoord + offset);
            vec4 weight = exp(-color * color);

            colors += color * weight;
            blurWeight += weight;
        }
    }

    color = colors / blurWeight;
}
