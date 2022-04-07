package com.lambda.client.module.modules.misc

import com.lambda.client.manager.managers.WaypointManager
import com.lambda.client.module.Category
import com.lambda.client.module.Module
import com.lambda.client.module.modules.movement.AutoWalk
import com.lambda.client.util.BaritoneUtils
import com.lambda.client.util.TickTimer
import com.lambda.client.util.TimeUnit
import com.lambda.client.util.math.CoordinateConverter.asString
import com.lambda.client.util.text.MessageSendHelper
import com.lambda.client.util.threads.defaultScope
import com.lambda.client.util.threads.onMainThread
import com.lambda.client.util.threads.safeListener
import com.lambda.client.commons.extension.synchronized
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import net.minecraft.client.audio.PositionedSoundRecord
import net.minecraft.init.SoundEvents
import net.minecraft.tileentity.*
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.ChunkPos
import net.minecraftforge.fml.common.gameevent.TickEvent
import kotlin.math.roundToInt

object StashLogger : Module(
    name = "StashLogger",
    description = "Logs storage units in render distance",
    category = Category.MISC
) {
    private val saveToWaypoints by setting("Save To Waypoints", true)
    private val logToChat by setting("Log To Chat", true)
    private val playSound by setting("Play Sound", true)
    private val logChests by setting("Chests", true)
    private val chestDensity by setting("Min Chests", 5, 1..20, 1, { logChests })
    private val logShulkers by setting("Shulkers", true)
    private val shulkerDensity by setting("Min Shulkers", 1, 1..20, 1, { logShulkers })
    private val logDroppers by setting("Droppers", true)
    private val dropperDensity by setting("Min Droppers", 5, 1..20, 1, { logDroppers })
    private val logDispensers by setting("Dispensers", true)
    private val dispenserDensity by setting("Min Dispensers", 5, 1..20, 1, { logDispensers })
    private val logHoppers by setting("Hoppers", true)
    private val hopperDensity by setting("Min Hoppers", 5, 1..20, 1, { logHoppers })
    private val disableAutoWalk by setting("Disable Auto Walk", false, description = "Disables AutoWalk when a stash is found")
    private val cancelBaritone by setting("Cancel Baritone", false, description = "Cancels Baritone when a stash is found")

    private val chunkData = LinkedHashMap<Long, ChunkStats>()
    private val knownPositions = HashSet<BlockPos>()
    private val timer = TickTimer(TimeUnit.SECONDS)

    init {
        safeListener<TickEvent.ClientTickEvent> {
            if (it.phase != TickEvent.Phase.END || !timer.tick(3L)) return@safeListener

            defaultScope.launch {
                coroutineScope {
                    launch {
                        world.loadedTileEntityList.toList().forEach(::logTileEntity)
                        notification()
                    }
                }
            }
        }
    }

    private suspend fun notification() {
        var found = false

        for (chunkStats in chunkData.values) {
            if (!chunkStats.hot) continue

            chunkStats.hot = false
            val center = chunkStats.center()
            val string = chunkStats.toString()

            if (saveToWaypoints) {
                WaypointManager.add(center, string)
            }

            if (playSound) {
                onMainThread {
                    mc.soundHandler.playSound(PositionedSoundRecord.getRecord(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f))
                }
            }

            if (logToChat) {
                val positionString = center.asString()
                MessageSendHelper.sendChatMessage("$chatName Found $string at ($positionString) ")
            }

            found = true
        }

        if (found) {
            if (disableAutoWalk && AutoWalk.isEnabled) AutoWalk.disable()
            if (cancelBaritone && (BaritoneUtils.isPathing || BaritoneUtils.isActive)) BaritoneUtils.cancelEverything()
        }
    }

    private fun logTileEntity(tileEntity: TileEntity) {
        if (!checkTileEntityType(tileEntity)) return
        if (!knownPositions.add(tileEntity.pos)) return

        val chunk = ChunkPos.asLong(tileEntity.pos.x shr 4, tileEntity.pos.z shr 4)
        val chunkStats = chunkData.getOrPut(chunk, ::ChunkStats)

        chunkStats.add(tileEntity)
    }

    private fun checkTileEntityType(tileEntity: TileEntity) =
        logChests && tileEntity is TileEntityChest
            || logShulkers && tileEntity is TileEntityShulkerBox
            || logDroppers && tileEntity is TileEntityDropper
            || logDispensers && tileEntity is TileEntityDispenser
            || logHoppers && tileEntity is TileEntityHopper

    private class ChunkStats {
        var chests = 0; private set
        var shulkers = 0; private set
        var droppers = 0; private set
        var dispensers = 0; private set
        var hoppers = 0; private set

        var hot = false

        private val tileEntities = ArrayList<TileEntity>().synchronized()

        fun add(tileEntity: TileEntity) {
            when (tileEntity) {
                is TileEntityChest -> chests++
                is TileEntityShulkerBox -> shulkers++
                is TileEntityDropper -> droppers++
                is TileEntityDispenser -> dispensers++
                is TileEntityHopper -> hoppers++
                else -> return
            }

            tileEntities.add(tileEntity)

            if (chests >= chestDensity
                || shulkers >= shulkerDensity
                || droppers >= dropperDensity
                || dispensers >= dispenserDensity
                || hoppers >= hopperDensity) {
                hot = true
            }
        }

        fun center(): BlockPos {
            var x = 0.0
            var y = 0.0
            var z = 0.0
            val size = tileEntities.size

            for (tileEntity in tileEntities) {
                x += tileEntity.pos.x
                y += tileEntity.pos.y
                z += tileEntity.pos.z
            }

            x /= size
            y /= size
            z /= size

            return BlockPos(x.roundToInt(), y.roundToInt(), z.roundToInt())
        }

        override fun toString(): String {
            val statList = mutableListOf<String>()
            if (chests > 0 && logChests) statList.add("$chests chest${if (chests == 1) "" else "s"}")
            if (shulkers > 0 && logShulkers) statList.add("$shulkers shulker${if (shulkers == 1) "" else "s"}")
            if (droppers > 0 && logDroppers) statList.add("$droppers dropper${if (droppers == 1) "" else "s"}")
            if (dispensers > 0 && logDispensers) statList.add("$dispensers dispenser${if (dispensers == 1) "" else "s"}")
            if (hoppers > 0 && logHoppers) statList.add("$hoppers hopper${if (hoppers == 1) "" else "s"}")
            return statList.joinToString()
        }
    }
}
