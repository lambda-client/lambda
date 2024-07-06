package com.lambda.gui.api.component.core

import com.lambda.util.math.MathUtils.coerceIn
import com.lambda.util.math.Rect
import com.lambda.util.math.Vec2d

abstract class DockingRect {
    abstract var relativePos: Vec2d
    protected abstract val width: Double
    protected abstract val height: Double
    protected val size get() = Vec2d(width, height)

    val rect get() = Rect.basedOn(position, size)
    open val dockingBase get() = rect.center

    open val autoDocking = false
    open val allowHAlign = true
    open val allowVAlign = true

    open var dockingH = HAlign.LEFT; set(to) {
        val from = field
        field = to

        val delta = to.multiplier - from.multiplier
        relativePos += Vec2d.RIGHT * delta * (size.x - screenSize.x)
    }

    open var dockingV = VAlign.TOP; set(to) {
        val from = field
        field = to

        val delta = to.multiplier - from.multiplier
        relativePos += Vec2d.BOTTOM * delta * (size.y - screenSize.y)
    }

    var screenSize: Vec2d = Vec2d.ZERO

    var position
        get() = relativeToAbs(relativePos).coerceIn(0.0, screenSize.x - size.x, 0.0, screenSize.y - size.y)
        set(value) { relativePos = absToRelative(value); if (autoDocking) autoDocking() }

    private val dockingOffset get() = (screenSize - size) * Vec2d(dockingH.multiplier, dockingV.multiplier)

    private fun relativeToAbs(posIn: Vec2d) = posIn + dockingOffset
    private fun absToRelative(posIn: Vec2d) = posIn - dockingOffset

    fun autoDocking() {
        val screenCenterX = (screenSize.x * 0.3333)..(screenSize.x * 0.6666)
        val screenCenterY = (screenSize.y * 0.3333)..(screenSize.y * 0.6666)

        val drawableCenter = dockingBase

        dockingH = if (allowHAlign) {
            when {
                drawableCenter.x < screenCenterX.start -> HAlign.LEFT
                drawableCenter.x > screenCenterX.endInclusive -> HAlign.RIGHT
                else -> HAlign.CENTER
            }
        } else HAlign.LEFT

        dockingV = if (allowVAlign) {
            when {
                drawableCenter.y < screenCenterY.start -> VAlign.TOP
                drawableCenter.y > screenCenterY.endInclusive -> VAlign.BOTTOM
                else -> VAlign.CENTER
            }
        } else VAlign.TOP
    }

    enum class HAlign(val multiplier: Double) {
        LEFT(0.0),
        CENTER(0.5),
        RIGHT(1.0)
    }

    enum class VAlign(val multiplier: Double) {
        TOP(0.0),
        CENTER(0.5),
        BOTTOM(1.0)
    }
}