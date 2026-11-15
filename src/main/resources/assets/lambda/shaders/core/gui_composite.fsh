#version 330

// Lays the GUI over the game. Wherever the GUI drew anything the game behind it is swapped for
// its blurred copy, which is what makes translucent windows look like frosted glass; around the
// GUI a blurred copy of its own silhouette is tinted into a glow.

uniform sampler2D GuiSampler;      // The GUI as Compose drew it, premultiplied alpha
uniform sampler2D BackdropSampler; // The game, blurred
uniform sampler2D HaloSampler;     // The GUI, blurred

layout(std140) uniform GuiEffects {
    vec4 GlowColorA;
    vec4 GlowColorB;
    float GlowIntensity;
    float Frost;
    float Time;
};

in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec4 gui = texture(GuiSampler, texCoord);

    // Coverage rather than alpha: a window tinted at 30% is still wholly a window
    float covered = clamp(gui.a * 64.0, 0.0, 1.0);

    float frost = Frost * covered;
    vec4 under = vec4(texture(BackdropSampler, texCoord).rgb, 1.0) * frost;

    if (GlowIntensity > 0.0) {
        // A straight edge blurs to half coverage, so doubling makes the glow start at full strength
        float halo = clamp(texture(HaloSampler, texCoord).a * 2.0, 0.0, 1.0);
        float glow = clamp(pow(halo, 1.5) * GlowIntensity, 0.0, 1.0) * (1.0 - covered);
        float wave = 0.5 + 0.5 * sin(Time * 2.0 + texCoord.x * 5.0 + texCoord.y * 3.0);
        under += vec4(mix(GlowColorA.rgb, GlowColorB.rgb, wave), 1.0) * glow * (1.0 - frost);
    }

    fragColor = gui + under * (1.0 - gui.a);
}
