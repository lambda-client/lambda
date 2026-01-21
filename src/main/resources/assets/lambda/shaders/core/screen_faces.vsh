#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

// Vertex inputs - matches SCREEN_FACE_FORMAT
in vec3 Position;    // Screen-space position (x, y, 0)
in vec4 Color;
in float Layer;      // Layer depth for draw order

// Outputs to fragment shader
out vec4 v_Color;
out float v_Layer;

void main() {
    // Transform to clip space
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    
    // Pass data to fragment shader
    v_Color = Color;
    v_Layer = Layer;
}
