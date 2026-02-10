#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

// Vertex inputs - Position + UV + Color (POSITION_TEXTURE_COLOR)
in vec4 Position;    // Clip space position (xyzw)
in vec2 UV0;         // Texture coordinates for alpha testing
in vec4 Color;       // ESP color (solid)

// Outputs to fragment shader
out vec2 v_TexCoord;
out vec4 v_Color;

void main() {
    // Vertices are already in Clip Space from capture.
    // Proj and ModelView are set to Identity in the renderer.
    gl_Position = Position;
    // Apply a near-plane safe depth bias to pull the silhouette forward without crossing the clipping boundary (Z=0.05)
    gl_Position.z = gl_Position.z * 0.9999 - 0.00001 * gl_Position.w;

    v_TexCoord = UV0;
    v_Color = Color;
}
