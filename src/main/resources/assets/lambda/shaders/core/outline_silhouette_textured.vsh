#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in vec2 UV2; // Lightmap coordinates (unused but part of the format)

out vec4 v_Color;
out vec2 v_TexCoord;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position + ModelOffset, 1.0);
    v_Color = Color;
    v_TexCoord = UV0;
}
