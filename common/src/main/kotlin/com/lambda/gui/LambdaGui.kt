package com.lambda.gui

import com.lambda.gui.component.IComponent
import com.lambda.threading.runSafe
import com.lambda.util.KeyCode
import com.lambda.util.Mouse
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.Screen
import net.minecraft.text.Text

interface LambdaGui : IComponent {
    fun onCloseRequest(): Boolean = true

    // We need to find an alternative to this
    fun show() = runSafe {
        mc.setScreen(object : Screen(Text.of("Lambda Screen")) {
            override fun onDisplayed() {
                onShow()
            }

            override fun removed() {
                onHide()
            }

            override fun render(context: DrawContext?, mouseX: Int, mouseY: Int, delta: Float) {
                onRender()
            }

            override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
                onKey(KeyCode(keyCode))

                if (keyCode == KeyCode.Escape.key && onCloseRequest()) {
                    this.close()
                }

                return true
            }

            override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
                onMouse(Mouse.Button(button), Mouse.Action.Click)
                return true
            }

            override fun mouseReleased(mouseX: Double, mouseY: Double, button: Int): Boolean {
                onMouse(Mouse.Button(button), Mouse.Action.Release)
                return true
            }

            override fun shouldPause() = false
        })
    }
}
