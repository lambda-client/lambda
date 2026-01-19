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
    vec2 perpDir = vec2(-lineDir.y, lineDir.x);  // Perpendicular to line
    
    // Project fragment position onto line to find closest point
    vec2 toFragment = v_ExpandedPos - v_LineStart;
    float projLength = dot(toFragment, lineDir);
    
    // Perpendicular distance (signed) - this is stable for AA calculation
    float perpDist = abs(dot(toFragment, perpDir));
    
    // Clamp to segment bounds [0, segmentLength] for capsule behavior
    float clampedProj = clamp(projLength, 0.0, v_SegmentLength);
    
    // For end caps, we need the actual distance to the endpoint
    float dist2D;
    if (projLength < 0.0) {
        // Before start - distance to start point
        dist2D = length(v_ExpandedPos - v_LineStart);
    } else if (projLength > v_SegmentLength) {
        // After end - distance to end point
        dist2D = length(v_ExpandedPos - v_LineEnd);
    } else {
        // Along the line - use perpendicular distance
        dist2D = perpDist;
    }
    
    // Calculate AA width from screen-space derivatives of fragment position
    // This is always stable regardless of line orientation
    float aaWidth = length(vec2(fwidth(v_ExpandedPos.x), fwidth(v_ExpandedPos.y)));
    
    // Use requested line width - no minimum enforcement for thin lines
    float radius = v_LineWidth * 0.5;
    
    // SDF: distance to capsule surface (positive = outside, negative = inside)
    float sdf = dist2D - radius;
    
    // Adaptive AA: thin lines get softer edges, thick lines get crisp edges
    // Below 2px width, scale up AA for smooth thin lines; above 2px, use tight 0.5px AA
    float thinness = clamp(1.0 - v_LineWidth / (2.0 * aaWidth), 0.0, 1.0);
    float adaptiveAA = mix(aaWidth * 0.5, aaWidth * 1.5, thinness);
    
    float alpha = 1.0 - smoothstep(-adaptiveAA, adaptiveAA, sdf);
    
    // Skip fragments outside the line
    if (alpha < 0.004) {
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
        
        // Use UNCLAMPED projLength so dashes continue through endcaps
        float dashPos = projLength + animatedOffset * cycleLength;
        float posInCycle = mod(dashPos, cycleLength);
        
        // SDF for dash edges with anti-aliasing
        float dashSdf;
        if (posInCycle > dashLength) {
            // In gap region - positive SDF
            float distToGapEnd = cycleLength - posInCycle;
            dashSdf = min(posInCycle - dashLength, distToGapEnd);
        } else {
            // In dash region - negative SDF (distance to nearest gap)
            float distToDashEnd = dashLength - posInCycle;
            float distFromDashStart = posInCycle;
            dashSdf = -min(distToDashEnd, distFromDashStart);
        }
        
        // Apply anti-aliasing at dash edges (use fwidth of SDF for consistent AA with capsule)
        float dashAaWidth = fwidth(dashSdf);
        float dashAlpha = 1.0 - smoothstep(-dashAaWidth, dashAaWidth, dashSdf);
        
        if (dashAlpha <= 0.0) {
            discard;
        }
        
        alpha *= dashAlpha;
    }
    
    // Apply color
    vec4 color = v_Color * ColorModulator;
    color.a *= alpha;
    
    fragColor = color;
}
