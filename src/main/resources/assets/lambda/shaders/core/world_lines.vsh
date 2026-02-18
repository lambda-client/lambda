#version 330

#moj_import <minecraft:globals.glsl>
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

in vec3 Position;
in vec4 Color;
in vec3 Normal;
in float LineWidth;
in vec4 Dash;

out vec4 v_Color;
out vec2 v_TexCoord;
out vec3 v_WorldPos;
out vec3 v_ExpandedPos;
out vec3 v_Normal;
out vec2 v_LocalPos;
flat out vec3 v_LineCenter;
out float v_LineWidth;
out float v_WorldPixelSize;
flat out float v_SegmentLength;
flat out float v_IsStart;
flat out vec4 v_Dash;

void main() {
    int vertexIndex = gl_VertexID % 4;
    bool isStart = (vertexIndex < 2);
    float side = (vertexIndex == 0 || vertexIndex == 3) ? -1.0 : 1.0;
    
    float segmentLength = length(Normal);
    vec3 lineDir = Normal / segmentLength;
    
    vec3 pos = Position + ModelOffset;
    vec3 lineCenter = isStart ? (pos + Normal * 0.5) : (pos - Normal * 0.5);
    
    vec3 lineStart = lineCenter - lineDir * (segmentLength * 0.5);
    vec3 lineEnd = lineCenter + lineDir * (segmentLength * 0.5);
    vec3 thisPoint = isStart ? lineStart : lineEnd;
    
    mat3 rotationInv = transpose(mat3(ModelViewMat));
    vec3 translation = vec3(ModelViewMat[3]);
    vec3 cameraPos = rotationInv * (-translation);
    
    vec3 toCamera = normalize(cameraPos - thisPoint);
    
    vec3 perpDir = cross(lineDir, toCamera);
    if (length(perpDir) < 0.001) {
        perpDir = cross(lineDir, vec3(0.0, 1.0, 0.0));
        if (length(perpDir) < 0.001) {
            perpDir = cross(lineDir, vec3(1.0, 0.0, 0.0));
        }
    }
    perpDir = normalize(perpDir);
    
    vec4 viewPos = ModelViewMat * vec4(thisPoint, 1.0);
    float viewDepth = -viewPos.z;
    
    float tanHalfFov = 1.0 / ProjMat[1][1];
    
    float actualLineWidth;
    if (LineWidth < 0.0) {
        float screenFraction = -LineWidth;
        actualLineWidth = screenFraction * 2.0 * viewDepth * tanHalfFov;
    } else actualLineWidth = LineWidth;
    
    float worldPixelSize = (viewDepth * tanHalfFov * 2.0) / ScreenSize.y;
    
    float halfWidth = actualLineWidth * 0.5;
    float aaPadding = max(halfWidth, worldPixelSize * 3.0); 
    float halfWidthPadded = halfWidth + aaPadding;
    
    vec3 perpOffset = perpDir * side * halfWidthPadded;
    float longitudinal = isStart ? -1.0 : 1.0;
    vec3 longOffset = lineDir * longitudinal * halfWidthPadded;
    
    vec3 expandedPos = thisPoint + perpOffset + longOffset;
    
    gl_Position = ProjMat * ModelViewMat * vec4(expandedPos, 1.0);
    
    v_Color = Color;
    v_WorldPos = thisPoint;
    v_ExpandedPos = expandedPos;
    v_Normal = Normal;
    v_LocalPos = vec2(side * halfWidthPadded, (isStart ? -halfWidthPadded : segmentLength + halfWidthPadded));
    v_LineCenter = lineCenter;
    v_LineWidth = actualLineWidth;
    v_WorldPixelSize = worldPixelSize;
    v_SegmentLength = segmentLength;
    v_IsStart = isStart ? 1.0 : 0.0;
    v_Dash = Dash;
    v_TexCoord = vec2(0.0);
}
