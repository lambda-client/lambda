package com.lambda.config.groups

interface BuildConfig {
    val collectDrops: Boolean
    val breakWeakBlocks: Boolean
    val pathing: Boolean
    val interactLimit: Int
    val breakInstantAtOnce: Boolean
    val rotateForBreak: Boolean
    val swingHand: Boolean
    val particlesOnBreak: Boolean
}