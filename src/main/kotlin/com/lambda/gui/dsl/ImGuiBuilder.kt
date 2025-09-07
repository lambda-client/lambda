/*
 * Copyright 2025 Lambda
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

@file:Suppress("unused")

/*
 * Copyright 2025 Lambda
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

package com.lambda.gui.dsl

import com.lambda.gui.dsl.ImGuiBuilder.text
import com.lambda.module.modules.client.ClickGui
import com.lambda.util.math.Vec2d
import imgui.*
import imgui.ImGui.*
import imgui.flag.*
import imgui.type.*
import net.minecraft.util.math.Vec2f
import net.minecraft.util.math.Vec3d
import net.minecraft.util.math.Vec3i
import java.awt.Color
import kotlin.reflect.KMutableProperty0

typealias ProcedureBlock = ImGuiBuilder.() -> Unit
typealias WrappedBlock<In, Out> = ImGuiBuilder.(In) -> Out

@DslMarker
annotation class ImGuiDsl

/**
 * Kotlin DSL wrapper for ImGui Java bindings.
 * Provides a more idiomatic Kotlin interface to ImGui functionality.
 */
object ImGuiBuilder {
    /**
     * Access the IO structure (mouse/keyboard/gamepad inputs, time, various configuration options/flags).
     */
    val io: ImGuiIO get() = getIO()

    /**
     * Access the Style structure (colors, sizes). Always use PushStyleCol(), PushStyleVar() to modify style mid-frame!
     */
    val style: ImGuiStyle get() = getStyle()

    /**
     * Get the compiled version string e.g. "1.80 WIP" (essentially the value for IMGUI_VERSION from the compiled version of imgui.cpp)
     */
    val version: String get() = getVersion()

    val isWindowAppearing: Boolean get() = isWindowAppearing()
    val isWindowCollapsed: Boolean get() = isWindowCollapsed()

    /**
     * Returns the current window position in screen space
     *
     * It is unlikely you need to use this. Consider using current layout pos instead, GetScreenCursorPos().
     */
    val windowPos: ImVec2 get() = getWindowPos()
    val windowX: Float get() = getWindowPosX()
    val windowY: Float get() = getWindowPosY()

    /**
     * Returns the current window size
     * It is unlikely you need to use this. Consider using GetScreenCursorPos() and GetContentRegionAvail() instead.
     */
    val windowSize: ImVec2 get() = getWindowSize()
    val windowWidth: Float get() = getWindowWidth()
    val windowHeight: Float get() = getWindowHeight()

    /**
     * Returns the viewport associated to the current window.
     */
    val windowViewport: ImGuiViewport get() = getWindowViewport()

    /**
     * Returns whether any item hovered and usable (not blocked by a popup, etc.).
     */
    val isAnyItemHovered: Boolean get() = isAnyItemHovered()

    /**
     * Returns whether:
     *  - Any button is being held
     *  - Any text field is being edited
     *  - Any item is being held and allows interaction
     */
    val isAnyItemActive: Boolean get() = isAnyItemActive()

    /**
     * Returns whether any item is focused via keyboard/gamepad navigation
     */
    val isAnyItemFocused: Boolean get() = isAnyItemFocused()

    /**
     * Executes the specified block if the current window is hovered, based on the provided flags.
     *
     * @param flags Optional flags to control the behavior of the hover state. The default value is `ImGuiWindowFlags.None`.
     * @param block A lambda block of code to be executed when the window is hovered.
     */
    @ImGuiDsl
    fun onWindowFocus(flags: Int = ImGuiWindowFlags.None, block: ProcedureBlock) =
        if (isWindowHovered(flags)) block() else Unit

    /**
     * Executes a given block of code when the current ImGui window is being hovered.
     *
     * @param flags Optional flag settings for specifying conditions under which the window hover is detected.
     *              Defaults to `ImGuiWindowFlags.None`.
     * @param block The block of code to execute when the hover condition is met.
     */
    @ImGuiDsl
    fun onWindowHover(flags: Int = ImGuiWindowFlags.None, block: ProcedureBlock) =
        if (isWindowHovered(flags)) block() else Unit

    /**
     * Executes the given block of code if the current ImGui item is hovered.
     *
     * @param flags Customization flags for determining hover behavior. Defaults to `ImGuiHoveredFlags.None`.
     * @param block The block of code to execute when the item is hovered.
     */
    @ImGuiDsl
    fun onItemHover(flags: Int = ImGuiHoveredFlags.None, block: ProcedureBlock) =
        if (isItemHovered(flags)) block() else Unit

    /**
     * Executes the provided block of code if the current item is active in the ImGui context.
     *
     * @param block The block of code to be executed when the item is active.
     */
    @ImGuiDsl
    fun onItemActive(block: ProcedureBlock) =
        if (isItemActive()) block() else Unit

    /**
     * Executes the given [block] when the currently active item in the ImGui interface gains focus.
     *
     * @param block The block of code to execute if the current item is focused.
     */
    @ImGuiDsl
    fun onItemFocus(block: ProcedureBlock) =
        if (isItemFocused()) block() else Unit

    /**
     * Returns whether the last hovered item is clicked on
     *
     * IsMouseClicked(mouseButton) && IsItemHovered()
     *
     * this is NOT equivalent to the behavior of e.g. Button(). Read comments in function definition.
     */
    @ImGuiDsl
    fun onItemClick(button: Int = ImGuiMouseButton.Right, block: ProcedureBlock) =
        if (isItemClicked(button)) block() else Unit

    /**
     * Returns whether:
     *  - The last item modified its value in this frame
     *  - Was pressed
     */
    val isItemEdited: Boolean get() = isItemEdited()

    /**
     * Returns whether the last item was made active.
     */
    val isItemActivated: Boolean get() = isItemActivated()

    /**
     * Returns whether the last item was made inactive
     *
     * Useful for Undo/Redo patterns with widgets that require continuous editing.
     */
    val isItemDeactivated: Boolean get() = isItemDeactivated()

    /**
     * Returns whether the last item was made inactive and its value was changed when active (e.g. Slider/Drag moved).
     *
     * Useful for Undo/Redo patterns with widgets that require continuous editing. Note that you may get false positives (some widgets such as Combo()/ListBox()/Selectable() will return true even when clicking an already selected item).
     */
    val isItemDeactivatedAfterEdit: Boolean get() = isItemDeactivatedAfterEdit()

    /**
     * Returns whether the last item state toggled (set by TreeNode()).
     */
    val isItemToggledOpen: Boolean get() = isItemToggledOpen()

    /**
     * Returns the ID of last item (~~ often same ImGui::GetID(label) beforehand)
     */
    val itemID: Int get() = getItemID()


    val font: ImFont get() = getFont()

    /**
     * Returns the font size (= height in pixels) with the current scale applied.
     */
    val fontSize: Int get() = getFontSize()


    /**
     * Add basic help/info block (not a window) on how to manipulate ImGui as an end-user (mouse/keyboard controls).
     */
    @ImGuiDsl
    fun showUserGuide() = ImGui.showUserGuide()

    /**
     * Creates a new window.
     *
     * @param name The title of the window
     * @param open Boolean property that controls window visibility
     * @param flags Window flags (see ImGuiWindowFlags)
     * @param block Content of the window
     *
     * @see ImGuiWindowFlags
     */
    @ImGuiDsl
    inline fun window(
        name: String,
        open: KMutableProperty0<Boolean>,
        flags: Int = ImGuiWindowFlags.None,
        block: ProcedureBlock
    ) =
        withBool(open) { window(name, it, flags, block) }

    /**
     * Creates a new window.
     *
     * @param name The title of the window
     * @param open Optional ImBoolean that controls window visibility
     * @param flags Window flags (see ImGuiWindowFlags)
     * @param block Content of the window
     *
     * @see ImGuiWindowFlags
     */
    @ImGuiDsl
    inline fun window(
        name: String,
        open: ImBoolean? = null,
        flags: Int = ImGuiWindowFlags.None,
        block: ProcedureBlock
    ) {
        if (open != null) begin(name, open, flags)
        else begin(name, flags)

        block()

        end()
    }

    /**
     * Creates a child window.
     *
     * @param strId Unique identifier for the child
     * @param width Width of the child (0.0f = auto)
     * @param height Height of the child (0.0f = auto)
     * @param border Whether to show a border
     * @param extraFlags Additional window flags
     * @param block Content of the child window
     *
     * @see ImGuiWindowFlags
     */
    @ImGuiDsl
    inline fun child(
        strId: String,
        width: Float = 0f,
        height: Float = 0f,
        border: Boolean = false,
        extraFlags: Int = ImGuiWindowFlags.None,
        block: ProcedureBlock
    ) {
        if (beginChild(strId, width, height, border, extraFlags))
            block()

        endChild()
    }

    /**
     * Raw text without formatting.
     *
     * Roughly equivalent to Text("%s", text) but:
     *  - doesn't require null terminated string if 'textEnd' is specified,
     *  - it's faster, no memory copy is done, no buffer size limits, recommended for long chunks of text.
     *
     * @param text The text to display
     */
    @ImGuiDsl
    fun text(text: String) = textUnformatted(text)

    /**
     * Text with disabled coloring.
     *
     * @param text The text to display
     */
    @ImGuiDsl
    fun textDisabled(text: String) = ImGui.textDisabled(text)

    /**
     * Text that can be copied to clipboard.
     *
     * @param text The text to display
     */
    @ImGuiDsl
    fun textCopyable(text: String) {
        text(text)
        onItemHover {
            if (isMouseClicked(ImGuiMouseButton.Left)) {
                setClipboardText(text)
            }
        }
    }

    /**
     * Formatted text (similar to printf).
     *
     * @param fmt Format string
     * @param args Arguments for formatting
     *
     * @see <a href="https://man.freebsd.org/cgi/man.cgi?query=sprintf">printf() family functions
     */
    @ImGuiDsl
    fun textFmt(fmt: String, vararg args: Any) = text(fmt.format(*args))

    /**
     * Text with a bullet point.
     *
     * @param text The text to display
     */
    @ImGuiDsl
    fun bulletText(text: String) = ImGui.bulletText(text)

    /**
     * Label with text aligned to the right.
     *
     * @param text The text to display
     */
    @ImGuiDsl
    fun textRightAligned(text: String) {
        val width = calcTextSize(text).x
        setCursorPosX(windowWidth - width - style.framePadding.x)
        text(text)
    }

    /**
     * Creates a button.
     *
     * @param label The button text
     * @param width Width of the button (0.0f = auto)
     * @param height Height of the button (0.0f = auto)
     * @param block Action to perform when clicked
     */
    @ImGuiDsl
    inline fun button(label: String, width: Float = 0f, height: Float = 0f, block: ProcedureBlock = {}) {
        if (ImGui.button(label, width, height))
            block()
    }

    /**
     * Creates a small button.
     *
     * @param label The button text
     * @param block Action to perform when clicked
     */
    @ImGuiDsl
    inline fun smallButton(label: String, block: ProcedureBlock = {}) {
        if (ImGui.smallButton(label))
            block()
    }

    /**
     * Creates an invisible button.
     *
     * @param strId Unique identifier
     * @param width Width of the button
     * @param height Height of the button
     * @param flags Button flags
     * @param block Action to perform when clicked
     */
    @ImGuiDsl
    inline fun invisibleButton(strId: String, width: Float, height: Float, flags: Int = 0, block: ProcedureBlock = {}) {
        if (ImGui.invisibleButton(strId, width, height, flags))
            block()
    }

    /**
     * Creates an arrow button.
     *
     * @param strId Unique identifier
     * @param dir Direction of the arrow (default is None)
     * @param block Action to perform when clicked
     *
     * @see ImGuiDir
     */
    @ImGuiDsl
    inline fun arrowButton(strId: String, dir: Int = ImGuiDir.None, block: ProcedureBlock = {}) {
        if (ImGui.arrowButton(strId, dir))
            block()
    }

    /**
     * Creates a checkbox.
     *
     * @param label Label for the checkbox
     * @param bool Boolean property to bind to
     * @param block Action to perform when changed
     */
    @ImGuiDsl
    inline fun checkbox(label: String, bool: KMutableProperty0<Boolean>, block: ProcedureBlock = {}) =
        withBool(bool) { checkbox(label, it, block) }

    /**
     * Creates a checkbox.
     *
     * @param label Label for the checkbox
     * @param bool ImBoolean to bind to
     * @param block Action to perform if checked
     */
    @ImGuiDsl
    inline fun checkbox(label: String, bool: ImBoolean, block: ProcedureBlock = {}) {
        if (ImGui.checkbox(label, bool)) block()
    }

    /**
     * Creates a radio button group.
     *
     * @param label Label for the group
     * @param current Currently selected index
     * @param items Items in the group
     * @param block Action to perform when selection changes
     */
    @ImGuiDsl
    inline fun <T> radioButtons(
        label: String,
        current: KMutableProperty0<T>,
        items: List<Pair<String, T>>,
        block: (T) -> Unit = {}
    ) {
        text(label)
        indent()
        for ((itemLabel, itemValue) in items) {
            if (ImGui.radioButton(itemLabel, current.get() == itemValue)) {
                current.set(itemValue)
                block(itemValue)
            }
            sameLine()
        }
        unindent()
    }

    /**
     * Creates a single radio button.
     *
     * @param label Label for the button
     * @param active Whether the button is active
     * @param block Action to perform when clicked
     */
    @ImGuiDsl
    inline fun radioButton(label: String, active: Boolean, block: ProcedureBlock = {}) {
        if (ImGui.radioButton(label, active)) block()
    }

    @ImGuiDsl
    inline fun combo(
        label: String,
        preview: String?,
        flags: Int = ImGuiComboFlags.None,
        block: () -> Unit = {},
    ) {
        if (beginCombo(label, preview, flags)) {
            block()
            endCombo()
        }
    }

    /**
     * Creates a combo box.
     *
     * @param label Label for the combo
     * @param currentItem Currently selected index
     * @param items Items in the combo
     * @param heightInItems Height in items (-1 = auto)
     * @param block Action to perform when selection changes
     */
    @ImGuiDsl
    inline fun combo(
        label: String,
        currentItem: KMutableProperty0<Int>,
        items: Array<String>,
        heightInItems: Int = -1,
        block: (ImInt) -> Unit = {},
    ) = withInt(currentItem) { combo(label, it, items, heightInItems, block) }

    @ImGuiDsl
    inline fun combo(
        label: String,
        currentItem: KMutableProperty0<Int>,
        items: Collection<String>,
        heightInItems: Int = -1,
        block: (ImInt) -> Unit = {},
    ) = combo(label, currentItem, items.toTypedArray(), heightInItems, block)

    @ImGuiDsl
    inline fun combo(
        label: String,
        currentItem: ImInt,
        items: Array<String>,
        heightInItems: Int = -1,
        block: (ImInt) -> Unit = {},
    ) {
        if (ImGui.combo(label, currentItem, items, heightInItems))
            block(currentItem)
    }

    @ImGuiDsl
    inline fun combo(
        label: String,
        currentItem: ImInt,
        items: Collection<String>,
        heightInItems: Int = -1,
        block: (ImInt) -> Unit = {},
    ) = combo(label, currentItem, items.toTypedArray(), heightInItems, block)

    /**
     * Creates a drag slider for float values.
     *
     * @param label Label for the slider
     * @param value Current value
     * @param vSpeed Speed of dragging
     * @param vMin Minimum value
     * @param vMax Maximum value
     * @param format Display format
     * @param flags Slider flags
     * @param block Action to perform when value changes
     */
    @ImGuiDsl
    inline fun drag(
        label: String,
        value: KMutableProperty0<Float>,
        vSpeed: Float = 1.0f,
        vMin: Float = 0.0f,
        vMax: Float = 0.0f,
        format: String = "%.3f",
        flags: Int = 0,
        block: (ImFloat) -> Unit = {},
    ) = withFloat(value) { drag(label, it, vSpeed, vMin, vMax, format, flags, block) }

    @ImGuiDsl
    inline fun drag(
        label: String,
        value: ImFloat,
        vSpeed: Float = 1.0f,
        vMin: Float = 0.0f,
        vMax: Float = 0.0f,
        format: String = "%.3f",
        flags: Int = 0,
        block: (ImFloat) -> Unit = {},
    ) {
        if (dragFloat(label, value.data, vSpeed, vMin, vMax, format, flags))
            block(value)
    }

    /**
     * Creates a drag slider for int values.
     *
     * @param label Label for the slider
     * @param value Current value
     * @param vSpeed Speed of dragging
     * @param vMin Minimum value
     * @param vMax Maximum value
     * @param format Display format
     * @param flags Slider flags
     * @param block Action to perform when value changes
     */
    @ImGuiDsl
    inline fun drag(
        label: String,
        value: KMutableProperty0<Int>,
        vSpeed: Float = 1.0f,
        vMin: Int = 0,
        vMax: Int = 0,
        format: String = "%d",
        flags: Int = 0,
        block: (ImInt) -> Unit = {},
    ) = withInt(value) { drag(label, it, vSpeed, vMin, vMax, format, flags, block) }

    @ImGuiDsl
    inline fun drag(
        label: String,
        value: ImInt,
        vSpeed: Float = 1.0f,
        vMin: Int = 0,
        vMax: Int = 0,
        format: String = "%d",
        flags: Int = 0,
        block: (ImInt) -> Unit = {},
    ) {
        if (dragInt(label, value.data, vSpeed, vMin, vMax, format, flags))
            block(value)
    }

    /**
     * Creates a slider for float values.
     *
     * @param label Label for the slider
     * @param value Current value
     * @param vMin Minimum value
     * @param vMax Maximum value
     * @param format Display format
     * @param flags Slider flags
     * @param block Action to perform when value changes
     */
    @ImGuiDsl
    inline fun slider(
        label: String,
        value: KMutableProperty0<Float>,
        vMin: Float,
        vMax: Float,
        format: String = "%.3f",
        flags: Int = 0,
        block: (ImFloat) -> Unit = {},
    ) = withFloat(value) { slider(label, it, vMin, vMax, format, flags, block) }

    @ImGuiDsl
    inline fun slider(
        label: String,
        value: ImFloat,
        vMin: Float,
        vMax: Float,
        format: String = "%.3f",
        flags: Int = 0,
        block: (ImFloat) -> Unit = {},
    ) {
        if (sliderFloat(label, value.data, vMin, vMax, format, flags))
            block(value)
    }

    /**
     * Creates a slider for int values.
     *
     * @param label Label for the slider
     * @param value Current value
     * @param vMin Minimum value
     * @param vMax Maximum value
     * @param format Display format
     * @param flags Slider flags
     * @param block Action to perform when value changes
     */
    @ImGuiDsl
    inline fun slider(
        label: String,
        value: KMutableProperty0<Int>,
        vMin: Int,
        vMax: Int,
        format: String = "%d",
        flags: Int = 0,
        block: (ImInt) -> Unit = {},
    ) = withInt(value) { slider(label, it, vMin, vMax, format, flags, block) }

    @ImGuiDsl
    inline fun slider(
        label: String,
        value: ImInt,
        vMin: Int,
        vMax: Int,
        format: String = "%d",
        flags: Int = 0,
        block: (ImInt) -> Unit = {},
    ) {
        if (sliderInt(label, value.data, vMin, vMax, format, flags))
            block(value)
    }

    /**
     * Creates a single integer input field.
     *
     * @param label Label for the input
     * @param value Current integer value
     * @param step Step for increment/decrement
     * @param stepFast Fast step for increment/decrement
     * @param flags Input flags
     * @param block Action to perform when value changes
     */
    @ImGuiDsl
    inline fun inputInt(
        label: String,
        value: KMutableProperty0<Int>,
        step: Int = 1,
        stepFast: Int = 100,
        flags: Int = 0,
        block: (ImInt) -> Unit = {}
    ) = withInt(value) { inputInt(label, it, step, stepFast, flags, block) }

    @ImGuiDsl
    inline fun inputInt(
        label: String,
        value: ImInt,
        step: Int = 1,
        stepFast: Int = 100,
        flags: Int = 0,
        block: (ImInt) -> Unit = {}
    ) {
        if (ImGui.inputInt(label, value, step, stepFast, flags))
            block(value)
    }

    /**
     * Creates a 2-component integer input field.
     *
     * @param label Label for the input
     * @param values Current integer values (will be modified)
     * @param flags Input flags
     * @param block Action to perform when values change
     */
    @ImGuiDsl
    inline fun inputInt2(
        label: String,
        values: IntArray,
        flags: Int = 0,
        block: (IntArray) -> Unit = {}
    ) {
        if (ImGui.inputInt2(label, values, flags))
            block(values)
    }

    /**
     * Creates a 3-component integer input field.
     *
     * @param label Label for the input
     * @param values Current integer values (will be modified)
     * @param flags Input flags
     * @param block Action to perform when values change
     */
    @ImGuiDsl
    inline fun inputInt3(
        label: String,
        values: IntArray,
        flags: Int = ImGuiInputTextFlags.None,
        block: (IntArray) -> Unit = {}
    ) {
        if (ImGui.inputInt3(label, values, flags))
            block(values)
    }

    @ImGuiDsl
    inline fun inputVec3i(
        label: String,
        vec: Vec3i,
        flags: Int = ImGuiInputTextFlags.None,
        block: (Vec3i) -> Unit = {}
    ) {
        val ints = intArrayOf(vec.x, vec.y, vec.z)

        if (ImGui.inputInt3(label, ints, flags))
            block(Vec3i(ints[0], ints[1], ints[2]))
    }

    /**
     * Creates a 4-component integer input field.
     *
     * @param label Label for the input
     * @param values Current integer values (will be modified)
     * @param flags Input flags
     * @param block Action to perform when values change
     */
    @ImGuiDsl
    inline fun inputInt4(
        label: String,
        values: IntArray,
        flags: Int = ImGuiInputTextFlags.None,
        block: (IntArray) -> Unit = {}
    ) {
        if (ImGui.inputInt4(label, values, flags))
            block(values)
    }

    /**
     * Creates a single float input field.
     *
     * @param label Label for the input
     * @param value Current float value
     * @param step Step for increment/decrement
     * @param stepFast Fast step for increment/decrement
     * @param format Display format string
     * @param flags Input flags
     * @param block Action to perform when value changes
     */
    @ImGuiDsl
    inline fun inputFloat(
        label: String,
        value: KMutableProperty0<Float>,
        step: Float = 0f,
        stepFast: Float = 0f,
        decimals: Int = 2,
        format: String = "%.${decimals}f",
        flags: Int = ImGuiInputTextFlags.None,
        block: (ImFloat) -> Unit = {}
    ) = withFloat(value) { inputFloat(label, it, step, stepFast, decimals, format, flags, block) }

    @ImGuiDsl
    inline fun inputFloat(
        label: String,
        value: ImFloat,
        step: Float = 0f,
        stepFast: Float = 0f,
        decimals: Int = 2,
        format: String = "%.${decimals}f",
        flags: Int = ImGuiInputTextFlags.None,
        block: (ImFloat) -> Unit = {}
    ) {
        if (inputFloat(label, value, step, stepFast, format, flags))
            block(value)
    }

    /**
     * Creates a 2-component float input field.
     *
     * @param label Label for the input
     * @param values Current float values (will be modified)
     * @param format Display format string
     * @param flags Input flags
     * @param block Action to perform when values change
     */
    @ImGuiDsl
    inline fun inputFloat2(
        label: String,
        values: FloatArray,
        decimals: Int = 2,
        format: String = "%.${decimals}f",
        flags: Int = ImGuiInputTextFlags.None,
        block: (FloatArray) -> Unit = {}
    ) {
        if (inputFloat2(label, values, format, flags))
            block(values)
    }

    @ImGuiDsl
    inline fun inputVec2f(
        label: String,
        vec: Vec2f,
        decimals: Int = 2,
        format: String = "%.${decimals}f",
        flags: Int = ImGuiInputTextFlags.None,
        block: (Vec2f) -> Unit = {}
    ) {
        val floats = floatArrayOf(vec.x, vec.y)

        if (inputFloat2(label, floats, format, flags))
            block(Vec2f(floats[0], floats[1]))
    }

    /**
     * Creates a 3-component float input field.
     *
     * @param label Label for the input
     * @param values Current float values (will be modified)
     * @param format Display format string
     * @param flags Input flags
     * @param block Action to perform when values change
     */
    @ImGuiDsl
    inline fun inputFloat3(
        label: String,
        values: FloatArray,
        decimals: Int = 2,
        format: String = "%.${decimals}f",
        flags: Int = ImGuiInputTextFlags.None,
        block: (FloatArray) -> Unit = {}
    ) {
        if (inputFloat3(label, values, format, flags))
            block(values)
    }

    /**
     * Creates a 4-component float input field.
     *
     * @param label Label for the input
     * @param values Current float values (will be modified)
     * @param format Display format string
     * @param flags Input flags
     * @param block Action to perform when values change
     */
    @ImGuiDsl
    inline fun inputFloat4(
        label: String,
        values: FloatArray,
        decimals: Int = 2,
        format: String = "%.${decimals}f",
        flags: Int = ImGuiInputTextFlags.None,
        block: (FloatArray) -> Unit = {}
    ) {
        if (inputFloat4(label, values, format, flags))
            block(values)
    }

    /**
     * Creates a single double input field.
     *
     * @param label Label for the input
     * @param value Current double value
     * @param step Step for increment/decrement
     * @param stepFast Fast step for increment/decrement
     * @param format Display format string
     * @param flags Input flags
     * @param block Action to perform when value changes
     */
    @ImGuiDsl
    inline fun inputDouble(
        label: String,
        value: KMutableProperty0<Double>,
        step: Double = 0.0,
        stepFast: Double = 0.0,
        decimals: Int = 2,
        format: String = "%.${decimals}f",
        flags: Int = ImGuiInputTextFlags.None,
        block: (Double) -> Unit = {},
    ) = withDouble(value) {
        if (inputDouble(label, it, step, stepFast, format, flags)) {
            value.set(it.get())
            block(it.get())
        }
    }

    /**
     * Creates a [Vec2d] input field.
     *
     * @param label Label for the input
     * @param vec Current float values (will be modified)
     * @param format Display format string
     * @param flags Input flags
     * @param block Action to perform when values change
     */
    @ImGuiDsl
    inline fun inputVec2d(
        label: String,
        vec: Vec2d,
        decimals: Int = 2,
        format: String = "%.${decimals}f",
        flags: Int = ImGuiInputTextFlags.None,
        block: (Vec2d) -> Unit = {},
    ) {
        val doubles = floatArrayOf(vec.x.toFloat(), vec.y.toFloat())

        if (inputFloat3(label, doubles, format, flags))
            block(Vec2d(doubles[0], doubles[1]))
    }

    /**
     * Creates a [Vec3d] input field.
     *
     * @param label Label for the input
     * @param vec Current float values (will be modified)
     * @param format Display format string
     * @param flags Input flags
     * @param block Action to perform when values change
     */
    @ImGuiDsl
    inline fun inputVec3d(
        label: String,
        vec: Vec3d,
        decimals: Int = 2,
        format: String = "%.${decimals}f",
        flags: Int = ImGuiInputTextFlags.None,
        block: (Vec3d) -> Unit = {},
    ) {
        val doubles = floatArrayOf(vec.x.toFloat(), vec.y.toFloat(), vec.z.toFloat())

        if (inputFloat3(label, doubles, format, flags))
            block(Vec3d(doubles[0].toDouble(), doubles[1].toDouble(), doubles[2].toDouble()))
    }

    /**
     * Creates a text input field.
     *
     * @param label Label for the input
     * @param value Current text value
     * @param flags Input flags
     * @param block Action to perform when text changes
     *
     * @see ImGuiInputTextFlags
     */
    @ImGuiDsl
    inline fun inputText(
        label: String,
        value: KMutableProperty0<String>,
        flags: Int = ImGuiInputTextFlags.None,
        block: (ImString) -> Unit = {},
    ) = withString(value) { inputText(label, it, flags, block) }

    @ImGuiDsl
    inline fun inputText(
        label: String,
        value: ImString,
        flags: Int = ImGuiInputTextFlags.None,
        block: (ImString) -> Unit = {},
    ) {
        if (ImGui.inputText(label, value, flags))
            block(value)
    }

    /**
     * Creates a multi-line text input field with optional size constraints.
     *
     * @param label Label displayed for the input field
     * @param value Mutable string property that holds the current text value
     * @param width Width of the input field in pixels (0.0f = auto-resize)
     * @param height Height of the input field in pixels (0.0f = auto-resize)
     * @param flags Additional input flags
     * @param block Optional callback that executes when the text changes,
     *              providing access to the underlying ImString
     *
     * @see ImGuiInputTextFlags
     */
    @ImGuiDsl
    inline fun inputTextMultiline(
        label: String,
        value: KMutableProperty0<String>,
        width: Float = 0f,
        height: Float = 0f,
        flags: Int = 0,
        block: (ImString) -> Unit = {},
    ) = withString(value) { inputTextMultiline(label, it, width, height, flags, block) }

    @ImGuiDsl
    inline fun inputTextMultiline(
        label: String,
        value: ImString,
        width: Float = 0f,
        height: Float = 0f,
        flags: Int = 0,
        block: (ImString) -> Unit = {},
    ) {
        if (ImGui.inputTextMultiline(label, value, width, height, flags))
            block(value)
    }

    /**
     * Color button and picker combined
     */
    @ImGuiDsl
    inline fun colorEdit(
        label: String,
        color: KMutableProperty0<Color>,
        flags: Int = ImGuiColorEditFlags.None,
        block: (Color) -> Unit = {},
    ) {
        val default = floatArrayOf(0f, 0f, 0f, 0f)
        val col = color.get()
        val components = col.getComponents(default)

        if (colorEdit4(label, components)) {
            val (r, g, b, a) = components

            color.set(Color(r, g, b, a))
            block(col)
        }
    }

    /**
     * Creates a color picker.
     *
     * @param label Label for the picker
     * @param color Current color value
     * @param flags Picker flags
     * @param block Action to perform when color changes
     *
     * @see ImGuiColorEditFlags
     */
    @ImGuiDsl
    @JvmName("colorPickerReference")
    inline fun colorPicker(
        label: String,
        color: KMutableProperty0<Color>,
        flags: Int = ImGuiColorEditFlags.None,
        block: (Color) -> Unit = {},
    ) {
        val default = floatArrayOf(0f, 0f, 0f, 0f)
        val col = color.get()
        val components = col.getComponents(default)

        if (colorPicker4(label, components, flags)) {
            val (r, g, b, a) = components

            color.set(Color(r, g, b, a))
            block(col)
        }
    }

    @ImGuiDsl
    inline fun colorPicker(
        label: String,
        color: KMutableProperty0<FloatArray>,
        flags: Int = ImGuiColorEditFlags.None,
        block: (FloatArray) -> Unit = {},
    ) {
        val col = color.get()
        if (colorPicker4(label, col, flags)) {
            color.set(col)
            block(col)
        }
    }

    /**
     * Creates a color button.
     *
     * @param descId Description ID
     * @param color Color value
     * @param flags Button flags
     * @param block Action to perform when clicked
     *
     * @see ImGuiColorEditFlags
     */
    @ImGuiDsl
    inline fun colorButton(
        descId: String,
        color: Color,
        flags: Int = ImGuiColorEditFlags.None,
        block: ProcedureBlock = {}
    ) {
        val floats = floatArrayOf(0f, 0f, 0f, 0f)
        val (r, g, b, a) = color.getColorComponents(floats)

        if (colorButton(descId, r, g, b, a, flags))
            block()
    }

    /**
     * Creates a tree node.
     *
     * @param label Label for the node
     * @param block Content of the node when expanded
     */
    @ImGuiDsl
    inline fun treeNode(label: String, block: ProcedureBlock) {
        if (treeNode(label)) {
            block()
            treePop()
        }
    }

    /**
     * Creates a tree node with a unique ID.
     *
     * @param label Label for the node
     * @param id Unique identifier
     * @param block Content of the node when expanded
     */
    @ImGuiDsl
    inline fun treeNode(label: String, id: String, block: ProcedureBlock) {
        if (treeNode(label, id)) {
            block()
            treePop()
        }
    }

    /**
     * Creates a collapsing header.
     *
     * @param label Label for the header
     * @param flags TreeNode flags
     * @param block Content of the header when expanded
     *
     * @see ImGuiTreeNodeFlags
     */
    @ImGuiDsl
    inline fun collapsingHeader(label: String, flags: Int = ImGuiTreeNodeFlags.None, block: ProcedureBlock) {
        if (collapsingHeader(label, flags))
            block()
    }

    /**
     * Creates a text filer
     */
    @ImGuiDsl
    inline fun filter(label: String, defaultFilter: String = "", block: (ImGuiTextFilter) -> Unit) =
        ImGuiTextFilter(defaultFilter).apply { draw(label) }.apply(block)

    /**
     * Creates a selectable item.
     *
     * @param label Label for the item
     * @param selected Whether the item is selected
     * @param flags Selectable flags
     * @param size Size of the item
     * @param block Action to perform when clicked
     *
     * @see ImGuiSelectableFlags
     */
    @ImGuiDsl
    inline fun selectable(
        label: String,
        selected: Boolean = false,
        flags: Int = ImGuiSelectableFlags.None,
        size: ImVec2 = ImVec2(),
        block: ProcedureBlock = {},
    ) {
        if (ImGui.selectable(label, selected, flags, size))
            block()
    }

    /**
     * Creates a list box.
     *
     * @param label Label for the list
     * @param currentItem Currently selected index
     * @param items Items in the list
     * @param heightInItems Height in items (-1 = auto)
     */
    @ImGuiDsl
    fun listBox(
        label: String,
        currentItem: KMutableProperty0<Int>,
        items: Array<String>,
        heightInItems: Int = -1,
    ) = withInt(currentItem) { listBox(label, it, items, heightInItems) }

    @ImGuiDsl
    fun listBox(
        label: String,
        currentItem: KMutableProperty0<Int>,
        items: Collection<String>,
        heightInItems: Int = -1,
    ) = listBox(label, currentItem, items.toTypedArray(), heightInItems)

    @ImGuiDsl
    fun listBox(
        label: String,
        currentItem: ImInt,
        items: Array<String>,
        heightInItems: Int = -1,
    ) = ImGui.listBox(label, currentItem, items, heightInItems)

    @ImGuiDsl
    fun listBox(
        label: String,
        currentItem: ImInt,
        items: Collection<String>,
        heightInItems: Int = -1,
    ) = listBox(label, currentItem, items.toTypedArray(), heightInItems)

    /**
     * Creates a plot of float values.
     *
     * @param label Label for the plot
     * @param values Values to plot
     * @param valuesOffset Offset in the values array
     * @param overlayText Text to overlay
     * @param scaleMin Minimum scale value
     * @param scaleMax Maximum scale value
     * @param graphSize Size of the graph
     * @param stride Sample decimation step (>= 1). Use 0/1 for contiguous data.
     */
    @ImGuiDsl
    fun plotLines(
        label: String,
        values: FloatArray,
        valuesOffset: Int = 0,
        overlayText: String = "",
        scaleMin: Float = Float.MAX_VALUE,
        scaleMax: Float = Float.MAX_VALUE,
        graphSize: ImVec2 = ImVec2(),
        stride: Int = 0,
    ) {
        val (src, sMin, sMax) = preparePlotSeries(values, stride, scaleMin, scaleMax)
        val count = src.size
        val offset = if (count == 0) 0 else valuesOffset.coerceIn(0, count - 1)
        if (count < 2) {
            plotLines(label, floatArrayOf(), 0, 0, overlayText, sMin, sMax, graphSize.x, graphSize.y)
            return
        }
        plotLines(label, src, count, offset, overlayText, sMin, sMax, graphSize.x, graphSize.y)
    }

    /**
     * Creates a plot of histogram values.
     *
     * @param label Label for the plot
     * @param values Values to plot
     * @param valuesOffset Offset in the values array
     * @param overlayText Text to overlay
     * @param scaleMin Minimum scale value
     * @param scaleMax Maximum scale value
     * @param graphSize Size of the graph
     * @param stride Sample decimation step (>= 1). Use 0/1 for contiguous data.
     */
    @ImGuiDsl
    fun plotHistogram(
        label: String,
        values: FloatArray,
        valuesOffset: Int = 0,
        overlayText: String = "",
        scaleMin: Float = Float.MAX_VALUE,
        scaleMax: Float = Float.MAX_VALUE,
        graphSize: ImVec2 = ImVec2(),
        stride: Int = 0,
    ) {
        val (src, sMin, sMax) = preparePlotSeries(values, stride, scaleMin, scaleMax)
        val count = src.size
        val offset = if (count == 0) 0 else valuesOffset.coerceIn(0, count - 1)
        if (count < 1) {
            plotHistogram(label, floatArrayOf(), 0, 0, overlayText, sMin, sMax, graphSize.x, graphSize.y)
            return
        }
        plotHistogram(label, src, count, offset, overlayText, sMin, sMax, graphSize.x, graphSize.y)
    }

    private fun preparePlotSeries(
        values: FloatArray,
        stride: Int,
        scaleMin: Float,
        scaleMax: Float
    ): Triple<FloatArray, Float, Float> {
        val contiguous = (stride <= 1)
        val src = if (contiguous) {
            values
        } else {
            val outSize = (values.size + stride - 1) / stride
            val out = FloatArray(outSize)
            var i = 0
            var j = 0
            while (i < values.size) {
                out[j++] = values[i]
                i += stride
            }
            out
        }
        var sMin = scaleMin
        var sMax = scaleMax
        if (sMin == Float.MAX_VALUE && sMax == Float.MAX_VALUE) {
            var minV = Float.POSITIVE_INFINITY
            var maxV = Float.NEGATIVE_INFINITY
            for (v in src) if (v.isFinite()) {
                if (v < minV) minV = v
                if (v > maxV) maxV = v
            }
            if (!minV.isFinite() || !maxV.isFinite()) {
                minV = 0f; maxV = 1f
            }
            if (minV == maxV) {
                val base = if (minV == 0f) 1f else kotlin.math.abs(minV)
                val pad = base * 0.01f
                minV -= pad
                maxV += pad
            }
            sMin = minV
            sMax = maxV
        }
        return Triple(src, sMin, sMax)
    }

    /**
     * Creates a main menu bar.
     *
     * @param block Content of the menu bar
     */
    @ImGuiDsl
    inline fun mainMenuBar(block: ProcedureBlock) {
        if (beginMainMenuBar()) {
            block()
            endMainMenuBar()
        }
    }

    /**
     * Append to menu-bar of current window (requires ImGuiWindowFlags_MenuBar flag set on parent window).
     *
     * @param block Content of the menu bar
     */
    @ImGuiDsl
    inline fun menuBar(block: ProcedureBlock) {
        if (beginMenuBar()) {
            block()
            endMenuBar()
        }
    }

    /**
     * Create a sub-menu entry.
     *
     * @param label Label for the menu
     * @param enabled Whether the menu is enabled
     * @param block Content of the menu
     */
    @ImGuiDsl
    inline fun menu(label: String, enabled: Boolean = true, block: ProcedureBlock) {
        if (beginMenu(label, enabled)) {
            block()
            endMenu()
        }
    }

    /**
     * Creates an item in the sub-menu
     *
     * Shortcuts are displayed for convenience but not processed by ImGui at the moment.
     *
     * @param label Label for the item
     * @param shortcut Shortcut text
     * @param selected Whether the item is selected
     * @param enabled Whether the item is enabled
     * @param block Action to perform when clicked
     */
    @ImGuiDsl
    inline fun menuItem(
        label: String,
        shortcut: String = "",
        selected: Boolean = false,
        enabled: Boolean = true,
        block: ProcedureBlock = {},
    ) {
        if (ImGui.menuItem(label, shortcut, selected, enabled))
            block()
    }

    /**
     * Creates a tooltip window.
     *
     * @param block Content of the tooltip
     */
    @ImGuiDsl
    inline fun tooltip(block: ProcedureBlock) {
        beginTooltip()
        block()
        endTooltip()
    }

    /**
     * Displays a tooltip with the specified description when the current ImGui item is hovered.
     *
     * @param description The text content to display in the tooltip.
     */
    @ImGuiDsl
    fun lambdaTooltip(description: String) {
        if (description.isBlank()) return
        onItemHover(ClickGui.tooltipType.flag) {
            tooltip {
                withTextWrapPos(fontSize * 35f) {
                    textUnformatted(description)
                }
            }
        }
    }

    @ImGuiDsl
    fun lambdaTooltip(description: () -> String) {
        lambdaTooltip(description())
    }

    /**
     * Creates a popup.
     *
     * @param strId Unique identifier
     * @param flags Popup flags
     * @param block Content of the popup
     */
    @ImGuiDsl
    inline fun popup(strId: String, flags: Int = ImGuiPopupFlags.None, block: ProcedureBlock) {
        if (beginPopup(strId, flags)) {
            block()
            endPopup()
        }
    }

    /**
     * Creates a context popup for an item.
     *
     * @param strId Unique identifier
     * @param popupFlags Popup flags
     * @param block Content of the popup
     */
    @ImGuiDsl
    inline fun popupContextItem(
        strId: String = "",
        popupFlags: Int = ImGuiPopupFlags.MouseButtonRight,
        block: ProcedureBlock
    ) {
        if (beginPopupContextItem(strId, popupFlags)) {
            block()
            endPopup()
        }
    }

    /**
     * Creates a context popup for a window.
     *
     * @param strId Unique identifier
     * @param popupFlags Popup flags
     * @param block Content of the popup
     */
    @ImGuiDsl
    inline fun popupContextWindow(
        strId: String = "",
        popupFlags: Int = ImGuiPopupFlags.MouseButtonRight,
        block: ProcedureBlock
    ) {
        if (beginPopupContextWindow(strId, popupFlags)) {
            block()
            endPopup()
        }
    }

    /**
     * Creates a context popup in empty space.
     *
     * @param strId Unique identifier
     * @param popupFlags Popup flags
     * @param block Content of the popup
     */
    @ImGuiDsl
    inline fun popupContextVoid(
        strId: String = "",
        popupFlags: Int = ImGuiPopupFlags.MouseButtonRight,
        block: ProcedureBlock
    ) {
        if (beginPopupContextVoid(strId, popupFlags)) {
            block()
            endPopup()
        }
    }

    /**
     * Creates a modal popup.
     *
     * @param title Title of the modal
     * @param value Boolean reference to control visibility
     * @param windowFlags Additional window flags
     * @param block Content of the modal
     *
     * @see ImGuiPopupFlags
     */
    @ImGuiDsl
    inline fun popupModal(
        title: String,
        value: KMutableProperty0<Boolean>,
        windowFlags: Int = ImGuiWindowFlags.None,
        block: ProcedureBlock,
    ) {
        if (withBool(value) { beginPopupModal(title, it, windowFlags) }) {
            block()
            endPopup()
        }
    }

    @ImGuiDsl
    inline fun popupModal(
        title: String,
        windowFlags: Int = ImGuiWindowFlags.None,
        block: ProcedureBlock,
    ) {
        if (beginPopupModal(title, windowFlags)) {
            block()
            endPopup()
        }
    }

    /**
     * Creates a tab bar.
     *
     * @param strId Unique identifier
     * @param flags Tab bar flags
     * @param block Content of the tab bar
     *
     * @see ImGuiTabBarFlags
     */
    @ImGuiDsl
    inline fun tabBar(strId: String, flags: Int = ImGuiTabBarFlags.None, block: ProcedureBlock) {
        if (beginTabBar(strId, flags)) {
            block()
            endTabBar()
        }
    }

    /**
     * Creates a tab item.
     *
     * @param label Label for the tab
     * @param value Boolean reference to control visibility
     * @param flags Tab item flags
     * @param block Content of the tab
     *
     * @see ImGuiTabBarFlags
     */
    @ImGuiDsl
    inline fun tabItem(
        label: String,
        value: KMutableProperty0<Boolean>,
        flags: Int = 0,
        block: ProcedureBlock,
    ) {
        if (withBool(value) { beginTabItem(label, it, flags) }) {
            block()
            endTabItem()
        }
    }

    @ImGuiDsl
    inline fun tabItem(
        label: String,
        flags: Int = ImGuiTabBarFlags.None,
        block: ProcedureBlock,
    ) {
        if (beginTabItem(label, flags)) {
            block()
            endTabItem()
        }
    }

    /**
     * Creates a drag and drop source.
     *
     * @param flags Drag and drop flags
     * @param block Content of the source
     */
    @ImGuiDsl
    inline fun dragDropSource(flags: Int = 0, block: ProcedureBlock) {
        if (beginDragDropSource(flags)) {
            block()
            endDragDropSource()
        }
    }

    /**
     * Creates a drag and drop target.
     *
     * You can call AcceptDragDropPayload() in the [block]
     *
     * @param block Content of the target
     */
    @ImGuiDsl
    inline fun dragDropTarget(block: ProcedureBlock) {
        if (beginDragDropTarget()) {
            block()
            endDragDropTarget()
        }
    }

    /**
     * Places the next item on the same line.
     *
     * @param offsetFromStartX Offset from the start
     * @param spacing Spacing between items
     */
    @ImGuiDsl
    fun sameLine(offsetFromStartX: Float = 0f, spacing: Float = -1f) =
        ImGui.sameLine(offsetFromStartX, spacing)

    /**
     * Adds vertical spacing.
     */
    @ImGuiDsl
    fun spacing() = ImGui.spacing()

    /**
     * Adds a separator line.
     */
    @ImGuiDsl
    fun separator() = ImGui.separator()

    /**
     * Indents the next widgets.
     * @param indentWidth Width of the indent
     */
    @ImGuiDsl
    fun indent(indentWidth: Float = 0f) = ImGui.indent(indentWidth)

    /**
     * Unindents the next widgets.
     * @param indentWidth Width of the unindent
     */
    @ImGuiDsl
    fun unindent(indentWidth: Float = 0f) = ImGui.unindent(indentWidth)

    /**
     * Groups the next widgets together.
     *
     * @param block Content of the group
     */
    @ImGuiDsl
    inline fun group(block: ProcedureBlock) {
        beginGroup()
        block()
        endGroup()
    }

    /**
     * Creates a new line.
     */
    @ImGuiDsl
    fun newLine() = ImGui.newLine()

    /**
     * Moves to the next column (in a columns layout).
     */
    @ImGuiDsl
    fun nextColumn() = ImGui.nextColumn()

    /**
     * Sets the scroll position.
     *
     * @param x Horizontal scroll position ratio
     * @param y Vertical scroll position ratio
     */
    @ImGuiDsl
    fun setScrollHere(x: Float = 0.5f, y: Float = 0.5f) {
        setScrollHereX(x)
        setScrollHereY(y)
    }

    /**
     * Sets the vertical scroll position.
     *
     * @param y Vertical scroll position ratio
     */
    @ImGuiDsl
    fun setScrollHereY(y: Float = 0.5f) = ImGui.setScrollHereY(y)

    /**
     * Sets the horizontal scroll position.
     * @param centerXRatio Ratio for centering
     */
    @ImGuiDsl
    fun setScrollHereX(centerXRatio: Float = 0.5f) = ImGui.setScrollHereX(centerXRatio)

    /**
     * Push integer into the ID stack (will hash integer).
     *
     * @param id Integer ID
     * @param block Content of the scope
     */
    @ImGuiDsl
    inline fun withId(id: Int, block: ProcedureBlock) {
        pushID(id)
        block()
        popID()
    }

    /**
     * Push string into the ID stack (will hash string).
     *
     * @param id String ID
     * @param block Content of the scope
     */
    @ImGuiDsl
    inline fun withId(id: String, block: ProcedureBlock) {
        pushID(id)
        block()
        popID()
    }

    /**
     * Push integer into the ID stack (will hash integer).
     *
     * @param id Enum ID (uses ordinal)
     * @param block Content of the scope
     */
    @ImGuiDsl
    inline fun <E : Enum<E>> withId(id: E, block: ProcedureBlock) {
        pushID(id.ordinal)
        block()
        popID()
    }

    /**
     * Creates a scope with custom style colors.
     *
     * @param idx Color index (see ImGuiCol)
     * @param col Color value (RGB)
     * @param block Content of the scope
     *
     * See: https://github.com/ocornut/imgui/wiki/Styling
     */
    @ImGuiDsl
    inline fun withStyleColor(idx: Int, col: Int, block: ProcedureBlock) {
        pushStyleColor(idx, col)
        block()
        popStyleColor()
    }

    @ImGuiDsl
    inline fun withStyleColor(idx: Int, color: Color, block: ProcedureBlock) =
        withStyleColor(idx, color.rgb, block)

    @ImGuiDsl
    inline fun withStyleColor(idx: Int, red: Float, green: Float, blue: Float, alpha: Float, block: ProcedureBlock) {
        pushStyleColor(idx, red, green, blue, alpha)
        block()
        popStyleColor()
    }

    @ImGuiDsl
    inline fun withStyleColor(idx: Int, color: FloatArray, block: ProcedureBlock) {
        pushStyleColor(idx, color[0], color[1], color[2], color[3])
        block()
        popStyleColor()
    }

    @ImGuiDsl
    inline fun withStyleColor(idx: Int, red: Int, green: Int, blue: Int, alpha: Int, block: ProcedureBlock) {
        pushStyleColor(idx, red, green, blue, alpha)
        block()
        popStyleColor()
    }

    /**
     * Creates a scope with custom style variables.
     *
     * @param styleVar Style variable index
     * @param value Float value
     * @param block Content of the scope
     *
     * @see imgui.flag.ImGuiStyleVar
     */
    @ImGuiDsl
    inline fun withStyleVar(styleVar: Int, value: Float, block: ProcedureBlock) {
        pushStyleVar(styleVar, value)
        block()
        popStyleVar()
    }

    /**
     * Creates a scope with custom style variables.
     *
     * @param styleVar Style variable index
     * @param valueX First float value
     * @param valueY Second float value
     * @param block Content of the scope
     *
     * * @see imgui.flag.ImGuiStyleVar
     */
    @ImGuiDsl
    inline fun withStyleVar(styleVar: Int, valueX: Float, valueY: Float, block: ProcedureBlock) {
        pushStyleVar(styleVar, valueX, valueY)
        block()
        popStyleVar()
    }

    /**
     * Push width of items for common large "item+label" widgets.
     *
     * - if >0.0f: width in pixels
     * - if <0.0f align xx pixels to the right of window
     *
     * (so -1.0f always align width to the right side).
     *
     * @param itemWidth Width in pixels
     * @param block Content of the scope
     */
    @ImGuiDsl
    inline fun withItemWidth(itemWidth: Int, block: ProcedureBlock) = withItemWidth(itemWidth.toFloat(), block)

    @ImGuiDsl
    inline fun withItemWidth(itemWidth: Float, block: ProcedureBlock) {
        pushItemWidth(itemWidth)
        block()
        popItemWidth()
    }

    /**
     * Push Word-wrapping positions for Text*() commands.
     * - if <0.0f: no wrapping
     * - if 0.0f: wrap to end of window (or column)
     * - if >0.0f: wrap at 'wrap_posX' position in window local space
     *
     * @param wrapPos Position to wrap at
     * @param block Content of the scope
     */
    @ImGuiDsl
    inline fun withTextWrapPos(wrapPos: Float = 0f, block: ProcedureBlock) {
        pushTextWrapPos(wrapPos)
        block()
        popTextWrapPos()
    }

    /**
     * Creates a scope with custom font.
     *
     * @param font Font to use
     * @param block Content of the scope
     */
    @ImGuiDsl
    inline fun withFont(font: ImFont, block: ProcedureBlock) {
        pushFont(font)
        block()
        popFont()
    }

    @ImGuiDsl
    inline fun <T> withBool(property: KMutableProperty0<Boolean>, block: WrappedBlock<ImBoolean, T>) =
        ImBoolean(property()).let { v -> block(v).also { property.set(v.get()) } }

    @ImGuiDsl
    inline fun withBool(value: Boolean, block: WrappedBlock<ImBoolean, Unit>) =
        block(ImBoolean(value))

    @ImGuiDsl
    inline fun withFloat(value: Float, block: WrappedBlock<ImFloat, Unit>) =
        block(ImFloat(value))

    @ImGuiDsl
    inline fun withDouble(value: Double, block: WrappedBlock<ImDouble, Unit>) =
        block(ImDouble(value))

    @ImGuiDsl
    inline fun <T : Any> withDouble(property: KMutableProperty0<Double>, block: WrappedBlock<ImDouble, T>) =
        ImDouble(property()).let { v -> block(v).also { property.set(v.get()) } }

    @ImGuiDsl
    inline fun <T : Any> withFloat(property: KMutableProperty0<Float>, block: WrappedBlock<ImFloat, T>) =
        ImFloat(property()).let { v -> block(v).also { property.set(v.get()) } }

    @ImGuiDsl
    inline fun <T : Any> withInt(property: KMutableProperty0<Int>, block: WrappedBlock<ImInt, T>) =
        ImInt(property()).let { v -> block(v).also { property.set(v.get()) } }

    @ImGuiDsl
    inline fun <T : Any> withString(
        property: KMutableProperty0<String>,
        allowedChars: String = "",
        isResizable: Boolean = true,
        size: Int = 10,
        block: WrappedBlock<ImString, T>,
    ) {
        val string = ImString(property())
        string.inputData.allowedChars = allowedChars
        string.inputData.isResizable = isResizable
        string.inputData.resizeFactor = size

        block(string)

        property.set(string.get())
    }

    /**
     * Gets the current draw list for custom drawing.
     */
    @ImGuiDsl
    val windowDrawList: ImDrawList get() = getWindowDrawList()

    /**
     * Gets the background draw list for custom drawing.
     */
    @ImGuiDsl
    val backgroundDrawList: ImDrawList get() = getBackgroundDrawList()

    /**
     * Gets the foreground draw list for custom drawing.
     */
    @ImGuiDsl
    val foregroundDrawList: ImDrawList get() = getForegroundDrawList()

    /**
     * Represents the minimum X-coordinate of the current item's rectangle in the UI.
     *
     * This value is typically used to calculate the dimensions or positioning
     * of graphical elements relative to the current UI item.
     */
    @ImGuiDsl
    val itemRectMinX: Float get() = getItemRectMinX()

    @ImGuiDsl
    val itemRectMinY: Float get() = getItemRectMinY()

    @ImGuiDsl
    val itemRectMaxX: Float get() = getItemRectMaxX()

    @ImGuiDsl
    val itemRectMaxY: Float get() = getItemRectMaxY()

    @ImGuiDsl
    val frameHeight: Float get() = getFrameHeight()

    @ImGuiDsl
    val frameHeightWithSpacing: Float get() = getFrameHeightWithSpacing()

    @ImGuiDsl
    val windowContentRegionMaxX: Float get() = getWindowContentRegionMaxX()

    @ImGuiDsl
    val windowContentRegionMaxY: Float get() = getWindowContentRegionMaxY()

    /**
     * Creates a frame with optional border.
     */
    @ImGuiDsl
    fun ImDrawList.addFrame(
        minX: Float,
        minY: Float,
        maxX: Float,
        maxY: Float,
        col: Int,
        border: Boolean = true,
        rounding: Float = getStyle().frameRounding
    ) {
        addRectFilled(minX, minY, maxX, maxY, col, rounding)

        val borderSize = getStyle().frameBorderSize

        if (border && borderSize > 0) {
            addRect(
                minX + 1f, minY + 1f, maxX + 1f, maxY + 1f,
                getColorU32(ImGuiCol.BorderShadow), rounding, ImDrawListFlags.None, borderSize
            )

            addRect(
                minX, minY, maxX, maxY,
                getColorU32(ImGuiCol.Border), rounding, ImDrawListFlags.None, borderSize
            )
        }
    }

    @ImGuiDsl
    var cursorPosX: Float get() = getCursorPosX(); set(value) {
        setCursorPosX(value)
    }

    @ImGuiDsl
    var cursorPosY: Float get() = getCursorPosY(); set(value) {
        setCursorPosY(value)
    }

    @ImGuiDsl
    fun imageHorizontallyCentered(textureId: Long, width: Float, height: Float) {
        val contentW = getContentRegionAvail().x
        val offsetX = (contentW - width) * 0.5f
        cursorPosX += maxOf(0f, offsetX)
        image(textureId, width, height)
    }

    @ImGuiDsl
    fun buildLayout(block: ProcedureBlock) {
        block()
    }
}
