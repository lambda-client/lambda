#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

// Vertex inputs (POSITION_TEXTURE_COLOR format)
in vec3 Position;
in vec2 UV0;
in vec4 Color;

// Outputs to fragment shader
out vec2 texCoord0;
out vec4 vertexColor;

void main() {
    // Screen-space position - already in screen coordinates
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    
    texCoord0 = UV0;
    vertexColor = Color;
}
