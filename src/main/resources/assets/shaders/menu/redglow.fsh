#ifdef GL_ES
precision highp float;
#endif

uniform float time;
uniform vec2 mouse;
uniform vec2 resolution;

const float COUNT = 10.0;

//MythicalFire by CuriousChettai@gmail.com

void main(void) {
    vec2 uPos = (gl_FragCoord.xy / resolution.y);//normalize wrt y axis
    uPos -= vec2((resolution.x/resolution.y)/2.0, 0.5);//shift origin to center

    float y = uPos.y;

    float vertColor = 0.0;
    for (float i=0.0; i<COUNT; i++){
        float t = time + (i+0.3);

        uPos.x += sin(-t+uPos.y*11.0+cos(t/1.0))*0.2 * cos(t+uPos.x*20.0)*0.2;
        uPos.y += sin(-t+uPos.x*10.0)*0.2 - t/20.0;


        float stripColor = 1.0/uPos.x/20.0;

        vertColor += abs(stripColor);
    }

    float temp = vertColor;//*(y-0.7);
    vec3 color = vec3(temp*0.9, temp*0.1, temp*0.1*abs(y-1.0)) * 0.7;
    color *= color.r+color.g+color.b;
    gl_FragColor = vec4(color, 1.0);
}