package com.lambda.newgui.impl.clickgui

import com.lambda.graphics.animation.Animation.Companion.exp
import com.lambda.module.Module
import com.lambda.module.modules.client.NewCGui
import com.lambda.newgui.component.HAlign
import com.lambda.newgui.component.VAlign
import com.lambda.newgui.component.core.UIBuilder
import com.lambda.newgui.component.layout.Layout
import com.lambda.newgui.component.window.Window
import com.lambda.util.Mouse
import com.lambda.util.math.Vec2d
import com.lambda.util.math.lerp
import com.lambda.util.math.setAlpha
import java.awt.Color

class ModuleLayout(
    owner: Layout,
    module: Module
) : Window(
    owner,
    module.name,
    Vec2d.ZERO, Vec2d.ZERO,
    false, false, true, false,
    AutoResize.Disabled, // ToDo: should be ForceEnabled, temporarily using this mode to set the height manually
    true
) {
    private val animation = animationTicker()
    private val cursorController = cursorController()

    private var enableAnimation by animation.exp(0.0, 1.0, 0.6, module::isEnabled)

    init {
        minimized = true
        height = 100.0

        overrideX { owner.renderPositionX + NewCGui.padding }
        overrideWidth { owner.renderWidth - NewCGui.padding * 2 }

        with(titleBar) {
            with(textField) {
                bold = false
                horizontalAlignment = HAlign.LEFT

                onUpdate {
                    positionX = titleBar.renderPositionX + (titleBar.renderHeight - textHeight) * 0.5
                }
            }

            onMouseClick { button, action ->
                if (button == Mouse.Button.Left && action == Mouse.Action.Click) {
                    module.toggle()
                }
            }
        }

        onShow {
            enableAnimation = 0.0
        }

        titleBarRect.onUpdate {
            setColor(lerp(enableAnimation, DISABLED_COLOR, NewCGui.titleBackgroundColor))
        }

        contentRect.onUpdate {
            setColor(lerp(enableAnimation, DISABLED_COLOR, NewCGui.backgroundColor))
        }

        outlineRect.onUpdate {
            setColor(lerp(enableAnimation, DISABLED_COLOR, NewCGui.outlineColor))
        }

        onTick {
            val cursor = if (titleBar.isHovered) Mouse.Cursor.Pointer else Mouse.Cursor.Arrow
            cursorController.setCursor(cursor)
        }

    }

    companion object {
        /**
         * Creates a [ModuleLayout] - visual representation of the [Module]
         */
        @UIBuilder
        fun Layout.moduleLayout(module: Module) =
            ModuleLayout(this, module).apply(children::add)

        private val DISABLED_COLOR = Color.BLACK.setAlpha(0.1)
    }
}