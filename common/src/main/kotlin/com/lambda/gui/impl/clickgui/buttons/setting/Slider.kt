/*
 * Copyright 2024 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.lambda.gui.impl.clickgui.buttons.setting

import com.lambda.config.AbstractSetting
import com.lambda.graphics.animation.Animation.Companion.exp
import com.lambda.gui.api.GuiEvent
import com.lambda.gui.api.component.core.list.ChildLayer
import com.lambda.gui.impl.clickgui.buttons.ModuleButton
import com.lambda.gui.impl.clickgui.buttons.SettingButton
import com.lambda.module.modules.client.ClickGui
import com.lambda.module.modules.client.GuiSettings
import com.lambda.sound.LambdaSound
import com.lambda.sound.SoundManager.playSound
import com.lambda.util.Mouse
import com.lambda.util.math.Vec2d
import com.lambda.util.math.lerp
import com.lambda.util.math.multAlpha
import com.lambda.util.math.transform

abstract class Slider<V : Any, T : AbstractSetting<V>>(
    setting: T, owner: ChildLayer.Drawable<SettingButton<*, *>, ModuleButton>,
) : SettingButton<V, T>(setting, owner) {
    protected abstract val progress: Double

    // Force this slider to follow mouse when dragging instead of rounding to the closest setting value
    private val progressAnimation by animation.exp({ mouseX?.let(::getProgressByMouse) ?: progress }, 0.6)
    private val renderProgress get() = lerp(showAnimation, 0.0, progressAnimation)

    protected abstract fun setValueByProgress(progress: Double)
    private var lastPlayedValue = value
    private var lastPlayedTiming = 0L

    private var mouseX: Double? = null;
        get() {
            if (activeButton != Mouse.Button.Left) field = null
            return field
        }

    override fun onEvent(e: GuiEvent) {
        super.onEvent(e)

        when (e) {
            is GuiEvent.Render -> {
                // Slider rect
                renderer.filled.build(
                    rect = rect.moveSecond(Vec2d(-rect.size.x * (1.0 - renderProgress), 0.0)).shrink(shrinkAnimation),
                    roundRadius = ClickGui.buttonRadius,
                    color = GuiSettings.mainColor.multAlpha(showAnimation * 0.3),
                    shade = GuiSettings.shade
                )

                slide()
            }

            is GuiEvent.MouseMove -> {
                mouseX = e.mouse.x
            }
        }
    }

    override fun onPress(e: GuiEvent.MouseClick) {
        super.onPress(e)
        mouseX = e.mouse.x
    }

    protected open fun slide() = mouseX?.let { mouseX ->
        setValueByProgress(getProgressByMouse(mouseX))
        playClickSound()
    }

    protected fun playClickSound() {
        val time = System.currentTimeMillis()
        if (lastPlayedValue == value || time - lastPlayedTiming < 50) return

        lastPlayedValue = value
        lastPlayedTiming = time

        playSound(LambdaSound.BUTTON_CLICK.event, lerp(progress, 0.9, 1.2))
    }

    private fun getProgressByMouse(mouseX: Double) =
        transform(mouseX, rect.left, rect.right, 0.0, 1.0).coerceIn(0.0, 1.0)
}
