#version 330 core

in vec2 v_TexCoord;
in vec4 v_Color;

out vec4 color;

void main()
{
    float a = 1.0 - length(v_TexCoord - 0.5) * 2.0;
    color = v_Color * vec4(1.0, 1.0, 1.0, a);
}