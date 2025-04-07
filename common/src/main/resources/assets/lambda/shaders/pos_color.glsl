attributes {
    vec4 pos;
    vec4 color;
};

export {
    vec4 v_Color; # color
};

void fragment() {
    color = v_Color;
}#