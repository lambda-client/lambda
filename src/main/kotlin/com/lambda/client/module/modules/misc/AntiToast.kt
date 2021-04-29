package com.lambda.client.module.modules.misc

import net.minecraft.client.gui.GuiUtilRenderComponents
import net.minecraftforge.fml.common.gameevent.TickEvent
import com.lambda.client.module.Category
import com.lambda.client.module.Module
import com.lambda.event.listener.listener

internal object AntiToast : Module(
    name = "AntiToast",
    category = Category.MISC,
    description = "Hide Minecraft toasts"
) {

    private val constant by setting("Constant", false, description = "Forces the module to remain on, instead of instantly toggling")

    init {
        listener<TickEvent.ClientTickEvent> {
            mc.toastGui.clear()
            if (!constant) disable()
        }
    }
}