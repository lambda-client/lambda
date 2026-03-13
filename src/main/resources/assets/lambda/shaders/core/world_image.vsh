#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

in vec3 Position;
in vec2 UV0;
in vec4 Color;
in vec3 Anchor;
in vec2 BillboardData;
in vec4 OverlayUV;

out vec2 v_TexCoord;
out vec4 v_Color;
out vec4 v_OverlayUV;

void main() {
    float scale = BillboardData.x;
    float billboardFlag = BillboardData.y;
    
    vec3 anchor = Anchor + ModelOffset;
    vec4 mvPos;
    if (billboardFlag < 0.5) {
        mvPos = ModelViewMat * vec4(anchor, 1.0);
        mvPos.xy += Position.xy * scale;
    } else mvPos = ModelViewMat * vec4(anchor + Position * scale, 1.0);
    
    gl_Position = ProjMat * mvPos;
    
    v_TexCoord = UV0;
    v_Color = Color;
    v_OverlayUV = OverlayUV;
}
