#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

// Vertex inputs (SCREEN_TEXT_SDF_FORMAT)
in vec3 Position;
in vec2 UV0;
in vec4 Color;
// SDFStyle: vec4(outlineWidth, glowRadius, shadowSoftness, threshold)
in vec4 SDFStyle;
in float Layer;      // Layer depth for draw order

// Outputs to fragment shader
out vec2 texCoord0;
out vec4 vertexColor;
out vec4 sdfStyleParams;
out float v_Layer;   // Layer depth for draw order

void main() {
    // Screen-space position - already in screen coordinates
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    
    texCoord0 = UV0;
    vertexColor = Color;
    sdfStyleParams = SDFStyle;
    v_Layer = Layer;
}
