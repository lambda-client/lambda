#version 330 core

uniform sampler2D u_FontTexture;
uniform sampler2D u_EmojiTexture;

in vec2 v_TexCoord;
in vec2 v_Scissor1;
in vec2 v_Scissor2;

in vec4 v_Color;

out vec4 color;

bool scissorFailed(vec2 coord) {
    return coord.x < v_Scissor1.x || coord.x > v_Scissor2.x || coord.y < v_Scissor1.y || coord.y > v_Scissor2.y;
}

void main() {
    vec2 coord = v_TexCoord.x > 0.0 ? v_TexCoord : -v_TexCoord;
    if (scissorFailed(coord)) discard;

    color = texture(v_TexCoord.x > 0.0 ? u_FontTexture : u_EmojiTexture, coord) * v_Color;
}
