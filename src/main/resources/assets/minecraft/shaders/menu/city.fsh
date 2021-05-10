//--- skyline ---
// by Catzpaw 2016
#ifdef GL_ES
precision mediump float;
#endif

//#extension GL_OES_standard_derivatives : enable

uniform float time;
uniform vec2 mouse;
uniform vec2 resolution;

//hash
float hash(vec2 p){return fract(sin(p.x*733.7+p.y*63.6)*5.5);}
vec2 hash2(vec2 p){return vec2(hash(p*484.4),hash(373.3*p.yx+22.2))-.5;}

//2d perlin noise
vec2 acc=vec2(1,0);
vec2 noise2(vec2 v){vec2 p=floor(v);vec2 f=fract(v);f=f*f*(3.0-2.0*f);
	vec2 n=mix(mix(hash2(p),hash2(p+acc.xy),f.x),mix(hash2(p+acc.yx),hash2(p+acc.xx),f.x),f.y);
	return n;}
vec2 pnoise(vec2 v){vec2 n=vec2(0);float s=1.0;
	for(int i=0;i<6;i++){n+=abs(noise2(v)+.5 )*s;v*=2.;s*=.5;}
	return n;}

//buildings
vec3 rect(vec2 p,vec2 s,vec2 e){
	float r=0.5+abs(sin(hash(e)*100.+time*1.5));
	if(e.y>.1&&length(p-vec2(e.x,e.y))<=0.004)return vec3(r,0,0);
	if(e.y>.1&&length(p-vec2(s.x,e.y))<=0.004)return vec3(r,0,0);
	if(p.x<min(s.x,e.x)||p.x>max(s.x,e.x))return vec3(-1);
	if(p.y<min(s.y,e.y)||p.y>max(s.y,e.y))return vec3(-1);
	if(p.x<e.x-.006&&p.x>s.x+.006&&p.y<e.y-.006&&sin(p.y*400.+e.y*12.)>0.6&&hash(p)<.7)return vec3(.5,.4,.4);
	return vec3(0);}
vec3 rects(vec2 p,float w,float h,float d){p.x=mod(p.x,d);
	return rect(p,vec2(0.,0.),vec2(w,h));}

//main
void main()
{
	vec2 uv=(gl_FragCoord.xy*2.-resolution.xy)/min(resolution.x,resolution.y);
	float lines=120.0;
	uv.x=floor(uv.x*lines)/lines;
	uv.y=floor(uv.y*lines)/lines;
	vec3 finalColor=vec3(0);
	float c = 0.;

	float waterline=-0.4;
	float wind=time/8.0;

	uv.y+=waterline;

	//water
	if(uv.y<0.){
		uv.y+=sin(hash(uv))*.01+sin(uv.x*4.+50./uv.y+time*3.)*.06+sin(-uv.x*7.+70./uv.y+time*3.)*.06;
	}

	//sky
	finalColor=mix(vec3(0.8,0.4,0.6),vec3(0.1,0.3,0.5),floor(abs(uv.y*8.)+sin(uv.y*lines*2.)*.4)/8.);
	//finalColor+=floor(abs(1.-clamp(abs(uv.y)*3.,0.,1.))*4.)/8.;

	//star
	c=hash2(uv).x;
	if(c>0.495&&uv.y>0.)finalColor+=floor(abs(uv.y)*clamp(length(uv+vec2(0.3,-0.6))*.6,0.,1.)*5.)*.2;

	//moon
	float l=length(vec2(uv.x,abs(uv.y))+vec2(0.3,-0.6));
	c=floor(max(fract(smoothstep(l,0.1,0.099)),fract(smoothstep(l,0.5,0.35))*.5)*7.)*.1;
	finalColor+=c*vec3(.8,.9,.9);

	//cloud
	float dist=4.-log2(pow(abs(uv.y),1.0))*2.0;
	vec2 p=vec2(uv.x*dist*.4+wind,abs(uv.y)*dist*5.);
	vec2 n=pnoise(p);
	c = floor((2.1-n.x-n.y)*abs(uv.y*7.));
	finalColor+= clamp(c,0.,.5)*vec3(0.6,0.8,0.9);

	//skyline
	vec3 color=vec3(-1);
	color=max(color,rects(uv+vec2(0.1,0),0.07,0.21,0.33));
	color=max(color,rects(uv+vec2(0.2,0),0.08,0.19,0.38));
	color=max(color,rects(uv+vec2(-.2,0),0.05,0.10,0.26));
	color=max(color,rects(uv+vec2(0.3,0),0.05,0.11,0.23));
	color=max(color,rects(uv+vec2(0.5,0),0.07,0.13,0.27));
	color=max(color,rects(uv+vec2(-.1,0),0.18,0.05,0.31));
	color=max(color,rects(uv+vec2(0.0,0),0.21,0.02,0.20));
	if(color.b>=0.)finalColor=color;

	//street
	if(abs(uv.y)<.01&&hash(uv)<.2)finalColor=vec3(.6,.6,.5);
	if(abs(uv.y)<.005&&hash(vec2(floor(time*3.),uv.x*100.))<.2)finalColor=vec3(sin(uv.x*50.+time),.5,.5);

	gl_FragColor = vec4(finalColor, 1.0);
}
