#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:globals.glsl>

in vec4 v_Color;
in vec2 v_ExpandedPos;
flat in vec2 v_LineStart;
flat in vec2 v_LineEnd;
flat in float v_LineWidth;
flat in float v_SegmentLength;
flat in vec4 v_Dash;
in float v_Layer;

out vec4 fragColor;

void main() {
    vec2 lineDir = normalize(v_LineEnd - v_LineStart);
    vec2 perpDir = vec2(-lineDir.y, lineDir.x);
    
    vec2 toFragment = v_ExpandedPos - v_LineStart;
    float projLength = dot(toFragment, lineDir);
    float perpDist = abs(dot(toFragment, perpDir));
    
    vec2 dPos_dx = dFdx(v_ExpandedPos);
    vec2 dPos_dy = dFdy(v_ExpandedPos);
    float pixelSize = (length(dPos_dx) + length(dPos_dy)) * 0.5;
    
    float dist2D;
    if (projLength < 0.0) {
        dist2D = length(v_ExpandedPos - v_LineStart);
    } else if (projLength > v_SegmentLength) {
        dist2D = length(v_ExpandedPos - v_LineEnd);
    } else {
        dist2D = perpDist;
    }
    
    float screenLineWidth = v_LineWidth / max(pixelSize, 0.0001);
    
    float minWidth = pixelSize;
    float effectiveRadius = max(v_LineWidth * 0.5, minWidth * 0.5);
    
    float alphaScale = min(screenLineWidth, 1.0);
    
    float sdf = dist2D - effectiveRadius;
    
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
        
        float dashAaWidth = fwidth(dashSdf);
        float dashAlpha = 1.0 - smoothstep(-dashAaWidth, dashAaWidth, dashSdf);
        
        if (dashAlpha <= 0.0) {
            discard;
        }
        
        alpha *= dashAlpha;
    }
    
    vec4 color = v_Color * ColorModulator;
    color.a *= alpha;
    
    fragColor = color;
    
    gl_FragDepth = (1000.0 - v_Layer) / 2000.0;
}
