#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

in vec4 Position;
in vec2 UV0;
in vec4 Color;

out vec2 v_TexCoord;
out vec4 v_Color;

void main() {
    gl_Position = Position;
    gl_Position.z = gl_Position.z * 0.9999 - 0.00001 * gl_Position.w;

    v_TexCoord = UV0;
    v_Color = Color;
}
