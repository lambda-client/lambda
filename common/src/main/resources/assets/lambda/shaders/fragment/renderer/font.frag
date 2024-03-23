#version 330 core

uniform sampler2D u_Texture;

in vec2 v_TexCoord;
in vec4 v_Color;

out vec4 color;

void main() {
    float alpha = texture(u_Texture, v_TexCoord).a;
    color = vec4(v_Color.rgb, v_Color.a * alpha);
}