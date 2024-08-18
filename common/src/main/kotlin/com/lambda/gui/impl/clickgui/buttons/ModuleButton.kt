package com.lambda.gui.impl.clickgui.buttons

import com.lambda.config.settings.NumericSetting
import com.lambda.config.settings.StringSetting
import com.lambda.config.settings.comparable.BooleanSetting
import com.lambda.config.settings.comparable.EnumSetting
import com.lambda.config.settings.complex.KeyBindSetting
import com.lambda.graphics.animation.Animation.Companion.exp
import com.lambda.graphics.gl.Scissor.scissor
import com.lambda.gui.api.GuiEvent
import com.lambda.gui.api.RenderLayer
import com.lambda.gui.api.component.WindowComponent
import com.lambda.gui.api.component.button.ListButton
import com.lambda.gui.api.component.core.list.ChildLayer
import com.lambda.gui.impl.clickgui.buttons.setting.*
import com.lambda.module.Module
import com.lambda.module.modules.client.ClickGui
import com.lambda.module.modules.client.GuiSettings
import com.lambda.sound.LambdaSound
import com.lambda.sound.SoundManager.playSoundRandomly
import com.lambda.util.Mouse
import com.lambda.util.math.ColorUtils.multAlpha
import com.lambda.util.math.ColorUtils.setAlpha
import com.lambda.util.math.MathUtils.lerp
import com.lambda.util.math.MathUtils.toInt
import com.lambda.util.math.Rect
import com.lambda.util.math.Vec2d
import com.lambda.util.math.transform
import java.awt.Color
import kotlin.math.abs

class ModuleButton(
    val module: Module,
    override val owner: ChildLayer.Drawable<ModuleButton, WindowComponent<ModuleButton>>,
) : ListButton(owner) {
    override val text get() = module.name
    private val enabled get() = module.isEnabled

    override var activeAnimation by animation.exp(0.0, 1.0, 0.15, ::enabled)
    private val toggleFxDirection by animation.exp(0.0, 1.0, 0.7, ::enabled)

    override val listStep: Double get() = super.listStep + renderHeight

    private var isOpen = false
    override val isActive get() = isOpen

    private val openAnimation by animation.exp(0.0, 1.0, 0.7, ::isOpen)
    override val childShowAnimation get() = lerp(0.0, openAnimation, owner.childShowAnimation)

    private var settingsHeight = 0.0
    private var renderHeight by animation.exp(::settingsHeight, 0.6)
    private val settingsRect
        get() = rect
            .moveFirst(Vec2d(0.0, size.y + super.listStep))
            .moveSecond(Vec2d(0.0, renderHeight))

    private val settingsRenderer = RenderLayer()
    val settingsLayer =
        ChildLayer.Drawable<SettingButton<*, *>, ModuleButton>(owner.gui, this, settingsRenderer, ::settingsRect) {
            it.visible && abs(settingsHeight - renderHeight) < 3
        }

    init {
        // TODO: resort when all settings are implemented
        module.settings.mapNotNull {
            when (it) {
                is BooleanSetting -> BooleanButton(it, settingsLayer)
                is NumericSetting<*> -> NumberSlider(it, settingsLayer)
                is StringSetting -> StringButton(it, settingsLayer)
                is EnumSetting<*> -> EnumSlider(it, settingsLayer)
                is KeyBindSetting -> BindButton(it, settingsLayer)
                else -> null
            }
        }.forEach(settingsLayer.children::add)
    }

    override fun onEvent(e: GuiEvent) {
        when (e) {
            is GuiEvent.Show -> {
                isOpen = false
                updateHeight()
                renderHeight = settingsHeight
            }

            is GuiEvent.Tick -> {
                if (renderHeight < 0.5) return
                updateHeight()

                var y = 0.0
                settingsLayer.children.filter(SettingButton<*, *>::visible).forEach { button ->
                    button.heightOffset = y
                    y += button.size.y + button.listStep
                }
            }

            is GuiEvent.Render -> {
                super.onEvent(e)

                settingsRenderer.font.scaleMultiplier = ClickGui.settingsFontScale

                // Shadow
                renderer.filled.apply {
                    val rect = Rect(
                        rect.leftTop + Vec2d(0.0, size.y),
                        rect.rightTop + Vec2d(0.0, size.y + 5.0)
                    )

                    val progress = transform(renderHeight, 0.0, 10.0, 0.0, 1.0).coerceIn(0.0, 1.0)
                    val topColor = Color.BLACK.setAlpha(0.2 * progress * showAnimation)
                    val bottomColor = Color.BLACK.setAlpha(0.0)

                    build(rect, 0.0, topColor, topColor, bottomColor, bottomColor)
                }

                // Bottom shadow
                renderer.filled.apply {
                    val last = this@ModuleButton.owner.ownerComponent.contentComponents.children.lastOrNull()
                    val show = this@ModuleButton != last

                    val rect = Rect(
                        settingsRect.leftBottom - Vec2d(0.0, 5.0),
                        settingsRect.rightBottom
                    )

                    val progress = transform(renderHeight, 0.0, 10.0, 0.0, 1.0).coerceIn(0.0, 1.0) * show.toInt()
                    val topColor = Color.BLACK.setAlpha(0.0)
                    val bottomColor = Color.BLACK.setAlpha(0.2 * progress * showAnimation)

                    build(rect, 0.0, topColor, topColor, bottomColor, bottomColor)
                }

                // Toggle fx
                renderer.filled.apply {
                    val left = rect - Vec2d(rect.size.x, 0.0)
                    val right = rect + Vec2d(rect.size.x, 0.0)

                    val rect = lerp(left, right, activeAnimation)
                        .clamp(rect)
                        .shrink(shrinkAnimation)

                    // 0.0 .. 1.0 .. 0.0 animation
                    val alpha = 1.0 - (abs(activeAnimation - 0.5) * 2.0)
                    val color = GuiSettings.mainColor.multAlpha(alpha * 0.6 * showAnimation)

                    // "Tail" effect
                    val leftColor = color.multAlpha(1.0 - toggleFxDirection)
                    val rightColor = color.multAlpha(toggleFxDirection)

                    build(rect, roundRadius, leftColor, rightColor, rightColor, leftColor, GuiSettings.shade)
                }

                if (renderHeight > 0.5) {
                    scissor(settingsRect) {
                        settingsLayer.onEvent(e)
                        settingsRenderer.render()
                    }
                }

                return
            }
        }

        super.onEvent(e)
        settingsLayer.onEvent(e)
    }

    private fun updateHeight() {
        settingsHeight = if (isOpen) {
            var lastStep = 0.0
            settingsLayer.children
                .filter(SettingButton<*, *>::visible)
                .sumOf { lastStep = it.listStep; it.size.y + it.listStep } - lastStep + super.listStep
        } else 0.0
    }

    override fun performClickAction(e: GuiEvent.MouseClick) {
        val sound = when (e.button) {
            Mouse.Button.Left -> {
                module.toggle()
                if (module.isEnabled) LambdaSound.MODULE_ON else LambdaSound.MODULE_OFF
            }

            Mouse.Button.Right -> {
                // Don't let user spam
                val targetHeight = if (isOpen) settingsHeight else 0.0
                if (abs(targetHeight - renderHeight) > 1) return

                isOpen = !isOpen
                if (isOpen) settingsLayer.onEvent(GuiEvent.Show())
                updateHeight()

                if (isOpen) LambdaSound.SETTINGS_OPEN else LambdaSound.SETTINGS_CLOSE
            }

            else -> return
        }

        playSoundRandomly(sound.event)
    }

    override fun equals(other: Any?) =
        (other as? ModuleButton)?.module == module

    override fun hashCode() =
        module.hashCode()
}
