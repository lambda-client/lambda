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

import com.lambda.context.SafeContext
import com.lambda.event.events.GuiEvent
import com.lambda.event.events.RenderEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.graphics.mc.renderer.ImmediateRenderer
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.util.NamedEnum
import com.lambda.util.extension.tickDeltaF
import imgui.ImGui
import net.minecraft.entity.Entity
import net.minecraft.entity.ItemEntity
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.decoration.ArmorStandEntity
import net.minecraft.entity.decoration.EndCrystalEntity
import net.minecraft.entity.mob.HostileEntity
import net.minecraft.entity.mob.MobEntity
import net.minecraft.entity.passive.AnimalEntity
import net.minecraft.entity.passive.PassiveEntity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.entity.projectile.ProjectileEntity
import net.minecraft.entity.vehicle.AbstractMinecartEntity
import net.minecraft.entity.vehicle.BoatEntity
import net.minecraft.util.math.Vec3d
import java.awt.Color

object EntityESP : Module(
	name = "EntityESP",
	description = "Highlight entities with smooth interpolated rendering",
	tag = ModuleTag.RENDER
) {
	private val esp = ImmediateRenderer("EntityESP")

	private data class LabelData(
		val screenX: Float,
		val screenY: Float,
		val text: String,
		val color: Color,
		val scale: Float
	)

	private val pendingLabels = mutableListOf<LabelData>()

	private val lineLength by setting("Line Length", 5f, 0f..15f, 0.1f)
	private val outlineWidth by setting("Outline Width", 0.15f, 0f..1f, 0.01f)
	private val glowWidth by setting("Glow Width", 0.25f, 0f..1f, 0.01f)
	private val shadowDistance by setting("Shadow Distance", 0.2f, 0f..1f, 0.01f)
	private val shadowAngle by setting("Shadow Angle", 135f, 0f..360f, 1f)
	private val animationSpeed by setting("Animation Speed", 1f, 0.1f..5f, 0.1f)

	private val throughWalls by setting("Through Walls", true, "Render through blocks").group(Group.General)
	private val self by setting("Self", false, "Render own player in third person").group(Group.General)

	private val players by setting("Players", true, "Highlight players").group(Group.Entities)
	private val hostiles by setting("Hostiles", true, "Highlight hostile mobs").group(Group.Entities)
	private val passives by setting("Passives", false, "Highlight passive mobs (animals)").group(Group.Entities)
	private val neutrals by setting("Neutrals", false, "Highlight neutral mobs").group(Group.Entities)
	private val items by setting("Items", false, "Highlight dropped items").group(Group.Entities)
	private val projectiles by setting("Projectiles", false, "Highlight projectiles").group(Group.Entities)
	private val vehicles by setting("Vehicles", false, "Highlight boats and minecarts").group(Group.Entities)
	private val crystals by setting("Crystals", true, "Highlight end crystals").group(Group.Entities)
	private val armorStands by setting("Armor Stands", false, "Highlight armor stands").group(Group.Entities)

	private val drawBoxes by setting("Boxes", true, "Draw entity boxes").group(Group.Render)
	private val drawFilled by setting("Filled", true, "Fill entity boxes") { drawBoxes }.group(Group.Render)
	private val drawOutline by setting("Outline", true, "Draw box outlines") { drawBoxes }.group(Group.Render)
	private val filledAlpha by setting("Filled Alpha", 0.2, 0.0..1.0, 0.05) { drawBoxes && drawFilled }.group(Group.Render)
	private val outlineAlpha by setting("Outline Alpha", 0.8, 0.0..1.0, 0.05) { drawBoxes && drawOutline }.group(Group.Render)
//	private val outlineWidth by setting("Outline Width", 1.0f, 0.5f..5.0f, 0.5f) { drawBoxes && drawOutline }.group(Group.Render)

	private val tracers by setting("Tracers", true, "Draw lines to entities").group(Group.Tracers)
	private val tracerOrigin by setting("Tracer Origin", TracerOrigin.Eyes, "Where tracers start from") { tracers }.group(Group.Tracers)
	private val tracerWidth by setting("Tracer Width", 1.5f, 0.5f..5f, 0.5f) { tracers }.group(Group.Tracers)
	private val dashedTracers by setting("Dashed Tracers", false, "Use dashed lines for tracers") { tracers }.group(Group.Tracers)
	private val dashLength by setting("Dash Length", 1.0, 0.25..2.0, 0.25) { tracers && dashedTracers }.group(Group.Tracers)
	private val gapLength by setting("Gap Length", 0.5, 0.1..1.0, 0.1) { tracers && dashedTracers }.group(Group.Tracers)

	private val nameTags by setting("Name Tags", false, "Show entity name tags").group(Group.NameTags)
	private val nameTagDistance by setting("Show Distance", true, "Show distance in name tags") { nameTags }.group(Group.NameTags)
	private val nameTagHealth by setting("Show Health", true, "Show health in name tags") { nameTags }.group(Group.NameTags)
	private val nameTagBackground by setting("Name Background", true, "Draw background behind name tags") { nameTags }.group(Group.NameTags)

	private val playerColor by setting("Player Color", Color(255, 50, 50), "Color for players").group(Group.Colors)
	private val hostileColor by setting("Hostile Color", Color(255, 100, 0), "Color for hostile mobs").group(Group.Colors)
	private val passiveColor by setting("Passive Color", Color(50, 255, 50), "Color for passive mobs").group(Group.Colors)
	private val neutralColor by setting("Neutral Color", Color(255, 255, 50), "Color for neutral mobs").group(Group.Colors)
	private val itemColor by setting("Item Color", Color(100, 100, 255), "Color for items").group(Group.Colors)
	private val projectileColor by setting("Projectile Color", Color(200, 200, 200), "Color for projectiles").group(Group.Colors)
	private val vehicleColor by setting("Vehicle Color", Color(150, 100, 50), "Color for vehicles").group(Group.Colors)
	private val crystalColor by setting("Crystal Color", Color(255, 0, 255), "Color for end crystals").group(Group.Colors)
	private val otherColor by setting("Other Color", Color(200, 200, 200), "Color for other entities").group(Group.Colors)

	init {
		listen<RenderEvent.Render> {
			esp.depthTest = !throughWalls
			esp.tick()
			val tickDelta = mc.tickDeltaF

//			esp.shapes {
//				val startPos = lerp(mc.tickDelta, player.prevPos, player.pos)
//				lineGradient(
//					startPos,
//					Color.BLUE,
//					startPos.offset(Direction.EAST, lineLength.toDouble()),
//					Color.RED,
//					0.1f,
//					marchingAnts(speed = animationSpeed)
//				)
//				worldText(
//					"Test FONT FONT FONT BLEHHHHH!",
//					startPos.offset(Direction.EAST,
//						lineLength.toDouble()),
//					style = RenderBuilder.TextStyle(
//						outline = RenderBuilder.TextOutline(),
//						glow = RenderBuilder.TextGlow(),
//						shadow = RenderBuilder.TextShadow()
//					)
//				)
//			}

			// Test SDF text rendering with glow and outline
			val eyePos = player.eyePos.add(player.rotationVector.multiply(2.0)) // 2 blocks in front
//			SDFTextRenderer.drawWorld(
//				text = "SDFTextRenderer World",
//				pos = eyePos,
//				fontSize = 0.5f,
//				style = SDFTextRenderer.TextStyle(
//					color = Color.WHITE,
//					outline = SDFTextRenderer.TextOutline(Color.BLACK, outlineWidth),
//					glow = SDFTextRenderer.TextGlow(Color(0, 200, 255, 180), glowWidth),
//					shadow = SDFTextRenderer.TextShadow(Color.YELLOW, offset = shadowDistance, angle = shadowAngle)
//				),
//				centered = true,
//				seeThrough = true
//			)

//			SDFTextRenderer.drawScreen(
//				text = "SDFTextRenderer Screen",
//				x = 20f,
//				y = 20f,
//				fontSize = 24f,
//				style = SDFTextRenderer.TextStyle(
//					color = Color.WHITE,
//					outline = SDFTextRenderer.TextOutline(Color.BLACK, 0.15f),
//					glow = SDFTextRenderer.TextGlow(Color(0, 200, 255, 180), 0.2f),
//					shadow = SDFTextRenderer.TextShadow(Color.YELLOW, 0.2f)
//				)
//			)

			esp.upload()
			esp.render()

			// Clear pending labels from previous frame
			pendingLabels.clear()
		}

		// Draw ImGUI labels using pre-computed screen coordinates
		listen<GuiEvent.NewFrame> {
			val drawList = ImGui.getBackgroundDrawList()
			val font = ImGui.getFont()

			pendingLabels.forEach { label ->
				val fontSize = (font.fontSize * label.scale).toInt()
				val textSize = imgui.ImVec2()
				ImGui.calcTextSize(textSize, label.text)

				// Scale text size based on our custom scale
				val tw = textSize.x * label.scale
				val th = textSize.y * label.scale

				// Center text horizontally
				val x = label.screenX - tw / 2f
				val y = label.screenY

				// Color conversion (ABGR for ImGui)
				val textColor =
					(label.color.alpha shl 24) or
							(label.color.blue shl 16) or
							(label.color.green shl 8) or
							label.color.red
				val shadowColor = (200 shl 24) or 0 // Black with alpha

				if (nameTagBackground) {
					val bgColor = (160 shl 24) or 0 // Black with 160 alpha
					val padX = 4f * label.scale
					val padY = 2f * label.scale
					drawList.addRectFilled(
						x - padX,
						y - padY,
						x + tw + padX,
						y + th + padY,
						bgColor,
						3f * label.scale
					)
				} else {
					// Shadow
					drawList.addText(
						font,
						fontSize,
						imgui.ImVec2(
							x + 1f * label.scale,
							y + 1f * label.scale
						),
						shadowColor,
						label.text
					)
				}
				drawList.addText(
					font,
					fontSize,
					imgui.ImVec2(x, y),
					textColor,
					label.text
				)
			}
		}

		onDisable { esp.close() }
	}

	private fun SafeContext.shouldRender(entity: Entity): Boolean {
		if (entity == player && !self) return false
		if (entity is LivingEntity && !entity.isAlive) return false
		return when (entity) {
			is PlayerEntity -> players
			is HostileEntity -> hostiles
			is AnimalEntity -> passives
			is PassiveEntity -> passives
			is MobEntity -> neutrals
			is ItemEntity -> items
			is ProjectileEntity -> projectiles
			is BoatEntity -> vehicles
			is AbstractMinecartEntity -> vehicles
			is EndCrystalEntity -> crystals
			is ArmorStandEntity -> armorStands
			else -> false
		}
	}

	private fun getEntityColor(entity: Entity): Color {
		return when (entity) {
			is PlayerEntity -> playerColor
			is HostileEntity -> hostileColor
			is AnimalEntity -> passiveColor
			is PassiveEntity -> passiveColor
			is MobEntity -> neutralColor
			is ItemEntity -> itemColor
			is ProjectileEntity -> projectileColor
			is BoatEntity -> vehicleColor
			is AbstractMinecartEntity -> vehicleColor
			is EndCrystalEntity -> crystalColor
			else -> otherColor
		}
	}

	private fun getInterpolatedPos(entity: Entity, tickDelta: Float): Vec3d {
		val x = entity.lastRenderX + (entity.x - entity.lastRenderX) * tickDelta
		val y = entity.lastRenderY + (entity.y - entity.lastRenderY) * tickDelta
		val z = entity.lastRenderZ + (entity.z - entity.lastRenderZ) * tickDelta
		return Vec3d(x, y, z)
	}

	private fun SafeContext.getTracerStartPos(tickDelta: Float): Vec3d {
		val playerPos = getInterpolatedPos(player, tickDelta)

		return when (tracerOrigin) {
			TracerOrigin.Feet -> playerPos
			TracerOrigin.Center -> playerPos.add(0.0, player.height / 2.0, 0.0)
			TracerOrigin.Eyes ->
				playerPos.add(0.0, player.standingEyeHeight.toDouble(), 0.0)
			TracerOrigin.Crosshair -> {
				val camera = mc.gameRenderer?.camera ?: return playerPos
				camera.cameraPos.add(Vec3d(camera.horizontalPlane).multiply(0.1))
			}
		}
	}

	private fun SafeContext.buildNameTag(entity: Entity): String {
		val builder = StringBuilder()
		val name = entity.displayName?.string ?: entity.type.name.string
		builder.append(name)
		if (nameTagHealth && entity is LivingEntity) {
			builder.append(" [${entity.health.toInt()}/${entity.maxHealth.toInt()}]")
		}
		if (nameTagDistance) {
			val dist = player.distanceTo(entity).toInt()
			builder.append(" ${dist}m")
		}
		return builder.toString()
	}

	enum class TracerOrigin(override val displayName: String) : NamedEnum {
		Feet("Feet"),
		Center("Center"),
		Eyes("Eyes"),
		Crosshair("Crosshair")
	}

	private enum class Group(override val displayName: String) : NamedEnum {
		General("General"),
		Entities("Entities"),
		Render("Render"),
		Tracers("Tracers"),
		NameTags("Name Tags"),
		Colors("Colors")
	}
}
