package com.lambda.modules

import com.lambda.BladePackPlugin
import com.lambda.client.event.events.RenderWorldEvent
import com.lambda.client.module.Category
import com.lambda.client.plugin.api.PluginModule
import com.lambda.client.util.color.ColorHolder
import com.lambda.client.util.graphics.ESPRenderer
import com.lambda.client.util.graphics.LambdaTessellator
import com.lambda.client.util.math.VectorUtils.toBlockPos
import com.lambda.client.util.threads.safeListener
import net.minecraft.init.Blocks
import net.minecraft.network.play.client.CPacketPlayerDigging
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.RayTraceResult
import net.minecraftforge.fml.common.gameevent.TickEvent

object CivBreak : Module(
    name = "CivBreak",
    category = Category.PLAYER,
    description = "Break blocks just left-clicking on them",
    alias = arrayOf("CivBreak", "ClickBreak", "FastBreak", "PacketBreak")
) {
    private val color by setting("Color", ColorHolder(200, 80, 80))
    private val filled by setting("Filled", true)
    private val outline by setting("Outline", true)
    private val aFilled by setting("Filled Alpha", 63, 0..255, 1, { filled })
    private val aOutline by setting("Outline Alpha", 200, 0..255, 1, { outline })
    private val thickness by setting("Line Thickness", 2.0f, 0.25f..5.0f, 0.25f)

    private var target = BlockPos(0, -1, 0)
    private val renderer = ESPRenderer()

    var isCivBreaking = false

    init{
        safeListener<RenderWorldEvent> {
            if(isCivBreaking) {
                val blockState = world.getBlockState(target)
                val box = blockState.getSelectedBoundingBox(world, target)
                val side = (GeometryMasks.Quad.ALL)
                renderer.add(box.grow(0.004), color, side)
                renderer.render(true)


                renderer.aFilled = if (filled) aFilled else 0
                renderer.aOutline = if (outline) aOutline else 0
                renderer.thickness = thickness
                renderer.add(target, color)
                renderer.render(clear = true, cull = false)
            }
        }
        safeListener<TickEvent.ClientTickEvent> {
            val viewEntity = mc.renderViewEntity ?: player
            val eyePos = viewEntity.getPositionEyes(LambdaTessellator.pTicks())
            if (!world.isAirBlock(eyePos.toBlockPos())) return@safeListener
            val hitObject = mc.objectMouseOver ?: return@safeListener

            if (hitObject.typeOfHit == RayTraceResult.Type.BLOCK && mc.gameSettings.keyBindAttack.isKeyDown) {
                setTartget(hitObject.blockPos)
            }
            if(isBreaked(target)){
                renderer.clear()
                resetBreak()
            }

        }

        onEnable {
            resetBreak()
        }

        onDisable {
            resetBreak()
        }
    }
    private fun setTartget(blockPos: BlockPos){
        if(target != blockPos &&
            mc.world.getBlockState(blockPos).block != Blocks.BEDROCK &&
            mc.world.getBlockState(blockPos).block != Blocks.BARRIER
        ){

            target = blockPos
            doBreak()
        }
    }
    private fun doBreak(){
        resetBreak()

        isCivBreaking = true
        val packetStart = CPacketPlayerDigging(CPacketPlayerDigging.Action.START_DESTROY_BLOCK, target, mc.player.horizontalFacing)
        val packetStop = CPacketPlayerDigging(CPacketPlayerDigging.Action.STOP_DESTROY_BLOCK, target, mc.player.horizontalFacing)
        mc.player.connection.sendPacket(packetStart)
        mc.player.connection.sendPacket(packetStop)
    }
    private fun resetBreak(){
        isCivBreaking = false
        mc.player.connection.sendPacket(CPacketPlayerDigging(CPacketPlayerDigging.Action.ABORT_DESTROY_BLOCK, target, mc.player.horizontalFacing))
    }
    private fun isBreaked(pos: BlockPos): Boolean {
        return try {
            mc.world.getBlockState(pos).block == Blocks.AIR
        } catch (e: NullPointerException) {
            false
        }
    }
}
