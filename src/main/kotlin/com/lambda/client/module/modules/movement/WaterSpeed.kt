package com.lambda.client.module.modules.movement

import com.lambda.client.module.Category
import com.lambda.client.module.Module
import com.lambda.client.util.threads.safeListener
import net.minecraftforge.fml.common.gameevent.TickEvent

internal object WaterSpeed : Module(
    name = "WaterSpeed",
    category = Category.MISC,
    description = "Sink and Rise faster"
) {
    private val forward by setting("Forward Control", true)
    private val up by setting("Fast Up", true)
    private val down by setting("Fast Down", true)
    private val sprint by setting("Toggle Sprint", true)

    //Trial and error found these numbers. Blame the 3 o'clock code for jank

    init {
        safeListener<TickEvent.ClientTickEvent> {
                if (sprint && (mc.player.isInLava || mc.player.isInWater)) {
                    mc.player.isSprinting = true
                }
                if ((mc.player.isInWater || mc.player.isInLava) && mc.gameSettings.keyBindJump.isKeyDown && up) {
                    mc.player.motionY = 0.725 / 5
                }
                if (mc.player.isInWater || mc.player.isInLava) {
                    if (forward && mc.gameSettings.keyBindForward.isKeyDown || mc.gameSettings.keyBindLeft.isKeyDown || mc.gameSettings.keyBindRight.isKeyDown || mc.gameSettings.keyBindBack.isKeyDown) {
                        mc.player.jumpMovementFactor = 0.34f / 5
                    } else {
                        mc.player.jumpMovementFactor = 0.0f
                    }
                }
                if (mc.player.isInWater && down && mc.gameSettings.keyBindSneak.isKeyDown) {
                    mc.player.motionY = 2.2 / -5
                }
                if (mc.player.isInLava && down && mc.gameSettings.keyBindSneak.isKeyDown) {
                    mc.player.motionY = 0.91 / -5
                }
        }
    }
}