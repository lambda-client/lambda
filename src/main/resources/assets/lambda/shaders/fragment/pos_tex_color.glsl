#version 330 core

in vec2 v_TexCoord;
in vec4 v_Color;

out vec4 color;

uniform sampler2D u_Texture;

void main()
{
    color = texture(u_Texture, v_TexCoord) * v_Color
}