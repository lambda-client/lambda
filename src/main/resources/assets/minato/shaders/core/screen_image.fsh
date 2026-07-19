#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:globals.glsl>

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;

in vec2 v_TexCoord;
in vec4 v_Color;
in vec4 v_OverlayUV;
in float v_Layer;

out vec4 fragColor;

void main() {
    vec4 texColor = texture(Sampler0, v_TexCoord);
    
    vec4 color = texColor * v_Color * ColorModulator;
    
    if (color.a < 0.004) discard;
    
    if (v_OverlayUV.z > 0.5) {
        float aspectRatio = v_OverlayUV.y;
        
        vec2 baseUV = vec2(v_TexCoord.x * aspectRatio, v_TexCoord.y);
        
        vec4 transformedUV = TextureMat * vec4(baseUV, 0.0, 1.0);
        
        vec4 glint = texture(Sampler1, fract(transformedUV.xy));
        
        vec3 layer = glint.rgb * glint.a * 0.75;
        color.rgb += (layer * layer);
    }
    
    fragColor = color;
    
    gl_FragDepth = (1000.0 - v_Layer) / 2000.0;
}
