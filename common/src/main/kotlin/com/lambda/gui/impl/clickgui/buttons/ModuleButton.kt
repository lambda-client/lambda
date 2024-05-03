package com.lambda.gui.impl.clickgui.buttons

import com.lambda.graphics.animation.Animation.Companion.exp
import com.lambda.gui.api.GuiEvent
import com.lambda.gui.api.component.WindowComponent
import com.lambda.gui.api.component.button.ListButton
import com.lambda.gui.impl.clickgui.windows.SettingWindow
import com.lambda.module.Module
import com.lambda.module.modules.client.GuiSettings
import com.lambda.util.Mouse
import com.lambda.util.math.ColorUtils.multAlpha
import com.lambda.util.math.MathUtils.lerp
import com.lambda.util.math.Vec2d
import com.mojang.blaze3d.systems.RenderSystem.recordRenderCall
import kotlin.math.abs

class ModuleButton(val module: Module, owner: WindowComponent<*>) : ListButton(owner) {
    override val text get() = module.name
    private val active get() = module.isEnabled
    private val gui = owner.owner

    override var activeAnimation by animation.exp(0.0, 1.0, 0.15, ::active)
    private var toggleFxDirection by animation.exp(0.0, 1.0, 0.7, ::active)

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

        if (e is GuiEvent.Show) toggleFxDirection = 0.0
    }

    override fun performClickAction(e: GuiEvent.MouseClick) {
        when (e.button) {
            Mouse.Button.Left -> if (hovered) module.toggle()
            Mouse.Button.Right -> { // Open new settings window
                gui.apply {
                    val check = windows.children
                        .filterIsInstance<SettingWindow>()
                        .filter { it.button.module == module }
                        .onEach { it.isOpen = false }
                        .isNotEmpty()

                    if (check) return

                    // we have to wait this tag window to be focused after handling a click event
                    // to place settings window over it
                    recordRenderCall {
                        SettingWindow(this@ModuleButton, this).apply {
                            forceSetPosition(e.mouse)
                        }.apply(windows::addChild)
                    }
                }
            }
        }
    }

    override fun equals(other: Any?) =
        (other as? ModuleButton)?.module == module

    override fun hashCode() =
        module.hashCode()
}