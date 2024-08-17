package com.lambda.config.groups

interface TargetingConfig {
    val targetingRange: Double

    val players: Boolean
    val hostiles: Boolean
    val animals: Boolean

    val invisible: Boolean
    val dead: Boolean
}