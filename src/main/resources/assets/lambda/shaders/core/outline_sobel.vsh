#version 330

in vec3 Position;
in vec2 UV0;

out vec2 v_TexCoord;

void main() {
    gl_Position = vec4(Position.xy, 0.0, 1.0);
    v_TexCoord = UV0;
}
