#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

// Vertex inputs - matches SCREEN_IMAGE_FORMAT
in vec3 Position;    // Screen-space position (x, y, 0)
in vec2 UV0;         // Main texture UV coordinates
in vec4 Color;       // Tint color
in vec3 OverlayUV;   // vec3(overlayU, overlayV, hasOverlay)
in float Layer;      // Layer depth for draw order

// Outputs to fragment shader
out vec2 v_TexCoord;
out vec4 v_Color;
out vec3 v_OverlayUV;
out float v_Layer;

void main() {
    // Transform to clip space
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    
    // Pass data to fragment shader
    v_TexCoord = UV0;
    v_Color = Color;
    v_OverlayUV = OverlayUV;
    v_Layer = Layer;
}
