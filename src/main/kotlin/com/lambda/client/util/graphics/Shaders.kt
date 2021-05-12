package com.lambda.client.util.graphics

@Suppress("UNUSED")
enum class Shaders(val s: String) {
    BLUEGRID("/assets/minecraft/shaders/menu/bluegrid.fsh"),
    BLUENEBULA("/assets/minecraft/shaders/menu/bluenebula.fsh"),
    BLUEVORTEX("/assets/minecraft/shaders/menu/bluevortex.fsh"),
    CAVE("/assets/minecraft/shaders/menu/cave.fsh"),
    CLOUDS("/assets/minecraft/shaders/menu/clouds.fsh"),
    DOUGHNUTS("/assets/minecraft/shaders/menu/doughnuts.fsh"),
    FIRE("/assets/minecraft/shaders/menu/fire.fsh"),
    JUPITER("/assets/minecraft/shaders/menu/jupiter.fsh"),
    MATRIX("/assets/minecraft/shaders/menu/matrix.fsh"),
    MINECRAFT("/assets/minecraft/shaders/menu/minecraft.fsh"),
    PURPLEGRID("/assets/minecraft/shaders/menu/purplegrid.fsh"),
    PURPLEMIST("/assets/minecraft/shaders/menu/purplemist.fsh"),
    REDGLOW("/assets/minecraft/shaders/menu/redglow.fsh"),
    SKY("/assets/minecraft/shaders/menu/sky.fsh"),
    SNAKE("/assets/minecraft/shaders/menu/snake.fsh"),
    SPACE("/assets/minecraft/shaders/menu/space.fsh"),
    SPACE2("/assets/minecraft/shaders/menu/space2.fsh"),
    STORM("/assets/minecraft/shaders/menu/storm.fsh"),
    TRIANGLE("/assets/minecraft/shaders/menu/triangle.fsh");

    fun get(): String {
        return s
    }
}