package com.lambda.client.gui.hudgui.elements.world

import com.lambda.client.event.SafeClientEvent
import com.lambda.client.gui.hudgui.LabelHud
import com.lambda.client.util.AsyncCachedValue
import com.lambda.client.util.EntityUtils.isHostile
import com.lambda.client.util.EntityUtils.isNeutral
import com.lambda.client.util.EntityUtils.isPassive
import com.lambda.client.util.items.originalName
import com.lambda.client.util.threads.runSafe
import net.minecraft.entity.Entity
import net.minecraft.entity.item.*
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.entity.projectile.EntityEgg
import net.minecraft.entity.projectile.EntitySnowball
import net.minecraft.entity.projectile.EntityWitherSkull
import net.minecraft.tileentity.TileEntityLockable
import java.util.*

internal object EntityList : LabelHud(
    name = "EntityList",
    category = Category.WORLD,
    description = "List of entities nearby"
) {

    private val item by setting("Items", true)
    private val passive by setting("Passive Mobs", true)
    private val neutral by setting("Neutral Mobs", true)
    private val hostile by setting("Hostile Mobs", true)
    private val tileEntities by setting("Tile Entities", true)
    private val onlyContainer by setting("Only Containers", true, { tileEntities })
    private val maxEntries by setting("Max Entries", 8, 4..32, 1)
    private val range by setting("Range", 64, 16..256, 16, fineStep = 1)

    private var remainingEntriesEntity = 0
    private var remainingEntriesTileEntity = 0

    private val entityCacheMap by AsyncCachedValue(50L) {
        val map = TreeMap<String, Int>()

        runSafe {
            for (entity in world.loadedEntityList.toList()) {
                if (entity == null) continue
                if (entity == player || entity == mc.renderViewEntity) continue

                if (!item && entity is EntityItem) continue
                if (!passive && entity.isPassive) continue
                if (!neutral && entity.isNeutral) continue
                if (!hostile && entity.isHostile) continue

                if (player.getDistance(entity) > range) continue

                val name = entity.entityListName

                if (entity is EntityItem) {
                    map[name] = map.getOrDefault(name, 0) + entity.item.count
                } else {
                    map[name] = map.getOrDefault(name, 0) + 1
                }
            }
        }

        remainingEntriesEntity = map.size - maxEntries
        map.entries.take(maxEntries)
    }

    private val tileEntityCacheMap by AsyncCachedValue(50L) {
        val map = TreeMap<String, Int>()

        runSafe {
            if (tileEntities) world.loadedTileEntityList.filter {
                if (onlyContainer) it is TileEntityLockable else true
            }.mapNotNull {
                map[it.blockType.localizedName] = map.getOrDefault(it.blockType.localizedName, 0) + 1
            }
        }

        remainingEntriesTileEntity = map.size - maxEntries
        map.entries.take(maxEntries)
    }

    override fun SafeClientEvent.updateText() {
        if (entityCacheMap.isNotEmpty()) {
            if (tileEntities) displayText.addLine("Entities", secondaryColor)
            for ((name, count) in entityCacheMap) {
                displayText.add(name, primaryColor)
                displayText.addLine("x$count", secondaryColor)
            }
            if (remainingEntriesEntity > 0) {
                displayText.addLine("...and $remainingEntriesEntity more")
            }
        }

        if (tileEntityCacheMap.isNotEmpty()) {
            displayText.addLine("TileEntities", secondaryColor)
            for ((name, count) in tileEntityCacheMap) {
                displayText.add(name, primaryColor)
                displayText.addLine("x$count", secondaryColor)
            }
            if (remainingEntriesTileEntity > 0) {
                displayText.addLine("...and $remainingEntriesTileEntity more")
            }
        }
    }

    private val Entity.entityListName
        get() = when (this) {
            is EntityPlayer -> {
                "Player"
            }
            is EntityItem -> {
                this.item.originalName
            }
            is EntityWitherSkull -> {
                "Wither skull"
            }
            is EntityEnderCrystal -> {
                "End crystal"
            }
            is EntityEnderPearl -> {
                "Thrown ender pearl"
            }
            is EntityMinecart -> {
                "Minecart"
            }
            is EntityItemFrame -> {
                "Item frame"
            }
            is EntityEgg -> {
                "Thrown egg"
            }
            is EntitySnowball -> {
                "Thrown snowball"
            }
            else -> {
                this.name ?: this.javaClass.simpleName
            }
        }
}
