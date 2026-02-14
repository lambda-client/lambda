#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

in vec3 Position;
in vec2 UV0;
in vec4 Color;
in vec4 OverlayUV;
in float Layer;

out vec2 v_TexCoord;
out vec4 v_Color;
out vec4 v_OverlayUV;
out float v_Layer;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    
    v_TexCoord = UV0;
    v_Color = Color;
    v_OverlayUV = OverlayUV;
    v_Layer = Layer;
}
