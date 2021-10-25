package com.lambda.client.gui.clickgui

import com.lambda.client.gui.AbstractLambdaGui
import com.lambda.client.gui.clickgui.component.ModuleButton
import com.lambda.client.gui.clickgui.component.PluginButton
import com.lambda.client.gui.clickgui.window.ModuleSettingWindow
import com.lambda.client.gui.rgui.Component
import com.lambda.client.gui.rgui.windows.ListWindow
import com.lambda.client.module.AbstractModule
import com.lambda.client.module.ModuleManager
import com.lambda.client.module.modules.client.ClickGUI
import com.lambda.client.plugin.PluginManager
import com.lambda.client.util.math.Vec2f
import org.lwjgl.input.Keyboard

object LambdaClickGui : AbstractLambdaGui<ModuleSettingWindow, AbstractModule>() {

    private val windows = ArrayList<ListWindow>()
    private var pluginWindow: ListWindow

    init {
        val allButtons = ModuleManager.modules
            .groupBy { it.category.displayName }
            .mapValues { (_, modules) -> modules.map { ModuleButton(it) } }

        var posX = 0.0f
        var posY = 0.0f
        val screenWidth = mc.displayWidth / ClickGUI.getScaleFactorFloat()

        /* Modules */
        for ((category, buttons) in allButtons) {
            val window = ListWindow(category, posX, posY, 90.0f, 300.0f, Component.SettingGroup.CLICK_GUI)

            window.children.addAll(buttons.customSort())
            windows.add(window)
            posX += 90.0f

            if (posX > screenWidth) {
                posX = 0.0f
                posY += 100.0f
            }
        }

        /* Plugins */
        pluginWindow = ListWindow("Plugins", posX, posY, 90.0f, 300.0f, Component.SettingGroup.CLICK_GUI)
        windows.add(pluginWindow)

        windowList.addAll(windows)
    }

    override fun onDisplayed() {
        updatePlugins()
        reorderModules()

        super.onDisplayed()
    }

    override fun onGuiClosed() {
        super.onGuiClosed()
        setModuleButtonVisibility { true }
    }

    override fun newSettingWindow(element: AbstractModule, mousePos: Vec2f): ModuleSettingWindow {
        return ModuleSettingWindow(element, mousePos.x, mousePos.y)
    }

    override fun keyTyped(typedChar: Char, keyCode: Int) {
        if (keyCode == Keyboard.KEY_ESCAPE || keyCode == ClickGUI.bind.value.key && !searching && settingWindow?.listeningChild == null) {
            ClickGUI.disable()
        } else {
            super.keyTyped(typedChar, keyCode)

            val string = typedString.replace(" ", "")

            if (string.isNotEmpty()) {
                setModuleButtonVisibility { moduleButton ->
                    moduleButton.module.name.contains(string, true)
                        || moduleButton.module.alias.any { it.contains(string, true) }
                }
            } else {
                setModuleButtonVisibility { true }
            }
        }
    }

    private fun setModuleButtonVisibility(function: (ModuleButton) -> Boolean) {
        windowList.filterIsInstance<ListWindow>().forEach {
            for (child in it.children) {
                if (child !is ModuleButton) continue
                child.visible = function(child)
            }
        }
    }

    private fun updatePlugins() {
        PluginManager.loadedPlugins.forEach { plugin ->
            if (pluginWindow.children.none { it.name == plugin.name }) {
                pluginWindow.children.add(PluginButton(plugin))
            }
        }
    }

    fun reorderModules() {
        val allButtons = ModuleManager.modules
            .groupBy { it.category.displayName }
            .mapValues { (_, modules) -> modules.map { ModuleButton(it) } }

        windows.forEach { window ->
            if (window != pluginWindow) {
                window.children.clear()
                allButtons[window.name]?.let { window.children.addAll(it.customSort()) }
            }
        }
    }

    private fun List<ModuleButton>.customSort(): List<ModuleButton> {
        return when (ClickGUI.sortBy.value) {
            ClickGUI.SortByOptions.CUSTOM -> this.sortedByDescending { it.module.priorityForGui.value }
            ClickGUI.SortByOptions.FREQUENCY -> this.sortedByDescending { it.module.clicks.value }
            else -> this.sortedBy { it.name }
        }
    }
}