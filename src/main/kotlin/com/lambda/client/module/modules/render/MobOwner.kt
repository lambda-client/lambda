package com.lambda.client.module.modules.render

import com.lambda.client.commons.utils.MathUtils.round
import com.lambda.client.manager.managers.UUIDManager
import com.lambda.client.module.Category
import com.lambda.client.module.Module
import com.lambda.client.util.threads.runSafe
import com.lambda.client.util.threads.safeListener
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
            for (entity in world.loadedEntityList) {
                /* Non Horse types, such as wolves */
                if (entity is EntityTameable) {
                    val ownerUUID = entity.ownerId
                    if (!entity.isTamed || ownerUUID == null) continue
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
                    entity.customNameTag = "${if (entityData.originalCustomNameTag != "") "${entityData.originalCustomNameTag} | " else ""}Owner: " + ownerName + getHealth(entity)
                    entityData.previous = entity.customNameTag
                }

                if (entity is AbstractHorse) {
                    val ownerUUID = entity.ownerUniqueId
                    if (!entity.isTame || ownerUUID == null) continue
                    val owner = if (nullUUIDs.contains(ownerUUID)) null else UUIDManager.getByUUID(ownerUUID)
                    val ownerName = if (owner == null) {
                        nullUUIDs.add(ownerUUID)
                        invalidText
                    } else {
                        owner.name
                    }
                    val entityData = originalCustomNameTags.getOrPut(entity.uniqueID) { EntityData(entity.customNameTag) }
                    if (entityData.previous != null && entity.customNameTag != entityData.previous) {
                        entityData.originalCustomNameTag = entity.customNameTag
                    }
                    entity.alwaysRenderNameTag = true
                    entity.customNameTag = "${if (entityData.originalCustomNameTag != "") "${entityData.originalCustomNameTag} | " else ""}Owner: " + ownerName + getSpeed(entity) + getJump(entity) + getHealth(entity)
                    entityData.previous = entity.customNameTag
                }
            }
        }

        onDisable {
            runSafe {
                for (entity in world.loadedEntityList) {
                    // Revert customNameTag back to original.
                    val entityData = originalCustomNameTags[entity.uniqueID]
                    entity.customNameTag = entityData?.originalCustomNameTag ?: entity.customNameTag
                    try {
                        entity.alwaysRenderNameTag = false
                    } catch (_: Exception) {
                        // Ignored
                    }
                }
                originalCustomNameTags.clear()
                nullUUIDs.clear()
            }
        }
    }

    private fun getSpeed(horse: AbstractHorse): String {
        return if (!speed) "" else " S: " + round(43.17 * horse.aiMoveSpeed, 2)
    }

    private fun getJump(horse: AbstractHorse): String {
        return if (!jump) "" else " J: " + round(-0.1817584952 * horse.horseJumpStrength.pow(3.0) + 3.689713992 * horse.horseJumpStrength.pow(2.0) + 2.128599134 * horse.horseJumpStrength - 0.343930367, 2)
    }

    private fun getHealth(horse: AbstractHorse): String {
        return if (!hp) "" else " HP: " + round(horse.health, 2)
    }

    private fun getHealth(tameable: EntityTameable): String {
        return if (!hp) "" else " HP: " + round(tameable.health, 2)
    }
}