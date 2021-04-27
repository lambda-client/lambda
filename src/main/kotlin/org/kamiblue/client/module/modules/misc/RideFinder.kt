package org.kamiblue.client.module.modules.misc

import net.minecraft.entity.Entity
import net.minecraft.entity.passive.EntityDonkey
import net.minecraft.entity.passive.EntityHorse
import net.minecraft.entity.passive.EntityLlama
import net.minecraft.entity.passive.EntitySkeletonHorse
import net.minecraftforge.fml.common.gameevent.TickEvent
import org.kamiblue.client.module.Category
import org.kamiblue.client.module.Module
import org.kamiblue.client.util.Wrapper.world
import org.kamiblue.client.util.text.MessageSendHelper
import org.kamiblue.event.listener.listener
import kotlin.math.roundToInt


internal object RideFinder : Module(
    name = "RideFinder",
    category = Category.MISC,
    description = "Assist in finding rideable entities"
) {
    private val llama by setting("Detect llamas", false)
    private val entities: MutableSet<Entity> = HashSet()

    init {
        onEnable {
            entities.clear()
        }

        listener<TickEvent.ClientTickEvent> {
            if (world == null) return@listener
            for (entity in mc.world.loadedEntityList) {
                if (entities.contains(entity)) continue
                if (entity is EntityDonkey || entity is EntityHorse || entity is EntitySkeletonHorse || (entity is EntityLlama && llama))
                MessageSendHelper.sendChatMessage(entity.name + " located at: (" + entity.posX.roundToInt().toString() + ", " + entity.posY.roundToInt().toString() + ", " + entity.posZ.roundToInt().toString() + ")")
                entities.add(entity)
            }
        }
    }
}