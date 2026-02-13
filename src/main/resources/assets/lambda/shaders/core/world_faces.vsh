#version 330
#moj_import <minecraft:projection.glsl>
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:fog.glsl>

in vec3 Position;
in vec4 Color;

out vec4 v_Color;
out float sphericalVertexDistance;
out float cylindricalVertexDistance;

void main() {
    vec3 worldPos = Position + ModelOffset;
    vec4 viewPos = ModelViewMat * vec4(worldPos, 1.0);
    gl_Position = ProjMat * viewPos;
    
    v_Color = Color;
    sphericalVertexDistance = fog_spherical_distance(viewPos.xyz);
    cylindricalVertexDistance = fog_cylindrical_distance(viewPos.xyz);
}
