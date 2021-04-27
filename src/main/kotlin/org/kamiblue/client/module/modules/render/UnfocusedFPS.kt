package org.kamiblue.client.module.modules.render

import net.minecraft.client.Minecraft
import net.minecraftforge.fml.common.gameevent.TickEvent
import org.kamiblue.client.module.Category
import org.kamiblue.client.module.Module
import org.kamiblue.client.util.threads.safeListener
import org.lwjgl.opengl.Display

internal object UnfocusedFPS : Module(
    name = "UnfocusedFPS",
    description = "Reduces framerate when the game is minimized",
    category = Category.RENDER
) {

    private val FpsCap by setting("Frame Cap", 20, 5..120, 5)

    var Settings = Minecraft.getMinecraft().gameSettings

    init {
        safeListener<TickEvent.ClientTickEvent> {
            if (!Display.isActive()) Settings.limitFramerate = FpsCap
             else Settings.limitFramerate = 260 //According to my options folder, 260 is VSync
        }
    }
}