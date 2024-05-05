package com.lambda.gui.impl.clickgui.buttons

import com.lambda.graphics.animation.Animation.Companion.exp
import com.lambda.graphics.gl.Scissor.scissor
import com.lambda.gui.api.GuiEvent
import com.lambda.gui.api.component.button.ListButton
import com.lambda.gui.api.component.core.list.ChildLayer
import com.lambda.gui.api.layer.RenderLayer
import com.lambda.module.Module
import com.lambda.module.modules.client.GuiSettings
import com.lambda.util.Mouse
import com.lambda.util.math.ColorUtils.multAlpha
import com.lambda.util.math.MathUtils.lerp
import com.lambda.util.math.MathUtils.toInt
import com.lambda.util.math.Vec2d
import kotlin.math.abs

class ModuleButton(val module: Module, owner: ChildLayer.Drawable<*>) : ListButton(owner) {
    override val text get() = module.name
    private val active get() = module.isEnabled

    override val size: Vec2d get() = super.size + Vec2d(0.0, renderHeight)
    override val textY: Double get() = super.size.y * 0.5

    override var activeAnimation by animation.exp(0.0, 1.0, 0.15, ::active)
    private val toggleFxDirection by animation.exp(0.0, 1.0, 0.7, ::active)

    private var isOpen = false
    override val isActive get() = isOpen

    private var settingsHeight = 0.0
    private var renderHeight by animation.exp(::settingsHeight, 0.6)
    private val settingsRect get() = rect.moveFirst(Vec2d(0.0, super.size.y))

    private val settingsRenderer = RenderLayer()
    private val settingsLayer = ChildLayer.Drawable<SettingButton<*, *>>(owner.gui, this, settingsRenderer, ::settingsRect)

    init {
        // Toggle fx
        renderer.filled {
            val left  = rect - Vec2d(rect.size.x, 0.0)
            val right = rect + Vec2d(rect.size.x, 0.0)

            position = lerp(left, right, activeAnimation)
                .clamp(rect)
                .shrink(interactAnimation)

            // 0.0 .. 1.0 .. 0.0 animation
            val alpha = 1.0 - (abs(activeAnimation - 0.5) * 2.0)
            val color = GuiSettings.mainColor.multAlpha(alpha * 0.6 * showAnimation)

            // "Tail" effect
            val leftColor  = color.multAlpha(1.0 - toggleFxDirection)
            val rightColor = color.multAlpha(toggleFxDirection)

            shade = GuiSettings.shade
            colorH(leftColor, rightColor)
        }
    }

    override fun onEvent(e: GuiEvent) {
        super.onEvent(e)

        when (e) {
            is GuiEvent.Show -> {
                isOpen = false
                renderHeight = 0.0
            }

            is GuiEvent.Tick -> {
                updateSettingsHeight()
            }

            is GuiEvent.Render -> {
                scissor(settingsRect) {
                    settingsRenderer.render()
                    settingsLayer.onEvent(e)
                }
                return
            }
        }
    }

    private fun updateSettingsHeight() {
        /*val c = settingsLayer.children
        settingsHeight = c.sumOf { it.size.y } + ((c.size - 1) * ClickGui.buttonStep).coerceAtLeast(0.0)*/

        settingsHeight = 30.0 * isOpen.toInt()
    }

    override fun performClickAction(e: GuiEvent.MouseClick) {
        when (e.button) {
            Mouse.Button.Left -> module.toggle()
            Mouse.Button.Right -> {
                // Don't let user spam
                val targetHeight = if (isOpen) settingsHeight else 0.0
                if (abs(targetHeight - renderHeight) > 1) return

                isOpen = !isOpen
                updateSettingsHeight()
            }
        }
    }

    override fun equals(other: Any?) =
        (other as? ModuleButton)?.module == module

    override fun hashCode() =
        module.hashCode()
}