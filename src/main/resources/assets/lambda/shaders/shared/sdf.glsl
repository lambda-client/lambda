float sdf(float channel, float min, float max) {
    return 1.0 - smoothstep(min, max, 1.0 - channel);
}#