#version 330

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:globals.glsl>
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

// Vertex inputs
in vec3 Position;
in vec4 Color;
in vec3 Normal;      // Direction vector to other endpoint (length = segment length)
in float LineWidth;  // Line width: positive = world units, negative = screen-space fraction
in vec4 Dash;        // Dash parameters

// Outputs to fragment shader
out vec4 v_Color;
out vec3 v_WorldPos;
out vec3 v_ExpandedPos;
flat out vec3 v_Normal;
flat out vec3 v_LineCenter;
flat out float v_LineWidth;          // Always positive (actual width in world units)
flat out float v_SegmentLength;
flat out float v_IsStart;
flat out vec4 v_Dash;
out float sphericalVertexDistance;
out float cylindricalVertexDistance;

void main() {
    int vertexIndex = gl_VertexID % 4;
    bool isStart = (vertexIndex < 2);
    float side = (vertexIndex == 0 || vertexIndex == 3) ? -1.0 : 1.0;
    
    float segmentLength = length(Normal);
    vec3 lineDir = Normal / segmentLength;
    
    vec3 lineCenter = isStart ? (Position + Normal * 0.5) : (Position - Normal * 0.5);
    
    vec3 lineStart = lineCenter - lineDir * (segmentLength * 0.5);
    vec3 lineEnd = lineCenter + lineDir * (segmentLength * 0.5);
    vec3 thisPoint = isStart ? lineStart : lineEnd;
    
    // Extract camera position from ModelViewMat
    mat3 rotationInv = transpose(mat3(ModelViewMat));
    vec3 translation = vec3(ModelViewMat[3]);
    vec3 cameraPos = rotationInv * (-translation);
    
    vec3 toCamera = normalize(cameraPos - lineCenter);
    
    vec3 perpDir = cross(lineDir, toCamera);
    if (length(perpDir) < 0.001) {
        perpDir = cross(lineDir, vec3(0.0, 1.0, 0.0));
        if (length(perpDir) < 0.001) {
            perpDir = cross(lineDir, vec3(1.0, 0.0, 0.0));
        }
    }
    perpDir = normalize(perpDir);
    
    // Calculate actual line width in world units
    float actualLineWidth;
    if (LineWidth < 0.0) {
        // Distance-scaled mode: negative value = screen-space fraction
        // Convert screen fraction to world units at this distance
        float screenFraction = -LineWidth;
        float distToCamera = length(cameraPos - lineCenter);
        
        // Extract tan(fov/2) from projection matrix: ProjMat[1][1] = 1/tan(fov/2)
        float tanHalfFov = 1.0 / ProjMat[1][1];
        
        // At distance d, visible height = 2 * d * tan(fov/2)
        // world width = screenFraction * visible height
        actualLineWidth = screenFraction * 2.0 * distToCamera * tanHalfFov;
    } else {
        actualLineWidth = LineWidth;
    }
    
    // Expand for AA
    float halfWidth = actualLineWidth / 2.0;
    float aaPadding = actualLineWidth * 0.3;
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
    v_LineCenter = lineCenter;
    v_LineWidth = actualLineWidth;  // Pass the computed world-unit width
    v_SegmentLength = segmentLength;
    v_IsStart = isStart ? 1.0 : 0.0;
    v_Dash = Dash;
    
    sphericalVertexDistance = fog_spherical_distance(Position);
    cylindricalVertexDistance = fog_cylindrical_distance(Position);
}
