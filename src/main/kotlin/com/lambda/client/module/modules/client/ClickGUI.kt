package com.lambda.client.module.modules.client

import com.lambda.client.event.events.ShutdownEvent
import com.lambda.client.event.listener.listener
import com.lambda.client.gui.clickgui.LambdaClickGui
import com.lambda.client.module.Category
import com.lambda.client.module.Module
import com.lambda.client.util.StopTimer
import com.lambda.client.util.threads.safeListener
import net.minecraftforge.fml.common.gameevent.TickEvent
import org.lwjgl.input.Keyboard
import kotlin.math.round

object ClickGUI : Module(
    name = "ClickGUI",
    description = "Opens the Click GUI",
    category = Category.CLIENT,
    showOnArray = false,
    alwaysListening = true
) {
    private val scaleSetting = setting("Scale", 100, 50..400, 5)
    val radius by setting("Corner Radius", 2.0, 0.0..10.0, 0.2, unit = "px")
    val blur by setting("Blur", 0.0f, 0.0f..1.0f, 0.05f)
    val windowOutline by setting("Window Outline", false)
    val buttonOutline by setting("Button Outline", false)
    val outlineWidth by setting("Outline Width", 2.5f, 0.5f..3.5f, 0.5f, { windowOutline || buttonOutline })
    val resizeBar by setting("Resize Bar", 5, 0..20, 1, unit = "px")
    val horizontalMargin by setting("Horizontal Margin", 0.0f, 0.0f..10.0f, 0.5f, unit = "px")
    val verticalMargin by setting("Vertical Margin", 0.0f, 0.0f..10.0f, 0.5f, unit = "px")
    val darkness by setting("Darkness", 0.25f, 0.0f..1.0f, 0.05f)
    val gridSize by setting("Snap Grid", 10.0f, 0.0f..50.0f, 1.0f, unit = "px")
    val fadeInTime by setting("Fade In Time", 0.25f, 0.0f..1.0f, 0.05f, unit = "s")
    val fadeOutTime by setting("Fade Out Time", 0.1f, 0.0f..1.0f, 0.05f, unit = "s")
    val scrollRubberband by setting("Scroll Rubberband", false)
    val scrollRubberbandSpeed by setting("Scroll Rubberband Speed", 0.25f, 0.01f..1.0f, 0.05f, { scrollRubberband })
    val showModifiedInBold by setting("Show Modified In Bold", false, description = "Display modified settings in a bold font")
    private val resetComponents = setting("Reset Positions", false)
    private val resetScale = setting("Reset Scale", false)
    val sortBy = setting("Sort By", SortByOptions.ALPHABETICALLY)

    private var prevScale = scaleSetting.value / 100.0f
    private var scale = prevScale
    private val settingTimer = StopTimer()

    enum class SortByOptions {
        ALPHABETICALLY, FREQUENCY, CUSTOM
    }

    private fun resetScale() {
        scaleSetting.value = 100
        prevScale = 1.0f
        scale = 1.0f
    }

    fun getScaleFactorFloat() = (prevScale + (scale - prevScale) * mc.renderPartialTicks) * 2.0f

    fun getScaleFactor() = (prevScale + (scale - prevScale) * mc.renderPartialTicks) * 2.0

    init {
        safeListener<TickEvent.ClientTickEvent> {
            prevScale = scale
            if (settingTimer.stop() > 1000L) {
                val diff = scale - getRoundedScale()
                when {
                    diff < -0.025 -> scale += 0.025f
                    diff > 0.025 -> scale -= 0.025f
                    else -> scale = getRoundedScale()
                }
            }
        }

        listener<ShutdownEvent> {
            disable()
        }

        sortBy.listeners.add { LambdaClickGui.reorderModules() }

        resetComponents.listeners.add {
            LambdaClickGui.windowList.forEach {
                it.resetPosition()
            }
            resetComponents.value = false
        }

        resetScale.listeners.add {
            resetScale()
            resetScale.value = false
        }
    }

    private fun getRoundedScale(): Float {
        return round((scaleSetting.value / 100.0f) / 0.1f) * 0.1f
    }

    init {
        onEnable {
            if (mc.currentScreen !is LambdaClickGui) {
                HudEditor.disable()
                mc.displayGuiScreen(LambdaClickGui)
                LambdaClickGui.isFocused = true
                LambdaClickGui.onDisplayed()
            }
        }

        onDisable {
            if (mc.currentScreen is LambdaClickGui) {
                mc.displayGuiScreen(null)
            }
        }

        bind.value.setBind(Keyboard.KEY_Y)
        scaleSetting.listeners.add {
            settingTimer.reset()
        }
    }
}
