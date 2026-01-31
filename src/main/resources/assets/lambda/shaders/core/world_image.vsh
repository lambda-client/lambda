#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

// Vertex inputs - matches WORLD_IMAGE_FORMAT
in vec3 Position;     // Local offset (x, y, z)
in vec2 UV0;          // Main texture UV coordinates
in vec4 Color;        // Tint color
in vec3 Anchor;       // Camera-relative world anchor position
in vec2 BillboardData;// (scale, billboardFlag)
in vec3 OverlayUV;    // (overlayU, overlayV, hasOverlay)

// Outputs to fragment shader
out vec2 v_TexCoord;
out vec4 v_Color;
out vec3 v_OverlayUV;

void main() {
    float scale = BillboardData.x;
    float billboardFlag = BillboardData.y;
    
    // Get rotation matrix for billboarding (camera-facing)
    // In perspective mode, we can extract the right/up vectors from the inverse model-view
    // For billboard, we want quads to always face the camera
    
    vec3 worldPos;
    
    if (billboardFlag < 0.5) {
        // Billboard mode: face the camera
        // Extract camera right and up from inverse model-view matrix
        // For a perspective view, these are the transpose of the first two rows
        vec3 camRight = vec3(ModelViewMat[0][0], ModelViewMat[1][0], ModelViewMat[2][0]);
        vec3 camUp = vec3(ModelViewMat[0][1], ModelViewMat[1][1], ModelViewMat[2][1]);
        
        // Apply local offset (scaled) in camera space, then add anchor
        worldPos = Anchor + (Position.x * scale * camRight) + (Position.y * scale * camUp);
    } else {
        // Fixed rotation mode: use pre-transformed Position
        worldPos = Anchor + Position * scale;
    }
    
    // Transform to clip space
    gl_Position = ProjMat * ModelViewMat * vec4(worldPos, 1.0);
    
    // Pass data to fragment shader
    v_TexCoord = UV0;
    v_Color = Color;
    v_OverlayUV = OverlayUV;
}
