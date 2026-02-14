#version 330
#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:globals.glsl>

uniform sampler2D Sampler0;

in vec2 v_TexCoord;
in vec4 v_Color;
in float sphericalVertexDistance;
in float cylindricalVertexDistance;
in vec4 sdfStyleParams;
flat in int v_LayerType;

out vec4 fragColor;

void main() {
    float OutlineWidth = sdfStyleParams.x;
    float GlowRadius = sdfStyleParams.y;
    float ShadowSoftness = sdfStyleParams.z;
    float SDFThreshold = sdfStyleParams.w;

    vec4 texSample = texture(Sampler0, v_TexCoord);
    float sdfValue = texSample.a;

    float smoothing = fwidth(sdfValue) * 0.5;

    int layerType = v_LayerType;

    float alpha;

    if (layerType == 3) {
        alpha = smoothstep(SDFThreshold - smoothing, SDFThreshold + smoothing, sdfValue);
    } else if (layerType == 2) {
        float outlineEdge = SDFThreshold - OutlineWidth;
        alpha = smoothstep(outlineEdge - smoothing, outlineEdge + smoothing, sdfValue);
        float textMask = smoothstep(SDFThreshold - smoothing, SDFThreshold + smoothing, sdfValue);
        alpha = alpha * (1.0 - textMask);
    } else if (layerType == 1) {
        float glowEdge = (OutlineWidth > 0.001) ? (SDFThreshold - OutlineWidth) : SDFThreshold;
        float glowStart = glowEdge - GlowRadius;
        float glowEnd = glowEdge;
        alpha = smoothstep(glowStart, glowEnd, sdfValue) * 0.6;
        float outlineMask = smoothstep(glowEdge - smoothing, glowEdge + smoothing, sdfValue);
        alpha = alpha * (1.0 - outlineMask);
    } else {
        float shadowStart = SDFThreshold - ShadowSoftness - 0.15;
        float shadowEnd = SDFThreshold - 0.1;
        alpha = smoothstep(shadowStart, shadowEnd, sdfValue) * 0.5;
    }

    vec4 result = vec4(v_Color.rgb, alpha * v_Color.a);

    if (result.a <= 0.005) discard;

    result *= ColorModulator;

    fragColor = apply_fog(result, sphericalVertexDistance, cylindricalVertexDistance,
                          FogEnvironmentalStart, FogEnvironmentalEnd,
                          FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
}
