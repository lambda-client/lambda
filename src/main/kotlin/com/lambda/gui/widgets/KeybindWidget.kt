package com.lambda.gui.widgets

import com.lambda.event.events.KeyboardEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.gui.dsl.ImGuiBuilder
import com.lambda.util.KeyCode
import imgui.ImGui
import imgui.flag.ImGuiCol
import imgui.flag.ImGuiHoveredFlags
import imgui.flag.ImGuiMouseButton
import org.lwjgl.glfw.GLFW

class KeybindWidget(
    private val label: String,
    private val description: String = "",
    private val valueGetter: () -> KeyCode,
    private val valueSetter: (KeyCode) -> Unit,
) {
    private var listening = false

    init {
        listen<KeyboardEvent.Press> { event ->
            if (!listening) return@listen
            if (!event.isPressed) return@listen
            val translated = event.translated

            when (translated.keyCode) {
                GLFW.GLFW_KEY_ESCAPE -> {
                    listening = false
                }
                GLFW.GLFW_KEY_BACKSPACE, GLFW.GLFW_KEY_DELETE -> {
                    valueSetter(KeyCode.UNBOUND)
                    listening = false
                }
                else -> {
                    valueSetter(translated)
                    listening = false
                }
            }
        }
    }

    fun ImGuiBuilder.build() {
        val current = valueGetter()
        val translated = KeyCode.virtualMapUS(current.keyCode, 0)
        val preview = if (listening) "$label: Press any key…" else "$label: ${translated.prettyDisplay()}"

        if (listening) {
            withStyleColor(ImGuiCol.Button, 0.20f, 0.50f, 1.00f, 1.00f) {
                withStyleColor(ImGuiCol.ButtonHovered, 0.25f, 0.60f, 1.00f, 1.00f) {
                    withStyleColor(ImGuiCol.ButtonActive, 0.20f, 0.50f, 0.95f, 1.00f) {
                        button(preview)
                    }
                }
            }
        } else {
            button(preview) { listening = true }
        }

        lambdaTooltip(
            if (!listening)
                description.ifBlank { "Click to set. Right-click to unbind. Esc cancels. Backspace/Delete unbinds." }
            else
                "Listening… Press a key to bind. Esc to cancel. Backspace/Delete to unbind."
        )

        onItemClick(ImGuiMouseButton.Right) {
            valueSetter(KeyCode.UNBOUND)
            listening = false
        }

        if (listening && !isAnyItemHovered && ImGui.isMouseClicked(ImGuiMouseButton.Left)) {
            listening = false
        }

        sameLine()
        smallButton("Unbind") {
            valueSetter(KeyCode.UNBOUND)
            listening = false
        }
        onItemHover(ImGuiHoveredFlags.Stationary) {
            lambdaTooltip("Clear binding")
        }
    }

    private fun KeyCode.prettyDisplay(): String {
        if (this == KeyCode.UNBOUND) return "Unbound"
        val name = GLFW.glfwGetKeyName(keyCode, 0)
        return name?.ifBlank { null }?.uppercase() ?: this.name
    }
}
