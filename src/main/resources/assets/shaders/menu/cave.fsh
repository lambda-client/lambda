#ifdef GL_ES
precision mediump float;
#endif
uniform float time;
uniform vec2 resolution;
#define iTime time
#define iResolution resolution
#define A5
const vec4 iMouse = vec4(0.0);

mat2 r2d(float a) {
    float c = cos(a), s = sin(a);
    return mat2(c, s, -s, c);
}

vec2 path(float t) {
    float a = sin(t*.2 + 1.5), b = sin(t*.2);
    return vec2(2.*a, a*b);
}

float g = 0.;
float de(vec3 p) {
    p.xy -= path(p.z);

    float d = -length(p.xy) + 4.;

    g += .01 / (.01 + d * d);
    return d;
}

void mainImage(out vec4 fragColor, in vec2 fragCoord)
{
    vec2 uv = fragCoord / iResolution.xy - .5;
    uv.x *= iResolution.x / iResolution.y;

    float dt = iTime * 1.;
    vec3 ro = vec3(0, 0, -5. + dt);
    vec3 ta = vec3(0, 0, dt);

    ro.xy += path(ro.z);
    ta.xy += path(ta.z);

    vec3 fwd = normalize(ta - ro);
    vec3 right = cross(fwd, vec3(0, 1, 0));
    vec3 up = cross(right, fwd);
    vec3 rd = normalize(fwd + uv.x*right + uv.y*up);

    rd.xy *= r2d(sin(-ro.x / 3.14)*.3);
    vec3 p = floor(ro) + .5;
    vec3 mask;
    vec3 drd = 1. / abs(rd);
    rd = sign(rd);
    vec3 side = drd * (rd * (p - ro) + .5);

    float t = 0., ri = 0.;
    for (float i = 0.; i < 1.; i += .01) {
        ri = i;
        if (de(p) < 0.) break;
        mask = step(side, side.yzx) * step(side, side.zxy);

        side += drd * mask;
        p += rd * mask;
    }
    t = length(p - ro);

    vec3 c = vec3(1) * length(mask * vec3(1., .5, .75));
    c = mix(vec3(.2, .2, .7), vec3(.2, .1, .2), c);
    c += g * .4;
    c.r += sin(iTime)*0. + .42*sin(p.z*.25 - iTime * 0.);
    c = mix(c, vec3(.2, .1, .2), 1. - exp(-.001*t*t));

    fragColor = vec4(c, 1.0);
}

void main(void)
{
    mainImage(gl_FragColor, gl_FragCoord.xy);
}