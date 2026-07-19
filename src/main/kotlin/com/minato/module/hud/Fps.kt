
package com.minato.module.hud

import com.minato.event.events.RenderEvent
import com.minato.event.listener.SafeListener.Companion.listen
import com.minato.gui.dsl.ImGuiBuilder
import com.minato.module.HudModule
import com.minato.module.tag.ModuleTag
import com.minato.util.collections.LimitedDecayQueue
import kotlin.time.Duration.Companion.seconds

@Suppress("unused")
object Fps : HudModule(
	name = "FPS",
	description = "Displays your games frames per second",
	tag = ModuleTag.HUD
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