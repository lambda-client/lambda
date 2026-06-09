/*
 * Copyright 2026 Lambda
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

import com.lambda.config.ConfigEditor.editTypedSettings
import com.lambda.config.Tab
import com.lambda.config.settings.blocks.EntitySelectionSettings
import com.lambda.config.withEdits
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.util.EntityUtils.createNameMap
import com.lambda.util.ReflectionUtils.scanResult
import net.minecraft.block.entity.BlockEntity
import net.minecraft.client.particle.Particle
import net.minecraft.entity.Entity

//ToDo: Implement unimplemented settings. (Keep in mind compatibility with other mods like sodium)
object NoRender : Module(
	name = "NoRender",
	description = "Disables rendering of certain things",
	tag = ModuleTag.Render,
) {
	private val particleMap = createParticleNameMap()

	private const val EffectTab = "Effect"
	private const val HudTab = "Hud"
	private const val EntityTab = "Entity"
	private const val WorldTab = "World"

	@Tab(EffectTab) @JvmStatic val noBlindness by setting("No Blindness", true)
	@Tab(EffectTab) @JvmStatic val noDarkness by setting("No Darkness", true)
	@Tab(EffectTab) @JvmStatic val noNausea by setting("No Nausea", true)

	@Tab(HudTab) @JvmStatic val noFireOverlay by setting("No Fire Overlay", false)
	@Tab(HudTab) @JvmStatic val fireOverlayYOffset by setting("Fire Overlay Y Offset", 0.0, -0.4..0.4, 0.02) { !noFireOverlay }
	@Tab(HudTab) @JvmStatic val noPortalOverlay by setting("No Portal Overlay", true)
	@Tab(HudTab) @JvmStatic val noFluidOverlay by setting("No Fluid Overlay", true)
	@Tab(HudTab) @JvmStatic val noPowderedSnowOverlay by setting("No Powdered Snow Overlay", true)
	@Tab(HudTab) @JvmStatic val noInWall by setting("No In Wall Overlay", true)
	@Tab(HudTab) @JvmStatic val noPumpkinOverlay by setting("No Pumpkin Overlay", true)
	@Tab(HudTab) @JvmStatic val noVignette by setting("No Vignette", true)
	@Tab(HudTab) @JvmStatic val noChatVerificationToast by setting("No Chat Verification Toast", true)
	@Tab(HudTab) @JvmStatic val noSpyglassOverlay by setting("No Spyglass Overlay", false)
	@Tab(HudTab) @JvmStatic val noGuiShadow by setting("No Gui Shadow", false)
	@Tab(HudTab) @JvmStatic val noFloatingItemAnimation by setting("No Floating Item Animation", false, "Disables floating item animations, typically used when a totem pops")
	@Tab(HudTab) @JvmStatic val noCrosshair by setting("No Crosshair", false)
	@Tab(HudTab) @JvmStatic val noBossBar by setting("No Boss Bar", false)
	@Tab(HudTab) @JvmStatic val noScoreBoard by setting("No Score Board", false)
	@Tab(HudTab) @JvmStatic val noStatusEffects by setting("No Status Effects", false)
	@Tab(HudTab) @JvmStatic val no2b2tActionText by setting("No 2b2t Action Text", true, description = "Blocks the '2b2t.org' text from the action bar 2b2t randomly sends")

	@Tab(EntityTab) @JvmStatic val noArmor by setting("No Armor", false)
	@Tab(EntityTab) @JvmStatic val includeNoOtherHeadItems by setting("Include No Other Head Items", false) { noArmor }
	@Tab(EntityTab) @JvmStatic val noElytra by setting("No Elytra", false)
	@Tab(EntityTab) @JvmStatic val noInvisibility by setting("No Invisibility", true)
	@Tab(EntityTab) @JvmStatic val noGlow by setting("No Glow", false)
	@Tab(EntityTab) @JvmStatic val noNametags by setting("No Nametags", false)
//    RenderLayer.getArmorEntityGlint(), RenderLayer.getGlint(), RenderLayer.getGlintTranslucent(), RenderLayer.getEntityGlint()
//    @JvmStatic val noEnchantmentGlint by setting("No Enchantment Glint", false).group(Group.Entity)
//    @JvmStatic val noDeadEntities by setting("No Dead Entities", false).group(Group.Entity)
	@Tab(EntityTab) private val entitySettings by configBlock(EntitySelectionSettings(this))
		.withEdits {
			editTypedSettings(::playerEntities, ::mobEntities, ::bossEntities) { defaultValue(mutableSetOf()) }
		}

	@Tab(WorldTab) @JvmStatic val noTerrainFog by setting("No Terrain Fog", false)
	@Tab(WorldTab) @JvmStatic val noSignText by setting("No Sign Text", false)
	@Tab(WorldTab) @JvmStatic val noWorldBorder by setting("No World Border", false)
	@Tab(WorldTab) @JvmStatic val noEnchantingTableBook by setting("No Enchanting Table Book", false)
	// Couldn't get to work with block entities without crashing with sodium on boot
//    @JvmStatic val noBlockBreakingOverlay by setting("No Block Breaking Overlay", false).group(Group.World)
	@Tab(WorldTab) @JvmStatic val noBeaconBeams by setting("No Beacon Beams", false)
	@Tab(WorldTab) @JvmStatic val noSpawnerMob by setting("No Spawner Mob", false)
	@Tab(WorldTab) private val particles by setting("Particles", emptySet(), particleMap.values.toSet(), "Particles to omit from rendering")

	private fun createParticleNameMap() =
		scanResult
			.getSubclasses(Particle::class.java)
			.filter { !it.isAbstract }
			.createNameMap("net.minecraft.client.particle.", "Particle")

	@JvmStatic
	fun shouldOmitParticle(particle: Particle) =
		isEnabled && particleMap[particle.javaClass.simpleName] in particles

	@JvmStatic
	fun shouldOmitParticle(particle: Class<out Particle>) =
		isEnabled && particleMap[particle.simpleName] in particles

	@JvmStatic
	fun shouldOmitEntity(entity: Entity): Boolean = entitySettings.isSelected(entity)

	@JvmStatic
	fun shouldOmitBlockEntity(blockEntity: BlockEntity) = entitySettings.isSelected(blockEntity)
}
