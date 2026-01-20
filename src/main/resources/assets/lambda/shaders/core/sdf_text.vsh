#version 330

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

// Position contains local glyph offset (x, y) with z unused
in vec3 Position;
in vec2 UV0;
in vec4 Color;
// Anchor is the camera-relative world position of the text
in vec3 Anchor;
// BillboardData.x = scale, BillboardData.y = billboardFlag (0 = auto-billboard)
in vec2 BillboardData;
// SDFStyle: vec4(outlineWidth, glowRadius, shadowSoftness, threshold)
in vec4 SDFStyle;

out vec2 texCoord0;
out vec4 vertexColor;
out float sphericalVertexDistance;
out float cylindricalVertexDistance;
// Pass SDF style to fragment shader
out vec4 sdfStyleParams;

void main() {
    float scale = BillboardData.x;
    float billboardFlag = BillboardData.y;
    
    vec3 worldPos;
    
    if (billboardFlag == 0.0) {
        // Billboard mode: compute right/up vectors from ModelViewMat
        // ModelViewMat transforms from world to view space
        // To billboard, we need right and up vectors in world space
        // For a view matrix, the inverse transpose gives us camera orientation
        // The first column of ModelViewMat is the right vector (in view space)
        // The second column is the up vector
        // Since ModelViewMat = View, and we want to face the camera:
        // right = normalize(ModelViewMat[0].xyz)
        // up = normalize(ModelViewMat[1].xyz)
        
        vec3 right = vec3(ModelViewMat[0][0], ModelViewMat[1][0], ModelViewMat[2][0]);
        vec3 up = vec3(ModelViewMat[0][1], ModelViewMat[1][1], ModelViewMat[2][1]);
        
        // Apply scale (negative Y to flip for correct text orientation)
        float scaledX = Position.x * scale;
        float scaledY = Position.y * -scale;  // Negate Y to flip
        
        // Compute world position from anchor + billboard offset
        worldPos = Anchor + right * scaledX + up * scaledY;
    } else {
        // Fixed rotation mode: position is already transformed, just add anchor
        // In this case, Position.xy contains the pre-transformed local offset (scaled)
        // We still need to apply the anchor offset
        worldPos = Anchor + Position * scale;
    }
    
    gl_Position = ProjMat * ModelViewMat * vec4(worldPos, 1.0);

    texCoord0 = UV0;
    vertexColor = Color;
    sdfStyleParams = SDFStyle;

    sphericalVertexDistance = fog_spherical_distance(worldPos);
    cylindricalVertexDistance = fog_cylindrical_distance(worldPos);
}