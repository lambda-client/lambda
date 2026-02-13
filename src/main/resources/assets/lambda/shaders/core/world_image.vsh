#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

// Vertex inputs - matches WORLD_IMAGE_FORMAT
in vec3 Position;     // Local offset (x, y, z)
in vec2 UV0;          // Main texture UV coordinates
in vec4 Color;        // Tint color
in vec3 Anchor;       // Camera-relative world anchor position
in vec2 BillboardData;// (scale, billboardFlag)
in vec4 OverlayUV;    // (overlayU, overlayV, hasOverlay, diffuseAmount)

// Outputs to fragment shader
out vec2 v_TexCoord;
out vec4 v_Color;
out vec4 v_OverlayUV;

void main() {
    float scale = BillboardData.x;
    float billboardFlag = BillboardData.y;
    
    vec3 anchor = Anchor + ModelOffset;
    vec4 mvPos;
    if (billboardFlag < 0.5) {
        // Billboard mode: face the camera perfectly
        // 1. Transform anchor to camera space
        mvPos = ModelViewMat * vec4(anchor, 1.0);
        // 2. Apply local offset in camera-aligned XY plane
        mvPos.xy += Position.xy * scale;
    } else {
        // Fixed rotation mode: everything is pre-calculated/transformed
        mvPos = ModelViewMat * vec4(anchor + Position * scale, 1.0);
    }
    
    gl_Position = ProjMat * mvPos;
    
    v_TexCoord = UV0;
    v_Color = Color;
    v_OverlayUV = OverlayUV;
}
