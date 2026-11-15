#version 330

// Downsampling half of a dual Kawase blur: the target is half the size of InSampler, so the
// centre tap and the four diagonal ones each average a 2x2 block through bilinear filtering.

uniform sampler2D InSampler;

layout(std140) uniform BlurSpread {
    float Spread;
};

in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec2 texel = Spread / vec2(textureSize(InSampler, 0));

    vec4 sum = texture(InSampler, texCoord) * 4.0;
    sum += texture(InSampler, texCoord + vec2(-texel.x, -texel.y));
    sum += texture(InSampler, texCoord + vec2( texel.x, -texel.y));
    sum += texture(InSampler, texCoord + vec2(-texel.x,  texel.y));
    sum += texture(InSampler, texCoord + vec2( texel.x,  texel.y));

    fragColor = sum / 8.0;
}
