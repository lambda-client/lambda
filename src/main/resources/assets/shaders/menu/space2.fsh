//
// Is it meant to be so ... jiggly? the stars seem to jump. Pretty though!


//CBS
//Parallax scrolling fractal galaxy.
//Inspired by JoshP's Simplicity shader: https://www.shadertoy.com/view/lslGWr

// http://www.fractalforums.com/new-theories-and-research/very-simple-formula-for-fractal-patterns/
// Ported from ShaderToys.com by redexe@gmail.com

#ifdef GL_ES
precision mediump float;
#endif

uniform float time;
uniform vec2 mouse;
uniform vec2 resolution;

vec3 nrand3(vec2 co)
{
    vec3 a = fract(cos(co.x*8.3e-3 + co.y)*vec3(1.3e5, 4.7e5, 2.9e5));
    vec3 b = fract(sin(co.x*0.3e-3 + co.y)*vec3(8.1e5, 1.0e5, 0.1e5));
    vec3 c = mix(a, b, 0.5);
    return c;
}
float field(in vec3 p, float s, vec3 nebulae) {
    float strength = 7. + .03 * log(1.e-6 + fract(sin(time) * 4373.11));
    float accum = s/4.;
    float prev = 0.;
    float tw = 0.;
    for (int i = 0; i < 26; ++i) {
        float mag = dot(p, p);
        p = abs(p) / mag + nebulae;// these lines
        float w = exp(-float(i) / 7.);
        accum += w * exp(-strength * pow(abs(mag - prev), 2.2));
        tw += w;
        prev = mag;
    }
    return max(0., 5. * accum / tw - .7);
}

void main() {

    vec2 startPos = vec2(1.6, 3.);
    float freqs[4];
    freqs[0] = 0.04;
    freqs[1] = 0.6;
    freqs[2] = 0.2;
    freqs[3] = 0.4;

    vec2 uv = 2. * gl_FragCoord.xy / resolution.xy - 1.;
    vec2 uvs = uv * resolution.xy / max(resolution.x, resolution.y);
    uvs += startPos;
    vec3 p = vec3(uvs / 4., 0) + vec3(1., -1.3, 0.);
    p += .2 * vec3(sin(time / 16.), sin(time / 12.), sin(time / 128.));

    float t = field(p, freqs[2], vec3(-.1, -.4, -1.5));
    float v = (1. - exp((abs(uv.x) - 1.) * 6.)) * (1. - exp((abs(uv.y) - 1.) * 6.));

    vec4 c1 = mix(freqs[3]-.3, 1., v) * vec4(1.5*freqs[2] * t * t* t, 1.2*freqs[1] * t * t, freqs[3]*t, 1.0);

    //Second Layer
    vec3 p2 = vec3(uvs *104., 0.)*time*0.11;//vec3(uvs / (4.+sin(time*0.11)*0.2+0.2+sin(time*0.15)*0.3+0.4), 1.5) + vec3(2., -1.3, -1.);
    //p2 += 0.25 * vec3(sin(time / 16.), sin(time / 12.),  sin(time / 128.));
    float t2 = field(p2, freqs[3], vec3(-.3, -.38, -1.41364));
    vec4 c2 = mix(.4, 1., v) * vec4(1.3 * t2 * t2 * t2, 1.8  * t2 * t2, t2* freqs[0], t2);


    vec4 starcolor = vec4(0);

    //Second Layer
    vec2 seed2 = p.xy * 2.0 * time*0.1;
    seed2 = floor(seed2 * resolution.x);
    vec3 rnd2 = nrand3(seed2);
    starcolor += vec4(pow(rnd2.z, 20.0));
    gl_FragColor = c1+c2+starcolor;
}