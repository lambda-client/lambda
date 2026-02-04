#version 330

// Sampler0: Main Atlas
// Sampler1: Overlay Texture (White/Hurt flash)
// Sampler2: Lightmap
// Sampler3: Glint Texture (enchantment shimmer)
#moj_import <minecraft:dynamictransforms.glsl>

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
uniform sampler2D Sampler2;
uniform sampler2D Sampler3;

layout(std140) uniform GlintTransforms {
    mat4 GlintMat2; // Mat2 packed into the ModelView slot
    vec4 UnusedColor;
    vec3 UnusedOffset;
    mat4 GlintMat;  // Mat1 original
};

in vec2 v_TexCoord;
in vec4 v_Color;
in vec4 v_OverlayUV; // (u, v, mode, diffuseAmount)
in vec2 v_LightCoord;
in vec2 v_EdgeData;
in vec3 v_Normal;
in vec3 v_LightDir;  // Primary light (pre-transformed)
in vec3 v_Light1Dir; // Fill light (pre-transformed)

out vec4 fragColor;

// DEBUG MODE:
// 0 = Normal rendering
// 1 = Show normals as RGB (red=+X, green=+Y, blue=+Z)
// 2 = Show light0 direction as RGB
// 3 = Show dot product with light0 as grayscale
// 4 = Show which faces are lit (d0 > 0.5 = red, d1 > 0.5 = green)
// 5 = Show glint scroll values as colors (R=scrollX, G=scrollY, B=mode/10)
// 6 = Show raw glint texture with base UVs (no animation)
// 7 = Show computed glint UVs as color (R=U fract, G=V fract, B=0.5)
#define DEBUG_MODE 0

// Glint constants matching vanilla (from TextureTransform.getGlintTransformation)
const float GLINT_ALPHA = 0.75; // Default from options.getGlintStrength()

void main() {
    float mode = floor(v_OverlayUV.z + 0.5);
    // Mode bits: 1=overlay, 2=AA, 4=glint
    bool useOverlay = (mod(mode, 2.0) >= 1.0);
    bool useAA      = (mod(floor(mode / 2.0), 2.0) >= 1.0);
    bool useGlint   = (mode >= 4.0);

    // 1. Determine Texture Coordinates (with optional Sharp AA)
    vec2 texCoord = v_TexCoord;
    if (useAA) {
        vec2 texSize = vec2(textureSize(Sampler0, 0));
        vec2 pixels = texCoord * texSize;
        vec2 t = fract(pixels - 0.5);
        vec2 d = fwidth(pixels);
        vec2 sharp_t = clamp((t - 0.5) / d + 0.5, 0.0, 1.0);
        texCoord = (floor(pixels - 0.5) + sharp_t + 0.5) / texSize;
    }

    // 2. Sample Base Texture from atlas
    vec4 texColor = texture(Sampler0, texCoord);
    if (texColor.a < 0.1) discard;
    
    // 3. Apply Multiplier (v_Color) and Global Modulator
    vec4 baseColor = texColor * v_Color * ColorModulator;
    
    // 4. Calculate lighting
    vec3 litColor = baseColor.rgb;
    
    // Handle Shading:
    // v_OverlayUV.w is 'shadingAmount': 1.0 = shaded, 0.0 = unshaded.
    // NOTE: For non-3D items, shadingAmount is 0.0. We want factor 1.0 (unshaded), not 0.0 (black).
    if (v_OverlayUV.w >= 0.0) {
        vec3 n = normalize(v_Normal);
        vec3 light0 = normalize(v_LightDir);
        vec3 light1 = normalize(v_Light1Dir);
        
        float d0 = max(0.0, dot(light0, n));
        float d1 = max(0.0, dot(light1, n));
        
        // Vanilla's diffuse lighting formula: diffuse = max(0, n.l) * 0.6 + 0.4
        float diffuse = min(1.0, (d0 + d1) * 0.6 + 0.4);
        
        // Final light factor: mix between 1.0 (unshaded) and 'diffuse' (shaded) based on shadingAmount.
        float lightFactor = mix(1.0, diffuse, v_OverlayUV.w);
        litColor *= lightFactor;
    } else {
        // For world objects without directional shading, use lightmap
        litColor *= texture(Sampler2, v_LightCoord).rgb;
    }

    // 5. Apply Hurt Flash / Overlay
    if (useOverlay) {
        vec4 overlayColor = texture(Sampler1, v_OverlayUV.xy);
        litColor = mix(litColor, overlayColor.rgb, overlayColor.a);
    }

    // 6. Apply Enchantment Glint (Absolute 1.21 Parity)
    if (useGlint) {
        // Use v_TexCoord (Atlas UVs) for parity with model glint logic
        vec2 transformedUV = (GlintMat * vec4(v_TexCoord, 0.0, 1.0)).xy;
        vec3 glint = texture(Sampler3, fract(transformedUV)).rgb;
        
        // Intensity = (sample * alpha)^2
        vec3 layer = glint * GLINT_ALPHA;
        litColor += (layer * layer);

        // Debug visualizations
        #if DEBUG_MODE == 5
            fragColor = vec4(v_EdgeData.x, v_EdgeData.y, 0.0, 1.0); return;
        #elif DEBUG_MODE == 6
            fragColor = texture(Sampler3, v_EdgeData); return;
        #elif DEBUG_MODE == 7
            fragColor = vec4(fract(guv.x), fract(guv.y), 0.5, 1.0); return;
        #elif DEBUG_MODE == 8
            // Show diffuse shading amount
            fragColor = vec4(vec3(v_OverlayUV.w), 1.0); return;
        #endif
    }
    
    fragColor = vec4(litColor, baseColor.a);
}
