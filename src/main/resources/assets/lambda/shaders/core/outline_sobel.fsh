#version 330
#moj_import <minecraft:dynamictransforms.glsl>

// Solid outline shader with optional fill
uniform sampler2D Sampler0; // Silhouette/ID/Group texture
uniform sampler2D Sampler1; // Silhouette Depth Buffer
uniform sampler2D Sampler2; // MC Depth Buffer

in vec2 v_TexCoord;
out vec4 fragColor;

/**
 * Check if the pixel with given alpha and coordinate is visible.
 */
bool isVisible(float alpha, vec2 coord) {
    if (alpha <= 0.0) return false;
    // Bit 7 (128) is our depth test flag.
    // Normalized 0-255 range: 128/255 = ~0.501
    // If < 0.5, it's Xray (no depth test)
    if (alpha < 0.5) return true;
    
    float silDepth = texture(Sampler1, coord).r;
    float worldDepth = texture(Sampler2, coord).r;
    
    // visible if silDepth is closer or equal to worldDepth
    return silDepth <= worldDepth + 0.00001;
}

void main() {
    vec4 center = texture(Sampler0, v_TexCoord);
    
    // OCULATION: If the center is an entity pixel, check its visibility for fill
    if (center.a > 0.0 && !isVisible(center.a, v_TexCoord)) {
        discard;
    }
    
    // Extract fill opacity from bits 0-6 (max 127)
    // We remove the depth test bit if present
    float fillAlpha = (center.a >= 0.5) ? (center.a - 128.0/255.0) * (255.0/125.0) : center.a * (255.0/125.0);
    
    // Handle fill
    if (fillAlpha > 1.5 / 255.0) {
        fragColor = vec4(center.rgb, fillAlpha);
        return;
    }
    
    // Edge detection
    vec2 texelSize = 1.0 / textureSize(Sampler0, 0);
    vec4 n = texture(Sampler0, v_TexCoord + vec2(0.0, texelSize.y));
    vec4 s = texture(Sampler0, v_TexCoord - vec2(0.0, texelSize.y));
    vec4 e = texture(Sampler0, v_TexCoord + vec2(texelSize.x, 0.0));
    vec4 w = texture(Sampler0, v_TexCoord - vec2(texelSize.x, 0.0));
    
    if (n.a > 0.0 || s.a > 0.0 || e.a > 0.0 || w.a > 0.0) {
        // We are on an edge. We are only visible if at least one neighbor that "owns" us is visible.
        if (!isVisible(n.a, v_TexCoord + vec2(0.0, texelSize.y)) &&
            !isVisible(s.a, v_TexCoord - vec2(0.0, texelSize.y)) &&
            !isVisible(e.a, v_TexCoord + vec2(texelSize.x, 0.0)) &&
            !isVisible(w.a, v_TexCoord - vec2(texelSize.x, 0.0))) {
            discard;
        }

        if (ModelOffset.x > 0.5) {
            fragColor = ColorModulator;
        } else {
            vec4 edgeSample = n.a > 0.0 ? n : (s.a > 0.0 ? s : (e.a > 0.0 ? e : w));
            fragColor = vec4(edgeSample.rgb, 1.0);
        }
    } else {
        discard;
    }
}
