package com.lambda.module

import com.lambda.event.events.RenderEvent
import com.lambda.event.listener.SafeListener.Companion.listener
import com.lambda.gui.api.RenderLayer
import com.lambda.module.tag.ModuleTag
import com.lambda.util.KeyCode
import com.lambda.util.math.Rect
import com.lambda.util.math.Vec2d

abstract class HudModule(
    name: String,
    description: String = "",
    alwaysListening: Boolean = false,
    enabledByDefault: Boolean = false,
    defaultKeybind: KeyCode = KeyCode.UNBOUND,
) : Module(name, description, setOf(ModuleTag.HUD), alwaysListening, enabledByDefault, defaultKeybind) {
    private val renderCallables = mutableListOf<RenderLayer.() -> Unit>()

    protected abstract val width: Double
    protected abstract val height: Double
    private val size get() = Vec2d(width, height)

    private var relativePosX by setting("Position X", 0.0, -10000.0..10000.0, 0.1) { false }
    private var relativePosY by setting("Position Y", 0.0, -10000.0..10000.0, 0.1) { false }
    private var relativePos get() = Vec2d(relativePosX, relativePosY); set(value) {
        val vec = absToRelative(relativeToAbs(value))
        relativePosX = vec.x; relativePosY = vec.y
    }

    var position get() = relativeToAbs(relativePos).let {
        val x = it.x.coerceIn(0.0, screenSize.x - width)
        val y = it.y.coerceIn(0.0, screenSize.y - height)
        Vec2d(x, y)
    }; set(value) { relativePos = absToRelative(value) }

    private fun relativeToAbs(posIn: Vec2d) = posIn + (screenSize - size) * dockingMultiplier
    private fun absToRelative(posIn: Vec2d) = posIn - (screenSize - size) * dockingMultiplier

    private val dockingH by setting("Docking H", HAlign.LEFT).apply {
        onValueChange { from, to ->
            val delta = to.multiplier - from.multiplier
            relativePosX += delta * (size.x - screenSize.x)
        }
    }
    private val dockingV by setting("Docking V", VAlign.TOP).apply {
        onValueChange { from, to ->
            val delta = to.multiplier - from.multiplier
            relativePosY += delta * (size.y - screenSize.y)
        }
    }

    private val dockingMultiplier get() = Vec2d(dockingH.multiplier, dockingV.multiplier)

    val rect get() = Rect.basedOn(position, width, height)

    private var screenSize = Vec2d.ZERO
    private val renderer = RenderLayer()

    protected fun onRender(block: RenderLayer.() -> Unit) =
        renderCallables.add(block)

    init {
        listener<RenderEvent.GUI.HUD> { event ->
            screenSize = event.screenSize

            renderCallables.forEach { function ->
                function.invoke(renderer)
            }

            renderer.render()
        }
    }

    @Suppress("UNUSED")
    enum class HAlign(val multiplier: Float) {
        LEFT(0.0f),
        CENTER(0.5f),
        RIGHT(1.0f)
    }

    @Suppress("UNUSED")
    enum class VAlign(val multiplier: Float) {
        TOP(0.0f),
        CENTER(0.5f),
        BOTTOM(1.0f)
    }
}