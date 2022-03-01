package com.lambda.client.module.modules.movement

import com.lambda.client.module.Category
import com.lambda.client.module.Module
import com.lambda.client.util.threads.safeListener
import net.minecraft.init.Blocks
import net.minecraftforge.fml.common.gameevent.TickEvent

object IceSpeed : Module(
    name = "IceSpeed",
    description = "Changes how slippery ice is",
    category = Category.MOVEMENT
) {
    private val slipperiness by setting("Slipperiness", 0.4f, 0.3f..0.5f, 0.005f)

    init {
        safeListener<TickEvent.ClientTickEvent> {
            Blocks.ICE.setDefaultSlipperiness(slipperiness)
            Blocks.PACKED_ICE.setDefaultSlipperiness(slipperiness)
            Blocks.FROSTED_ICE.setDefaultSlipperiness(slipperiness)
        }

        onDisable {
            Blocks.ICE.setDefaultSlipperiness(0.98f)
            Blocks.PACKED_ICE.setDefaultSlipperiness(0.98f)
            Blocks.FROSTED_ICE.setDefaultSlipperiness(0.98f)
        }
    }
}