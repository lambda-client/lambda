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

out vec2 v_TexCoord;
out vec4 v_Color;
out float sphericalVertexDistance;
out float cylindricalVertexDistance;
// Pass layer type and SDF style to fragment shader
flat out int v_LayerType;
out vec4 sdfStyleParams;

void main() {
    float scale = BillboardData.x;
    float billboardFlag = BillboardData.y;
    
    vec3 worldPos;
    vec3 anchor = Anchor + ModelOffset;
    
    if (billboardFlag == 0.0) {
        // Billboard mode: compute right/up vectors from ModelViewMat
        vec3 right = vec3(ModelViewMat[0][0], ModelViewMat[1][0], ModelViewMat[2][0]);
        vec3 up = vec3(ModelViewMat[0][1], ModelViewMat[1][1], ModelViewMat[2][1]);
        
        // Apply scale (negative Y to flip for correct text orientation)
        float scaledX = Position.x * scale;
        float scaledY = Position.y * -scale;  // Negate Y to flip
        
        // Compute world position from anchor + billboard offset
        worldPos = anchor + right * scaledX + up * scaledY;
    } else {
        // Fixed rotation mode: position is already transformed, just add anchor
        worldPos = anchor + Position * scale;
    }
    
    vec4 viewPos = ModelViewMat * vec4(worldPos, 1.0);
    gl_Position = ProjMat * viewPos;
    
    // Apply per-layer depth bias to prevent z-fighting between text effect layers
    // Layer type is provided in Position.z: 3 = text, 2 = outline, 1 = glow, 0 = shadow
    // Each layer needs a unique depth offset so they don't fight
    // Order from back to front: shadow < glow < outline < text
    int layerType = int(Position.z + 0.5);
    v_LayerType = layerType;
    float layerOffset;
    if (layerType == 3) {
        layerOffset = 0.0004;  // Text - closest to camera
    } else if (layerType == 2) {
        layerOffset = 0.0003;  // Outline
    } else if (layerType == 1) {
        layerOffset = 0.0002;  // Glow
    } else {
        layerOffset = 0.0001;  // Shadow - furthest back
    }
    
    gl_Position.z -= layerOffset * gl_Position.w;

    v_TexCoord = UV0;
    v_Color = Color;
    sdfStyleParams = SDFStyle;

    sphericalVertexDistance = fog_spherical_distance(viewPos.xyz);
    cylindricalVertexDistance = fog_cylindrical_distance(viewPos.xyz);
}