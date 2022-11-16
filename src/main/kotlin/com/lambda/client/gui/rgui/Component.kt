package com.lambda.client.gui.rgui

import com.lambda.client.LambdaMod
import com.lambda.client.commons.interfaces.Nameable
import com.lambda.client.module.modules.client.ClickGUI
import com.lambda.client.setting.GuiConfig
import com.lambda.client.setting.GuiConfig.setting
import com.lambda.client.setting.configs.AbstractConfig
import com.lambda.client.util.Wrapper
import com.lambda.client.util.graphics.VertexHelper
import com.lambda.client.util.graphics.font.HAlign
import com.lambda.client.util.graphics.font.VAlign
import com.lambda.client.util.math.Vec2f
import kotlin.math.max

open class Component(
    final override val name: String,
    posXIn: Float,
    posYIn: Float,
    widthIn: Float,
    heightIn: Float,
    val settingGroup: SettingGroup,
    val config: AbstractConfig<out Nameable> = GuiConfig
) : Nameable {

    // Basic info
    var componentName by setting("Name", name, { false })
    protected val visibleSetting = setting("Visible", true, { false }, { _, it -> it || !closeable })
    var visible by visibleSetting

    protected val dockingHSetting = setting("Docking H", HAlign.LEFT)
    protected val dockingVSetting = setting("Docking V", VAlign.TOP)

    private var widthSetting = setting("Width", widthIn, 0.0f..69420.914f, 0.1f, { false })
    private var heightSetting = setting("Height", heightIn, 0.0f..69420.914f, 0.1f, { false })

    private var relativePosXSetting = setting("Pos X", posXIn, -69420.914f..69420.914f, 0.1f, { false },
        { _, it -> if (this is WindowComponent && LambdaMod.ready) absToRelativeX(relativeToAbsX(it).coerceIn(.0f, max(scaledDisplayWidth - width, .0f))) else it })
    private var relativePosYSetting = setting("Pos Y", posYIn, -69420.914f..69420.914f, 0.1f, { false },
        { _, it -> if (this is WindowComponent && LambdaMod.ready) absToRelativeY(relativeToAbsY(it).coerceIn(.0f, max(scaledDisplayHeight - height, .0f))) else it })

    var width by widthSetting
    open var height by heightSetting
    var relativePosX by relativePosXSetting
    var relativePosY by relativePosYSetting
    var dockingH by dockingHSetting
    var dockingV by dockingVSetting

    var posX: Float
        get() {
            return relativeToAbsX(relativePosX)
        }
        set(value) {
            if (!LambdaMod.ready) return
            relativePosX = absToRelativeX(value)
        }

    var posY: Float
        get() {
            return relativeToAbsY(relativePosY)
        }
        set(value) {
            if (!LambdaMod.ready) return
            relativePosY = absToRelativeY(value)
        }

    init {
        dockingHSetting.listeners.add { posX = prevPosX }
        dockingVSetting.listeners.add { posY = prevPosY }
    }

    // Extra info
    protected val mc = Wrapper.minecraft
    open val minWidth = 1.0f
    open val minHeight = 1.0f
    open val maxWidth = -1.0f
    open val maxHeight = -1.0f
    open val closeable: Boolean get() = true

    // Rendering info
    private var prevPosX = 0.0f
    private var prevPosY = 0.0f
    val renderPosX get() = prevPosX + prevDockWidth + (posX + dockWidth - (prevPosX + prevDockWidth)) * mc.renderPartialTicks - dockWidth
    val renderPosY get() = prevPosY + prevDockHeight + (posY + dockHeight - (prevPosY + prevDockHeight)) * mc.renderPartialTicks - dockHeight

    private var prevWidth = 0.0f
    private var prevHeight = 0.0f
    val renderWidth get() = prevWidth + (width - prevWidth) * mc.renderPartialTicks
    open val renderHeight get() = prevHeight + (height - prevHeight) * mc.renderPartialTicks

    private fun relativeToAbsX(xIn: Float) = xIn + scaledDisplayWidth * dockingH.multiplier - dockWidth
    private fun relativeToAbsY(yIn: Float) = yIn + scaledDisplayHeight * dockingV.multiplier - dockHeight
    private fun absToRelativeX(xIn: Float) = xIn - scaledDisplayWidth * dockingH.multiplier + dockWidth
    private fun absToRelativeY(yIn: Float) = yIn - scaledDisplayHeight * dockingV.multiplier + dockHeight

    protected val scaledDisplayWidth get() = mc.displayWidth / ClickGUI.getScaleFactorFloat()
    protected val scaledDisplayHeight get() = mc.displayHeight / ClickGUI.getScaleFactorFloat()
    private val dockWidth get() = width * dockingH.multiplier
    private val dockHeight get() = height * dockingV.multiplier
    private val prevDockWidth get() = prevWidth * dockingH.multiplier
    private val prevDockHeight get() = prevHeight * dockingV.multiplier

    // Update methods
    open fun onDisplayed() {}

    open fun onClosed() {}

    open fun onGuiInit() {
        updatePrevPos()
        updatePrevSize()
    }

    open fun onTick() {
        updatePrevPos()
        updatePrevSize()
    }

    private fun updatePrevPos() {
        prevPosX = posX
        prevPosY = posY
    }

    private fun updatePrevSize() {
        prevWidth = width
        prevHeight = height
    }

    open fun onRender(vertexHelper: VertexHelper, absolutePos: Vec2f) {}

    open fun onPostRender(vertexHelper: VertexHelper, absolutePos: Vec2f) {}

    enum class SettingGroup(val groupName: String) {
        NONE(""),
        CLICK_GUI("click_gui"),
        HUD_GUI("hud_gui")
    }

    fun resetPosition() {
        widthSetting.resetValue()
        heightSetting.resetValue()
        relativePosXSetting.resetValue()
        relativePosYSetting.resetValue()
    }

}