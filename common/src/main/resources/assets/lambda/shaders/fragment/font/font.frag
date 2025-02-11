#version 330 core

uniform sampler2D u_FontTexture;
uniform sampler2D u_EmojiTexture;
uniform float u_SDFMin;
uniform float u_SDFMax;

in vec2 v_TexCoord;
in vec4 v_Color;

out vec4 color;

void main() {
    vec2 coord = v_TexCoord;

    bool isEmoji = coord.x < 0.0;
    if (isEmoji) coord = -v_TexCoord;

    if (isEmoji) {
        color = texture(u_EmojiTexture, coord) * v_Color;
        return;
    }

    float sdf = texture(u_FontTexture, coord).r;
    float alpha = 1.0 - smoothstep(u_SDFMin, u_SDFMax, 1.0 - sdf);

    color = vec4(1, 1, 1, alpha) * v_Color;
}
