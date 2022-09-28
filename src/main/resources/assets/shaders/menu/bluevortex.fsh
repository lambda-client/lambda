/*
 * Original shader from: https://www.shadertoy.com/view/3dcXzs
 */

#ifdef GL_ES
precision mediump float;
#endif

// glslsandbox uniforms
uniform float time;
uniform vec2 resolution;

// shadertoy emulation
#define iTime time
#define iResolution resolution

// --------[ Original ShaderToy begins here ]---------- //
#define PI 3.141592654

vec2 Rot(vec2 v, float angle)
{
    return vec2(v.x * cos(angle) + v.y * sin(angle),
    v.y * cos(angle) - v.x * sin(angle));
}

vec3 DrawStar(float len, float angle)
{
    vec3 baseColor = vec3(0.0, 0.3, 0.7);
    float fre1 = 30.0;
    float fre2 = 20.0;
    float radius = 0.03;
    float m = radius / (radius + abs(sin(len * fre1 * 1.0 - 0.5 * iTime)));
    float n = radius / (radius + abs(sin(angle * fre2 + len * 100.0)));
    float f6 = max(m * n - 0.1 * len, 0.0) * 100.0;
    return baseColor * f6;
}

float map(float l)
{
    float lm = 1.0;
    l = clamp(1e-1, l, l);
    float lm2 = lm * lm;
    float lm4 = lm2 * lm2;
    return sqrt(lm4 / (l * l) + lm2);
    // return 1.0/(l+1e-5);
}
vec3 DrawCloud(float dis, float angle, vec2 coord)
{
    vec3 baseColor = vec3(0.0, 0.0, 0.0);
    vec3 cloudColor = vec3(0.0, 0.3, 0.7);
    float x = angle + dis;
    float fre = 2.0;
    float ap = 1.0;
    float d = float(0.0);
    coord = Rot(coord, 0.3 * iTime);
    vec3 kp = vec3(coord * max(dis, 1.0), dis);
    for (int i = 1; i < 5; i++) {
        float k = 1.0 + sin(fre * x + 0.3 * iTime);
        k = k * k * 0.25;
        float p = fract(k + dis / float(i + 1));
        p = p * (1.0 - p);
        p = smoothstep(0.1, 0.25, p);
        d += ap * p;
        kp += sin(kp.zxy * 0.75 * fre + 0.3 * iTime);
        d -= abs(dot(cos(kp), sin(kp.yzx)) * ap);
        fre *= -2.0;
        ap *= 0.5;
    }
    float len2=dot(coord, coord);
    d+=len2*4.0;
    return baseColor + cloudColor * d;
}
vec3 Render(vec2 coord)
{
    float len = length(coord);
    float angle = PI - acos(coord.x / len) * sign(coord.y);

    vec3 baseColor = vec3(0.0, 0.0, 0.0);
    float dis = map(len);
    // baseColor+=DrawStar(dis/10.0,angle);
    // baseColor+=vec3(0.0,0.3,0.7)*dis/10.0;
    baseColor += DrawCloud(dis, angle, coord) * 0.3;
    vec3 fogColor = vec3(0.3, 1.5, 3.0);
    float fogC = pow(0.97, dis);
    baseColor = mix(fogColor, baseColor, fogC);
    return baseColor;
}

void mainImage(out vec4 fragColor, in vec2 fragCoord)
{
    vec2 uv = fragCoord.xy / iResolution.xy;
    vec2 coord = uv - 0.5;
    if (iResolution.y > iResolution.x) {
        coord.x *= iResolution.x / iResolution.y;
    } else {
        coord.y /= iResolution.x / iResolution.y;
    }
    vec3 baseColor = Render(coord);
    fragColor = vec4(baseColor*1.3, 1.0);
}
// --------[ Original ShaderToy ends here ]---------- //

void main(void)
{
    mainImage(gl_FragColor, gl_FragCoord.xy);
}