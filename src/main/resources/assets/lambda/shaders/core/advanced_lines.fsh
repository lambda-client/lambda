#version 330

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>

in vec4 vertexColor;
noperspective in float v_LineDist;
noperspective in float v_LineWidth;
noperspective in vec2 v_DistPixels;
noperspective in float v_LineLength;
in float sphericalVertexDistance;
in float cylindricalVertexDistance;

out vec4 fragColor;

void main() {
    // Closest point on the center line segment [0, L]
    float closestX = clamp(v_DistPixels.x, 0.0, v_LineLength);
    vec2 closestPoint = vec2(closestX, 0.0);
    
    // Pixel distance from the closest point (Round Capsule SDF)
    float dist = length(v_DistPixels - closestPoint);
    
    // SDF value: distance from the capsule edge
    float sdf = dist - (v_LineWidth / 2.0);
    
    // Ultra-sharp edges (AA transition of 0.3 pixels total)
    float alpha;
    if (v_LineWidth >= 1.0) {
        alpha = smoothstep(0.15, -0.15, sdf);
    } else {
        // Super thin lines: reduce opacity instead of shrinking width
        float transverseAlpha = (1.0 - smoothstep(0.0, 1.0, abs(v_DistPixels.y))) * v_LineWidth;
        alpha = transverseAlpha;
    }

    // Aggressive fade for tiny segments far away to prevent blobbing
    // If a segment is less than 0.8px on screen, fade it out to nothing
    float lengthFade = clamp(v_LineLength / 0.8, 0.0, 1.0);
    alpha *= lengthFade * lengthFade; // Quadratic falloff for tiny segments

    if (alpha <= 0.0) {
        discard;
    }

    vec4 color = vertexColor * ColorModulator;
    color.a *= alpha;
    
    fragColor = apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
}
