#version 330

#moj_import <minecraft:light.glsl>
#moj_import <minecraft:projection.glsl>
#moj_import <minecraft:dynamictransforms.glsl>

// Attributes matching WORLD_MODEL_FORMAT
in vec3 Position;
in vec4 Color;
in vec2 UV0;
in vec4 OverlayUV; // (u, v, mode, diffuseAmount)
in ivec2 UV2;      // Lightmap coords
in vec3 LightDir;  // Primary light direction (pre-transformed for ITEMS_FLAT)
in vec3 Light1Dir; // Fill light direction (pre-transformed)
in vec3 Normal;
in vec2 EdgeData;

out vec2 v_TexCoord;
out vec4 v_Color;
out vec4 v_OverlayUV;
out vec2 v_LightCoord;
out vec2 v_EdgeData;
out vec3 v_Normal;
out vec3 v_LightDir;  // Primary light
out vec3 v_Light1Dir; // Fill light

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    
    v_Color = Color;
    v_TexCoord = UV0;
    v_OverlayUV = OverlayUV;
    v_LightCoord = (vec2(UV2) + 0.5) / 256.0; // Normalize light coords
    v_EdgeData = EdgeData;
    
    // Both normals and light directions are already transformed on CPU
    v_Normal = Normal;
    v_LightDir = LightDir;
    v_Light1Dir = Light1Dir;
}
