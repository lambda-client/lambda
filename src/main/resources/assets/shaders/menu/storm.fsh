/*
 * Original shader from: https://www.shadertoy.com/view/wsBGDW
 */

#ifdef GL_ES
precision mediump float;
#endif

// glslsandbox uniforms
uniform float time;
uniform vec2 resolution;
uniform vec2 mouse;

// shadertoy emulation
#define iTime time
#define iResolution resolution
vec4 iMouse = vec4(0.);

// --------[ Original ShaderToy begins here ]---------- //

// Returns the matrix that rotates a point by 'a' radians
mat2 mm2(in float a) {
    float c = cos(a);
    float s = sin(a);
    return mat2(c, s, -s, c);
}

// Returns the clamped version of the input
//float saturate(float t) {
//    return clamp(t, 0.0, 1.0);
//}

// ----------------------------
// ------ HASH FUNCTIONS ------
// ----------------------------

// Hash functions by Dave Hoskins: https://www.shadertoy.com/view/4djSRW

float hash12(vec2 p) {

    vec3 p3  = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 19.19);
    return fract((p3.x + p3.y) * p3.z);

}

float hash13(vec3 p3) {

    p3  = fract(p3 * 0.1031);
    p3 += dot(p3, p3.yzx + 19.19);
    return fract((p3.x + p3.y) * p3.z);

}

vec3 hash31(float p) {

    vec3 p3 = fract(vec3(p) * vec3(.1031, .1030, .0973));
    p3 += dot(p3, p3.yzx+19.19);
    return fract((p3.xxy+p3.yzz)*p3.zyx);

}

// -------------------------
// ------ VALUE NOISE ------
// -------------------------


float valueNoise(vec2 p) {

    vec2 i = floor(p);
    vec2 f = fract(p);

    f = f*f*f*(f*(f*6.0-15.0)+10.0);

    vec2 add = vec2(1.0, 0.0);
    float res = mix(
    mix(hash12(i + add.yy), hash12(i + add.xy), f.x),
    mix(hash12(i + add.yx), hash12(i + add.xx), f.x),
    f.y);
    return res;

}

float valueNoise(vec3 p) {

    vec3 i = floor(p);
    vec3 f = fract(p);

    f = f*f*f*(f*(f*6.0-15.0)+10.0);

    vec2 add = vec2(1.0, 0.0);
    float res = mix(
    mix(
    mix(hash13(i + add.yyy), hash13(i + add.xyy), f.x),
    mix(hash13(i + add.yxy), hash13(i + add.xxy), f.x),
    f.y),
    mix(
    mix(hash13(i + add.yyx), hash13(i + add.xyx), f.x),
    mix(hash13(i + add.yxx), hash13(i + add.xxx), f.x),
    f.y),
    f.z);
    return res;

}

    #define SPEED 1.0

float noise(vec2 p) {
    return valueNoise(p);
}

float fbm4(vec2 p, mat2 m) {
    float f = 0.0;
    f += 0.5000 * noise(p); p.xy = m*p.xy;
    f += 0.2500 * noise(p); p.xy = m*p.xy;
    f += 0.1250 * noise(p); p.xy = m*p.xy;
    f += 0.0625 * noise(p);
    return f/0.9375;
}

float fbm6(vec2 p, mat2 m) {
    float f = 0.0;
    f += 0.500000 * noise(p); p.xy = m*p.xy;
    f += 0.250000 * noise(p); p.xy = m*p.xy;
    f += 0.125000 * noise(p); p.xy = m*p.xy;
    f += 0.062500 * noise(p); p.xy = m*p.xy;
    f += 0.031250 * noise(p); p.xy = m*p.xy;
    f += 0.015625 * noise(p);
    return f/0.96875;
}

float warpedNoise(vec2 q) {

    float o1 = 0.25;
    float o2 = 2.0;
    float n1 = 0.5;
    float n2 = 7.0;
    float p1 = 4.0;
    float p2 = 4.0;

    float angle = 0.0;
    float scale = 3.24;

    mat2 m = mat2(cos(angle), sin(angle), -sin(angle), cos(angle)) * scale;

    vec2 o = vec2(0.0);
    o.x = o1 * fbm6(o2*q + vec2(19.2), m);
    o.y = o1 * fbm6(o2*q + vec2(15.7), m);

    vec2 n = vec2(0.0);
    n.x = n1 * fbm6(n2*o + vec2(23.3), m);
    n.y = n1 * fbm6(n2*o + vec2(31.3), m);

    vec2 p = p1*q + p2*n;

    float f = fbm4(p, m);

    return f;

}

vec4 planeCol(vec2 p, float r) {

    // Current position of the camera
    vec3 pos = vec3(0.0, 0.0, 1.0) * iTime * SPEED;

    // Get the noise val
    float val = warpedNoise(p + vec2(r * 100.0));

    // Put a hole through the planes
    float radius = 1.0;
    val += min(length(p) - radius, 0.2);

    // Add a 'stepped' pattern to the noise
    float steps = 10.0;
    val = floor(val * steps) / steps;

    // If its not a hole then return a color based on distance to the camera,
    // otherwise return a fully transparent one
    if (val > 0.01) {

        float dist = r - pos.z;
        float val = 1.0 / (1.0 + exp(-0.3 * (dist - 8.4)));
        vec3 col = vec3(val);

        // Add a pulse
        vec3 pulseColor = vec3(1.0, 1.0, 0.0);
        pulseColor = normalize(pulseColor);
        pulseColor = pulseColor * 0.5 * 25.0 * clamp(sin(iTime + 0.1 * r) - 0.96, 0.0, 1.0);
        col = pulseColor + col;

        return vec4(col, 1.0);

    }
    return vec4(val, val, val, 0.0);

}

vec3 render(vec3 ro, vec3 rd) {

    // Get the ray that moves from one plane to another
    vec3 rayStep = rd * (1.0 / rd.z);

    // Move the current position forward to the first plane
    float planeGap = 0.6;
    vec3 currentPos = ro;
    currentPos += rayStep * (ceil(ro.z / planeGap) * planeGap - ro.z);

    // Go through the planes
    const int noOfPlanes = 30;
    for (int i = 0; i < noOfPlanes; i++) {
        vec4 col = planeCol(currentPos.xy, currentPos.z);
        if (col.a > 0.001) {
            return col.rgb;
        }
        currentPos += rayStep * planeGap;
    }

    // If no planes were hit, return a background color
    return vec3(1.0, 1.0, 1.0);

}

void mainImage(out vec4 fragColor, in vec2 fragCoord) {

    // Normalises the fragCoord
    vec2 uv = fragCoord/iResolution.xy;
    vec2 p = uv - 0.5;
    p.x *= iResolution.x/iResolution.y;

    // Gets the direction of the ray and the origin
    vec3 ro = vec3(0.0, 0.0, 0.0) + vec3(0.0, 0.0, 1.0) * iTime * SPEED;
    vec3 rd = normalize(vec3(p, 1.4));

    // Rotates the ray depending on the mouse position. I lifted this from
    // https://www.shadertoy.com/view/XtGGRt, but it seems to be the common approach
    vec2 mo = iMouse.xy / iResolution.xy-.5;
    mo = (mo==vec2(-.5))?mo=vec2(0.0, -0.0):mo;// Default position of camera
    mo.x *= iResolution.x/iResolution.y;
    mo *= 0.5;
    rd.yz *= mm2(mo.y);
    rd.xz *= mm2(mo.x);

    // Render the ray
    if (rd.z > 0.0) {
        vec3 col = render(ro, rd);
        fragColor = vec4(col, 1.0);
    }

    else {
        fragColor = vec4(1.0);
    }

}
// --------[ Original ShaderToy ends here ]---------- //

void main(void)
{
    iMouse = vec4(mouse * resolution, 0., 0.);
    mainImage(gl_FragColor, gl_FragCoord.xy);
}