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

import com.lambda.config.Group
import com.lambda.config.Tab
import com.lambda.config.applyEdits
import com.lambda.config.settings.blocks.EntityColorSettings
import com.lambda.config.settings.blocks.EntitySelectionSettings
import com.lambda.config.settings.blocks.ScreenLineSettings
import com.lambda.friend.FriendHandler.isFriend
import com.lambda.graphics.mc.renderer.ImmediateRenderer.Companion.immediateRenderer
import com.lambda.graphics.mc.renderer.RendererUtils.worldToScreenNormalized
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.util.NamedEnum
import com.lambda.util.extension.prevPos
import com.lambda.util.extension.tickDelta
import com.lambda.util.math.lerp
import net.minecraft.client.network.OtherClientPlayerEntity
import org.joml.Vector2f
import org.joml.component1
import org.joml.component2

@Suppress("unused")
object Tracers : Module(
	name = "Tracers",
	description = "Draws lines to entities within the world",
	tag = ModuleTag.Render
) {
	private enum class LineGroup(override val displayName: String) : NamedEnum {
		Other("Other"),
		Friend("Friend")
	}

	private const val GeneralTab = "General"
	private const val EntityTab = "Entities"
	private const val ColorsTab = "Colors"

	@Tab(GeneralTab) private val target by setting("Target", TracerMode.Feet)
	@Tab(GeneralTab) private val stem by setting("Stem", true)

	private const val FriendsLineGroup = "Friends"
	private const val OthersLineGroup = "Others"

	@Tab(GeneralTab) @Group(FriendsLineGroup) private val friendLineConfig by settingBlock(ScreenLineSettings(this)) {
		applyEdits { hide(::startColor, ::endColor) }
	}
	@Tab(GeneralTab) @Group(OthersLineGroup) private val otherLineConfig by settingBlock(ScreenLineSettings(this)) {
		applyEdits { hide(::startColor, ::endColor) }
	}

	@Tab(EntityTab) private val entitySettings by settingBlock(EntitySelectionSettings(this)) {
		applyEdits { hide(::self, ::blockEntities) }
	}
	@Tab(ColorsTab) private val entityColors by settingBlock(EntityColorSettings(this))

	init {
		immediateRenderer("Tracers Immediate Renderer") { safeContext ->
			with(safeContext) {
				world.entities.forEach { entity ->
					if (entity === player) return@forEach
					if (!entitySettings.isSelected(entity)) return@forEach
					val color = entityColors.getColor(entity)
					val lineConfig = if (entity is OtherClientPlayerEntity && entity.isFriend) friendLineConfig else otherLineConfig
					val lerpedPos = lerp(mc.tickDelta, entity.prevPos, entity.pos)
					val lerpedEyePos = lerpedPos.add(0.0, entity.standingEyeHeight.toDouble(), 0.0)
					val targetPos = when(target) {
						TracerMode.Feet -> lerpedPos
						TracerMode.Middle -> lerpedPos.add(0.0, entity.standingEyeHeight / 2.0, 0.0)
						TracerMode.Eyes -> lerpedEyePos
					}
					val (toX, toY) = worldToScreenNormalized(targetPos) ?: return@forEach
					screenLine(0.5f, 0.5f, toX, toY, color, lineConfig.width, lineConfig.getDashStyle())
					if (stem) {
						val (lowerX, lowerY) =
							if (target == TracerMode.Feet) Vector2f(toX, toY)
							else worldToScreenNormalized(lerpedPos) ?: return@forEach
						val (upperX, upperY) =
							if (target == TracerMode.Eyes) Vector2f(toX, toY)
							else worldToScreenNormalized(lerpedEyePos) ?: return@forEach
						screenLine(lowerX, lowerY, upperX, upperY, color, lineConfig.width, lineConfig.getDashStyle())
					}
				}
			}
		}
	}

	private enum class TracerMode {
		Feet,
		Middle,
		Eyes
	}
}