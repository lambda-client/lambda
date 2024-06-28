#version 330 core

uniform sampler2D u_Texture;
uniform float u_Progress;

in vec2 v_TexCoord;
out vec4 color;

void main() {
    vec2 coord = v_TexCoord;

    coord -= 0.5;
    coord *= 2.0;

    coord *= mix(0.9, 1.0, u_Progress);

    coord *= 0.5;
    coord += 0.5;

    vec4 tex = texture(u_Texture, coord);
    color = vec4(tex.rgb * mix(1.1, 1.0, u_Progress), tex.a * u_Progress);
}
