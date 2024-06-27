#version 330 core

uniform sampler2D u_Texture;

in vec2 v_TexCoord;
out vec4 color;

void main() {
    color = texture(u_Texture, v_TexCoord);
}