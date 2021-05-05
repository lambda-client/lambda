package com.lambda.client.util.graphics

@Suppress("UNUSED")
enum class Shaders(val s: String) {
    BLUEGRID("/assets/minecraft/shaders/menu/bluegrid.fsh"),
    BLUENEBULA("/assets/minecraft/shaders/menu/bluenebula.fsh"),
    BLUEVORTEX("/assets/minecraft/shaders/menu/bluevortex.fsh"),
    CAVE("/assets/minecraft/shaders/menu/cave.fsh"),
    CITY("/assets/minecraft/shaders/menu/city.fsh"),
    CLOUDS("/assets/minecraft/shaders/menu/clouds.fsh"),
    DJ("/assets/minecraft/shaders/menu/dj.fsh"),
    ICYFIRE("/assets/minecraft/shaders/menu/icyfire.fsh"),
    JUPITER("/assets/minecraft/shaders/menu/jupiter.fsh"),
    MATRIX("/assets/minecraft/shaders/menu/matrix.fsh"),
    MATRIXRED("/assets/minecraft/shaders/menu/matrixred.fsh"),
    MINECRAFT("/assets/minecraft/shaders/menu/minecraft.fsh"),
    PURPLEGRID("/assets/minecraft/shaders/menu/purplegrid.fsh"),
    PURPLEMIST("/assets/minecraft/shaders/menu/purplemist.fsh"),
    REDGLOW("/assets/minecraft/shaders/menu/redglow.fsh"),
    SKY("/assets/minecraft/shaders/menu/sky.fsh"),
    SNAKE("/assets/minecraft/shaders/menu/snake.fsh"),
    SPACE("/assets/minecraft/shaders/menu/space.fsh"),
    SPACE2("/assets/minecraft/shaders/menu/space2.fsh"),
    STORM("/assets/minecraft/shaders/menu/storm.fsh"),
    SUNSETWAVES("/assets/minecraft/shaders/menu/sunsetwaves.fsh"),
    TRIANGLE("/assets/minecraft/shaders/menu/triangle.fsh");

    fun get(): String {
        return s
    }
}