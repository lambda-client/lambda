#version 330

uniform sampler2D InSampler;

layout(std140) uniform BlurSpread {
	float Spread;
};

in vec2 texCoord;

out vec4 fragColor;

void main() {
	vec2 texel = Spread / vec2(textureSize(InSampler, 0));
	vec2 near = texel * 0.5;

	vec4 sum = texture(InSampler, texCoord + vec2(-texel.x, 0.0));
	sum += texture(InSampler, texCoord + vec2( texel.x, 0.0));
	sum += texture(InSampler, texCoord + vec2(0.0, -texel.y));
	sum += texture(InSampler, texCoord + vec2(0.0,  texel.y));
	sum += texture(InSampler, texCoord + vec2(-near.x, -near.y)) * 2.0;
	sum += texture(InSampler, texCoord + vec2( near.x, -near.y)) * 2.0;
	sum += texture(InSampler, texCoord + vec2(-near.x,  near.y)) * 2.0;
	sum += texture(InSampler, texCoord + vec2( near.x,  near.y)) * 2.0;

	fragColor = sum / 12.0;
}
