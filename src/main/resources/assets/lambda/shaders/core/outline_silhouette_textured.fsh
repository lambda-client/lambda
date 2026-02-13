#version 330

uniform sampler2D Sampler0; // Texture for alpha testing

in vec4 v_Color;
in vec2 v_TexCoord;

out vec4 fragColor;

void main() {
    // Standard alpha test
    float alpha = texture(Sampler0, v_TexCoord).a;
    if (alpha < 0.1) {
        discard;
    }
    
    // Output RGB color and Style ID (in alpha)
    fragColor = vec4(v_Color.rgb, v_Color.a);
}
