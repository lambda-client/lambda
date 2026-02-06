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
    
    // Compute line start and end from center
    vec3 lineStart = v_LineCenter - lineDir * halfLength;
    vec3 lineEnd = v_LineCenter + lineDir * halfLength;
    
    // ===== CAPSULE SDF =====
    vec3 toFragment = v_ExpandedPos - lineStart;
    float projLength = dot(toFragment, lineDir);
    
    // Perpendicular distance
    vec3 perpVec = toFragment - lineDir * projLength;
    float perpDist = length(perpVec);
    
    // Calculate stable pixel size from screen-space position derivatives
    // This is more reliable than fwidth(sdf) which can be unstable at edges
    vec3 dPos_dx = dFdx(v_ExpandedPos);
    vec3 dPos_dy = dFdy(v_ExpandedPos);
    float pixelSize = (length(dPos_dx) + length(dPos_dy)) * 0.5;
    
    // For end caps, compute actual distance to endpoints
    float dist3D;
    if (projLength < 0.0) {
        dist3D = length(v_ExpandedPos - lineStart);
    } else if (projLength > v_SegmentLength) {
        dist3D = length(v_ExpandedPos - lineEnd);
    } else {
        dist3D = perpDist;
    }
    
    // Calculate screen line width in pixels
    float screenLineWidth = v_LineWidth / max(pixelSize, 0.0001);
    
    // Minimum 1-pixel rendering width - thinner lines scale alpha instead of getting gaps
    float minWidth = pixelSize;  // 1 pixel
    float effectiveRadius = max(v_LineWidth * 0.5, minWidth * 0.5);
    
    // Alpha scaling: lines < 1px get proportionally reduced opacity
    float alphaScale = min(screenLineWidth, 1.0);
    
    // SDF: distance to capsule surface
    float sdf = dist3D - effectiveRadius;
    
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
    
    // Apply fog
    fragColor = apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance,
                          FogEnvironmentalStart, FogEnvironmentalEnd,
                          FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
}
