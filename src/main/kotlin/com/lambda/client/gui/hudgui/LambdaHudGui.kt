package com.lambda.client.gui.hudgui

import com.lambda.client.event.events.RenderOverlayEvent
import com.lambda.client.event.listener.listener
import com.lambda.client.gui.AbstractLambdaGui
import com.lambda.client.gui.hudgui.component.HudButton
import com.lambda.client.gui.hudgui.window.HudSettingWindow
import com.lambda.client.gui.rgui.Component
import com.lambda.client.gui.rgui.windows.ListWindow
import com.lambda.client.module.modules.client.ClickGUI
import com.lambda.client.module.modules.client.Hud
import com.lambda.client.module.modules.client.HudEditor
import com.lambda.client.util.graphics.GlStateUtils
import com.lambda.client.util.graphics.VertexHelper
import com.lambda.client.util.math.Vec2f
import com.lambda.client.util.threads.safeListener
import net.minecraftforge.fml.common.gameevent.InputEvent
import org.lwjgl.input.Keyboard
import org.lwjgl.opengl.GL11.*
import java.util.*

object LambdaHudGui : AbstractLambdaGui<HudSettingWindow, AbstractHudElement>() {

    override val alwaysTicking = true
    private val hudWindows = EnumMap<AbstractHudElement.Category, ListWindow>(AbstractHudElement.Category::class.java)

    init {
        var posX = 0.0f

        AbstractHudElement.Category.values().forEach { category ->
            val window = ListWindow(category.displayName, posX, 0.0f, 90.0f, 300.0f, Component.SettingGroup.HUD_GUI)
            windowList.add(window)
            hudWindows[category] = window

            posX += 90.0f
        }

        listener<InputEvent.KeyInputEvent> {
            val eventKey = Keyboard.getEventKey()

            if (eventKey == Keyboard.KEY_NONE || Keyboard.isKeyDown(Keyboard.KEY_F3)) return@listener

            windowList
                .filterIsInstance<AbstractHudElement>()
                .filter { it.bind.isDown(eventKey) }
                .forEach { it.visible = !it.visible }
        }
    }

    internal fun register(hudElement: AbstractHudElement) {
        val button = HudButton(hudElement)
        hudWindows[hudElement.category]?.add(button)
        windowList.add(hudElement)
    }

    internal fun unregister(hudElement: AbstractHudElement) {
        hudWindows[hudElement.category]?.children?.removeIf { it is HudButton && it.hudElement == hudElement }
        windowList.remove(hudElement)
    }

    override fun onGuiClosed() {
        super.onGuiClosed()
        setHudButtonVisibility { true }
    }

    override fun newSettingWindow(element: AbstractHudElement, mousePos: Vec2f): HudSettingWindow {
        return HudSettingWindow(element, mousePos.x, mousePos.y)
    }

    override fun keyTyped(typedChar: Char, keyCode: Int) {
        if (keyCode == Keyboard.KEY_ESCAPE ||
            (keyCode == ClickGUI.bind.value.key ||
                keyCode == HudEditor.bind.value.key)
            && !searching && settingWindow?.listeningChild == null) {
            HudEditor.disable()
        } else {
            super.keyTyped(typedChar, keyCode)

            val string = typedString.replace(" ", "")

            if (string.isNotEmpty()) {
                setHudButtonVisibility { hudButton ->
                    hudButton.hudElement.componentName.contains(string, true)
                        || hudButton.hudElement.alias.any { it.contains(string, true) }
                }
            } else {
                setHudButtonVisibility { true }
            }
        }
    }

    private fun setHudButtonVisibility(function: (HudButton) -> Boolean) {
        windowList.filterIsInstance<ListWindow>().forEach { window ->
            window.children.filterIsInstance<HudButton>().forEach { button ->
                button.visible = function(button)
            }
        }
    }

    init {
        safeListener<RenderOverlayEvent>(0) {
            if (Hud.isDisabled) return@safeListener

            val vertexHelper = VertexHelper(GlStateUtils.useVbo())
            GlStateUtils.rescaleLambda()

            windowList
                .filterIsInstance<AbstractHudElement>()
                .filter { it.visible }
                .forEach { window ->
                    renderHudElement(vertexHelper, window)
                }

            GlStateUtils.rescaleMc()
            GlStateUtils.depth(true)
        }
    }

    private fun renderHudElement(vertexHelper: VertexHelper, window: AbstractHudElement) {
        glPushMatrix()
        glTranslatef(window.renderPosX, window.renderPosY, 0.0f)

        if (Hud.hudFrame) window.renderFrame(vertexHelper)

        glScalef(window.scale, window.scale, window.scale)
        window.renderHud(vertexHelper)

        glPopMatrix()
    }

}