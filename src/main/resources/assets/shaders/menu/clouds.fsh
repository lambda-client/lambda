// ported by vitos1k
// it's a port from Shader toy Structured Volume sampling  https://www.shadertoy.com/view/Mt3GWs
// in the original there was noise texture iChannel0 used to generate some 3d noise.
// i used float noise(vec3) from this example http://glslsandbox.com/e#35155.0   to generate some random noise




/*
The MIT License (MIT)

Copyright (c) 2016 Huw Bowles, Daniel Zimmermann, Beibei Wang

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
*/

//
// For diagram shader showing how samples are taken:
//
// https://www.shadertoy.com/view/ll3GWs
//
// We are in the process of writing up this technique. The following github repos
// is the home of this research.
//
// https://github.com/huwb/volsample
//
//
//
// Additional credits - this scene is mostly mash up of these two amazing shaders:
//
// Clouds by iq: https://www.shadertoy.com/view/XslGRr
// Cloud Ten by nimitz: https://www.shadertoy.com/view/XtS3DD
//


#ifdef GL_ES
precision mediump float;
#endif

uniform float time;
uniform vec2 mouse;
uniform vec2 resolution;


#define SAMPLE_COUNT 16
#define PERIOD 2.
#define SEED 1337.37
#define PI 3.14159265359

// mouse toggle
bool STRUCTURED = true;

// cam moving in a straight line
vec3 lookDir = vec3(cos(PI * 1.2), 0., sin(PI * 1.2));
vec3 camVel = vec3(-20., 0., 0.);
float zoom = 1.0;// 1.5;

vec3 sundir = normalize(vec3(-1.0, 0.0, -1.));

// Noise salvaged from here http://glslsandbox.com/e#35155.0

float rand(vec3 p){
    return fract(4768.1232345456 * sin((p.z + p.x * 22.0)));
}


float noise(vec3 pos)
{
    vec3 ip = floor(pos);
    vec3 fp = smoothstep(0.0, 1.0, fract(pos));
    vec4 a = vec4(
    rand(ip + vec3(0, 0, 0)),
    rand(ip + vec3(1, 0, 0)),
    rand(ip + vec3(0, 1, 0)),
    rand(ip + vec3(1, 1, 0)));
    vec4 b = vec4(
    rand(ip + vec3(0, 0, 1)),
    rand(ip + vec3(1, 0, 1)),
    rand(ip + vec3(0, 1, 1)),
    rand(ip + vec3(1, 1, 1)));

    a = mix(a, b, fp.z);
    a.xy = mix(a.xy, a.zw, fp.yx);
    return mix(a.x, a.y, fp.x);
}


vec4 map(in vec3 p)
{
    float d = 0.2 + .8 * sin(0.6*p.z)*sin(0.5*p.x) - p.y;

    vec3 q = p;
    float f;

    f  = 0.5000*noise(q); q = q*2.02;
    f += 0.2500*noise(q); q = q*2.03;
    f += 0.1250*noise(q); q = q*2.01;
    f += 0.0625*noise(q);
    d += 2.75 * f;

    d = clamp(d, 0.0, 1.0);

    vec4 res = vec4(d);

    vec3 col = 1.15 * vec3(1.0, 0.95, 0.8);
    col += vec3(1., 0., 0.) * exp2(res.x*10.-10.);
    res.xyz = mix(col, vec3(0.7, 0.7, 0.7), res.x);

    return res;
}


    // to share with unity hlsl
    #define float2 vec2
    #define float3 vec3
    #define fmod mod
float mysign(float x) { return x < 0. ? -1. : 1.; }
    float2 mysign(float2 x) { return float2(x.x < 0. ? -1. : 1., x.y < 0. ? -1. : 1.); }

// compute ray march start offset and ray march step delta and blend weight for the current ray
void SetupSampling(out float2 t, out float2 dt, out float2 wt, in float3 ro, in float3 rd)
{
    if (!STRUCTURED)
    {
        dt = float2(PERIOD, PERIOD);
        t = dt;
        wt = float2(0.5, 0.5);
        return;
    }

        // the following code computes intersections between the current ray, and a set
        // of (possibly) stationary sample planes.

        // much of this should be more at home on the CPU or in a VS.

        // structured sampling pattern line normals
        float3 n0 = (abs(rd.x) > abs(rd.z)) ? float3(1., 0., 0.) : float3(0., 0., 1.);// non diagonal
float3 n1 = float3(mysign(rd.x * rd.z), 0., 1.);// diagonal

// normal lengths (used later)
float2 ln = float2(length(n0), length(n1));
    n0 /= ln.x;
    n1 /= ln.y;

    // some useful DPs
    float2 ndotro = float2(dot(ro, n0), dot(ro, n1));
float2 ndotrd = float2(dot(rd, n0), dot(rd, n1));

// step size
float2 period = ln * PERIOD;
    dt = period / abs(ndotrd);

    // dist to line through origin
    float2 dist = abs(ndotro / ndotrd);

    // raymarch start offset - skips leftover bit to get from ro to first strata lines
    t = -mysign(ndotrd) * fmod(ndotro, period) / abs(ndotrd);
    if (ndotrd.x > 0.) t.x += dt.x;
    if (ndotrd.y > 0.) t.y += dt.y;

    // sample weights
    float minperiod = PERIOD;
    float maxperiod = sqrt(2.)*PERIOD;
    wt = smoothstep(maxperiod, minperiod, dt/ln);
    wt /= (wt.x + wt.y);
}

vec4 raymarch(in vec3 ro, in vec3 rd)
{
    vec4 sum = vec4(0, 0, 0, 0);

    // setup sampling - compute intersection of ray with 2 sets of planes
    float2 t, dt, wt;
    SetupSampling(t, dt, wt, ro, rd);

    // fade samples at far extent
    float f = .6;// magic number - TODO justify this
    float endFade = f*float(SAMPLE_COUNT)*PERIOD;
    float startFade = .8*endFade;

    for (int i=0; i<SAMPLE_COUNT; i++)
    {
        if (sum.a > 0.99) continue;

        // data for next sample
        vec4 data = t.x < t.y ? vec4(t.x, wt.x, dt.x, 0.) : vec4(t.y, wt.y, 0., dt.y);
        // somewhat similar to: https://www.shadertoy.com/view/4dX3zl
        //vec4 data = mix( vec4( t.x, wt.x, dt.x, 0. ), vec4( t.y, wt.y, 0., dt.y ), float(t.x > t.y) );
        vec3 pos = ro + data.x * rd;
        float w = data.y;
        t += data.zw;

        // fade samples at far extent
        w *= smoothstep(endFade, startFade, data.x);

        vec4 col = map(pos);

        // iqs goodness
        float dif = clamp((col.w - map(pos+0.6*sundir).w)/0.6, 0.0, 1.0);
        vec3 lin = vec3(0.51, 0.53, 0.63)*1.35 + 0.55*vec3(0.85, 0.57, 0.3)*dif;
        col.xyz *= lin;

        col.xyz *= col.xyz;

        col.a *= 0.75;
        col.rgb *= col.a;

        // integrate. doesn't account for dt yet, wip.
        sum += col * (1.0 - sum.a) * w;
    }

    sum.xyz /= (0.001+sum.w);

    return clamp(sum, 0.0, 1.0);
}

vec3 sky(vec3 rd)
{
    vec3 col = vec3(0.);

    float hort = 1. - clamp(abs(rd.y), 0., 1.);
    col += 0.5*vec3(.99, .5, .0)*exp2(hort*8.-8.);
    col += 0.1*vec3(.5, .9, 1.)*exp2(hort*3.-3.);
    col += 0.55*vec3(.6, .6, .9);

    float sun = clamp(dot(sundir, rd), 0.0, 1.0);
    col += .2*vec3(1.0, 0.3, 0.2)*pow(sun, 2.0);
    col += .5*vec3(1., .9, .9)*exp2(sun*650.-650.);
    col += .1*vec3(1., 1., 0.1)*exp2(sun*100.-100.);
    col += .3*vec3(1., .7, 0.)*exp2(sun*50.-50.);
    col += .5*vec3(1., 0.3, 0.05)*exp2(sun*10.-10.);

    float ax = atan(rd.y, length(rd.xz))/1.;
    float ay = atan(rd.z, rd.x)/2.;
    float st = noise(vec3(ax, ay, 1.0));
    float st2 = noise(.25*vec3(ax, ay, 0.5));
    st *= st2;
    st = smoothstep(0.65, .9, st);
    col = mix(col, col+1.8*st, clamp(1.-1.1*length(col), 0., 1.));

    return col;
}


void main(void) {


    vec2 q = gl_FragCoord.xy / resolution.xy;
    vec2 p = -1.0 + 2.0*q;
    p.x *= resolution.x/ resolution.y;

    // camera
    vec3 ro = vec3(0., 1.5, 0.) + 0.01*time*camVel;
    vec3 ta = ro + lookDir;//vec3(ro.x, ro.y, ro.z-1.);
    vec3 ww = normalize(ta - ro);
    vec3 uu = normalize(cross(vec3(0.0, 1.0, 0.0), ww));
    vec3 vv = normalize(cross(ww, uu));
    float fov = 1.;
    vec3 rd = normalize(fov*p.x*uu + fov*1.2*p.y*vv + 1.5*ww);

    // divide by forward component to get fixed z layout instead of fixed dist layout
    //vec3 rd_layout = rd/mix(dot(rd,ww),1.0,samplesCurvature);
    vec4 clouds = raymarch(ro, rd);

    vec3 col = clouds.xyz;

    // sky if visible
    if (clouds.w <= 0.99)
    col = mix(sky(rd), col, clouds.w);

    col = clamp(col, 0., 1.);
    col = smoothstep(0., 1., col);
    col *= pow(16.0*q.x*q.y*(1.0-q.x)*(1.0-q.y), 0.12);//Vign

    gl_FragColor = vec4(col, 1.0);

}