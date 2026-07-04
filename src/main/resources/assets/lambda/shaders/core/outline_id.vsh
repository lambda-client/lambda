#version 330

in vec4 Position;
in vec2 UV0;
in vec4 Color;

out vec2 v_TexCoord;
out vec4 v_Color;

void main() {
    gl_Position = Position;

    v_TexCoord = UV0;
    v_Color = Color;
}
