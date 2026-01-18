#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:globals.glsl>

// Inputs from vertex shader
in vec4 v_Color;
in vec2 v_ExpandedPos;            // Fragment position (expanded for AA)
flat in vec2 v_LineStart;         // Line start point
flat in vec2 v_LineEnd;           // Line end point
flat in float v_LineWidth;        // Line width
flat in float v_SegmentLength;    // Segment length
flat in vec4 v_Dash;              // Dash params (x=dashLen, y=gapLen, z=offset, w=speed)

out vec4 fragColor;

void main() {
    // ===== CAPSULE SDF =====
    vec2 lineDir = normalize(v_LineEnd - v_LineStart);
    float radius = v_LineWidth / 2.0;
    
    // Project fragment position onto line to find closest point
    vec2 toFragment = v_ExpandedPos - v_LineStart;
    float projLength = dot(toFragment, lineDir);
    
    // Clamp to segment bounds [0, segmentLength] for capsule behavior
    float clampedProj = clamp(projLength, 0.0, v_SegmentLength);
    
    // Closest point on line segment
    vec2 closestPoint = v_LineStart + lineDir * clampedProj;
    
    // 2D distance from fragment to closest point on line
    float dist2D = length(v_ExpandedPos - closestPoint);
    
    // SDF: distance to capsule surface (positive = outside, negative = inside)
    float sdf = dist2D - radius;
    
    // Anti-aliasing using screen-space derivatives (same as world-space)
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
    
    fragColor = color;
}
