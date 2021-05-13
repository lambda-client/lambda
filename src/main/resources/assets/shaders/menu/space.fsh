#ifdef GL_ES
precision mediump float;
#endif

uniform float time;

uniform vec2 resolution;



float field(in vec3 p,float s,  int idx) {
   float strength = 7. + .03 * log(1.e-6 + fract(sin(time) * 4373.11));
   float accum = s/4.;
   float prev = 0.;
   float tw = 0.;
   for (int i = 0; i < 26; ++i) {
      float mag = dot(p, p);
      p = abs(p) / mag + vec3(-.5, -.4, -1.5);
      float w = exp(-float(i) / 7.);
      accum += w * exp(-strength * pow(abs(mag - prev), 2.2));
      tw += w;
      prev = mag;
   }
   return max(0., 5. * accum / tw - .7);
}


vec3 nrand3( vec2 co )
{
   vec3 a = fract( cos( co.x*8.3e-3 + co.y )*vec3(1.3e5, 4.7e5, 2.9e5) );
   vec3 b = fract( sin( co.x*0.3e-3 + co.y )*vec3(8.1e5, 1.0e5, 0.1e5) );
   vec3 c = mix(a, b, 0.5);
   return c;
}


void main()

{
    vec2 uv = 2. * gl_FragCoord.xy / resolution.xy - 1.;
   vec2 uvs = uv * resolution.xy / max(resolution.x, resolution.y);
   vec3 p = vec3(uvs / 4., 0) + vec3(1., -1.3, 0.);
   p += .2 * vec3(sin(time / 16.), sin(time / 12.),  sin(time / 128.));


   float freqs[4];
   freqs[0] = 0.05;
   freqs[1] = 0.3;
   freqs[2] = 0.3;
   freqs[3] = 0.7;

   float t = field(p,freqs[3], 26);
   float v = (1. - exp((abs(uv.x) - 1.) * 6.)) * (1. - exp((abs(uv.y) - 1.) * 6.));

    //Second Layer
   vec3 p2 = vec3(uvs / (4.+sin(time*0.11)*0.2+0.2+sin(time*0.15)*0.3+0.4), 1.5) + vec3(2., -1.3, -1.);
   p2 += 0.25 * vec3(sin(time / 16.), sin(time / 12.),  sin(time / 128.));
   float t2 = field(p2,freqs[3], 18);
   vec4 c2 =  mix(.2, 0.2, v) * vec4(1.3 * t2 * t2 * t2 ,1.8  * t2 * t2 , t2* freqs[0], t2);


   //Let's add some stars
   //Thanks to http://glsl.heroku.com/e#6904.0
   vec2 seed = p.xy * 2.0;
   seed = floor(seed * resolution.x);
   vec3 rnd = nrand3( seed );
   vec4 starcolor = vec4(pow(rnd.y,20.0));

   //Second Layer
   vec2 seed2 = p2.xy * 3.0;
   seed2 = floor(seed2 * resolution.x);
   vec3 rnd2 = nrand3( seed2 );
   starcolor += vec4(pow(rnd2.y,40.0));

   gl_FragColor = mix(freqs[3]-.5, 1.,1.0) * vec4(1.5*freqs[2] * t * t* t , 1.2*freqs[1] * t * t, freqs[3]*t, 1.0) +c2+starcolor;
}