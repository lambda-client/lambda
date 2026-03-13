#version 330
#moj_import <minecraft:dynamictransforms.glsl>

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
uniform sampler2D Sampler2;

in vec2 v_TexCoord;
out vec4 fragColor;

float u_Thickness;
float u_GlowIntensity;
float u_GlowRadius;
float u_FillOpacity;

bool depthTestVisible(vec2 coord) {
    float silDepth = texture(Sampler1, coord).r;
    float worldDepth = texture(Sampler2, coord).r;
    return silDepth <= worldDepth + 0.00001;
}

bool isVisible(float alpha, vec2 coord) {
    if (alpha <= 0.0) return false;

    if (ModelOffset.x > 0.5) {
        if (ModelOffset.y > 0.5) return depthTestVisible(coord);
        return true;
    }

    if (alpha < 0.5) return true;
    return depthTestVisible(coord);
}

bool hasEntityData(vec2 coord) {
    return texture(Sampler0, coord).a > 0.0;
}

vec3 getEntityColor(vec4 sample) {
    return sample.rgb;
}

void main() {
    float screenHeight = float(textureSize(Sampler0, 0).y);
    u_Thickness = TextureMat[0][0] * screenHeight;
    u_GlowIntensity = TextureMat[0][1];
    u_GlowRadius = TextureMat[0][2] * screenHeight;
    u_FillOpacity = TextureMat[0][3];

    if (u_Thickness <= 0.0) u_Thickness = 1.0;

    float outlineAlpha = clamp(u_Thickness, 0.0, 1.0);
    float effectiveThickness = max(u_Thickness, 1.0);

    vec2 texelSize = 1.0 / textureSize(Sampler0, 0);
    vec4 center = texture(Sampler0, v_TexCoord);
    bool centerHasData = center.a > 0.0;
    bool centerVisible = centerHasData && isVisible(center.a, v_TexCoord);

    float maxRadius = effectiveThickness + u_GlowRadius;
    int maxRadiusInt = int(ceil(maxRadius));

    float minDistToEdge = maxRadius + 1.0;

    if (centerHasData) {
        if (!centerVisible) discard;

        if (u_FillOpacity > 0.0) {
            vec3 color;
            if (ModelOffset.x > 0.5) color = ColorModulator.rgb;
            else color = getEntityColor(center);
            fragColor = vec4(color, u_FillOpacity);
            return;
        }

        discard;
    } else {
        vec3 nearestColor = vec3(0.0);
        bool nearestVisible = false;

        int step = max(1, maxRadiusInt / 12);
        int bestDx = 0, bestDy = 0;

        for (int dy = -maxRadiusInt; dy <= maxRadiusInt; dy += step) {
            for (int dx = -maxRadiusInt; dx <= maxRadiusInt; dx += step) {
                if (dx == 0 && dy == 0) continue;
                float dist = length(vec2(float(dx), float(dy)));
                if (dist > maxRadius || dist >= minDistToEdge) continue;

                vec2 sampleCoord = v_TexCoord + vec2(float(dx), float(dy)) * texelSize;
                vec4 s = texture(Sampler0, sampleCoord);
                if (s.a > 0.0) {
                    minDistToEdge = dist;
                    bestDx = dx;
                    bestDy = dy;
                    nearestVisible = isVisible(s.a, sampleCoord);
                    if (ModelOffset.x > 0.5) nearestColor = ColorModulator.rgb;
                    else nearestColor = getEntityColor(s);
                }
            }
        }

        if (step > 1 && minDistToEdge < maxRadius + 1.0) {
            int rMin = step;
            for (int dy = bestDy - rMin; dy <= bestDy + rMin; dy++) {
                for (int dx = bestDx - rMin; dx <= bestDx + rMin; dx++) {
                    if (dx == 0 && dy == 0) continue;
                    float dist = length(vec2(float(dx), float(dy)));
                    if (dist > maxRadius || dist >= minDistToEdge) continue;

                    vec2 sampleCoord = v_TexCoord + vec2(float(dx), float(dy)) * texelSize;
                    vec4 s = texture(Sampler0, sampleCoord);
                    if (s.a > 0.0) {
                        minDistToEdge = dist;
                        nearestVisible = isVisible(s.a, sampleCoord);
                        if (ModelOffset.x > 0.5) nearestColor = ColorModulator.rgb;
                        else nearestColor = getEntityColor(s);
                    }
                }
            }
        }

        if (!nearestVisible) discard;

        if (minDistToEdge <= effectiveThickness) {
            fragColor = vec4(nearestColor, outlineAlpha);
            return;
        }

        if (u_GlowIntensity > 0.0 && u_GlowRadius > 0.0 && minDistToEdge <= maxRadius) {
            float glowDist = minDistToEdge - effectiveThickness;
            float glowFactor = 1.0 - (glowDist / u_GlowRadius);
            glowFactor = clamp(glowFactor, 0.0, 1.0);
            glowFactor = glowFactor * glowFactor;
            float alpha = glowFactor * u_GlowIntensity * outlineAlpha;
            fragColor = vec4(nearestColor, alpha);
            return;
        }

        discard;
    }
}
