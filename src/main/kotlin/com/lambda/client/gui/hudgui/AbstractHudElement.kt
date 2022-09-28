package com.lambda.client.gui.hudgui

import com.lambda.client.commons.interfaces.Alias
import com.lambda.client.commons.interfaces.DisplayEnum
import com.lambda.client.commons.interfaces.Nameable
import com.lambda.client.event.LambdaEventBus
import com.lambda.client.gui.rgui.windows.BasicWindow
import com.lambda.client.module.modules.client.GuiColors
import com.lambda.client.module.modules.client.Hud
import com.lambda.client.setting.GuiConfig
import com.lambda.client.setting.GuiConfig.setting
import com.lambda.client.setting.configs.AbstractConfig
import com.lambda.client.util.Bind
import com.lambda.client.util.graphics.RenderUtils2D
import com.lambda.client.util.graphics.VertexHelper
import com.lambda.client.util.graphics.font.FontRenderAdapter
import com.lambda.client.util.math.Vec2d
import com.lambda.client.util.math.Vec2f
import com.lambda.client.util.text.MessageSendHelper
import com.lambda.client.util.threads.safeListener
import net.minecraftforge.fml.common.gameevent.TickEvent
import org.lwjgl.opengl.GL11.glScalef

abstract class AbstractHudElement(
    name: String,
    final override val alias: Array<String>,
    val category: Category,
    val description: String,
    val alwaysListening: Boolean,
    enabledByDefault: Boolean,
    config: AbstractConfig<out Nameable>
) : BasicWindow(name, 20.0f, 20.0f, 100.0f, 50.0f, SettingGroup.HUD_GUI, config), Alias {

    val bind by setting("Bind", Bind())
    val scale by setting("Scale", 1.0f, 0.1f..4.0f, 0.05f)
    val default = setting("Default", false)

    override val resizable = false

    final override val minWidth: Float get() = FontRenderAdapter.getFontHeight() * scale * 2.0f
    final override val minHeight: Float get() = FontRenderAdapter.getFontHeight() * scale

    final override val maxWidth: Float get() = hudWidth * scale
    final override val maxHeight: Float get() = hudHeight * scale

    open val hudWidth: Float get() = 20f
    open val hudHeight: Float get() = 10f

    val settingList get() = GuiConfig.getSettings(this)

    init {
        safeListener<TickEvent.ClientTickEvent> {
            if (it.phase != TickEvent.Phase.END || !visible) return@safeListener
            width = maxWidth
            height = maxHeight
        }
    }

    override fun onGuiInit() {
        super.onGuiInit()
        if (alwaysListening || visible) LambdaEventBus.subscribe(this)
    }

    override fun onClosed() {
        super.onClosed()
        if (alwaysListening || visible) LambdaEventBus.subscribe(this)
    }

    final override fun onTick() {
        super.onTick()
    }

    final override fun onRender(vertexHelper: VertexHelper, absolutePos: Vec2f) {
        renderFrame(vertexHelper)
        glScalef(scale, scale, scale)
        renderHud(vertexHelper)
    }

    open fun renderHud(vertexHelper: VertexHelper) {}

    open fun renderFrame(vertexHelper: VertexHelper) {
        RenderUtils2D.drawRectFilled(vertexHelper, Vec2d(0.0, 0.0), Vec2f(renderWidth, renderHeight).toVec2d(), GuiColors.backGround)
        RenderUtils2D.drawRectOutline(vertexHelper, Vec2d(0.0, 0.0), Vec2f(renderWidth, renderHeight).toVec2d(), 1.5f, GuiColors.outline)
    }

    init {
        visibleSetting.valueListeners.add { _, it ->
            if (it) {
                LambdaEventBus.subscribe(this)
                lastActiveTime = System.currentTimeMillis()
            } else if (!alwaysListening) {
                LambdaEventBus.unsubscribe(this)
            }
        }

        default.valueListeners.add { _, it ->
            if (it) {
                settingList.filter { it != visibleSetting && it != default }.forEach { it.resetValue() }
                default.value = false
                MessageSendHelper.sendChatMessage("$name Set to defaults!")
            }
        }

        if (!enabledByDefault) visible = false
    }

    enum class Category(override val displayName: String) : DisplayEnum {
        CLIENT("Client"),
        COMBAT("Combat"),
        PLAYER("Player"),
        WORLD("World"),
        MISC("Misc")
    }

    protected companion object {
        val primaryColor get() = Hud.primaryColor
        val secondaryColor get() = Hud.secondaryColor
    }

}