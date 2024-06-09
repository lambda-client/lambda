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

    private var px by setting("Position X", 10.0, 0.0..10000.0, 1.0) { false }
    private var py by setting("Position Y", 10.0, 0.0..10000.0, 1.0) { false }

    var position get() = Vec2d(px, py); set(value) { px = value.x; py = value.y }
    val rect get() = Rect.basedOn(position, width, height)

    private val renderer = RenderLayer()

    protected fun onRender(block: RenderLayer.() -> Unit) =
        renderCallables.add(block)

    init {
        listener<RenderEvent.GUI.HUD> { event ->
            renderCallables.forEach { function ->
                function.invoke(renderer)
            }

            renderer.render()
        }
    }
}