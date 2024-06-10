package com.lambda.config.groups

interface BuildConfig {
    val breakCoolDown: Int
    val placeCooldown: Int
    val placeConfirmation: Boolean
    val breakConfirmation: Boolean
    val collectDrops: Boolean
    val breakWeakBlocks: Boolean
    val pathing: Boolean
    val breaksPerTick: Int
    val rotateForBreak: Boolean
    val rotateForPlace: Boolean
    val swingHand: Boolean
}