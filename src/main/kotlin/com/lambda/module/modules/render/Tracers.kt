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

import com.lambda.config.ConfigEditor.hide
import com.lambda.config.Group
import com.lambda.config.Tab
import com.lambda.config.blocks.EntityColorSettings
import com.lambda.config.blocks.EntitySelectionSettings
import com.lambda.config.blocks.ScreenLineSettings
import com.lambda.config.withEdits
import com.lambda.friend.FriendHandler.isFriend
import com.lambda.graphics.mc.renderer.ImmediateRenderer.Companion.immediateRenderer
import com.lambda.graphics.mc.renderer.RendererUtils.worldToScreenNormalized
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.threading.runSafe
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
	tag = ModuleTag.RENDER
) {
	private const val GENERAL_TAB = "General"
	private const val ENTITY_TAB = "Entities"
	private const val COLORS_TAB = "Colors"

	@Tab(GENERAL_TAB) private val target by setting("Target", TracerMode.Feet)
	@Tab(GENERAL_TAB) private val stem by setting("Stem", true)

	private const val FRIENDS_LINE_GROUP = "Friends"
	private const val OTHERS_LINE_GROUP = "Others"

	@Tab(GENERAL_TAB) @Group(FRIENDS_LINE_GROUP) private val friendLineConfig by configBlock(ScreenLineSettings(this))
		.withEdits { hide(::startColor, ::endColor) }
	@Tab(GENERAL_TAB) @Group(OTHERS_LINE_GROUP) private val otherLineConfig by configBlock(ScreenLineSettings(this))
		.withEdits { hide(::startColor, ::endColor) }

	@Tab(ENTITY_TAB) private val entitySettings by configBlock(EntitySelectionSettings(this))
		.withEdits { hide(::self, ::blockEntities) }
	@Tab(COLORS_TAB) private val entityColors by configBlock(EntityColorSettings(this))

	init {
		immediateRenderer("Tracers Immediate Renderer") {
			runSafe {
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