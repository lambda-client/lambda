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

import com.lambda.config.Group
import com.lambda.config.Tab
import com.lambda.graphics.mc.renderer.TickedRenderer.Companion.tickedRenderer
import com.lambda.interaction.construction.simulation.result.Drawable
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import java.awt.Color

object Client : Module(
	name = "Client",
	description = "Global settings for Lambda",
	tag = ModuleTag.CLIENT,
	enabledByDefault = true
) {
	private const val GENERAL_TAB = "General"
	private const val DEBUG_TAB = "Debug"

	private const val RENDERING_GROUP = "Rendering"

	@Tab(GENERAL_TAB) val clientSounds by setting("Client Sounds", true, "Plays sounds when certain actions are performed with lambda. Toggling modules, for example")
	@Tab(GENERAL_TAB) val buildTaskRenders by setting("Build Task Renders", false, "Displays renders from some build sim results generated from the build task")
	@Tab(GENERAL_TAB) val avoidInventoryDesync by setting("Avoid Inventory Desync", true, "Cancels incoming inventory update packets if they match previous actions")
	@Tab(GENERAL_TAB) val desyncTimeout by setting("Desync Timeout", 30, 1..30, 1, unit = " ticks", description = "Time to store previous inventory actions before dropping the cache") { avoidInventoryDesync }
	@Tab(GENERAL_TAB) val scanShrinkFactor by setting("Scan Shrink Factor", 0.001, 0.0..1.0, 0.001, "How much to shrink block scans from the edges to avoid flagging anticheats")
	@Tab(GENERAL_TAB) @Group(RENDERING_GROUP) val chunkUploadsPerTick by setting("Chunk Uploads", 16, 1..256, 1, unit = " chunks/tick")
	@Tab(GENERAL_TAB) @Group(RENDERING_GROUP) val chunkRebuildsPerTick by setting("Chunk Rebuilds", 64, 1..256, 1, unit = " chunks/tick")
	@Tab(GENERAL_TAB) val highlightColor by setting("Text Highlight Color", Color(214, 55, 87), "Base text highlight color")

	@Tab(DEBUG_TAB) val showAllEntries by setting("Show All Entries", false, "Show all entries in the task tree")
	@Tab(DEBUG_TAB) val ignoreItemDropWarnings by setting("Ignore Drop Warnings", false, "Hides the item drop warnings from the break manager")
	@Tab(DEBUG_TAB) val verboseDebug by setting("Verbose Debug", false, "Prints more, and more detailed, debug logs")

	@Volatile
	var drawables = listOf<Drawable>()

	init {
		tickedRenderer("Client Ticked Renderer") {
			if (buildTaskRenders) drawables.forEach { with(it) { render() } }
		}
	}
}