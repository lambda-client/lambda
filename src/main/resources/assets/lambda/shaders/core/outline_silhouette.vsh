#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

// Vertex inputs - Position + Color
in vec3 Position;    // Camera-relative world position
in vec4 Color;       // RGBA color

// Outputs to fragment shader
out vec4 v_Color;

void main() {
    // Transform to clip space
    // ModelViewMat contains camera rotation
    // TextureMat contains projection  
    gl_Position = TextureMat * ModelViewMat * vec4(Position, 1.0);
    
    // Pass color to fragment shader
    v_Color = Color;
}
