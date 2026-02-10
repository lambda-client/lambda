#version 330

// Solid outline shader with optional fill
uniform sampler2D Sampler0;

in vec2 v_TexCoord;
out vec4 fragColor;

void main() {
    vec4 center = texture(Sampler0, v_TexCoord);
    
    // If we are on a silhouette pixel, we draw the fill (with some opacity)
    if (center.a > 0.0) {
        // Optional: reduce opacity for fill
        fragColor = vec4(center.rgb, 0.2); // 20% fill opacity
        return;
    }
    
    // If we are not on a silhouette, check neighbors to see if we are on the edge
    vec2 texelSize = 1.0 / textureSize(Sampler0, 0);
    
    // Check 4 adjacent neighbors for better performance than 8-tap
    // Using a 1-pixel radius for a sharp outline
    vec4 n = texture(Sampler0, v_TexCoord + vec2(0.0, texelSize.y));
    vec4 s = texture(Sampler0, v_TexCoord - vec2(0.0, texelSize.y));
    vec4 e = texture(Sampler0, v_TexCoord + vec2(texelSize.x, 0.0));
    vec4 w = texture(Sampler0, v_TexCoord - vec2(texelSize.x, 0.0));
    
    if (n.a > 0.0 || s.a > 0.0 || e.a > 0.0 || w.a > 0.0) {
        // We are adjacent to a silhouette pixel, find the color of that pixel
        vec4 edgeColor = n.a > 0.0 ? n : (s.a > 0.0 ? s : (e.a > 0.0 ? e : w));
        fragColor = vec4(edgeColor.rgb, 1.0); // Full opacity outline
    } else {
        discard;
    }
}
