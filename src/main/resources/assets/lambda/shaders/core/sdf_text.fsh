#version 330
#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>

uniform sampler2D Sampler0;

in vec2 texCoord0;
in vec4 vertexColor;
in float sphericalVertexDistance;
in float cylindricalVertexDistance;

out vec4 fragColor;

void main() {
    // Sample the SDF texture - use ALPHA channel
    vec4 texSample = texture(Sampler0, texCoord0);
    float sdfValue = texSample.a;  // SDF in alpha channel

    // IMPORTANT: Adjust smoothing based on distance field range
    // For a typical SDF with 0.5 at the edge:
    float smoothing = fwidth(sdfValue) * 0.5;  // Reduced from 0.7

    int layerType = int(vertexColor.a * 255.0 + 0.5);  // +0.5 for proper rounding

    float alpha;

    if (layerType >= 200) {
        // Main text
        alpha = smoothstep(0.5 - smoothing, 0.5 + smoothing, sdfValue);
    } else if (layerType >= 100) {
        // Outline - use wider threshold
        alpha = smoothstep(0.4 - smoothing, 0.45 + smoothing * 2.0, sdfValue);
    } else if (layerType >= 50) {
        // Glow - softer, wider
        alpha = smoothstep(0.3, 0.45, sdfValue) * 0.6;
    } else {
        // Shadow
        alpha = smoothstep(0.25, 0.4, sdfValue) * 0.5;
    }

    // Apply vertex color and discard
    vec4 result = vec4(vertexColor.rgb, alpha);

    if (result.a <= 0.001) discard;

    result *= ColorModulator;
    fragColor = apply_fog(result, sphericalVertexDistance, cylindricalVertexDistance,
                          FogEnvironmentalStart, FogEnvironmentalEnd,
                          FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
}