#version 420

in vec2 v_TexCoord;
in vec4 v_Color;

out vec4 color;

float sdf(float channel, float min, float max) {
    return 1.0 - smoothstep(min, max, 1.0 - channel);
}

void main()
{
    bool isEmoji = v_TexCoord.x < 0.0;

    if (isEmoji) {
        vec4 c = texture(u_EmojiTexture, -v_TexCoord);
        color = vec4(c.rgb, sdf(c.a, u_SDFMin, u_SDFMax)) * v_Color;
        return;
    }

    float sdf = sdf(texture(u_FontTexture, v_TexCoord).r, u_SDFMin, u_SDFMax);
    color = vec4(1.0, 1.0, 1.0, sdf) * v_Color;
}