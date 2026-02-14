#version 330

#moj_import <minecraft:light.glsl>
#moj_import <minecraft:projection.glsl>
#moj_import <minecraft:dynamictransforms.glsl>

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in vec4 OverlayUV;
in ivec2 UV2;
in vec3 LightDir;
in vec3 Light1Dir;
in vec3 Normal;
in vec2 EdgeData;

out vec2 v_TexCoord;
out vec4 v_Color;
out vec4 v_OverlayUV;
out vec2 v_LightCoord;
out vec2 v_EdgeData;
out vec3 v_Normal;
out vec3 v_LightDir;
out vec3 v_Light1Dir;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position + ModelOffset, 1.0);
    
    v_Color = Color;
    v_TexCoord = UV0;
    v_OverlayUV = OverlayUV;
    v_LightCoord = (vec2(UV2) + 0.5) / 256.0;
    v_EdgeData = EdgeData;
    
    v_Normal = Normal;
    v_LightDir = LightDir;
    v_Light1Dir = Light1Dir;
}
