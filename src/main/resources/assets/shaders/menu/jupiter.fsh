#ifdef GL_ES
precision mediump float;
#endif

uniform vec2 resolution;
uniform float time;
uniform vec2 mouse;

const float color_intensity = 0.45;

const float Pi = 3.14159;

void main()
{
    vec2 p=(2.0*gl_FragCoord.xy-resolution)/max(resolution.x, resolution.y);
    for (int i=1;i<64;i++)
    {
        vec2 newp=p;
        newp.x+=1./float(i)*sin(float(i)*.5*p.y+time*.1)+1.;
        newp.y+=1./float(i)*cos(float(i)*.5*p.x+time*.1)-1.;
        p=newp;
    }
    vec3 col=vec3(sin(p.x+p.y)*.5+.5, sin(p.x+p.y+6.)*.5+.5, sin(p.x+p.y+12.)*.5+.5);
    gl_FragColor=vec4(col, 1.0);
}
