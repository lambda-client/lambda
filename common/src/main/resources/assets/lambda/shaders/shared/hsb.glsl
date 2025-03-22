vec3 hsb2rgb(vec3 hsb) {
    float C = hsb.z * hsb.y;
    float X = C * (1.0 - abs(mod(hsb.x / 60.0, 2.0) - 1.0));
    float m = hsb.z - C;

    vec3 rgb;

    if (0.0 <= hsb.x && hsb.x < 60.0) {
        rgb = vec3(C, X, 0.0);
    } else if (60.0 <= hsb.x && hsb.x < 120.0) {
        rgb = vec3(X, C, 0.0);
    } else if (120.0 <= hsb.x && hsb.x < 180.0) {
        rgb = vec3(0.0, C, X);
    } else if (180.0 <= hsb.x && hsb.x < 240.0) {
        rgb = vec3(0.0, X, C);
    } else if (240.0 <= hsb.x && hsb.x < 300.0) {
        rgb = vec3(X, 0.0, C);
    } else {
        rgb = vec3(C, 0.0, X);
    }

    return (rgb + vec3(m));
}#

float hue(vec2 uv) {
    vec2 centered = uv * 2.0 - 1.0;
    float hue = degrees(atan(centered.y, centered.x)) + 180.0;

    return hue;
}#