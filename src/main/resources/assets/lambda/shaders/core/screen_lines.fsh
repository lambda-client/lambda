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
in float v_Layer;                 // Layer depth for draw order

out vec4 fragColor;

void main() {
    // ===== CAPSULE SDF =====
    vec2 lineDir = normalize(v_LineEnd - v_LineStart);
    vec2 perpDir = vec2(-lineDir.y, lineDir.x);
    
    vec2 toFragment = v_ExpandedPos - v_LineStart;
    float projLength = dot(toFragment, lineDir);
    float perpDist = abs(dot(toFragment, perpDir));
    
    // Calculate stable pixel size from screen-space position derivatives
    // This is more reliable than fwidth(dist2D) which can be unstable at edges
    vec2 dPos_dx = dFdx(v_ExpandedPos);
    vec2 dPos_dy = dFdy(v_ExpandedPos);
    // Average pixel size in screen units
    float pixelSize = (length(dPos_dx) + length(dPos_dy)) * 0.5;
    
    // For end caps, compute actual distance to endpoints
    float dist2D;
    if (projLength < 0.0) {
        dist2D = length(v_ExpandedPos - v_LineStart);
    } else if (projLength > v_SegmentLength) {
        dist2D = length(v_ExpandedPos - v_LineEnd);
    } else {
        dist2D = perpDist;
    }
    
    // Calculate screen line width in pixels
    float screenLineWidth = v_LineWidth / max(pixelSize, 0.0001);
    
    // Minimum 1-pixel rendering width - thinner lines scale alpha instead of getting gaps
    float minWidth = pixelSize;  // 1 pixel
    float effectiveRadius = max(v_LineWidth * 0.5, minWidth * 0.5);
    
    // Alpha scaling: lines < 1px get proportionally reduced opacity
    float alphaScale = min(screenLineWidth, 1.0);
    
    // SDF: distance to capsule surface
    float sdf = dist2D - effectiveRadius;
    
    // AA: 1 pixel transition for crisp edges
    float aaWidth = pixelSize;
    float alpha = 1.0 - smoothstep(-aaWidth, aaWidth, sdf);
    
    // Apply alpha scaling for sub-pixel lines
    alpha *= alphaScale;
    
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
        if (animationSpeed != 0.0) {
            animatedOffset -= GameTime * animationSpeed * 1200.0;
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
    
    // Map layer to depth: higher layer = smaller depth = renders on top (LEQUAL)
    // Layer starts at -800 and increments, so later elements have higher values
    gl_FragDepth = (1000.0 - v_Layer) / 2000.0;
}
