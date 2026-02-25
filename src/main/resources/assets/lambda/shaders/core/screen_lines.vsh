#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>
#moj_import <minecraft:globals.glsl>

in vec3 Position;
in vec4 Color;
in vec2 Direction;
in float LineWidth;
in vec4 Dash;
in float Layer;

out vec4 v_Color;
out vec2 v_ExpandedPos;
flat out vec2 v_LineStart;
flat out vec2 v_LineEnd;
flat out float v_LineWidth;
flat out float v_SegmentLength;
flat out vec4 v_Dash;
out float v_Layer;

void main() {
    int vertexIndex = gl_VertexID % 4;
    bool isStart = (vertexIndex < 2);
    float side = (vertexIndex == 0 || vertexIndex == 3) ? -1.0 : 1.0;
    
    float segmentLength = length(Direction);
    vec2 lineDir = Direction / max(segmentLength, 0.001);
    
    vec2 lineCenter = isStart ? (Position.xy + Direction * 0.5) : (Position.xy - Direction * 0.5);
    
    vec2 lineStart = lineCenter - lineDir * (segmentLength * 0.5);
    vec2 lineEnd = lineCenter + lineDir * (segmentLength * 0.5);
    vec2 thisPoint = isStart ? lineStart : lineEnd;
    
    vec2 perpDir = vec2(-lineDir.y, lineDir.x);
    
    float halfWidth = LineWidth / 2.0;
    float aaPadding = max(LineWidth * 0.5, 2.0);
    float halfWidthPadded = halfWidth + aaPadding;
    
    vec2 perpOffset = perpDir * side * halfWidthPadded;
    float longitudinal = isStart ? -1.0 : 1.0;
    vec2 longOffset = lineDir * longitudinal * halfWidthPadded;
    
    vec2 expandedPos = thisPoint + perpOffset + longOffset;
    
    gl_Position = ProjMat * ModelViewMat * vec4(expandedPos, 0.0, 1.0);
    
    v_Color = Color;
    v_ExpandedPos = expandedPos;
    v_LineStart = lineStart;
    v_LineEnd = lineEnd;
    v_LineWidth = LineWidth;
    v_SegmentLength = segmentLength;
    v_Dash = Dash;
    v_Layer = Layer;
}
