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
    
    // Perpendicular distance - stable for AA calculation along the line body
    vec3 perpVec = toFragment - lineDir * projLength;
    float perpDist = length(perpVec);
    
    // Clamp to segment bounds [0, segmentLength] for capsule behavior
    float clampedProj = clamp(projLength, 0.0, v_SegmentLength);
    
    // For end caps, we need the actual distance to the endpoint
    float dist3D;
    if (projLength < 0.0) {
        // Before start - distance to start point
        dist3D = length(v_ExpandedPos - lineStart);
    } else if (projLength > v_SegmentLength) {
        // After end - distance to end point
        dist3D = length(v_ExpandedPos - lineEnd);
    } else {
        // Along the line - use perpendicular distance
        dist3D = perpDist;
    }
    
    // SDF: distance to capsule surface (positive = outside, negative = inside)
    float sdf = dist3D - radius;
    
    // Use fwidth(sdf) for AA - this measures how fast the SDF changes per pixel,
    // which is stable regardless of viewing angle. When looking down the line,
    // the SDF change per pixel remains consistent because we care about the
    // perpendicular distance to the capsule surface, not world-space position.
    float sdfGrad = fwidth(sdf);
    
    // Calculate screen-space line width in pixels (diameter)
    float screenLineWidth = (radius * 2.0) / max(sdfGrad, 0.0001);
    
    // For sub-pixel lines: fade alpha based on line width
    // This allows lines to naturally disappear at distance
    float coverageFactor = clamp(screenLineWidth, 0.0, 1.0);
    
    // Adaptive AA using consistent sdfGrad units:
    // - Thick lines (>4px): crisp edges with 0.5px AA on each side
    // - Thin lines (<2px): soft edges with 1.5px AA on each side
    // All in screen-space for consistency
    float thinness = clamp(1.0 - (screenLineWidth - 2.0) / 2.0, 0.0, 1.0);
    float aaWidth = mix(sdfGrad * 0.5, sdfGrad * 1.5, thinness);
    
    float alpha = 1.0 - smoothstep(-aaWidth, aaWidth, sdf);
    
    // Apply coverage factor to fade sub-pixel lines
    alpha *= coverageFactor;
    
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
