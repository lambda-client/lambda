#version 330

uniform sampler2D GuiSampler;
uniform sampler2D BackdropSampler;
uniform sampler2D GlowSampler;

layout(std140) uniform GuiEffects {
	vec4 GlowColorA;
	vec4 GlowColorB;
	float GlowIntensity;
	float Blur;
	float Time;
	float Dim;
};

const vec3 DIM_COLOR = vec3(16.0 / 255.0);

in vec2 texCoord;

out vec4 fragColor;

void main() {
	vec4 gui = texture(GuiSampler, texCoord);

	float covered = clamp(gui.a * 64.0, 0.0, 1.0);

	float blurred = Blur * covered;
	vec3 backdrop = mix(texture(BackdropSampler, texCoord).rgb, DIM_COLOR, Dim);
	vec4 under = vec4(backdrop, 1.0) * blurred;

	if (GlowIntensity > 0.0) {
		float glowMask = clamp(texture(GlowSampler, texCoord).a * 2.0, 0.0, 1.0);
		float glow = clamp(pow(glowMask, 1.5) * GlowIntensity, 0.0, 1.0) * (1.0 - covered);
		float wave = 0.5 + 0.5 * sin(Time * 2.0 + texCoord.x * 5.0 + texCoord.y * 3.0);
		under += vec4(mix(GlowColorA.rgb, GlowColorB.rgb, wave), 1.0) * glow * (1.0 - blurred);
	}

	vec4 over = gui + under * (1.0 - gui.a);
	fragColor = over + vec4(DIM_COLOR, 1.0) * Dim * (1.0 - over.a);
}
