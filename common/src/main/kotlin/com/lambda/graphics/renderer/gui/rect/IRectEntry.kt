package com.lambda.graphics.renderer.gui.rect

import com.lambda.graphics.renderer.IRenderEntry
import com.lambda.util.math.Rect
import java.awt.Color

interface IRectEntry <T : IRenderEntry<T>> : IRenderEntry<T> {
    var position: Rect
    var roundRadius: Double
    var shade: Boolean

    fun color(leftTop: Color, rightTop: Color, rightBottom: Color, leftBottom: Color)
    fun color(color: Color) = color(color, color, color, color)
    fun colorH(left: Color, right: Color) = color(left, right, right, left)
    fun colorV(top: Color, bottom: Color) = color(top, top, bottom, bottom)

    interface Filled : IRectEntry<Filled>

    interface Outline : IRectEntry<Outline> {
        var outerGlow: Double
        var innerGlow: Double
    }
}