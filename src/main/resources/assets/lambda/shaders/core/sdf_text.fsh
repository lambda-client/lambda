#version 330
#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>

uniform sampler2D Sampler0;

in vec2 texCoord0;
in vec4 vertexColor;
in float sphericalVertexDistance;
in float cylindricalVertexDistance;
// SDF style params from vertex shader: (outlineWidth, glowRadius, shadowSoftness, threshold)
in vec4 sdfStyleParams;

out vec4 fragColor;

void main() {
    // Extract SDF parameters from vertex attributes
    float OutlineWidth = sdfStyleParams.x;
    float GlowRadius = sdfStyleParams.y;
    float ShadowSoftness = sdfStyleParams.z;
    float SDFThreshold = sdfStyleParams.w;

    // Sample the SDF texture - use ALPHA channel
    vec4 texSample = texture(Sampler0, texCoord0);
    float sdfValue = texSample.a;

    // Screen-space anti-aliasing
    float smoothing = fwidth(sdfValue) * 0.5;

    // Decode layer type from vertex alpha
    int layerType = int(vertexColor.a * 255.0 + 0.5);

    float alpha;

    if (layerType >= 200) {
        // Main text layer - sharp edge at threshold
        alpha = smoothstep(SDFThreshold - smoothing, SDFThreshold + smoothing, sdfValue);
    } else if (layerType >= 100) {
        // Outline layer - uses OutlineWidth
        float outlineEdge = SDFThreshold - OutlineWidth;
        alpha = smoothstep(outlineEdge - smoothing, outlineEdge + smoothing, sdfValue);
        // Mask out the main text area
        float textMask = smoothstep(SDFThreshold - smoothing, SDFThreshold + smoothing, sdfValue);
        alpha = alpha * (1.0 - textMask);
    } else if (layerType >= 50) {
        // Glow layer - starts from outline edge (if outline enabled) or text edge
        // Only expand past outline if OutlineWidth is actually set
        float glowEdge = (OutlineWidth > 0.001) ? (SDFThreshold - OutlineWidth) : SDFThreshold;
        float glowStart = glowEdge - GlowRadius;
        float glowEnd = glowEdge;
        alpha = smoothstep(glowStart, glowEnd, sdfValue) * 0.6;
        // Mask out the text and outline area
        float outlineMask = smoothstep(glowEdge - smoothing, glowEdge + smoothing, sdfValue);
        alpha = alpha * (1.0 - outlineMask);
    } else {
        // Shadow layer - uses ShadowSoftness
        float shadowStart = SDFThreshold - ShadowSoftness - 0.15;
        float shadowEnd = SDFThreshold - 0.1;
        alpha = smoothstep(shadowStart, shadowEnd, sdfValue) * 0.5;
    }

    // Apply vertex color (RGB from vertex, alpha computed above)
    vec4 result = vec4(vertexColor.rgb, alpha);

    // Discard nearly transparent fragments
    if (result.a <= 0.001) discard;

    // Apply color modulator and fog
    result *= ColorModulator;
    fragColor = apply_fog(result, sphericalVertexDistance, cylindricalVertexDistance,
                          FogEnvironmentalStart, FogEnvironmentalEnd,
                          FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
}