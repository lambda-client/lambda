package com.lambda.client.module.modules.render

import com.lambda.client.event.SafeClientEvent
import com.lambda.client.event.events.RenderWorldEvent
import com.lambda.client.module.Category
import com.lambda.client.module.Module
import com.lambda.client.util.TickTimer
import com.lambda.client.util.color.ColorHolder
import com.lambda.client.util.graphics.ESPRenderer
import com.lambda.client.util.graphics.GeometryMasks
import com.lambda.client.util.math.VectorUtils.distanceTo
import com.lambda.client.util.threads.defaultScope
import com.lambda.client.util.threads.safeListener
import kotlinx.coroutines.launch
import net.minecraft.util.math.AxisAlignedBB
import net.minecraft.util.math.BlockPos

object VoidESP : Module(
    name = "VoidESP",
    description = "Highlights holes leading to the void",
    category = Category.RENDER
) {
    private val filled by setting("Filled", true)
    private val outline by setting("Outline", true)
    private val color by setting("Color", ColorHolder(148, 161, 255), false)
    private val aFilled by setting("Filled Alpha", 127, 0..255, 1)
    private val aOutline by setting("Outline Alpha", 255, 0..255, 1)
    private val renderMode by setting("Mode", Mode.BLOCK_HOLE)
    private val range by setting("Range", 8, 4..32, 1, unit = " blocks")
    private val dangerous by setting("Show safe void", false, description = "Show all void holes, rather than just dangerous ones")

    private enum class Mode {
        BLOCK_HOLE, BLOCK_VOID, FLAT
    }

    private val renderer = ESPRenderer()
    private val timer = TickTimer()

    init {
        safeListener<RenderWorldEvent> {
            if (timer.tick(133L)) { // Avoid running this on a tick
                updateRenderer()
            }
            renderer.render(false)
        }
    }

    private fun SafeClientEvent.updateRenderer() {
        renderer.aFilled = if (filled) aFilled else 0
        renderer.aOutline = if (outline) aOutline else 0

        val color = color.clone()
        val side = if (renderMode != Mode.FLAT) GeometryMasks.Quad.ALL else GeometryMasks.Quad.DOWN

        defaultScope.launch {
            val cached = ArrayList<Triple<AxisAlignedBB, ColorHolder, Int>>()

            for (x in -range..range) for (z in -range..range) {
                val pos = BlockPos(player.posX + x, 0.0, player.posZ + z)
                if (player.distanceTo(pos) > range) continue
                if (!isVoid(pos)) continue

                val renderPos = if (renderMode == Mode.BLOCK_VOID) pos.down() else pos
                cached.add(Triple(AxisAlignedBB(renderPos), color, side))
            }

            renderer.replaceAll(cached)
        }
    }

    private fun SafeClientEvent.isVoid(pos: BlockPos) =
        if (dangerous) {
            world.isAirBlock(pos)
        } else world.isAirBlock(pos)
            && world.isAirBlock(pos.up())
            && world.isAirBlock(pos.up().up())
}
