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
import com.lambda.config.ConfigEditor.editSetting
import com.lambda.config.ConfigEditor.hide
import com.lambda.config.Group
import com.lambda.config.Tab
import com.lambda.config.settings.blocks.EntitySelectionSettings
import com.lambda.config.settings.blocks.ScreenTextSettings
import com.lambda.config.withEdits
import com.lambda.friend.FriendHandler.isFriend
import com.lambda.graphics.mc.RenderBuilder
import com.lambda.graphics.mc.renderer.ImmediateRenderer.Companion.immediateRenderer
import com.lambda.graphics.mc.renderer.RendererUtils.worldToScreenNormalized
import com.lambda.graphics.text.FontHandler
import com.lambda.graphics.util.DynamicAABB.Companion.interpolatedBox
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.threading.runSafe
import com.lambda.util.extension.fullHealth
import com.lambda.util.extension.maxFullHealth
import com.lambda.util.math.MathUtils.roundToStep
import com.lambda.util.math.distSq
import com.lambda.util.math.lerp
import net.minecraft.entity.Entity
import net.minecraft.entity.EquipmentSlot
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.ItemStack
import net.minecraft.util.math.Vec3d
import org.joml.component1
import org.joml.component2
import java.awt.Color
import kotlin.math.max

object Nametags : Module(
	name = "Nametags",
	description = "Displays information about entities above them",
	tag = ModuleTag.Render
) {
	private const val GeneralTab = "General"
	private const val EntityTab = "Entities"
	private const val BackgroundTab = "Background"
	private const val TextTab = "Text"

	@Tab(GeneralTab) private val textSize by setting("Text Size", 18, 1..50, 1)
	@Tab(GeneralTab) private val itemScale by setting("Item Scale", 3f, 0.4f..5f, 0.01f)
	@Tab(GeneralTab) private val yOffset by setting("Y Offset", 0.2, 0.0..1.0, 0.01)
	@Tab(GeneralTab) private val spacing by setting("Spacing", 0, 0..10, 1)
	@Tab(GeneralTab) private val health by setting("Health", true)
	@Tab(GeneralTab) private val ping by setting("Ping", true)
	@Tab(GeneralTab) private val gear by setting("Gear", true)
	@Tab(GeneralTab) private val mainItem by setting("Main Item", true) { gear }
	@Tab(GeneralTab) private val offhandItem by setting("Offhand Item", true) { gear }
//ToDo: Implement 	private val enchantments by setting("Enchantments", false) { gear }
	@Tab(GeneralTab) private val itemName by setting("Item Name", true)
	@Tab(GeneralTab) private val itemNameScale by setting("Item Name Scale", 0.7f, 0.1f..1.0f, 0.01f) { itemName }
	@Tab(GeneralTab) private val itemCount by setting("Item Count", true)
	@Tab(GeneralTab) private val durabilityMode by setting("Durability Mode", DurabilityMode.Text) { gear }

	@Tab(EntityTab) private val entitySelectionSettings by configBlock(EntitySelectionSettings(this))
		.withEdits { hide(::blockEntities) }

	private const val FriendGroup = "Friends"
	private const val OtherGroup = "Others"

	@Tab(TextTab) @Group(FriendGroup) private val friendTextConfig by configBlock(ScreenTextSettings(this))
		.withEdits {
			hide(::sizeSetting)
			::textColor.editSetting { defaultValue(Color(0, 255, 255, 255)) }
		}
	@Tab(TextTab) @Group(OtherGroup) private val otherTextConfig by configBlock(ScreenTextSettings(this))
		.withEdits { hide(::sizeSetting) }

	@Tab(BackgroundTab) private val background by setting("Background", true)
	@Tab(BackgroundTab) private val backgroundColor by setting("Background Color", Color(0, 0, 0, 60)) { background }
	@Tab(BackgroundTab) private val backgroundSize by setting("Background Size", 1.0f, 1.0f..2.0f, 0.01f) { background }

	var heightWidthRatio = 0f
	var trueItemScaleX = 0f
	var trueItemScaleY = 0f
	var trueSpacingX = 0f
	var trueSpacingY = 0f
	var trueBGSizeX = 0f
	var trueBGSizeY = 0f

	init {
		immediateRenderer("Nametags Immediate Renderer") {
			runSafe {
				heightWidthRatio = mc.window.height / mc.window.width.toFloat()
				trueItemScaleY = itemScale * 0.01f
				trueItemScaleX = trueItemScaleY * heightWidthRatio
				trueSpacingY = spacing * 0.0005f
				trueSpacingX = trueSpacingY * heightWidthRatio
				trueBGSizeY = (backgroundSize * 0.005f) * 0.5f
				trueBGSizeX = trueBGSizeY * heightWidthRatio

				world.entities
					.sortedByDescending { it distSq mc.gameRenderer.camera.pos }
					.forEach { entity ->
						if (!shouldRenderNametag(entity)) return@forEach
						val textConfig =
							if (entity is PlayerEntity && entity.isFriend) friendTextConfig
							else otherTextConfig
						val textStyle = textConfig.getSDFStyle()
						val size = textSize * 0.001f
						val nameText = entity.displayName?.string ?: return@forEach
						val nameWidth = FontHandler.getStringWidthNormalized(nameText, size)
						val box = entity.interpolatedBox
						val boxCenter = box.center
						var (anchorX, anchorY) =
							worldToScreenNormalized(Vec3d(boxCenter.x, box.maxY + yOffset, boxCenter.z))
								?: return@forEach

						val halfNameWidth = nameWidth / 2

						if (entity !is LivingEntity) {
							if (background) {
								screenRect(anchorX - halfNameWidth - trueBGSizeX, anchorY - trueBGSizeY, nameWidth + (trueBGSizeX * 2), size + (trueBGSizeY * 2), backgroundColor)
							}
							screenText(nameText, anchorX, anchorY, size, style = textStyle, centered = true)
							return@forEach
						}

						val healthCount = if (health) entity.fullHealth else -1.0
						val healthText = if (health) " ${healthCount.roundToStep(0.01)}" else ""
						val healthWidth =
							FontHandler.getStringWidthNormalized(healthText, size)
								.let { if (healthCount > 0) it + trueSpacingX else it }

						val pingCount = if (ping && entity is PlayerEntity) connection.getPlayerListEntry(entity.uuid)?.latency ?: -1 else -1
						val pingText = if (pingCount >= 0) " [$pingCount]" else ""
						val pingWidth =
							FontHandler.getStringWidthNormalized(pingText, size)
								.let { if (pingCount >= 0) it + trueSpacingX else it }

						var combinedWidth = nameWidth + healthWidth + pingWidth
						val nameX = anchorX - (combinedWidth * 0.5f)

						val itemName = itemName && !entity.mainHandStack.isEmpty
						val itemNameText = if (itemName) entity.mainHandStack.name.string else ""
						val itemNameSize = if (itemName) size * itemNameScale else 0f

						if (background) {
							anchorY += trueBGSizeY
							val itemNameWidth = FontHandler.getStringWidthNormalized(itemNameText, itemNameSize)
							val maxWidth =
								if (itemName) max(itemNameWidth, combinedWidth)
								else combinedWidth
							screenRect((anchorX - (maxWidth * 0.5f)) - trueBGSizeX, anchorY - trueBGSizeY, maxWidth + (trueBGSizeX * 2), size + itemNameSize + trueSpacingY + (trueBGSizeY * 2), backgroundColor)
						}

						if (itemName) {
							screenText(itemNameText, anchorX, anchorY, itemNameSize, centered = true)
							anchorY += (itemNameSize * 1.1f) + trueSpacingY
						}
						screenText(nameText, nameX, anchorY, size, style = textStyle)
						if (healthCount >= 0) {
							val healthColor = lerp(entity.fullHealth / entity.maxFullHealth, Color.RED, Color.GREEN).brighter()
							screenText(healthText, nameX + nameWidth + trueSpacingX, anchorY, size, style = textStyle.apply { color = healthColor })
						}
						if (pingCount >= 0) {
							val pingColor = lerp(pingCount / 500.0, Color.GREEN, Color.RED).brighter()
							screenText(pingText, nameX + nameWidth + healthWidth + trueSpacingX, anchorY, size, style = textStyle.apply { color = pingColor })
						}

						if (!gear) return@forEach

						if (background) anchorY += trueBGSizeY

						if (EquipmentSlot.entries.none { it.index in 1..4 && !entity.getEquippedStack(it).isEmpty }) {
							anchorY -= size * 0.5f
							if (mainItem && !entity.mainHandStack.isEmpty)
								renderItem(entity.mainHandStack, nameX - trueItemScaleX - trueSpacingX, anchorY)
							if (offhandItem && !entity.offHandStack.isEmpty)
								renderItem(entity.offHandStack, anchorX + (combinedWidth * 0.5f) + trueSpacingX, anchorY)
						} else drawArmorAndItems(entity, anchorX, anchorY + size + trueSpacingY)
					}
			}
		}
	}

	private fun RenderBuilder.drawArmorAndItems(entity: LivingEntity, x: Float, y: Float) {
		val stepAmount = trueItemScaleX + trueSpacingX
		var iteratorX = x - (stepAmount * 3) + (trueSpacingX * 0.5f)
		if (mainItem && !entity.mainHandStack.isEmpty) renderItem(entity.mainHandStack, iteratorX, y)
		iteratorX += stepAmount
		val headStack = entity.getEquippedStack(EquipmentSlot.HEAD)
		val chestStack = entity.getEquippedStack(EquipmentSlot.CHEST)
		val legsStack = entity.getEquippedStack(EquipmentSlot.LEGS)
		val feetStack = entity.getEquippedStack(EquipmentSlot.FEET)
		if (!headStack.isEmpty) renderItem(headStack, iteratorX, y)
		iteratorX += stepAmount
		if (!chestStack.isEmpty) renderItem(chestStack, iteratorX, y)
		iteratorX += stepAmount
		if (!legsStack.isEmpty) renderItem(legsStack, iteratorX, y)
		iteratorX += stepAmount
		if (!feetStack.isEmpty) renderItem(feetStack, iteratorX, y)
		iteratorX += stepAmount
		if (offhandItem && !entity.offHandStack.isEmpty) renderItem(entity.offHandStack, iteratorX, y)
	}

	private fun RenderBuilder.renderItem(stack: ItemStack, x: Float, y: Float) {
		screenGuiItem(stack, x, y, trueItemScaleY, centered = false)
		var iteratorY = y
		iteratorY += trueItemScaleY
		if (durabilityMode != DurabilityMode.None && stack.isDamageable) {
			val dura = (1 - (stack.damage / stack.maxDamage.toDouble()))
			if (durabilityMode.bar) {
				val yOffset = trueItemScaleY / 16
				val xOffset = trueItemScaleX / 16
				val maxWidth = xOffset * 14
				screenRect(x + xOffset, y + yOffset, maxWidth, yOffset * 2, Color.BLACK)
				screenRect(x + xOffset, y + (yOffset * 2), maxWidth * dura.toFloat(), yOffset, lerp(dura, Color.RED, Color.GREEN).brighter())
			}
			if (durabilityMode.text) {
				val duraText = "${(dura * 100).toInt()}%"
				val textSize = FontHandler.getSizeForWidthNormalized(duraText, trueItemScaleX) * 0.9f
				screenText(duraText, x + (trueItemScaleX * 0.5f), iteratorY, textSize.coerceAtMost(trueItemScaleY * 0.33f), centered = true, style = RenderBuilder.SDFStyle(color = lerp(dura, Color.RED, Color.GREEN).brighter()))
			}
		}
		if (itemCount && stack.isStackable && stack.count > 1) {
			val countText = "${stack.count}"
			val textSize = trueItemScaleY * 0.5f
			val textWidth = FontHandler.getStringWidthNormalized(countText, textSize)
			screenText(countText, x - (textWidth - trueItemScaleX), y, textSize)
		}
	}

	@JvmStatic
	fun shouldRenderNametag(entity: Entity) =
		(entity !== mc.player || !mc.options.perspective.isFirstPerson || Freecam.isEnabled) &&
				entitySelectionSettings.isSelected(entity) && (entity !is LivingEntity || entity.isAlive)

	@Suppress("unused")
	private enum class DurabilityMode(val text: Boolean, val bar: Boolean) {
		None(false, false),
		Text(true, false),
		Bar(false, true),
		Both(true, true)
	}
}