#version 330 core

layout (location = 0) in vec4 pos;
layout (location = 1) in vec2 uv;
layout (location = 2) in vec4 color; // Does this fuck the padding ?

out vec2 v_TexCoord;
out vec4 v_Color;

uniform sampler2D u_FontTexture;
uniform sampler2D u_EmojiTexture;
uniform float u_SDFMin;
uniform float u_SDFMin;

void main()
{
    v_TexCoord = uv;
    v_Color = color;
}