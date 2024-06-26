#version 330 core

uniform sampler2D u_FontTexture;
uniform sampler2D u_EmojiTexture;

in vec2 v_TexCoord;
in vec4 v_Color;

out vec4 color;

void main() {
    vec4 tex;

    if (v_TexCoord.x > 0.0) {
        tex = texture(u_FontTexture, v_TexCoord);
    } else {
        tex = texture(u_EmojiTexture, -v_TexCoord);
    }

    color = tex * v_Color;
}
