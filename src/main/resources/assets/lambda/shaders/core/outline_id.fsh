#version 330

// Simplified ID shader using real UVs and ESP colors
uniform sampler2D Sampler0; // Block Atlas

in vec2 v_TexCoord;
in vec4 v_Color;

out vec4 fragColor;

void main() {
    // Universal alpha testing - now automatic because we bind the correct texture for every draw call
    float alpha = texture(Sampler0, v_TexCoord).a;
    if (alpha < 0.1) {
        discard;
    }
    
    // Output solid ESP color from the vertex
    // Alpha is set to 1.0 to ensure solid silhouette for Sobel edge detection
    fragColor = vec4(v_Color.rgb, 1.0);
}
