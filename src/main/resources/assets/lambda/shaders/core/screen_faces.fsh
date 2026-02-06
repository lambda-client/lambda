#version 330

#moj_import <minecraft:dynamictransforms.glsl>

// Inputs from vertex shader
in vec4 v_Color;
in float v_Layer;

out vec4 fragColor;

void main() {
    // Apply color modulator
    vec4 color = v_Color * ColorModulator;
    
    // Discard nearly transparent fragments
    if (color.a < 0.004) {
        discard;
    }
    
    fragColor = color;
    
    // Map layer to depth: higher layer = smaller depth = renders on top (LEQUAL)
    gl_FragDepth = (1000.0 - v_Layer) / 2000.0;
}
