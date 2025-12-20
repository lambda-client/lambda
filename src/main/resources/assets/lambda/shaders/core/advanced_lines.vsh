#version 330

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:globals.glsl>
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

in vec3 Position;
in vec4 Color;
in vec3 Normal;
in float LineWidth;

out vec4 vertexColor;
noperspective out float v_LineDist;
noperspective out float v_LineWidth;
noperspective out vec2 v_DistPixels;
noperspective out float v_LineLength;
out float sphericalVertexDistance;
out float cylindricalVertexDistance;

const float VIEW_SHRINK = 1.0 - (1.0 / 256.0);

void main() {
    int vertexIndex = gl_VertexID % 4;
    bool isStart = (vertexIndex < 2);

    float actualWidth = max(LineWidth, 0.1);
    float padding = 0.5; // AA padding
    float halfWidthExtended = actualWidth / 2.0 + padding;
    
    // Transform start and end
    vec4 posStart = ProjMat * ModelViewMat * vec4(isStart ? Position : Position - Normal, 1.0);
    vec4 posEnd   = ProjMat * ModelViewMat * vec4(isStart ? Position + Normal : Position, 1.0);

    vec3 ndcStart = posStart.xyz / posStart.w;
    vec3 ndcEnd   = posEnd.xyz / posEnd.w;

    // Screen space coordinates
    vec2 screenStart = (ndcStart.xy * 0.5 + 0.5) * ScreenSize;
    vec2 screenEnd   = (ndcEnd.xy * 0.5 + 0.5) * ScreenSize;
    
    vec2 delta = screenEnd - screenStart;
    float lenPixels = length(delta);
    
    // Stable direction
    vec2 lineDir = (lenPixels > 0.001) ? delta / lenPixels : vec2(1.0, 0.0);
    vec2 lineNormal = vec2(-lineDir.y, lineDir.x);

    // Quad vertex layout
    float side = (vertexIndex == 0 || vertexIndex == 3) ? -1.0 : 1.0;
    float longitudinalSide = isStart ? -1.0 : 1.0;

    // Expansion in pixels: full radius + padding to contain capsule end
    vec2 offsetPixels = lineNormal * side * halfWidthExtended + lineDir * longitudinalSide * halfWidthExtended;
    
    // Current point NDC
    vec3 ndcThis = isStart ? ndcStart : ndcEnd;
    float wThis = isStart ? posStart.w : posEnd.w;

    // Convert pixel offset back to NDC
    vec2 offsetNDC = (offsetPixels / ScreenSize) * 2.0;
    gl_Position = vec4((ndcThis + vec3(offsetNDC, 0.0)) * wThis, wThis);

    vertexColor = Color;
    
    // Pass coordinates for SDF
    v_LineDist = side; 
    v_DistPixels = vec2(isStart ? -halfWidthExtended : lenPixels + halfWidthExtended, side * halfWidthExtended);
    v_LineWidth = actualWidth;
    v_LineLength = lenPixels;
    
    sphericalVertexDistance = fog_spherical_distance(Position);
    cylindricalVertexDistance = fog_cylindrical_distance(Position);
}
