#version 330

#moj_import <minecraft:dynamictransforms.glsl>

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
uniform sampler2D Sampler2;
uniform sampler2D Sampler3;

layout(std140) uniform GlintTransforms {
    mat4 GlintMat2;
    vec4 UnusedColor;
    vec3 UnusedOffset;
    mat4 GlintMat;
};

in vec2 v_TexCoord;
in vec4 v_Color;
in vec4 v_OverlayUV;
in vec2 v_LightCoord;
in vec2 v_EdgeData;
in vec3 v_Normal;
in vec3 v_LightDir;
in vec3 v_Light1Dir;

out vec4 fragColor;

const float GLINT_ALPHA = 0.75;

void main() {
    float mode = floor(v_OverlayUV.z + 0.5);
    bool useOverlay = (mod(mode, 2.0) >= 1.0);
    bool useAA      = (mod(floor(mode / 2.0), 2.0) >= 1.0);
    bool useGlint   = (mode >= 4.0);

    vec2 texCoord = v_TexCoord;
    if (useAA) {
        vec2 texSize = vec2(textureSize(Sampler0, 0));
        vec2 pixels = texCoord * texSize;
        vec2 t = fract(pixels - 0.5);
        vec2 d = fwidth(pixels);
        vec2 sharp_t = clamp((t - 0.5) / d + 0.5, 0.0, 1.0);
        texCoord = (floor(pixels - 0.5) + sharp_t + 0.5) / texSize;
    }

    vec4 texColor = texture(Sampler0, texCoord);
    if (texColor.a < 0.1) discard;
    
    vec4 baseColor = texColor * v_Color * ColorModulator;
    
    vec3 litColor = baseColor.rgb;
    
    if (v_OverlayUV.w >= 0.0) {
        vec3 n = normalize(v_Normal);
        vec3 light0 = normalize(v_LightDir);
        vec3 light1 = normalize(v_Light1Dir);
        
        float d0 = max(0.0, dot(light0, n));
        float d1 = max(0.0, dot(light1, n));
        
        float diffuse = min(1.0, (d0 + d1) * 0.6 + 0.4);
        
        float lightFactor = mix(1.0, diffuse, v_OverlayUV.w);
        litColor *= lightFactor;
    } else {
        litColor *= texture(Sampler2, v_LightCoord).rgb;
    }

    if (useOverlay) {
        vec4 overlayColor = texture(Sampler1, v_OverlayUV.xy);
        litColor = mix(litColor, overlayColor.rgb, overlayColor.a);
    }

    if (useGlint) {
        vec2 transformedUV = (GlintMat * vec4(v_TexCoord, 0.0, 1.0)).xy;
        vec3 glint = texture(Sampler3, fract(transformedUV)).rgb;
        
        vec3 layer = glint * GLINT_ALPHA;
        litColor += (layer * layer);
    }
    
    fragColor = vec4(litColor, baseColor.a);
}
