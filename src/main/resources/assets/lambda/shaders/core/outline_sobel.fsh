#version 330
#moj_import <minecraft:dynamictransforms.glsl>

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
uniform sampler2D Sampler2;

in vec2 v_TexCoord;
out vec4 fragColor;

bool isVisible(float alpha, vec2 coord) {
    if (alpha <= 0.0) return false;
    if (alpha < 0.5) return true;
    
    float silDepth = texture(Sampler1, coord).r;
    float worldDepth = texture(Sampler2, coord).r;
    
    return silDepth <= worldDepth + 0.00001;
}

void main() {
    vec4 center = texture(Sampler0, v_TexCoord);
    
    if (center.a > 0.0 && !isVisible(center.a, v_TexCoord)) discard;
    
    float fillAlpha = (center.a >= 0.5) ? (center.a - 128.0/255.0) * (255.0/125.0) : center.a * (255.0/125.0);
    
    if (fillAlpha > 1.5 / 255.0) {
        fragColor = vec4(center.rgb, fillAlpha);
        return;
    }
    
    vec2 texelSize = 1.0 / textureSize(Sampler0, 0);
    vec4 n = texture(Sampler0, v_TexCoord + vec2(0.0, texelSize.y));
    vec4 s = texture(Sampler0, v_TexCoord - vec2(0.0, texelSize.y));
    vec4 e = texture(Sampler0, v_TexCoord + vec2(texelSize.x, 0.0));
    vec4 w = texture(Sampler0, v_TexCoord - vec2(texelSize.x, 0.0));
    
    if (n.a > 0.0 || s.a > 0.0 || e.a > 0.0 || w.a > 0.0) {
        if (!isVisible(n.a, v_TexCoord + vec2(0.0, texelSize.y)) &&
            !isVisible(s.a, v_TexCoord - vec2(0.0, texelSize.y)) &&
            !isVisible(e.a, v_TexCoord + vec2(texelSize.x, 0.0)) &&
            !isVisible(w.a, v_TexCoord - vec2(texelSize.x, 0.0))) {
            discard;
        }

        if (ModelOffset.x > 0.5) fragColor = ColorModulator;
        else {
            vec4 edgeSample = n.a > 0.0 ? n : (s.a > 0.0 ? s : (e.a > 0.0 ? e : w));
            fragColor = vec4(edgeSample.rgb, 1.0);
        }
    } else discard;
}
