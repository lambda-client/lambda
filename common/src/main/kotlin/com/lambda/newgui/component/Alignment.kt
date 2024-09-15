package com.lambda.newgui.component

enum class HAlign(val multiplier: Double, val offset: Double) {
    LEFT(0.0, -1.0),
    CENTER(0.5, 0.0),
    RIGHT(1.0, 1.0)
}

enum class VAlign(val multiplier: Double, val offset: Double) {
    TOP(0.0, -1.0),
    CENTER(0.5, 0.0),
    BOTTOM(1.0, 1.0)
}