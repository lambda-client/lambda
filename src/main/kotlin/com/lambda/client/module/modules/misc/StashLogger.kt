package com.lambda.client.module.modules.misc

import com.lambda.client.commons.extension.synchronized
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
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import net.minecraft.client.audio.PositionedSoundRecord
import net.minecraft.entity.Entity
import net.minecraft.entity.item.EntityMinecartChest
import net.minecraft.entity.item.EntityMinecartContainer
import net.minecraft.entity.item.EntityMinecartHopper
import net.minecraft.init.SoundEvents
import net.minecraft.tileentity.*
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.ChunkPos
import net.minecraftforge.fml.common.gameevent.TickEvent
import java.text.SimpleDateFormat
import java.util.*
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
    private val logMinecartChests by setting("Minecart Chests", true)
    private val minecartChestDensity by setting("Min Minecart Chests", 5, 1..20, 1, { logMinecartChests })
    private val logMinecartHoppers by setting("Minecart Hoppers", true)
    private val minecartHopperDensity by setting("Min Minecart Hoppers", 5, 1..20, 1, { logMinecartHoppers })

    private val disableAutoWalk by setting("Disable Auto Walk", false, description = "Disables AutoWalk when a stash is found")
    private val cancelBaritone by setting("Cancel Baritone", false, description = "Cancels Baritone when a stash is found")

    private val chunkData = LinkedHashMap<Long, ChunkStats>()
    private val knownPositions = HashSet<BlockPos>()
    private val knownEntityMinecartContainers = HashSet<EntityMinecartContainer>()
    private val timer = TickTimer(TimeUnit.SECONDS)

    init {
        safeListener<TickEvent.ClientTickEvent> {
            if (it.phase != TickEvent.Phase.END || !timer.tick(3L)) return@safeListener

            defaultScope.launch {
                coroutineScope {
                    launch {
                        world.loadedEntityList.toList().filterIsInstance<EntityMinecartContainer>().forEach(::logEntityMinecartContainer)
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
                val timeStr = SimpleDateFormat.getDateTimeInstance().format(Calendar.getInstance().time)
                MessageSendHelper.sendChatMessage("$chatName Found $string at ($positionString) [$timeStr]")
            }

            found = true
        }

        if (found) {
            if (disableAutoWalk && AutoWalk.isEnabled) AutoWalk.disable()
            if (cancelBaritone && (BaritoneUtils.isPathing || BaritoneUtils.isActive)) BaritoneUtils.cancelEverything()
        }
    }

    private fun logEntityMinecartContainer(entity: EntityMinecartContainer) {
        if (!checkEntityType(entity)) return
        if (!knownEntityMinecartContainers.add(entity)) return

        val chunk = ChunkPos.asLong(entity.position.x shr 4, entity.position.z shr 4)
        val chunkStats = chunkData.getOrPut(chunk, ::ChunkStats)
        chunkStats.addEntity(entity)
    }

    private fun checkEntityType(entity: Entity) =
        logMinecartChests && entity is EntityMinecartChest
            || logMinecartHoppers && entity is EntityMinecartHopper

    private fun logTileEntity(tileEntity: TileEntity) {
        if (!checkTileEntityType(tileEntity)) return
        if (!knownPositions.add(tileEntity.pos)) return

        val chunk = ChunkPos.asLong(tileEntity.pos.x shr 4, tileEntity.pos.z shr 4)
        val chunkStats = chunkData.getOrPut(chunk, ::ChunkStats)

        chunkStats.addTileEntity(tileEntity)
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
        var minecartChests = 0; private set
        var minecartHoppers = 0; private set

        var hot = false

        private val tileEntities = ArrayList<TileEntity>().synchronized()
        private val entityMinecartContainers = ArrayList<EntityMinecartContainer>().synchronized()

        fun addEntity(entity: EntityMinecartContainer) {
            when (entity) {
                is EntityMinecartChest -> minecartChests++
                is EntityMinecartHopper -> minecartHoppers++
                else -> return
            }

            entityMinecartContainers.add(entity)

            if (minecartChests >= minecartChestDensity
                || minecartHoppers >= minecartHopperDensity) {
                hot = true
            }
        }

        fun addTileEntity(tileEntity: TileEntity) {
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
            val size = tileEntities.size.or(entityMinecartContainers.size)

            for (entity in entityMinecartContainers) {
                x += entity.position.x
                y += entity.position.y
                z += entity.position.z
            }

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
            if (minecartChests > 0 && logMinecartChests) statList.add("$minecartChests minecart chest${if (minecartChests == 1) "" else "s"}")
            if (minecartHoppers > 0 && logMinecartHoppers) statList.add("$minecartHoppers minecart hopper${if (minecartHoppers == 1) "" else "s"}")

            return statList.joinToString()
        }
    }
}
