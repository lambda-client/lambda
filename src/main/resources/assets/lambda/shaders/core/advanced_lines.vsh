#version 330

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:globals.glsl>
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

// Vertex inputs
in vec3 Position;
in vec4 Color;
in vec3 Normal;      // Direction vector to other endpoint (length = segment length)
in float LineWidth;  // Line width in WORLD UNITS
in vec4 Dash;        // Dash parameters

// Outputs to fragment shader - ALL are debugging-friendly
out vec4 v_Color;
out vec3 v_WorldPos;                 // Original unexpanded position
out vec3 v_ExpandedPos;              // Expanded world position
flat out vec3 v_Normal;              // Raw Normal input (same for all vertices)
flat out vec3 v_LineCenter;          // Line center (computed consistently for all vertices)
flat out float v_LineWidth;          // Line width
flat out float v_SegmentLength;      // Computed segment length
flat out float v_IsStart;            // 1.0 if start vertex, 0.0 if end
flat out vec4 v_Dash;
out float sphericalVertexDistance;
out float cylindricalVertexDistance;

void main() {
    // Determine which corner of the quad this vertex is
    int vertexIndex = gl_VertexID % 4;
    bool isStart = (vertexIndex < 2);
    float side = (vertexIndex == 0 || vertexIndex == 3) ? -1.0 : 1.0;
    
    // Normal is the same for all vertices - use it directly
    float segmentLength = length(Normal);
    vec3 lineDir = Normal / segmentLength;
    
    // Line center (computed consistently for all vertices)
    vec3 lineCenter = isStart ? (Position + Normal * 0.5) : (Position - Normal * 0.5);
    
    // Reconstruct endpoints from center
    vec3 lineStart = lineCenter - lineDir * (segmentLength * 0.5);
    vec3 lineEnd = lineCenter + lineDir * (segmentLength * 0.5);
    vec3 thisPoint = isStart ? lineStart : lineEnd;
    
    // Billboard direction
    vec3 toCamera = normalize(-lineCenter);
    vec3 perpDir = cross(lineDir, toCamera);
    if (length(perpDir) < 0.001) {
        perpDir = cross(lineDir, vec3(0.0, 1.0, 0.0));
        if (length(perpDir) < 0.001) {
            perpDir = cross(lineDir, vec3(1.0, 0.0, 0.0));
        }
    }
    perpDir = normalize(perpDir);
    
    // Expand for AA
    float halfWidth = LineWidth / 2.0;
    float aaPadding = LineWidth * 0.3;
    float halfWidthPadded = halfWidth + aaPadding;
    
    // Expand vertex
    vec3 perpOffset = perpDir * side * halfWidthPadded;
    float longitudinal = isStart ? -1.0 : 1.0;
    vec3 longOffset = lineDir * longitudinal * halfWidthPadded;
    
    vec3 expandedPos = thisPoint + perpOffset + longOffset;
    
    // Transform to clip space
    gl_Position = ProjMat * ModelViewMat * vec4(expandedPos, 1.0);
    
    // Pass ALL debug data
    v_Color = Color;
    v_WorldPos = thisPoint;          // Position BEFORE expansion
    v_ExpandedPos = expandedPos;     // Position AFTER expansion
    v_Normal = Normal;               // Raw Normal (flat - same for all)
    v_LineCenter = lineCenter;       // Line center (flat - same for all)
    v_LineWidth = LineWidth;
    v_SegmentLength = segmentLength;
    v_IsStart = isStart ? 1.0 : 0.0;
    v_Dash = Dash;
    
    sphericalVertexDistance = fog_spherical_distance(Position);
    cylindricalVertexDistance = fog_cylindrical_distance(Position);
}
