#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

in vec3 Position;
in vec2 UV0;
in vec4 Color;
in vec3 Anchor;
in vec2 BillboardData;
in vec4 SDFStyle;

out vec2 v_TexCoord;
out vec4 v_Color;
flat out int v_LayerType;
out vec4 sdfStyleParams;

void main() {
    float scale = BillboardData.x;
    float billboardFlag = BillboardData.y;
    
    vec3 worldPos;
    vec3 anchor = Anchor + ModelOffset;
    
    if (billboardFlag == 0.0) {
        vec3 right = vec3(ModelViewMat[0][0], ModelViewMat[1][0], ModelViewMat[2][0]);
        vec3 up = vec3(ModelViewMat[0][1], ModelViewMat[1][1], ModelViewMat[2][1]);
        
        float scaledX = Position.x * scale;
        float scaledY = Position.y * -scale;
        
        worldPos = anchor + right * scaledX + up * scaledY;
    } else worldPos = anchor + Position * scale;
    
    vec4 viewPos = ModelViewMat * vec4(worldPos, 1.0);
    gl_Position = ProjMat * viewPos;
    
    int layerType = int(Position.z + 0.5);
    v_LayerType = layerType;
    float layerOffset;
    if (layerType == 3) layerOffset = 0.0004;
    else if (layerType == 2) layerOffset = 0.0003;
    else if (layerType == 1) layerOffset = 0.0002;
    else layerOffset = 0.0001;
    
    gl_Position.z -= layerOffset * gl_Position.w;

    v_TexCoord = UV0;
    v_Color = Color;
    sdfStyleParams = SDFStyle;
}