package com.lambda.graphics.animation

class AnimationTicker {
    private val animations = mutableListOf<Animation>()

    fun register(animation: Animation) =
        animations.add(animation)

    fun tick() =
        animations.forEach(Animation::tick)
}