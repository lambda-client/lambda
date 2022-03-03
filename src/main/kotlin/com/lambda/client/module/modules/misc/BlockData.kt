package com.lambda.client.module.modules.misc

import com.lambda.client.module.Category
import com.lambda.client.module.Module
import com.lambda.client.util.TickTimer
import com.lambda.client.util.text.MessageSendHelper
import com.lambda.client.util.threads.safeListener
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.RayTraceResult
import net.minecraftforge.fml.common.gameevent.InputEvent
import org.lwjgl.input.Mouse

object BlockData : Module(
    name = "BlockData",
    description = "Right click blocks to display their data",
    category = Category.MISC
) {
    private val timer = TickTimer()
    private var lastPos = BlockPos.ORIGIN

    init {
        safeListener<InputEvent.MouseInputEvent> {
            mc.objectMouseOver?.let {
                if (Mouse.getEventButton() != 1 || it.typeOfHit != RayTraceResult.Type.BLOCK) return@safeListener

                if (timer.tick(5000L) || it.blockPos != lastPos && timer.tick(500L)) {
                    val blockPos = it.blockPos
                    val blockState = world.getBlockState(blockPos)
                    lastPos = blockPos

                    if (blockState.block.hasTileEntity(blockState)) {
                        val tileEntity = world.getTileEntity(blockPos) ?: return@safeListener
                        val tag = NBTTagCompound().apply { tileEntity.writeToNBT(this) }
                        MessageSendHelper.sendChatMessage("""$chatName &6Block Tags:$tag""".trimIndent())
                    }
                }
            }
        }
    }
}