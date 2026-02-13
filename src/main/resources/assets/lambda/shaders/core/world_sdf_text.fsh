#version 330
#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:globals.glsl>

uniform sampler2D Sampler0;

in vec2 v_TexCoord;
in vec4 v_Color;
in float sphericalVertexDistance;
in float cylindricalVertexDistance;
// SDF style params from vertex shader: (outlineWidth, glowRadius, shadowSoftness, threshold)
in vec4 sdfStyleParams;
flat in int v_LayerType;

out vec4 fragColor;

void main() {
    // Extract SDF parameters from vertex attributes
    float OutlineWidth = sdfStyleParams.x;
    float GlowRadius = sdfStyleParams.y;
    float ShadowSoftness = sdfStyleParams.z;
    float SDFThreshold = sdfStyleParams.w;

    // Sample the SDF texture - use ALPHA channel
    vec4 texSample = texture(Sampler0, v_TexCoord);
    float sdfValue = texSample.a;

    // World-space anti-aliasing - use fwidth for consistent sharpness
    float smoothing = fwidth(sdfValue) * 0.5;

    // Layer selection from flat vertex input
    int layerType = v_LayerType;

    float alpha;

    if (layerType == 3) {
        // Main text layer - sharp edge at threshold
        alpha = smoothstep(SDFThreshold - smoothing, SDFThreshold + smoothing, sdfValue);
    } else if (layerType == 2) {
        // Outline layer - uses OutlineWidth
        float outlineEdge = SDFThreshold - OutlineWidth;
        alpha = smoothstep(outlineEdge - smoothing, outlineEdge + smoothing, sdfValue);
        // Mask out the main text area
        float textMask = smoothstep(SDFThreshold - smoothing, SDFThreshold + smoothing, sdfValue);
        alpha = alpha * (1.0 - textMask);
    } else if (layerType == 1) {
        // Glow layer - starts from outline edge (if outline enabled) or text edge
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
    vec4 result = vec4(v_Color.rgb, alpha * v_Color.a);

    // Discard nearly transparent fragments
    if (result.a <= 0.005) discard;

    // Apply color modulator (per-pipeline color)
    result *= ColorModulator;

    // Apply fog
    fragColor = apply_fog(result, sphericalVertexDistance, cylindricalVertexDistance,
                          FogEnvironmentalStart, FogEnvironmentalEnd,
                          FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
}
