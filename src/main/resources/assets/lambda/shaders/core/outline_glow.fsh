#version 330

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
uniform sampler2D Sampler2;

in vec2 v_TexCoord;
out vec4 fragColor;

layout(std140) uniform GlowData {
    vec2 u_HalfTexelSize;
    float u_Offset;
    int u_DepthTest;
};

bool depthTestVisible(vec2 coord) {
    if (u_DepthTest == 0) return true;

    float silDepth = texture(Sampler1, coord).r;
    float worldDepth = texture(Sampler2, coord).r;
    return silDepth <= worldDepth + 0.00001;
}

vec4 sampleVisible(vec2 coord) {
    vec4 source = texture(Sampler0, coord);
    if (source.a <= 0.0 || depthTestVisible(coord)) return source;
    return vec4(0.0);
}

void main() {
    fragColor = (
        sampleVisible(v_TexCoord) * 4.0 +
        sampleVisible(v_TexCoord - u_HalfTexelSize * u_Offset) +
        sampleVisible(v_TexCoord + u_HalfTexelSize * u_Offset) +
        sampleVisible(v_TexCoord + vec2(u_HalfTexelSize.x, -u_HalfTexelSize.y) * u_Offset) +
        sampleVisible(v_TexCoord - vec2(u_HalfTexelSize.x, -u_HalfTexelSize.y) * u_Offset)
    ) / 8.0;
}
