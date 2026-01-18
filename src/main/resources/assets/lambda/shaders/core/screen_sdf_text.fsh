#version 330

#moj_import <minecraft:dynamictransforms.glsl>

uniform sampler2D Sampler0;

// SDF effect parameters - matches world-space sdf_text
layout(std140) uniform SDFParams {
    float SDFThreshold;      // Main text edge threshold (default 0.5)
    float OutlineWidth;      // Outline width in SDF units (0 = no outline)
    float GlowRadius;        // Glow radius in SDF units (0 = no glow)
    float ShadowSoftness;    // Shadow softness (0 = no shadow)
};

// Inputs from vertex shader
in vec2 texCoord0;
in vec4 vertexColor;

out vec4 fragColor;

void main() {
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
        // Glow layer - starts from text edge and extends outward
        float glowStart = SDFThreshold - GlowRadius;
        float glowEnd = SDFThreshold;
        alpha = smoothstep(glowStart, glowEnd, sdfValue) * 0.6;
        // Mask out the main text area
        float textMask = smoothstep(SDFThreshold - smoothing, SDFThreshold + smoothing, sdfValue);
        alpha = alpha * (1.0 - textMask);
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

    // Apply color modulator (no fog for screen-space)
    fragColor = result * ColorModulator;
}
