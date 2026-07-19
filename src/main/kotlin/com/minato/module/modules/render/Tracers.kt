
package com.minato.module.modules.render

import com.minato.config.ConfigEditor.hide
import com.minato.config.Group
import com.minato.config.Tab
import com.minato.config.blocks.EntityColorSettings
import com.minato.config.blocks.EntitySelectionSettings
import com.minato.config.blocks.ScreenLineSettings
import com.minato.config.withEdits
import com.minato.graphics.mc.renderer.ImmediateRenderer.Companion.immediateRenderer
import com.minato.graphics.mc.renderer.RendererUtils.worldToScreenNormalized
import com.minato.interaction.handlers.FriendHandler.isFriend
import com.minato.module.Module
import com.minato.module.tag.ModuleTag
import com.minato.threading.runSafe
import com.minato.util.extension.prevPos
import com.minato.util.extension.tickDelta
import com.minato.util.math.lerp
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