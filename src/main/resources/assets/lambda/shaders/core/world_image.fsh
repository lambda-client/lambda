#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:globals.glsl>

// Sampler for main texture
uniform sampler2D Sampler0;
// Sampler for overlay texture (e.g., enchantment glint)
uniform sampler2D Sampler1;

// Inputs from vertex shader
in vec2 v_TexCoord;
in vec4 v_Color;
in vec3 v_OverlayUV;  // (time, unused, hasOverlay)

out vec4 fragColor;

void main() {
    // Sample main texture
    vec4 texColor = texture(Sampler0, v_TexCoord);
    
    // Apply tint color
    vec4 color = texColor * v_Color * ColorModulator;
    
    // Discard nearly transparent fragments
    if (color.a < 0.004) {
        discard;
    }
    
    // Apply overlay (enchantment glint) if present
    // v_OverlayUV.y = aspect ratio (width/height) for square tiling
    // v_OverlayUV.z = hasOverlay flag (1.0 = enabled)
    if (v_OverlayUV.z > 0.5) {
        float aspectRatio = v_OverlayUV.y;
        
        // Use texture coordinates corrected for aspect ratio
        // This ensures square tiling regardless of image dimensions
        vec2 baseUV = vec2(v_TexCoord.x * aspectRatio, v_TexCoord.y);
        
        // Apply TextureMat from DynamicTransforms - this contains the glint transform
        // calculated at render time using Util.getMeasuringTimeMs(), exactly like vanilla!
        // All renderers (Immediate, Ticked, Chunked) now include fresh glint matrix
        vec4 transformedUV = TextureMat * vec4(baseUV, 0.0, 1.0);
        
        // Sample glint texture using transformed coordinates
        vec4 glint = texture(Sampler1, fract(transformedUV.xy));
        
        // Apply with additive blending
        color.rgb += glint.rgb * glint.a * 0.4;
    }
    
    fragColor = color;
}
