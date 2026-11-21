/*
 * Copyright 2026 Lambda
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

package com.lambda.gui

import androidx.compose.ui.geometry.Offset
import com.lambda.Lambda.mc
import com.lambda.config.settings.complex.KeybindSetting
import com.lambda.gui.clickgui.ClickGuiState
import com.lambda.gui.component.TextFocus
import com.lambda.module.modules.client.Style
import com.lambda.module.modules.combat.autodisconnect.AutoDisconnectScreen
import com.lambda.util.render.CursorOverrideProvider
import net.minecraft.client.gui.Click
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.cursor.Cursor
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.gui.screen.TitleScreen
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen
import net.minecraft.client.input.CharInput
import net.minecraft.client.input.KeyInput
import net.minecraft.text.Text
import org.lwjgl.glfw.GLFW

object GuiScreen : Screen(Text.of("Lambda")), CursorOverrideProvider {
    private var parent: Screen? = null

    private val backgroundScreens =
        listOf(
            TitleScreen::class.java,
            MultiplayerScreen::class.java,
            AutoDisconnectScreen::class.java,
        )

    fun canOpenOver(screen: Screen?) =
        screen == null || backgroundScreens.any { it.isInstance(screen) }

    internal fun show() {
        parent = mc.currentScreen
        (parent as? OverlayBackgroundScreen)?.onOverlaidByGui()
        mc.setScreen(this)
    }

    override fun close() {
        val previous = parent
        parent = null
        mc.setScreen(previous)
    }

    override fun removed() {
        KeybindSetting.stopRecording()
        LambdaGui.onScreenRemoved()
    }

    override fun shouldPause() = false

    override fun render(
        context: DrawContext,
        mouseX: Int,
        mouseY: Int,
        deltaTicks: Float
    ) = Unit

    override fun renderBackground(
        context: DrawContext,
        mouseX: Int,
        mouseY: Int,
        delta: Float
    ) {
        val parent = parent
        if (parent != null) {
            parent.renderBackground(context, -1, -1, delta)
            parent.render(context, -1, -1, delta)
            context.drawDeferredElements()
        }
        if (Style.dimBackground && LambdaGui.screen == LambdaGui.Screen.ClickGui) {
            val alpha = (Style.dimStrength * 255).toInt().coerceIn(0, 255)
            context.fill(0, 0, width, height, (alpha shl 24) or DIM_COLOR)
        }
    }

    override fun resize(width: Int, height: Int) {
        super.resize(width, height)
        parent?.resize(width, height)
    }

    override fun getCursorOverride(mouseX: Int, mouseY: Int): Cursor? = LambdaGui.cursor

    override fun mouseMoved(mouseX: Double, mouseY: Double) {
        LambdaGui.input?.move(scenePosition(mouseX, mouseY))
    }

    override fun mouseClicked(click: Click, doubled: Boolean): Boolean {
        val position = scenePosition(click.x(), click.y())
        TextFocus.press(position)
        LambdaGui.input?.run {
            move(position)
            button(click.button(), true, click.modifiers())
        }
        return true
    }

    override fun mouseReleased(click: Click): Boolean {
        LambdaGui.input?.run {
            move(scenePosition(click.x(), click.y()))
            button(click.button(), false, click.modifiers())
        }
        return true
    }

    override fun mouseScrolled(
        mouseX: Double,
        mouseY: Double,
        horizontalAmount: Double,
        verticalAmount: Double
    ): Boolean {
        LambdaGui.input?.scroll(horizontalAmount, verticalAmount)
        return true
    }

    override fun keyPressed(input: KeyInput): Boolean {
        if (LambdaGui.input?.key(input.key(), true, input.modifiers()) == true) return true
        if (input.key() == GLFW.GLFW_KEY_F && input.modifiers() and GLFW.GLFW_MOD_CONTROL != 0) {
            ClickGuiState.focusSearch = LambdaGui.screen == LambdaGui.Screen.ClickGui
            return true
        }
        if (input.isEscape && LambdaGui.onEscape?.invoke() != true) LambdaGui.back()
        return true
    }

    override fun keyReleased(input: KeyInput): Boolean {
        LambdaGui.input?.key(input.key(), false, input.modifiers())
        return true
    }

    override fun charTyped(input: CharInput): Boolean {
        LambdaGui.input?.type(input.codepoint())
        return true
    }

    private fun scenePosition(x: Double, y: Double): Offset {
        val scale = mc.window.scaleFactor
        return Offset((x * scale).toFloat(), (y * scale).toFloat())
    }

    private const val DIM_COLOR = 0x101010
}
