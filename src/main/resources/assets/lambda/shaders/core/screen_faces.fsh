#version 330
#moj_import <minecraft:dynamictransforms.glsl>

in vec4 v_Color;
in float v_Layer;

out vec4 fragColor;

void main() {
    float alpha = v_Color.a;
    if (alpha <= 0.001) discard;
    fragColor = v_Color * ColorModulator;
    gl_FragDepth = (1000.0 - v_Layer) / 2000.0;
}
