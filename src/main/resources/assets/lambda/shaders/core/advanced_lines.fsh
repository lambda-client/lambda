#version 330

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:globals.glsl>
#moj_import <minecraft:dynamictransforms.glsl>

// Inputs from vertex shader
in vec4 v_Color;
in vec3 v_WorldPos;                  // Position before expansion (interpolated along line)
in vec3 v_ExpandedPos;               // Position after expansion (interpolated - fragment position)
flat in vec3 v_Normal;               // Raw Normal input (line direction * length)
flat in vec3 v_LineCenter;           // Line center (same for all vertices)
flat in float v_LineWidth;           // Line width
flat in float v_SegmentLength;       // Segment length
flat in float v_IsStart;             // 1.0 if from start vertex
flat in vec4 v_Dash;                 // x = dashLength, y = gapLength, z = dashOffset, w = animationSpeed
in float sphericalVertexDistance;
in float cylindricalVertexDistance;

out vec4 fragColor;

void main() {
    // Reconstruct line geometry from flat varyings
    vec3 lineDir = normalize(v_Normal);
    float halfLength = v_SegmentLength / 2.0;
    
    // Compute line start and end from center (which IS consistent)
    vec3 lineStart = v_LineCenter - lineDir * halfLength;
    vec3 lineEnd = v_LineCenter + lineDir * halfLength;
    
    float radius = v_LineWidth / 2.0;
    
    // ===== CAPSULE SDF =====
    // Project fragment position onto line to find closest point
    vec3 toFragment = v_ExpandedPos - lineStart;
    float projLength = dot(toFragment, lineDir);
    
    // Clamp to segment bounds [0, segmentLength] for capsule behavior
    float clampedProj = clamp(projLength, 0.0, v_SegmentLength);
    
    // Closest point on line segment
    vec3 closestPoint = lineStart + lineDir * clampedProj;
    
    // 3D distance from fragment to closest point on line
    float dist3D = length(v_ExpandedPos - closestPoint);
    
    // SDF: distance to capsule surface (positive = outside, negative = inside)
    float sdf = dist3D - radius;
    
    // Anti-aliasing using screen-space derivatives
    float aaWidth = fwidth(sdf);
    float alpha = 1.0 - smoothstep(-aaWidth, aaWidth, sdf);
    
    // Skip fragments outside the line
    if (alpha <= 0.0) {
        discard;
    }
    
    // ===== DASH PATTERN =====
    float dashLength = v_Dash.x;
    float gapLength = v_Dash.y;
    float dashOffset = v_Dash.z;
    float animationSpeed = v_Dash.w;
    
    // Only apply dash if dashLength > 0 (0 = solid line)
    if (dashLength > 0.0) {
        float cycleLength = dashLength + gapLength;
        
        // Calculate animated offset
        float animatedOffset = dashOffset;
        if (animationSpeed > 0.0) {
            animatedOffset += GameTime * animationSpeed * 1200.0;
        }
        
        // Use the CLAMPED position along the line for dash calculation
        // This ensures dashes are in world-space units
        float dashPos = clampedProj + animatedOffset * cycleLength;
        float posInCycle = mod(dashPos, cycleLength);
        
        // In gap = discard
        if (posInCycle > dashLength) {
            discard;
        }
    }
    
    // Apply color
    vec4 color = v_Color * ColorModulator;
    color.a *= alpha;
    
    // Apply fog
    fragColor = apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance,
                          FogEnvironmentalStart, FogEnvironmentalEnd,
                          FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
}
