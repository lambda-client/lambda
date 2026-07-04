#version 330

in vec2 v_TexCoord;

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
uniform sampler2D Sampler2;
uniform sampler2D Sampler3;

layout(std140) uniform PostData {
    vec2 u_Size;
    float u_Time;
};

layout(std140) uniform OutlineData {
    float fillOpacity;
    float glowMultiplier;
    float lineWidth;
    float lineIntensity;
    int outlineStyle;
    int glowPosition;
    int depthTest;
} u_Outline;

out vec4 fragColor;

const int OUTLINE_STYLE_LINE = 0;
const int OUTLINE_STYLE_GLOW = 1;
const int OUTLINE_STYLE_BOTH = 2;
const int GLOW_POSITION_INSET = 0;
const int GLOW_POSITION_OUTSET = 1;
const int GLOW_POSITION_BOTH = 2;

bool usesLine() {
    return u_Outline.outlineStyle == OUTLINE_STYLE_LINE || u_Outline.outlineStyle == OUTLINE_STYLE_BOTH;
}

bool usesGlow() {
    return u_Outline.outlineStyle == OUTLINE_STYLE_GLOW || u_Outline.outlineStyle == OUTLINE_STYLE_BOTH;
}

bool usesInset() {
    return u_Outline.glowPosition == GLOW_POSITION_INSET || u_Outline.glowPosition == GLOW_POSITION_BOTH;
}

bool usesOutset() {
    return u_Outline.glowPosition == GLOW_POSITION_OUTSET || u_Outline.glowPosition == GLOW_POSITION_BOTH;
}

vec4 lambda_applyCustom(vec4 baseColor, float edgeFactor, bool isOutline) {
    return baseColor;
}

bool depthTestVisible(vec2 coord) {
    if (u_Outline.depthTest == 0) return true;

    float silDepth = texture(Sampler2, coord).r;
    float worldDepth = texture(Sampler3, coord).r;
    return silDepth <= worldDepth + 0.00001;
}

vec4 sampleSource(vec2 coord) {
    vec4 source = texture(Sampler0, coord);
    if (source.a <= 0.0 || depthTestVisible(coord)) return source;
    return vec4(0.0);
}

vec4 sampleSingleLineOutline() {
    vec2 texel = (1.0 / u_Size) * u_Outline.lineWidth;
    vec4 outline = vec4(0.0);

    outline = max(outline, sampleSource(v_TexCoord + vec2(texel.x, 0.0)));
    outline = max(outline, sampleSource(v_TexCoord - vec2(texel.x, 0.0)));
    outline = max(outline, sampleSource(v_TexCoord + vec2(0.0, texel.y)));
    outline = max(outline, sampleSource(v_TexCoord - vec2(0.0, texel.y)));

    outline = max(outline, sampleSource(v_TexCoord + texel));
    outline = max(outline, sampleSource(v_TexCoord + vec2(texel.x, -texel.y)));
    outline = max(outline, sampleSource(v_TexCoord + vec2(-texel.x, texel.y)));
    outline = max(outline, sampleSource(v_TexCoord - texel));

    return outline;
}

vec4 sampleGlowOutline() {
    return texture(Sampler1, v_TexCoord);
}

vec4 sampleOutsetGlowOutline() {
    vec4 glow = sampleGlowOutline();
    if (!usesLine() || u_Outline.lineWidth <= 0.0 || glow.a <= 0.0001) return glow;

    vec2 texel = 1.0 / u_Size;
    float glowLeft = texture(Sampler1, v_TexCoord - vec2(texel.x, 0.0)).a;
    float glowRight = texture(Sampler1, v_TexCoord + vec2(texel.x, 0.0)).a;
    float glowDown = texture(Sampler1, v_TexCoord - vec2(0.0, texel.y)).a;
    float glowUp = texture(Sampler1, v_TexCoord + vec2(0.0, texel.y)).a;

    vec2 inward = vec2(glowRight - glowLeft, glowUp - glowDown);
    float inwardLength2 = dot(inward, inward);
    if (inwardLength2 <= 0.000001) return glow;

    vec2 offset = inward * inversesqrt(inwardLength2) * (texel * u_Outline.lineWidth);
    return max(glow, texture(Sampler1, v_TexCoord + offset));
}

vec4 sampleInsetGlowOutline(vec4 center) {
    vec4 glow = sampleGlowOutline();
    float edge = max(center.a - glow.a, 0.0);
    if (edge <= 0.0) return vec4(0.0);

    vec3 glowColor = glow.a > 0.0001 ? clamp(glow.rgb / glow.a, 0.0, 1.0) : center.rgb;
    return vec4(glowColor * edge, edge);
}

float getOutlineAlpha(vec4 lineOutline, vec4 glowOutline) {
    float lineAlpha = min(lineOutline.a * u_Outline.lineIntensity, 1.0);
    float glowAlpha = min(glowOutline.a * u_Outline.glowMultiplier, 1.0);
    return max(lineAlpha, glowAlpha);
}

vec4 alphaBlend(vec4 top, vec4 bottom) {
    float alpha = top.a + bottom.a * (1.0 - top.a);
    if (alpha <= 0.0001) return vec4(0.0);

    vec3 rgb = (
        top.rgb * top.a +
        bottom.rgb * bottom.a * (1.0 - top.a)
    ) / alpha;

    return vec4(rgb, alpha);
}

vec3 unpremultiply(vec4 c) {
    return c.a > 0.0001 ? clamp(c.rgb / c.a, 0.0, 1.0) : c.rgb;
}

vec4 buildOutlineColor(vec4 lineOutline, vec4 glowOutline) {
    float outlineAlpha = getOutlineAlpha(lineOutline, glowOutline);
    if (outlineAlpha <= 0.0001) return vec4(0.0);

    vec3 lineColor  = unpremultiply(lineOutline) * u_Outline.lineIntensity;
    vec3 glowColor  = unpremultiply(glowOutline) * u_Outline.glowMultiplier;
    vec3 outlineColor = clamp(max(glowColor, lineColor), 0.0, 1.0);

    float edge = clamp(max(lineOutline.a, glowOutline.a), 0.0, 1.0);
    return lambda_applyCustom(vec4(outlineColor, outlineAlpha), edge, true);
}

void main() {
    vec4 center = sampleSource(v_TexCoord);
    bool insideMask = center.a != 0.0;
    bool renderFill = insideMask && u_Outline.fillOpacity > 0.0;
    bool renderInsetGlow = insideMask && usesGlow() && usesInset();
    bool renderOutside = !insideMask;
    bool renderLine = renderOutside && usesLine();
    bool renderOutsetGlow = renderOutside && usesGlow() && usesOutset();

    if (!renderFill && !renderInsetGlow && !renderLine && !renderOutsetGlow) discard;

    if (insideMask) {
        vec4 fill = renderFill
            ? lambda_applyCustom(vec4(center.rgb, center.a * u_Outline.fillOpacity), 0.25, false)
            : vec4(0.0);

        if (!renderInsetGlow) {
            fragColor = fill;
        } else {
            vec4 glowOutline = sampleInsetGlowOutline(center);
            fragColor = alphaBlend(buildOutlineColor(vec4(0.0), glowOutline), fill);
        }
    } else {
        vec4 lineOutline = renderLine ? sampleSingleLineOutline() : vec4(0.0);
        vec4 glowOutline = renderOutsetGlow ? sampleOutsetGlowOutline() : vec4(0.0);
        fragColor = buildOutlineColor(lineOutline, glowOutline);
    }

    if (fragColor.a <= 0.0001) discard;
}
