#version 330

#moj_import <minecraft:dynamictransforms.glsl>

uniform sampler2D Sampler0;

in vec2 v_TexCoord;
in vec4 v_Color;

out vec4 fragColor;

void main() {
    float alpha = texture(Sampler0, v_TexCoord).a;
    if (alpha < 0.1) discard;

    vec4 baseColor = v_Color;
    fragColor = vec4(baseColor.rgb, baseColor.a);
}
