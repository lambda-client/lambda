#version 330

// Full-screen quad vertex shader for post-processing

// Vertex inputs
in vec3 Position;    // Quad vertex position (-1 to 1 normalized clip space)
in vec2 UV0;         // Texture coordinates (0 to 1)

// Outputs to fragment shader
out vec2 v_TexCoord;

void main() {
    // Position already in clip space for fullscreen quad
    gl_Position = vec4(Position.xy, 0.0, 1.0);
    
    // Pass UV to fragment shader
    v_TexCoord = UV0;
}
