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

package com.lambda.module.hud

import com.lambda.event.events.RenderEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.gui.dsl.ImGuiBuilder
import com.lambda.module.HudModule
import com.lambda.module.tag.ModuleTag
import com.lambda.util.collections.LimitedDecayQueue
import kotlin.time.Duration.Companion.seconds

@Suppress("unused")
object Fps : HudModule(
	name = "FPS",
	description = "Displays your games frames per second",
	tag = ModuleTag.Hud
) {
	val average by setting("Average", true)
	val updateDelay by setting("Update Delay", 50, 0..1000, 1, "Time between updating the fps value")

	val frames = LimitedDecayQueue<Unit>(Int.MAX_VALUE, 1.seconds.inWholeMilliseconds)
	var lastUpdated = System.currentTimeMillis()
	var lastFrameTime = System.nanoTime()
	var fps = 0

	init {
		listen<RenderEvent.RenderWorld> {
			var currentFps = 0
			if (average) {
				frames.add(Unit)
				currentFps = frames.size
			} else {
				val currentTimeNano = System.nanoTime()
				val elapsedNs = currentTimeNano - lastFrameTime
				currentFps = if (elapsedNs > 0) (1000000000 / elapsedNs).toInt() else 0
				lastFrameTime = currentTimeNano
			}

			val currentTypeMilli = System.currentTimeMillis()
			if (currentTypeMilli - lastUpdated >= updateDelay) {
				fps = currentFps
				lastUpdated = currentTypeMilli
			}
		}
	}

	override fun ImGuiBuilder.buildLayout() {
		text("FPS: $fps")
	}
}