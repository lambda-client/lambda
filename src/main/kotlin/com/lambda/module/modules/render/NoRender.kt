/*
 * Copyright 2025 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.lambda.module.modules.render

import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.util.reflections.scanResult
import io.github.classgraph.ClassInfo
import net.minecraft.block.entity.BlockEntity
import net.minecraft.client.particle.Particle
import net.minecraft.client.render.BackgroundRenderer.StatusEffectFogModifier
import net.minecraft.entity.Entity
import net.minecraft.entity.SpawnGroup
import net.minecraft.entity.effect.StatusEffects

object NoRender : Module(
    name = "NoRender",
    description = "Disables rendering of certain things",
    tag = ModuleTag.RENDER,
) {
    private val entities = scanResult
        .getSubclasses(Entity::class.java)
        .asSequence()
        .filter { !it.isAbstract && it.name.startsWith("net.minecraft") }

    private val particleMap = createParticleNameMap()
    private val blockEntityMap = createBlockEntityNameMap()
    private val playerEntityMap = createEntityNameMap("net.minecraft.client.network.")
    private val bossEntityMap = createEntityNameMap("net.minecraft.entity.boss.")
    private val decorationEntityMap = createEntityNameMap("net.minecraft.entity.decoration.")
    private val mobEntityMap = createEntityNameMap("net.minecraft.entity.mob.")
    private val passiveEntityMap = createEntityNameMap("net.minecraft.entity.passive.")
    private val projectileEntityMap = createEntityNameMap("net.minecraft.entity.projectile.")
    private val vehicleEntityMap = createEntityNameMap("net.minecraft.entity.vehicle.")
    private val miscEntityMap = createEntityNameMap("net.minecraft.entity.", strictDir = true)

    @JvmStatic val noBlindness by setting("No Blindness", true)
    @JvmStatic val noDarkness by setting("No Darkness", true)
    @JvmStatic val noBurning by setting("No Burning Overlay", true)
    @JvmStatic val fireOverlayYOffset by setting("Fire Overlay Y Offset", -0.3, -0.8..0.0, 0.1) { !noBurning }
    @JvmStatic val noUnderwater by setting("No Underwater Overlay", true)
    @JvmStatic val noInWall by setting("No In Wall Overlay", true)
    @JvmStatic val noChatVerificationToast by setting("No Chat Verification Toast", true)
    private val particles by setting("Particles", particleMap.values.toSet(), emptySet(), "Particles to omit from rendering")
    private val playerEntities by setting("Player Entities", playerEntityMap.values.toSet(), emptySet(), "Player entities to omit from rendering")
    private val bossEntities by setting("Boss Entities", bossEntityMap.values.toSet(), emptySet(), "Boss entities to omit from rendering")
    private val decorationEntities by setting("Decoration Entities", decorationEntityMap.values.toSet(), emptySet(), "Decoration entities to omit from rendering")
    private val mobEntities by setting("Mob Entities", mobEntityMap.values.toSet(), emptySet(), "Mob entities to omit from rendering")
    private val passiveEntities by setting("Passive Entities", passiveEntityMap.values.toSet(), emptySet(), "Passive entities to omit from rendering")
    private val projectileEntities by setting("Projectile Entities", projectileEntityMap.values.toSet(), emptySet(), "Projectile entities to omit from rendering")
    private val vehicleEntities by setting("Vehicle Entities", vehicleEntityMap.values.toSet(), emptySet(), "Vehicle entities to omit from rendering")
    private val miscEntities by setting("Misc Entities", miscEntityMap.values.toSet(), emptySet(), "Miscellaneous entities to omit from rendering")
    private val blockEntities by setting("Block Entities", blockEntityMap.values.toSet(), emptySet(), "Block entities to omit from rendering")

    private fun createParticleNameMap(): Map<String, String> {
        val subClasses = scanResult
            .getSubclasses(Particle::class.java)
            .filter { !it.isAbstract }
        return createNameMap(subClasses.asSequence(), "net.minecraft.client.particle.", "Particle")
    }

    private fun createEntityNameMap(directory: String, strictDir: Boolean = false): Map<String, String> {
        return createNameMap(entities, directory, "Entity", strictDir)
    }

    private fun createBlockEntityNameMap(): Map<String, String> {
        val subClasses = scanResult
            .getSubclasses(BlockEntity::class.java)
            .filter { !it.isAbstract }
        return createNameMap(subClasses.asSequence(), "net.minecraft.block.entity", "BlockEntity")
    }

    private fun createNameMap(
        items: Sequence<ClassInfo>,
        directory: String,
        removePattern: String = "",
        strictDirectory: Boolean = false
    ): Map<String, String> {
        val map = mutableMapOf<String, String>()
        items
            .filter { item ->
                if (strictDirectory) item.name.startsWith(directory) && !item.name.substring(directory.length).contains(".")
                else item.name.startsWith(directory)
            }
            .forEach { item ->
                val value = item.name
                    .substring(item.name.indexOfLast { it == '.' } + 1)
                    .replace(removePattern, "")
                    .fancyFormat()
                map[item.simpleName] = value
            }
        return map
    }

    private fun String.fancyFormat() =
        this
            .replace("$", " - ")
            .replace("(?<!\\s)[A-Z]".toRegex(), " $0")

    @JvmStatic
    fun shouldOmitParticle(particle: Particle) =
        isEnabled && particleMap[particle.javaClass.simpleName] in particles

    @JvmStatic
    fun shouldOmitEntity(entity: Entity): Boolean {
        val simpleName = entity.javaClass.simpleName
        return isEnabled && when (entity.type.spawnGroup) {
            SpawnGroup.MISC ->
                miscEntityMap[simpleName] in miscEntities ||
                        playerEntityMap[simpleName] in playerEntities ||
                        projectileEntityMap[simpleName] in projectileEntities ||
                        vehicleEntityMap[simpleName] in vehicleEntities ||
                        decorationEntityMap[simpleName] in decorationEntities ||
                        passiveEntityMap[simpleName] in passiveEntities ||
                        mobEntityMap[simpleName] in mobEntities ||
                        bossEntityMap[simpleName] in bossEntities
            SpawnGroup.WATER_AMBIENT,
            SpawnGroup.WATER_CREATURE,
            SpawnGroup.AMBIENT,
            SpawnGroup.AXOLOTLS,
            SpawnGroup.CREATURE,
            SpawnGroup.UNDERGROUND_WATER_CREATURE -> passiveEntityMap[simpleName] in passiveEntities
            SpawnGroup.MONSTER -> mobEntityMap[simpleName] in mobEntities
        }
    }

    @JvmStatic
    fun shouldOmitBlockEntity(blockEntity: BlockEntity) =
        isEnabled && blockEntityMap[blockEntity.javaClass.simpleName] in blockEntities

    @JvmStatic
    fun shouldAcceptFog(modifier: StatusEffectFogModifier) =
        when {
            isDisabled -> true
            modifier.statusEffect == StatusEffects.BLINDNESS && noBlindness -> false
            modifier.statusEffect == StatusEffects.DARKNESS && noDarkness -> false
            else -> true
        }
}