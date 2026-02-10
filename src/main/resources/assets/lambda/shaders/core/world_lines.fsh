#version 330

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:globals.glsl>
#moj_import <minecraft:dynamictransforms.glsl>

// Inputs from vertex shader
in vec4 v_Color;
in vec3 v_WorldPos;                  // Position before expansion (interpolated along line)
in vec3 v_ExpandedPos;               // Position after expansion (interpolated - fragment position)
in vec3 v_Normal;                    // Line direction * length
in vec2 v_LocalPos;                  // Local quad coordinates (world units)
flat in vec3 v_LineCenter;           // Line center (same for all vertices)
in float v_LineWidth;                // Line width (interpolated)
in float v_WorldPixelSize;           // Analytical world units per pixel
flat in float v_SegmentLength;       // Segment length
flat in float v_IsStart;             // 1.0 if from start vertex
flat in vec4 v_Dash;                 // x = dashLength, y = gapLength, z = dashOffset, w = animationSpeed
in float sphericalVertexDistance;
in float cylindricalVertexDistance;

out vec4 fragColor;

void main() {
    // ===== CAPSULE SDF (Local Coordinates) =====
    float projLength = v_LocalPos.y;
    float perpDist = abs(v_LocalPos.x);
    
    // For end caps, compute actual distance to local endpoints (origin and 0,v_SegmentLength)
    float dist3D;
    if (projLength < 0.0) {
        dist3D = length(v_LocalPos);
    } else if (projLength > v_SegmentLength) {
        dist3D = length(v_LocalPos - vec2(0.0, v_SegmentLength));
    } else {
        dist3D = perpDist;
    }
    
    // === DIRECTIONAL ANTI-ALIASING ===
    // We need to know how many world units are in one screen pixel in the direction
    // of the edge we are currently rendering. Isotropic averaging (fwidth) causes
    // blurriness when looking down a line because it includes the massive longitudinal 
    // recession at a distance.
    
    // 1. Derivatives of local coordinates (the Jacobian)
    vec2 dL_dx = dFdx(v_LocalPos);
    vec2 dL_dy = dFdy(v_LocalPos);
    
    // 2. Local gradient direction of the capsule SDF
    vec2 localGrad;
    if (projLength < 0.0) {
        localGrad = normalize(v_LocalPos);
    } else if (projLength > v_SegmentLength) {
        localGrad = normalize(v_LocalPos - vec2(0.0, v_SegmentLength));
    } else {
        localGrad = vec2(sign(v_LocalPos.x), 0.0);
    }
    
    // 3. Project derivatives onto the gradient to find pixel size in that direction
    // This gives the exact world-units-per-pixel facing the edge
    float pixelSize = length(localGrad.x * vec2(dL_dx.x, dL_dy.x) + localGrad.y * vec2(dL_dx.y, dL_dy.y));
    
    // For general thickness/scaling, we use the stable perpendicular pixel size
    float perpPixelSize = length(vec2(dL_dx.x, dL_dy.x));
    
    // Calculate screen line width in pixels (using perpendicular scale)
    float screenLineWidth = v_LineWidth / max(perpPixelSize, 0.0001);
    
    // Minimum 1-pixel rendering width (exactly matching screen_lines)
    float minWidth = perpPixelSize;  // 1 pixel in world units
    float effectiveRadius = max(v_LineWidth * 0.5, minWidth * 0.5);
    
    // Alpha scaling: lines < 1px get proportionally reduced opacity
    float alphaScale = min(screenLineWidth, 1.0);
    
    // SDF: distance to capsule surface (using expanded radius for sub-pixel lines)
    float sdf = dist3D - effectiveRadius;
    
    // AA: 2 pixel transition (exactly matching screen lines)
    float aaWidth = pixelSize;
    float alpha = 1.0 - smoothstep(-aaWidth, aaWidth, sdf);
    
    // Apply alpha scaling for sub-pixel lines
    alpha *= alphaScale;
    
    if (alpha < 0.004) {
        discard;
    }
    
    // === DASH PATTERN ===
    float dashLength = v_Dash.x;
    float gapLength = v_Dash.y;
    float dashOffset = v_Dash.z;
    float animationSpeed = v_Dash.w;
    
    // For dash edges, we use the longitudinal pixel size
    float longPixelSize = length(vec2(dL_dx.y, dL_dy.y));
    
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
        
        // Apply anti-aliasing at dash edges (directional for stability)
        float dashAlpha = 1.0 - smoothstep(-longPixelSize, longPixelSize, dashSdf);
        
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
