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

import com.lambda.Lambda.mc
import com.lambda.event.events.RenderEvent
import com.lambda.event.events.ScreenRenderEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.graphics.RenderMain.worldToScreenNormalized
import com.lambda.graphics.mc.RenderBuilder
import com.lambda.graphics.mc.renderer.ImmediateRenderer
import com.lambda.graphics.text.FontHandler.getDefaultFont
import com.lambda.graphics.util.DynamicAABB.Companion.interpolatedBox
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.util.EntityUtils
import com.lambda.util.EntityUtils.entityGroup
import com.lambda.util.extension.fullHealth
import com.lambda.util.extension.maxFullHealth
import com.lambda.util.math.MathUtils.roundToStep
import com.lambda.util.math.distSq
import com.lambda.util.math.lerp
import net.minecraft.entity.Entity
import net.minecraft.entity.EquipmentSlot
import net.minecraft.entity.LivingEntity
import net.minecraft.util.math.Vec3d
import org.joml.component1
import org.joml.component2
import java.awt.Color

//ToDo: implement all settings
object Nametags : Module(
	name = "Nametags",
	description = "Displays information about entities above them",
	tag = ModuleTag.RENDER
) {
	private val textScale by setting("Text Scale", 1.2f, 0.4f..5f, 0.01f)
	private val itemScale by setting("Item Scale", 1.9f, 0.4f..5f, 0.01f)
	private val yOffset by setting("Y Offset", 0.2, 0.0..1.0, 0.01)
	private val spacing by setting("Spacing", 0, 0..10, 1)
	private val color by setting("Color", Color.WHITE)
	private val friendColor by setting("Friend Color", Color.BLUE)
	private val self by setting("Self", false)
	private val health by setting("Health", false)
	private val gear by setting("Gear", true)
	private val mainItem by setting("Main Item", true) { gear }
	private val offhandItem by setting("Offhand Item", true) { gear }
	private val enchantments by setting("Enchantments", false) { gear }
	private val entities by setting("Entities", setOf(EntityUtils.EntityGroup.Player), EntityUtils.EntityGroup.entries)

	val renderer = ImmediateRenderer("Nametags")

	var heightWidthRatio = 0f
	var trueTextScale = 0f
	var trueItemScaleX = 0f
	var trueItemScaleY = 0f
	var trueSpacingX = 0f
	var trueSpacingY = 0f

	init {
		listen<RenderEvent.Render> {
			renderer.tick()
			heightWidthRatio = mc.window.height / mc.window.width.toFloat()
			trueTextScale = textScale * 0.01f
			trueItemScaleY = itemScale * 0.01f
			trueItemScaleX = trueItemScaleY * heightWidthRatio
			trueSpacingY = spacing * 0.0005f
			trueSpacingX = trueSpacingY * heightWidthRatio

			renderer.shapes {
				world.entities
					.sortedByDescending { it distSq mc.gameRenderer.camera.pos }
					.forEach { entity ->
						if (!shouldRenderNametag(entity)) return@forEach
						val nameText = entity.displayName?.string ?: return@forEach
						val box = entity.interpolatedBox
						val boxCenter = box.center
						val (anchorX, anchorY) =
							worldToScreenNormalized(Vec3d(boxCenter.x, box.maxY + yOffset, boxCenter.z))
								?: return@forEach

						if (entity is LivingEntity) {
							val healthCount = if (health) entity.fullHealth else -1.0
							val nameWidth = getDefaultFont().getStringWidthNormalized(nameText, trueTextScale)
							val healthText = if (health) " ${healthCount.roundToStep(0.01)}" else ""
							val healthWidth = getDefaultFont().getStringWidthNormalized(healthText, trueTextScale)
							var combinedWidth = nameWidth + healthWidth
							if (healthCount >= 0) combinedWidth += trueSpacingX
							val nameX = anchorX - (combinedWidth / 2)
							screenText(nameText, nameX, anchorY, trueTextScale)
							if (healthCount >= 0) {
								val healthColor = lerp(entity.fullHealth / entity.maxFullHealth, Color.RED, Color.GREEN)
								val healthStyle = RenderBuilder.SDFStyle(healthColor)
								screenText(healthText, nameX + nameWidth + trueSpacingX, anchorY, trueTextScale, style = healthStyle)
							}
							if (gear) {
								if (EquipmentSlot.entries.none { it.index in 1..4 && !entity.getEquippedStack(it).isEmpty }) {
									if (mainItem && !entity.mainHandStack.isEmpty)
										screenItem(entity.mainHandStack, nameX - trueItemScaleX - trueSpacingX, anchorY, trueItemScaleY)
									if (offhandItem && !entity.offHandStack.isEmpty)
										screenItem(entity.offHandStack, anchorX + (combinedWidth / 2) + trueSpacingX, anchorY, trueItemScaleY)
								} else drawArmorAndItems(entity, anchorX, anchorY + trueTextScale + trueSpacingY)
							}
						} else screenText(nameText, anchorX, anchorY + (trueTextScale / 2f), trueTextScale, centered = true)
					}
			}

			renderer.upload()
			renderer.render()
		}

		listen<ScreenRenderEvent> {
			renderer.renderScreen()
		}
	}

	private fun RenderBuilder.drawArmorAndItems(entity: LivingEntity, x: Float, y: Float) {
		val stepAmount = trueItemScaleX + trueSpacingX
		var iteratorX = x - (stepAmount * 3) + (trueSpacingX / 2)
		if (mainItem && !entity.mainHandStack.isEmpty) screenItem(entity.mainHandStack, iteratorX, y, trueItemScaleY)
		iteratorX += stepAmount
		val headStack = entity.getEquippedStack(EquipmentSlot.HEAD)
		val chestStack = entity.getEquippedStack(EquipmentSlot.CHEST)
		val legsStack = entity.getEquippedStack(EquipmentSlot.LEGS)
		val feetStack = entity.getEquippedStack(EquipmentSlot.FEET)
		if (!headStack.isEmpty) screenItem(headStack, iteratorX, y, trueItemScaleY)
		iteratorX += stepAmount
		if (!chestStack.isEmpty) screenItem(chestStack, iteratorX, y, trueItemScaleY)
		iteratorX += stepAmount
		if (!legsStack.isEmpty) screenItem(legsStack, iteratorX, y, trueItemScaleY)
		iteratorX += stepAmount
		if (!feetStack.isEmpty) screenItem(feetStack, iteratorX, y, trueItemScaleY)
		iteratorX += stepAmount
		if (offhandItem && !entity.offHandStack.isEmpty) screenItem(entity.offHandStack, iteratorX, y, trueItemScaleY)
	}

	@JvmStatic
	fun shouldRenderNametag(entity: Entity) =
		entity.entityGroup in entities && (self || entity !== mc.player) && (entity !is LivingEntity || entity.isAlive)
}