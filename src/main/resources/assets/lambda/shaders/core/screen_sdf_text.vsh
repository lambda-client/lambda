#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

in vec3 Position;
in vec2 UV0;
in vec4 Color;
in vec4 SDFStyle;
in float Layer;

out vec2 texCoord0;
out vec4 vertexColor;
out vec4 sdfStyleParams;
out float v_Layer;
flat out int v_LayerType;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position.xy, 0.0, 1.0);
    
    texCoord0 = UV0;
    vertexColor = Color;
    sdfStyleParams = SDFStyle;
    v_Layer = Layer;
    v_LayerType = int(Position.z + 0.5);
}
