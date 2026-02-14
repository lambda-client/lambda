#version 330

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:globals.glsl>
#moj_import <minecraft:dynamictransforms.glsl>

in vec4 v_Color;
in vec3 v_WorldPos;
in vec3 v_ExpandedPos;
in vec3 v_Normal;
in vec2 v_LocalPos;
flat in vec3 v_LineCenter;
in float v_LineWidth;
in float v_WorldPixelSize;
flat in float v_SegmentLength;
flat in float v_IsStart;
flat in vec4 v_Dash;
in float sphericalVertexDistance;
in float cylindricalVertexDistance;

out vec4 fragColor;

void main() {
    float projLength = v_LocalPos.y;
    float perpDist = abs(v_LocalPos.x);
    
    float dist3D;
    if (projLength < 0.0) {
        dist3D = length(v_LocalPos);
    } else if (projLength > v_SegmentLength) {
        dist3D = length(v_LocalPos - vec2(0.0, v_SegmentLength));
    } else {
        dist3D = perpDist;
    }
    
    vec2 dL_dx = dFdx(v_LocalPos);
    vec2 dL_dy = dFdy(v_LocalPos);
    
    vec2 localGrad;
    if (projLength < 0.0) {
        localGrad = normalize(v_LocalPos);
    } else if (projLength > v_SegmentLength) {
        localGrad = normalize(v_LocalPos - vec2(0.0, v_SegmentLength));
    } else {
        localGrad = vec2(sign(v_LocalPos.x), 0.0);
    }
    
    float pixelSize = length(localGrad.x * vec2(dL_dx.x, dL_dy.x) + localGrad.y * vec2(dL_dx.y, dL_dy.y));
    
    float perpPixelSize = length(vec2(dL_dx.x, dL_dy.x));
    
    float screenLineWidth = v_LineWidth / max(perpPixelSize, 0.0001);
    
    float minWidth = perpPixelSize;
    float effectiveRadius = max(v_LineWidth * 0.5, minWidth * 0.5);
    
    float alphaScale = min(screenLineWidth, 1.0);
    
    float sdf = dist3D - effectiveRadius;
    
    float aaWidth = pixelSize;
    float alpha = 1.0 - smoothstep(-aaWidth, aaWidth, sdf);
    
    alpha *= alphaScale;
    
    if (alpha < 0.004) {
        discard;
    }
    
    float dashLength = v_Dash.x;
    float gapLength = v_Dash.y;
    float dashOffset = v_Dash.z;
    float animationSpeed = v_Dash.w;
    
    float longPixelSize = length(vec2(dL_dx.y, dL_dy.y));
    
    if (dashLength > 0.0) {
        float cycleLength = dashLength + gapLength;
        
        float animatedOffset = dashOffset;
        if (animationSpeed != 0.0) {
            animatedOffset -= GameTime * animationSpeed * 1200.0;
        }
        
        float dashPos = projLength + animatedOffset * cycleLength;
        float posInCycle = mod(dashPos, cycleLength);
        
        float dashSdf;
        if (posInCycle > dashLength) {
            float distToGapEnd = cycleLength - posInCycle;
            dashSdf = min(posInCycle - dashLength, distToGapEnd);
        } else {
            float distToDashEnd = dashLength - posInCycle;
            float distFromDashStart = posInCycle;
            dashSdf = -min(distToDashEnd, distFromDashStart);
        }
        
        float dashAlpha = 1.0 - smoothstep(-longPixelSize, longPixelSize, dashSdf);
        
        if (dashAlpha <= 0.0) {
            discard;
        }
        
        alpha *= dashAlpha;
    }
    
    vec4 color = v_Color * ColorModulator;
    color.a *= alpha;
    
    fragColor = apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance,
                          FogEnvironmentalStart, FogEnvironmentalEnd,
                          FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
}
