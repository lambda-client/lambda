#version 330

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
