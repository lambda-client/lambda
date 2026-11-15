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
import com.lambda.config.settings.complex.Bind
import com.lambda.event.events.ButtonEvent
import com.lambda.gui.component.TextFocus
import com.lambda.module.modules.client.Style
import com.lambda.module.modules.combat.autodisconnect.AutoDisconnectScreen
import com.lambda.util.KeyCode
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

/**
 * The Minecraft screen behind every [LambdaGui.Screen]. It draws nothing itself: it frees the
 * cursor, keeps the game from reacting to input, and hands that input to the Compose scene.
 */
object GuiScreen : Screen(Text.of("Lambda")), CursorOverrideProvider {
    /** The screen the GUI was opened over, such as the title screen. Null when opened in game. */
    private var parent: Screen? = null

    /**
     * Set while a keybind setting waits for the user to press what they want to bind. The next
     * key or mouse button goes to it instead of the interface; null means the user cancelled.
     */
    internal var keybindRecorder: ((Bind?) -> Unit)? = null

    /** Screens, besides none at all, that the GUI may be opened over */
    private val backgroundScreens = listOf(
        TitleScreen::class.java,
        MultiplayerScreen::class.java,
        AutoDisconnectScreen::class.java,
    )

    fun canOpenOver(screen: Screen?) = screen == null || backgroundScreens.any { it.isInstance(screen) }

    internal fun show() {
        parent = mc.currentScreen
        // The parent comes back when the GUI closes, so it must keep what removed() would free
        (parent as? OverlayBackgroundScreen)?.onOverlaidByGui()
        mc.setScreen(this)
    }

    override fun close() {
        val previous = parent
        parent = null
        mc.setScreen(previous)
    }

    override fun removed() {
        keybindRecorder?.invoke(null)
        keybindRecorder = null
        LambdaGui.onScreenRemoved()
    }

    override fun shouldPause() = false

    // The interface is drawn by LambdaGui.render once the whole vanilla GUI is on screen
    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, deltaTicks: Float) = Unit

    override fun renderBackground(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        val parent = parent
        if (parent != null) {
            // Off-screen mouse coordinates keep the parent's widgets from looking hovered
            parent.renderBackground(context, -1, -1, delta)
            parent.render(context, -1, -1, delta)
            context.drawDeferredElements()
        }
        // Over the world or the screen the GUI was opened on, whichever is behind it. Drawn here
        // rather than asked of Minecraft, whose own darkening NoRender can switch off for every
        // other screen: this one has its own setting.
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
        keybindRecorder?.let { record ->
            keybindRecorder = null
            if (click.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                record(Bind(0, click.modifiers(), click.button()))
                return true
            }
            // The left button abandons the recording, and then is the click it would have been
            record(null)
        }
        val position = scenePosition(click.x(), click.y())
        // Pressing anywhere else is done typing, as in any other window
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

    override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontalAmount: Double, verticalAmount: Double): Boolean {
        LambdaGui.input?.scroll(horizontalAmount, verticalAmount)
        return true
    }

    override fun keyPressed(input: KeyInput): Boolean {
        if (recordKey(input, pressed = true)) return true
        if (LambdaGui.input?.key(input.key(), true, input.modifiers()) == true) return true
        if (input.isEscape && LambdaGui.onEscape?.invoke() != true) LambdaGui.back()
        return true
    }

    override fun keyReleased(input: KeyInput): Boolean {
        if (recordKey(input, pressed = false)) return true
        LambdaGui.input?.key(input.key(), false, input.modifiers())
        return true
    }

    override fun charTyped(input: CharInput): Boolean {
        LambdaGui.input?.type(input.codepoint())
        return true
    }

    /**
     * Feeds a key to the [keybindRecorder]. A modifier pressed on its own is only bound when it is
     * released again, so holding it to combine with another key still works.
     */
    private fun recordKey(input: KeyInput, pressed: Boolean): Boolean {
        val record = keybindRecorder ?: return false
        val key = ButtonEvent.Keyboard.Press(
            input.key(), input.scancode(), if (pressed) GLFW.GLFW_PRESS else GLFW.GLFW_RELEASE, input.modifiers()
        ).translated
        val isModifier = input.key() in GLFW.GLFW_KEY_LEFT_SHIFT..GLFW.GLFW_KEY_RIGHT_SUPER
        if (pressed == isModifier) return true

        record(
            when (key) {
                KeyCode.Escape -> null
                KeyCode.Backspace, KeyCode.Delete -> Bind.EMPTY
                // A released modifier still reports itself as held; it is the key here, not a modifier
                else -> Bind(key.code, if (isModifier) 0 else input.modifiers(), -1)
            }
        )
        keybindRecorder = null
        return true
    }

    /** The interface is laid out in framebuffer pixels; Minecraft reports the mouse in scaled GUI units */
    private fun scenePosition(x: Double, y: Double): Offset {
        val scale = mc.window.scaleFactor
        return Offset((x * scale).toFloat(), (y * scale).toFloat())
    }

    /** The near black Minecraft darkens the world with behind a menu */
    private const val DIM_COLOR = 0x101010
}
