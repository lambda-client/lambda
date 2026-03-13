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

import com.lambda.config.applyEdits
import com.lambda.config.groups.EntityColorSettings
import com.lambda.config.groups.EntitySelectionSettings
import com.lambda.config.groups.ScreenLineSettings
import com.lambda.friend.FriendManager.isFriend
import com.lambda.graphics.mc.renderer.ImmediateRenderer.Companion.immediateRenderer
import com.lambda.graphics.mc.renderer.RendererUtils.worldToScreenNormalized
import com.lambda.module.Module
import com.lambda.module.modules.render.ExtraTab.friendColor
import com.lambda.module.tag.ModuleTag
import com.lambda.util.EntityUtils.EntityGroup
import com.lambda.util.EntityUtils.entityGroup
import com.lambda.util.NamedEnum
import com.lambda.util.extension.prevPos
import com.lambda.util.extension.tickDelta
import com.lambda.util.math.dist
import com.lambda.util.math.lerp
import net.minecraft.client.network.OtherClientPlayerEntity
import org.joml.Vector2f
import org.joml.component1
import org.joml.component2
import java.awt.Color

object Tracers : Module(
	name = "Tracers",
	description = "Draws lines to entities within the world",
	tag = ModuleTag.RENDER
) {
	private enum class Group(override val displayName: String) : NamedEnum {
		General("General"),
		Entities("Entities"),
		Colors("Colors"),
		LineStyle("Line Style")
	}

	private enum class LineGroup(override val displayName: String) : NamedEnum {
		Other("Other"),
		Friend("Friend")
	}

	private val target by setting("Target", TracerMode.Feet).group(Group.General)
	private val stem by setting("Stem", true).group(Group.General)
	private val entitySettings = EntitySelectionSettings(c = this, baseGroup = arrayOf(Group.Entities)).apply {
		applyEdits {
			hide(::self, ::blockEntities)
		}
	}
	private val entityColors = EntityColorSettings(c = this, baseGroup = arrayOf(Group.Colors))

	private val friendLineConfig = ScreenLineSettings("Friend ", this, Group.LineStyle, LineGroup.Friend).apply {
		applyEdits { hide(::startColor, ::endColor) }
	}
	private val otherLineConfig = ScreenLineSettings("Other ", this, Group.LineStyle, LineGroup.Other).apply {
		applyEdits { hide(::startColor, ::endColor) }
	}

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