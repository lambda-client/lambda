package com.lambda.module

import com.lambda.event.events.RenderEvent
import com.lambda.event.listener.SafeListener.Companion.listener
import com.lambda.gui.api.RenderLayer
import com.lambda.module.tag.ModuleTag
import com.lambda.util.KeyCode
import com.lambda.util.math.MathUtils.coerceIn
import com.lambda.util.math.Rect
import com.lambda.util.math.Vec2d

abstract class HudModule(
    name: String,
    description: String = "",
    defaultTags: Set<ModuleTag> = setOf(),
    alwaysListening: Boolean = false,
    enabledByDefault: Boolean = false,
    defaultKeybind: KeyCode = KeyCode.UNBOUND,
) : Module(name, description, defaultTags, alwaysListening, enabledByDefault, defaultKeybind) {
    private val renderCallables = mutableListOf<RenderLayer.() -> Unit>()

    protected abstract val width: Double
    protected abstract val height: Double
    private val size get() = Vec2d(width, height)

    private var relativePosX by setting("Position X", 0.0, -10000.0..10000.0, 0.1) { false }
    private var relativePosY by setting("Position Y", 0.0, -10000.0..10000.0, 0.1) { false }
    private var relativePos get() = Vec2d(relativePosX, relativePosY)
        set(value) { relativePosX = value.x; relativePosY = value.y }

    var position
        get() = relativeToAbs(relativePos).coerceIn(0.0, screenSize.x - width, 0.0, screenSize.y - height)
        set(value) { relativePos = absToRelative(value); if (autoDocking) autoDocking() }

    private val dockingOffset get() = (screenSize - size) * Vec2d(dockingH.multiplier, dockingV.multiplier)

    private fun relativeToAbs(posIn: Vec2d) = posIn + dockingOffset
    private fun absToRelative(posIn: Vec2d) = posIn - dockingOffset

    private val autoDocking by setting("Auto Docking", true).apply {
        onValueChange { _, _ ->
            autoDocking()
        }
    }

    private var dockingH by setting("Docking H", HAlign.LEFT) { !autoDocking }.apply {
        onValueChange { from, to ->
            val delta = to.multiplier - from.multiplier
            relativePosX += delta * (size.x - screenSize.x)
        }
    }

    private var dockingV by setting("Docking V", VAlign.TOP) { !autoDocking }.apply {
        onValueChange { from, to ->
            val delta = to.multiplier - from.multiplier
            relativePosY += delta * (size.y - screenSize.y)
        }
    }

    val rect get() = Rect.basedOn(position, width, height)

    private var screenSize = Vec2d.ZERO
    private val renderer = RenderLayer()

    protected fun onRender(block: RenderLayer.() -> Unit) =
        renderCallables.add(block)

    init {
        listener<RenderEvent.GUI.Scaled> { event ->
            screenSize = event.screenSize

            renderCallables.forEach { function ->
                function.invoke(renderer)
            }

            renderer.render()
        }
    }

    private fun autoDocking() {
        val screenCenterX = (screenSize.x * 0.3333)..(screenSize.x * 0.6666)
        val screenCenterY = (screenSize.y * 0.3333)..(screenSize.y * 0.6666)

        val drawableCenter = rect.center

        dockingH = when {
            drawableCenter.x < screenCenterX.start -> HAlign.LEFT
            drawableCenter.x > screenCenterX.endInclusive -> HAlign.RIGHT
            else -> HAlign.CENTER
        }

        dockingV = when {
            drawableCenter.y < screenCenterY.start -> VAlign.TOP
            drawableCenter.y > screenCenterY.endInclusive -> VAlign.BOTTOM
            else -> VAlign.CENTER
        }
    }

    enum class HAlign(val multiplier: Float) {
        LEFT(0.0f),
        CENTER(0.5f),
        RIGHT(1.0f)
    }

    enum class VAlign(val multiplier: Float) {
        TOP(0.0f),
        CENTER(0.5f),
        BOTTOM(1.0f)
    }
}