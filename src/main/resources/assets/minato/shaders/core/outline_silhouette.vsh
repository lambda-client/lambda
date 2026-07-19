#version 330
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

in vec3 Position;
in vec4 Color;

out vec4 v_Color;

void main() {
    gl_Position = TextureMat * ModelViewMat * vec4(Position, 1.0);
    v_Color = Color;
}
