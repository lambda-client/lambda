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

package com.lambda.module.modules.client

import com.lambda.graphics.mc.renderer.TickedRenderer.Companion.tickedRenderer
import com.lambda.interaction.construction.simulation.result.Drawable
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.util.NamedEnum

object Client : Module(
	name = "Client",
	description = "Global settings for Lambda",
	tag = ModuleTag.CLIENT
) {
	private enum class Group(override val displayName: String) : NamedEnum {
		General("General"),
		Debug("Debug")
	}

	val clientSounds by setting("Client Sounds", true, "Plays sounds when certain actions are performed with lambda. Toggling modules, for example").group(Group.General)
	val buildTaskRenders by setting("Build Task Renders", false, "Displays renders from some build sim results generated from the build task").group(Group.General)
	val avoidInventoryDesync by setting("Avoid Inventory Desync", true, "Cancels incoming inventory update packets if they match previous actions").group(Group.General)
	val desyncTimeout by setting("Desync Timeout", 30, 1..30, 1, unit = " ticks", description = "Time to store previous inventory actions before dropping the cache") { avoidInventoryDesync }.group(Group.General)
	val scanShrinkFactor by setting("Scan Shrink Factor", 0.001, 0.0..1.0, 0.001).group(Group.General)
	val showAllEntries by setting("Show All Entries", false, "Show all entries in the task tree").group(Group.Debug)
	val ignoreItemDropWarnings by setting("Ignore Drop Warnings", false, "Hides the item drop warnings from the break manager").group(Group.Debug)
	val verboseDebug by setting("Verbose Debug", false, "Prints more, and more detailed, debug logs").group(Group.Debug)

	@Volatile
	var drawables = listOf<Drawable>()

	init {
		tickedRenderer("Client Ticked Renderer") {
			if (buildTaskRenders) drawables.forEach { with(it) { render() } }
		}
	}
}