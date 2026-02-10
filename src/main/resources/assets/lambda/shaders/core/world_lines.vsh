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
out vec3 v_Normal;
out vec2 v_LocalPos;              // Local quad coordinates (world units)
flat out vec3 v_LineCenter;
out float v_LineWidth;          // Interpolated world-unit width
out float v_WorldPixelSize;     // Analytical world units per pixel
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
    
    // Use per-vertex camera direction for better billboarding on long segments
    vec3 toCamera = normalize(cameraPos - thisPoint);
    
    vec3 perpDir = cross(lineDir, toCamera);
    if (length(perpDir) < 0.001) {
        perpDir = cross(lineDir, vec3(0.0, 1.0, 0.0));
        if (length(perpDir) < 0.001) {
            perpDir = cross(lineDir, vec3(1.0, 0.0, 0.0));
        }
    }
    perpDir = normalize(perpDir);
    
    // Calculate view-space depth for exact planar scaling
    vec4 viewPos = ModelViewMat * vec4(thisPoint, 1.0);
    float viewDepth = -viewPos.z;
    
    // Extract tan(fov/2) from projection matrix: ProjMat[1][1] = 1/tan(fov/2)
    float tanHalfFov = 1.0 / ProjMat[1][1];
    
    // Calculate actual line width in world units per vertex
    float actualLineWidth;
    if (LineWidth < 0.0) {
        // Distance-scaled mode: negative value = screen-space fraction
        float screenFraction = -LineWidth;
        
        // At distance d, visible height = 2 * d * tan(fov/2)
        // world width = screenFraction * visible height
        actualLineWidth = screenFraction * 2.0 * viewDepth * tanHalfFov;
    } else {
        actualLineWidth = LineWidth;
    }
    
    // Calculate world-unit size of 1 pixel at this depth for AA padding
    float worldPixelSize = (viewDepth * tanHalfFov * 2.0) / ScreenSize.y;
    
    // Expand for AA (match screen_lines behavior)
    // Ensure we always expand by enough to cover the 2-pixel AA gradient (plus safety margin)
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
    v_LineWidth = actualLineWidth;  // Pass the interpolated world-unit width
    v_WorldPixelSize = worldPixelSize; // Pass analytical pixel size
    v_SegmentLength = segmentLength;
    v_IsStart = isStart ? 1.0 : 0.0;
    v_Dash = Dash;
    
    sphericalVertexDistance = fog_spherical_distance(Position);
    cylindricalVertexDistance = fog_cylindrical_distance(Position);
}
