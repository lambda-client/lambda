package com.lambda.client.module.modules.misc

import com.lambda.client.module.Category
import com.lambda.client.module.Module
import com.lambda.client.util.TickTimer
import com.lambda.client.util.text.MessageSendHelper
import com.lambda.client.util.threads.safeListener
import net.minecraft.entity.Entity
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.util.math.RayTraceResult
import net.minecraftforge.fml.common.gameevent.InputEvent
import org.lwjgl.input.Mouse

object EntityTools : Module(
    name = "EntityTools",
    description = "Right click entities to perform actions on them",
    category = Category.MISC
) {
    private val mode by setting("Mode", Mode.INFO)

    private enum class Mode {
        DELETE, INFO
    }

    private val timer = TickTimer()
    private var lastEntity: Entity? = null

    init {
        safeListener<InputEvent.MouseInputEvent> {
            mc.objectMouseOver?.let {
                if (Mouse.getEventButton() != 1 || it.typeOfHit != RayTraceResult.Type.ENTITY) return@safeListener
                if (timer.tick(5000L) || it.entityHit != lastEntity && timer.tick(500L)) {
                    when (mode) {
                        Mode.DELETE -> {
                            world.removeEntity(it.entityHit)
                        }
                        Mode.INFO -> {
                            val tag = NBTTagCompound().apply { it.entityHit.writeToNBT(this) }
                            MessageSendHelper.sendChatMessage("""$chatName &6ID: ${it.entityHit.entityId} Tags:$tag""".trimIndent())
                        }
                    }
                }
            }
        }
    }
}
