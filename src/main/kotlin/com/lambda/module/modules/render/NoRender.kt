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
import com.lambda.util.DynamicReflectionSerializer.remappedName
import com.lambda.util.NamedEnum
import com.lambda.util.reflections.scanResult
import io.github.classgraph.ClassInfo
import net.minecraft.block.entity.BlockEntity
import net.minecraft.client.particle.Particle
import net.minecraft.client.render.BackgroundRenderer.StatusEffectFogModifier
import net.minecraft.entity.Entity
import net.minecraft.entity.SpawnGroup
import net.minecraft.entity.effect.StatusEffects

//ToDo: Implement unimplemented settings. (Keep in mind compatibility with other mods like sodium)
object NoRender : Module(
    name = "NoRender",
    description = "Disables rendering of certain things",
    tag = ModuleTag.RENDER,
) {
    private val entities = scanResult
        .getSubclasses(Entity::class.java)
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

    private enum class Group(override val displayName: String) : NamedEnum {
        Hud("Hud"),
        Entity("Entity"),
        World("World"),
        Effect("Effect")
    }

    @JvmStatic val noBlindness by setting("No Blindness", true).group(Group.Effect)
    @JvmStatic val noDarkness by setting("No Darkness", true).group(Group.Effect)
    @JvmStatic val noNausea by setting("No Nausea", true).group(Group.Effect)

    @JvmStatic val noFireOverlay by setting("No Fire Overlay", false).group(Group.Hud)
    @JvmStatic val fireOverlayYOffset by setting("Fire Overlay Y Offset", 0.0, -0.4..0.4, 0.02) { !noFireOverlay }.group(Group.Hud)
    @JvmStatic val noPortalOverlay by setting("No Portal Overlay", true).group(Group.Hud)
    @JvmStatic val noFluidOverlay by setting("No Fluid Overlay", true).group(Group.Hud)
    @JvmStatic val noPowderedSnowOverlay by setting("No Powdered Snow Overlay", true).group(Group.Hud)
    @JvmStatic val noInWall by setting("No In Wall Overlay", true).group(Group.Hud)
    @JvmStatic val noPumpkinOverlay by setting("No Pumpkin Overlay", true).group(Group.Hud)
    @JvmStatic val noVignette by setting("No Vignette", true).group(Group.Hud)
    @JvmStatic val noChatVerificationToast by setting("No Chat Verification Toast", true).group(Group.Hud)
    @JvmStatic val noSpyglassOverlay by setting("No Spyglass Overlay", false).group(Group.Hud)
    @JvmStatic val noGuiShadow by setting("No Gui Shadow", false).group(Group.Hud)
    @JvmStatic val noFloatingItemAnimation by setting("No Floating Item Animation", false, "Disables floating item animations, typically used when a totem pops").group(Group.Hud)
    @JvmStatic val noCrosshair by setting("No Crosshair", false).group(Group.Hud)
    @JvmStatic val noBossBar by setting("No Boss Bar", false).group(Group.Hud)
    @JvmStatic val noScoreBoard by setting("No Score Board", false).group(Group.Hud)
    @JvmStatic val noStatusEffects by setting("No Status Effects", false).group(Group.Hud)

    @JvmStatic val noArmor by setting("No Armor", false).group(Group.Entity)
    @JvmStatic val includeNoOtherHeadItems by setting("Include No Other Head Items", false) { noArmor }.group(Group.Entity)
    @JvmStatic val noElytra by setting("No Elytra", false).group(Group.Entity)
    @JvmStatic val noInvisibility by setting("No Invisibility", true).group(Group.Entity)
    @JvmStatic val noGlow by setting("No Glow", false).group(Group.Entity)
    @JvmStatic val noNametags by setting("No Nametags", false).group(Group.Entity)
//    RenderLayer.getArmorEntityGlint(), RenderLayer.getGlint(), RenderLayer.getGlintTranslucent(), RenderLayer.getEntityGlint()
//    @JvmStatic val noEnchantmentGlint by setting("No Enchantment Glint", false).group(Group.Entity)
//    @JvmStatic val noDeadEntities by setting("No Dead Entities", false).group(Group.Entity)
    private val playerEntities by setting("Player Entities", playerEntityMap.values.toSet(), emptySet(), "Player entities to omit from rendering").group(Group.Entity)
    private val bossEntities by setting("Boss Entities", bossEntityMap.values.toSet(), emptySet(), "Boss entities to omit from rendering").group(Group.Entity)
    private val decorationEntities by setting("Decoration Entities", decorationEntityMap.values.toSet(), emptySet(), "Decoration entities to omit from rendering").group(Group.Entity)
    private val mobEntities by setting("Mob Entities", mobEntityMap.values.toSet(), emptySet(), "Mob entities to omit from rendering").group(Group.Entity)
    private val passiveEntities by setting("Passive Entities", passiveEntityMap.values.toSet(), emptySet(), "Passive entities to omit from rendering").group(Group.Entity)
    private val projectileEntities by setting("Projectile Entities", projectileEntityMap.values.toSet(), emptySet(), "Projectile entities to omit from rendering").group(Group.Entity)
    private val vehicleEntities by setting("Vehicle Entities", vehicleEntityMap.values.toSet(), emptySet(), "Vehicle entities to omit from rendering").group(Group.Entity)
    private val miscEntities by setting("Misc Entities", miscEntityMap.values.toSet(), emptySet(), "Miscellaneous entities to omit from rendering").group(Group.Entity)
    private val blockEntities by setting("Block Entities", blockEntityMap.values.toSet(), emptySet(), "Block entities to omit from rendering").group(Group.Entity)

    @JvmStatic val noSignText by setting("No Sign Text", false).group(Group.World)
    @JvmStatic val noWorldBorder by setting("No World Border", false).group(Group.World)
    @JvmStatic val noEnchantingTableBook by setting("No Enchanting Table Book", false).group(Group.World)
    // Couldn't get to work with block entities without crashing with sodium on boot
//    @JvmStatic val noBlockBreakingOverlay by setting("No Block Breaking Overlay", false).group(Group.World)
    @JvmStatic val noBeaconBeams by setting("No Beacon Beams", false).group(Group.World)
    @JvmStatic val noSpawnerMob by setting("No Spawner Mob", false).group(Group.World)
    private val particles by setting("Particles", particleMap.values.toSet(), emptySet(), "Particles to omit from rendering").group(Group.World)

    private fun createParticleNameMap() =
        scanResult
            .getSubclasses(Particle::class.java)
            .filter { !it.isAbstract }
            .createNameMap("net.minecraft.client.particle.", "Particle")

    private fun createEntityNameMap(directory: String, strictDir: Boolean = false) =
        entities.createNameMap(directory, "Entity", strictDir)

    private fun createBlockEntityNameMap() =
        scanResult
            .getSubclasses(BlockEntity::class.java)
            .filter { !it.isAbstract }.createNameMap("net.minecraft.block.entity", "BlockEntity")

    private fun Collection<ClassInfo>.createNameMap(
        directory: String,
        removePattern: String = "",
        strictDirectory: Boolean = false
    ) = map {
            val remappedName = it.name.remappedName
            val displayName = remappedName
                .substring(remappedName.indexOfLast { it == '.' } + 1)
                .replace(removePattern, "")
                .fancyFormat()
            MappingInfo(it.simpleName, remappedName, displayName)
        }
        .sortedBy { it.displayName.lowercase() }
        .filter { info ->
            if (strictDirectory)
                info.remapped.startsWith(directory) && !info.remapped.substring(directory.length).contains(".")
            else info.remapped.startsWith(directory)
        }
        .associate { it.raw to it.displayName }

    private fun String.fancyFormat() =
        replace("$", " - ").replace("(?<!\\s)[A-Z]".toRegex(), " $0")

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

    private data class MappingInfo(
        val raw: String,
        val remapped: String,
        val displayName: String
    )
}