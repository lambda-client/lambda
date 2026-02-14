#version 330

uniform sampler2D Sampler0;

in vec4 v_Color;
in vec2 v_TexCoord;

out vec4 fragColor;

void main() {
    float alpha = texture(Sampler0, v_TexCoord).a;
    if (alpha < 0.1) discard;

    fragColor = vec4(v_Color.rgb, v_Color.a);
}
