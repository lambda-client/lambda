#version 330

#moj_import <minecraft:dynamictransforms.glsl>

// Inputs from vertex shader
in vec4 v_Color;

out vec4 fragColor;

void main() {
    // Apply color modulator for tinting
    vec4 color = v_Color * ColorModulator;
    
    // Output solid silhouette color
    // Alpha = 1.0 for edge detection
    fragColor = vec4(color.rgb, 1.0);
}
