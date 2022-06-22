#ifdef GL_ES
precision mediump float;
#endif

const int MAX_ITER = 60;

uniform float time;
uniform vec2 mouse;
uniform vec2 resolution;

#define pi 3.14159265359

vec3 rotateY(in vec3 v, in float a) {
    return vec3(cos(a)*v.x + sin(a)*v.z, v.y, -sin(a)*v.x + cos(a)*v.z);
}

vec3 rotateX(in vec3 v, in float a) {
    return vec3(v.x, cos(a)*v.y + sin(a)*v.z, -sin(a)*v.y + cos(a)*v.z);
}

float torus(in vec3 p, in float radius, in float dist){
    return max(pow(dist-length(p.xz), 2.0)+p.y*p.y-radius*radius, 0.0);
}

vec3 hsv(in float h, in float s, in float v) {
    return mix(vec3(1.0), clamp((abs(fract(h + vec3(3, 2, 1) / 3.0) * 6.0 - 3.0) - 1.0), 0.0, 1.0), s) * v;
}

float angleBetween(vec3 a, vec3 b){
    float f=acos(dot(a, b));
    if (sign(a.y)<0.0){
        return 2.0*pi-f;
    }
    return f;
}

vec2 positionOnTorus(in vec3 p, in float dist){
    p=fract(p)-0.5;
    float i=(atan(p.x, p.z)+pi)/(2.0*pi);

    vec3 p2=normalize(vec3(p.x, 0.0, p.z));
    vec3 p3=normalize(dist*p2-p);
    float j=angleBetween(p3, p2)/(2.0*pi);
    return vec2(i, j);
}

vec3 texture(vec2 p){
    float si1=0.55+0.01*sin(p.x*20.0*pi);
    float si2=0.95+0.01*sin(p.x*10.0*pi-0.3);
    if (p.y<si2&&p.y>si1){
        return vec3(1, 1, 1);
    }
    return mix(vec3(0.98, 0.8, 0.5), vec3(0.95, 0.6, 0.05), pow(abs(p.y-0.5)*2.0, 0.5));
}

//rayMarcher by http://glsl.heroku.com/e#14543.0
vec3 intersect(in vec3 rayOrigin, in vec3 rayDir)
{
    float total_dist = 990.0;
    vec3 p = rayOrigin;
    float d = 1.0;
    float iter = 0.0;

    for (int i = 0; i < MAX_ITER; i++)
    {
        if (d < 0.001) break;

        d = torus(fract(p)-0.5, 0.12, 0.2);
        p += d*rayDir;
        total_dist += d;
        iter++;
    }

    if (d < 0.001) {
        return texture(positionOnTorus(p, 0.2))*vec3(1.0-iter/float(MAX_ITER));
    }
    return vec3(0.0);
}

void main()
{
    vec2 screenPos=gl_FragCoord.xy/resolution-0.5;
    vec3 rayDir=normalize(vec3(screenPos.x*1.5, screenPos.y, 0.5));
    rayDir=rotateX(rayDir, 4.0*(mouse.y-0.5));
    rayDir=rotateY(rayDir, 4.0*(mouse.x-0.5));
    vec3 cameraOrigin = vec3(0, 0, time);

    gl_FragColor = vec4(intersect(cameraOrigin, rayDir), 1.0);
}
