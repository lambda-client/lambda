#version 330 core

uniform sampler2D u_FontTexture;
uniform sampler2D u_EmojiTexture;
uniform float u_SDFMin;
uniform float u_SDFMax;

in vec2 v_TexCoord;
in vec4 v_Color;

out vec4 color;

float sdf(float channel) {
    return 1.0 - smoothstep(u_SDFMin, u_SDFMax, 1.0 - channel);
}

vec4 sdf(vec4 texture) {
    return vec4(
        sdf(texture.r),
        sdf(texture.g),
        sdf(texture.b),
        sdf(texture.a)
    );
}

void main() {
    bool isEmoji = v_TexCoord.x < 0.0;

    if (isEmoji) {
        color = sdf(texture(u_EmojiTexture, -v_TexCoord)) * v_Color;
        return;
    }

    color = vec4(1.0, 1.0, 1.0, sdf(texture(u_FontTexture, v_TexCoord).r)) * v_Color;
}
