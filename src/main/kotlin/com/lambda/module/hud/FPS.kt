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

package com.lambda.module.hud

import com.lambda.event.events.RenderEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.gui.dsl.ImGuiBuilder
import com.lambda.module.HudModule
import com.lambda.module.tag.ModuleTag
import kotlin.time.Duration.Companion.seconds

object FPS : HudModule(
	name = "FPS",
	description = "Displays your games frames per second",
	tag = ModuleTag.HUD
) {
	val average by setting("Average", false)
	val updateDelay by setting("Update Delay", 50, 0..1000, 1, "Time between updating the fps value") {
		!average
	}

	val frames = mutableListOf<Long>();
	var lastUpdated = System.currentTimeMillis()
	var lastFrameTime = System.nanoTime()
	var fps = 0

	init {
		listen<RenderEvent.Render> {
			if (average) {
				frames.add(System.nanoTime() + 1.seconds.inWholeNanoseconds)
				frames.removeIf { System.nanoTime() > it }
				fps = frames.size
			} else {
				val currentTimeNano = System.nanoTime()

				val currentTypeMilli = System.currentTimeMillis()
				if (currentTypeMilli - lastUpdated >= updateDelay) {
					lastUpdated = currentTypeMilli
					val elapsedNs = currentTimeNano - lastFrameTime
					fps = if (elapsedNs > 0) (1000000000 / elapsedNs).toInt()
					else 0
				}

				lastFrameTime = currentTimeNano
			}
		}
	}

	override fun ImGuiBuilder.buildLayout() {
		text("FPS: $fps")
	}
}