package com.lambda.client.module.modules.render

import com.lambda.client.commons.utils.MathUtils.round
import com.lambda.client.manager.managers.UUIDManager
import com.lambda.client.module.Category
import com.lambda.client.module.Module
import com.lambda.client.util.threads.runSafe
import com.lambda.client.util.threads.safeListener
import net.minecraft.entity.Entity
import net.minecraft.entity.EntityLiving
import net.minecraft.entity.passive.AbstractHorse
import net.minecraft.entity.passive.EntityTameable
import net.minecraftforge.fml.common.gameevent.TickEvent
import java.util.*
import kotlin.math.pow

private data class EntityData(var originalCustomNameTag: String, var previous: String? = null)

object MobOwner : Module(
    name = "MobOwner",
    description = "Displays the owner of tamed mobs",
    category = Category.RENDER
) {
    private val speed by setting("Speed", true)
    private val jump by setting("Jump", true)
    private val hp by setting("Health", true)

    private const val invalidText = "Offline or invalid UUID!"

    // nullUUIDs caches all UUID's that returned an empty response from the Mojang API, effectively removing the endless
    // HTTP requests and subsequent rate-limit.
    private val nullUUIDs = mutableSetOf<UUID>()

    // originalCustomNameTags does two things. It stores an EntityData data class for every tamable entity.
    // In EntityData the original customNameTag is stored that someone might have applied to the entity, a stateful
    // previous variable holds the last customNameTag to keep track when a new customNameTag is applied by a player.
    private val originalCustomNameTags = mutableMapOf<UUID, EntityData>()

    init {
        safeListener<TickEvent.ClientTickEvent> {
            /* Non Horse types, such as wolves */
            world.loadedEntityList.filterIsInstance<EntityTameable>().filter {
                it.isTamed
            }.forEach { entity ->
                entity.ownerId?.let {
                    changeEntityData(it, entity)
                }
            }

            world.loadedEntityList.filterIsInstance<AbstractHorse>().filter {
                it.isTame
            }.forEach { entity ->
                entity.ownerUniqueId?.let {
                    changeEntityData(it, entity)
                }
            }
        }

        onDisable {
            runSafe {
                // Revert customNameTag back to original.
                world.loadedEntityList.filterNotNull().forEach {
                    val entityData = originalCustomNameTags[it.uniqueID]
                    it.customNameTag = entityData?.originalCustomNameTag ?: it.customNameTag

                    it.alwaysRenderNameTag = false
                }

                originalCustomNameTags.clear()
                nullUUIDs.clear()
            }
        }
    }

    private fun changeEntityData(ownerUUID: UUID, entity: Entity) {
        val owner = if (nullUUIDs.contains(ownerUUID)) null else UUIDManager.getByUUID(ownerUUID)
        val ownerName = if (owner == null) {
            nullUUIDs.add(ownerUUID)
            invalidText
        } else {
            owner.name
        }
        val entityData = originalCustomNameTags.getOrPut(entity.uniqueID) { EntityData(originalCustomNameTag = entity.customNameTag) }
        if (entityData.previous != null && entity.customNameTag != entityData.previous) {
            entityData.originalCustomNameTag = entity.customNameTag
        }
        entity.alwaysRenderNameTag = true
        entity.customNameTag = "${if (entityData.originalCustomNameTag != "") "${entityData.originalCustomNameTag} | " else ""}Owner: " + ownerName + getData(entity as EntityLiving)
        entityData.previous = entity.customNameTag
    }

    private fun getData(entity: EntityLiving): String {
        var data = ""
        if (entity is AbstractHorse) {
            if (speed) data += " S: " + round(43.17 * entity.aiMoveSpeed, 2)
            if (jump) data += " J: " + round(-0.1817584952 * entity.horseJumpStrength.pow(3.0) + 3.689713992 * entity.horseJumpStrength.pow(2.0) + 2.128599134 * entity.horseJumpStrength - 0.343930367, 2)
        }

        if (hp) data += " HP: " + round(entity.health, 2)

        return data
    }
}