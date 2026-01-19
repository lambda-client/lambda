#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>
#moj_import <minecraft:globals.glsl>

// Vertex inputs - matches SCREEN_LINE_FORMAT
in vec3 Position;    // Screen-space position (x, y, 0)
in vec4 Color;
in vec2 Direction;   // Line direction vector to OTHER endpoint (length = segment length)
in float LineWidth;  // Line width in pixels
in vec4 Dash;        // Dash parameters (dashLength, gapLength, offset, animSpeed)

// Outputs to fragment shader
out vec4 v_Color;
out vec2 v_ExpandedPos;           // Expanded screen position
flat out vec2 v_LineStart;        // Line start point
flat out vec2 v_LineEnd;          // Line end point
flat out float v_LineWidth;       // Line width
flat out float v_SegmentLength;   // Segment length
flat out vec4 v_Dash;             // Dash parameters (future: passed from vertex)

void main() {
    // Determine which corner of the quad this vertex is
    int vertexIndex = gl_VertexID % 4;
    bool isStart = (vertexIndex < 2);
    float side = (vertexIndex == 0 || vertexIndex == 3) ? -1.0 : 1.0;
    
    // Calculate segment properties
    float segmentLength = length(Direction);
    vec2 lineDir = Direction / max(segmentLength, 0.001);
    
    // Line center (reconstruct for each vertex consistently)
    vec2 lineCenter = isStart ? (Position.xy + Direction * 0.5) : (Position.xy - Direction * 0.5);
    
    // Reconstruct endpoints from center
    vec2 lineStart = lineCenter - lineDir * (segmentLength * 0.5);
    vec2 lineEnd = lineCenter + lineDir * (segmentLength * 0.5);
    vec2 thisPoint = isStart ? lineStart : lineEnd;
    
    // Perpendicular direction for line thickness
    vec2 perpDir = vec2(-lineDir.y, lineDir.x);
    
    // Expand for AA (capsule shape) - ensure minimum expansion for thin lines
    float halfWidth = LineWidth / 2.0;
    float aaPadding = max(LineWidth * 0.5, 2.0);  // At least 2 pixels for AA gradient
    float halfWidthPadded = halfWidth + aaPadding;
    
    // Expand vertex
    vec2 perpOffset = perpDir * side * halfWidthPadded;
    float longitudinal = isStart ? -1.0 : 1.0;
    vec2 longOffset = lineDir * longitudinal * halfWidthPadded;
    
    vec2 expandedPos = thisPoint + perpOffset + longOffset;
    
    // Transform to clip space
    gl_Position = ProjMat * ModelViewMat * vec4(expandedPos, 0.0, 1.0);
    
    // Pass data to fragment shader
    v_Color = Color;
    v_ExpandedPos = expandedPos;
    v_LineStart = lineStart;
    v_LineEnd = lineEnd;
    v_LineWidth = LineWidth;
    v_SegmentLength = segmentLength;
    v_Dash = Dash;
}
