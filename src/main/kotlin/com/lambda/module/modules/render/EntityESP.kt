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
import com.lambda.event.events.ScreenRenderEvent
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
	private val throughWalls by setting("Through Walls", true, "Render through blocks")
	private val self by setting("Self", false, "Render own player in third person")

	private val players by setting("Players", true, "Highlight players")
	private val hostiles by setting("Hostiles", true, "Highlight hostile mobs")
	private val passives by setting("Passives", false, "Highlight passive mobs (animals)")
	private val neutrals by setting("Neutrals", false, "Highlight neutral mobs")
	private val items by setting("Items", false, "Highlight dropped items")
	private val projectiles by setting("Projectiles", false, "Highlight projectiles")
	private val vehicles by setting("Vehicles", false, "Highlight boats and minecarts")
	private val crystals by setting("Crystals", true, "Highlight end crystals")
	private val armorStands by setting("Armor Stands", false, "Highlight armor stands")

	private val drawBoxes by setting("Boxes", true, "Draw entity boxes")
	private val drawFilled by setting("Filled", true, "Fill entity boxes") { drawBoxes }
	private val drawOutline by setting("Outline", true, "Draw box outlines") { drawBoxes }
	private val filledAlpha by setting("Filled Alpha", 0.2, 0.0..1.0, 0.05) { drawBoxes && drawFilled }
	private val outlineAlpha by setting("Outline Alpha", 0.8, 0.0..1.0, 0.05) { drawBoxes && drawOutline }

	private val playerColor by setting("Player Color", Color(255, 50, 50), "Color for players")
	private val hostileColor by setting("Hostile Color", Color(255, 100, 0), "Color for hostile mobs")
	private val passiveColor by setting("Passive Color", Color(50, 255, 50), "Color for passive mobs")
	private val neutralColor by setting("Neutral Color", Color(255, 255, 50), "Color for neutral mobs")
	private val itemColor by setting("Item Color", Color(100, 100, 255), "Color for items")
	private val projectileColor by setting("Projectile Color", Color(200, 200, 200), "Color for projectiles")
	private val vehicleColor by setting("Vehicle Color", Color(150, 100, 50), "Color for vehicles")
	private val crystalColor by setting("Crystal Color", Color(255, 0, 255), "Color for end crystals")
	private val otherColor by setting("Other Color", Color(200, 200, 200), "Color for other entities")

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
}
