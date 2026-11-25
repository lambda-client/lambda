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

package com.lambda.config

import com.lambda.config.Group
import com.lambda.config.Tab
import com.lambda.config.settings.comparable.boolSetting
import com.lambda.config.settings.complex.colorSetting
import com.lambda.config.settings.numeric.doubleSetting
import com.lambda.config.settings.numeric.intSetting
import com.lambda.config.categories.LambdaCategory
import com.lambda.core.Loadable
import com.lambda.graphics.mc.renderer.TickedRenderer.Companion.tickedRenderer
import com.lambda.interaction.construction.simulation.result.Drawable
import java.awt.Color

object ClientConfig : Loadable, Config("Client", LambdaCategory) {
	private const val GENERAL_TAB = "General"
	private const val DEBUG_TAB = "Debug"

	private const val RENDERING_GROUP = "Rendering"

	@Tab(GENERAL_TAB) val clientSounds by boolSetting("Client Sounds", true, "Plays sounds when certain actions are performed with lambda. Toggling modules, for example")
	@Tab(GENERAL_TAB) val buildTaskRenders by boolSetting("Build Task Renders", false, "Displays renders from some build sim results generated from the build task")
	@Tab(GENERAL_TAB) val avoidInventoryDesync by boolSetting("Avoid Inventory Desync", true, "Cancels incoming inventory update packets if they match previous actions")
	@Tab(GENERAL_TAB) val desyncTimeout by intSetting("Desync Timeout", 30, 1..30, 1, unit = " ticks", description = "Time to store previous inventory actions before dropping the cache") { avoidInventoryDesync }
	@Tab(GENERAL_TAB) val scanShrinkFactor by doubleSetting("Scan Shrink Factor", 0.001, 0.0..1.0, 0.001, "How much to shrink block scans from the edges to avoid flagging anticheats")
	@Tab(GENERAL_TAB) @Group(RENDERING_GROUP) val chunkUploadsPerTick by intSetting("Chunk Uploads", 16, 1..256, 1, unit = " chunks/tick")
	@Tab(GENERAL_TAB) @Group(RENDERING_GROUP) val chunkRebuildsPerTick by intSetting("Chunk Rebuilds", 64, 1..256, 1, unit = " chunks/tick")
	@Tab(GENERAL_TAB) val highlightColor by colorSetting("Text Highlight Color", Color(214, 55, 87), "Base text highlight color")

	@Tab(DEBUG_TAB) val ignoreItemDropWarnings by boolSetting("Ignore Drop Warnings", false, "Hides the item drop warnings from the break manager")
	@Tab(DEBUG_TAB) val verboseDebug by boolSetting("Verbose Debug", false, "Prints more, and more detailed, debug logs")
	@Tab(DEBUG_TAB) val showInvisibleTasks by boolSetting("Show Invisible Tasks", false, "Shows utility tasks such as chaining wrappers in the Task Flow HUD")

	@Volatile
	var drawables = listOf<Drawable>()

	init {
		tickedRenderer("Client Ticked Renderer") {
			if (buildTaskRenders) drawables.forEach { with(it) { render() } }
		}
	}
}