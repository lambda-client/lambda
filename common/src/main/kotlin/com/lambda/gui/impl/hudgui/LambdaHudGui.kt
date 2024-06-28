package com.lambda.gui.impl.hudgui

import com.lambda.gui.HudGuiConfigurable
import com.lambda.gui.api.GuiEvent
import com.lambda.gui.impl.AbstractClickGui
import com.lambda.gui.impl.clickgui.LambdaClickGui
import com.lambda.module.HudModule
import com.lambda.module.Module
import com.lambda.module.ModuleRegistry
import com.lambda.util.Mouse
import com.lambda.util.math.Vec2d

object LambdaHudGui : AbstractClickGui("HudGui") {
    override val moduleFilter: (Module) -> Boolean = {
        it is HudModule
    }

    override val configurable get() = HudGuiConfigurable
    private val hudModules get() = ModuleRegistry.modules.filterIsInstance<HudModule>()

    private var dragInfo: Pair<Vec2d, HudModule>? = null

    override fun onEvent(e: GuiEvent) {
        super.onEvent(e)

        when (e) {
            is GuiEvent.Show -> {
                dragInfo = null

                setCloseTask {
                    LambdaClickGui.show()
                }
            }

            is GuiEvent.MouseMove -> {
                if (closing) dragInfo = null

                dragInfo?.let {
                    it.second.position = e.mouse - it.first
                }
            }

            is GuiEvent.MouseClick -> {
                dragInfo = null

                if (hoveredWindow == null &&
                    e.action == Mouse.Action.Click &&
                    e.button == Mouse.Button.Left
                ) hudModules.filter(Module::isEnabled).firstOrNull { e.mouse in it.rect }?.let {
                    dragInfo = e.mouse - it.position to it
                }
            }
        }
    }
}