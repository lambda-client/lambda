#version 330 core

uniform sampler2D u_Texture;
uniform vec2 u_Direction;

uniform int u_BlurLevel;

in vec2 v_TexCoord;
out vec4 color;

void main() {
    vec4 col = texture(u_Texture, v_TexCoord);
    int amt = 1;

    for (float i = -u_BlurLevel * 0.5; i < u_BlurLevel * 0.5; ++i) {
        vec2 ofs = i * u_Direction;
        col += texture(u_Texture, v_TexCoord + ofs);
        amt++;
    }

    color = col / amt;
}
