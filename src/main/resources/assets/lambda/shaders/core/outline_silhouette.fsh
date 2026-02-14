#version 330
#moj_import <minecraft:dynamictransforms.glsl>

in vec4 v_Color;
out vec4 fragColor;

void main() {
    vec4 color = v_Color * ColorModulator;
    fragColor = vec4(color.rgb, 1.0);
}
