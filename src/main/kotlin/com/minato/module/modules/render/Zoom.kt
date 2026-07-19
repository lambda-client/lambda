
package com.minato.module.modules.render

import com.minato.event.events.ButtonEvent
import com.minato.event.listener.SafeListener.Companion.listen
import com.minato.module.Module
import com.minato.module.tag.ModuleTag
import com.minato.util.NamedEnum
import java.lang.Math.clamp

object Zoom : Module(
	name = "Zoom",
	description = "Zooms the current view",
	tag = ModuleTag.RENDER,
) {
	private var zoom by setting("Zoom", 2f, 1f..10f, 0.1f)
	private val style by setting("Style", ZoomStyle.EaseOut)
	private val animationDuration by setting("Animation Duration", 200, 40..1500, 20, unit = "ms") { style != ZoomStyle.Instant }
	private val disableDuration by setting("Disable Duration", 100, 0..1500, 20, unit = "ms") { style != ZoomStyle.Instant }
	private val scroll by setting("Scroll", true)
	private val persistentScroll by setting("Persistent Scroll", false) { scroll }
	private val sensitivity by setting("Sensitivity", 0.2f, 0.1f..1f, 0.1f) { scroll }
	@JvmStatic val smoothMovement by setting("Smooth Movement", false)

	private var extraZoom = 0f
		set(value) {
			field = value.coerceAtLeast(-zoom + 1)
		}
	@JvmStatic val targetZoom: Float
		get() = zoom + extraZoom
	private var currentZoom = 1f
	@JvmStatic var lerpedZoom = 1f; private set
	private var lastZoomTime = 1L
	private val zoomProgress
		get() = clamp((System.currentTimeMillis() - lastZoomTime) / (if (isEnabled) animationDuration else disableDuration).toDouble(), 0.0, 1.0).toFloat()

	init {
		listen<ButtonEvent.Mouse.Scroll> { event ->
			val yDelta = event.delta.y.toFloat()
			val delta = (yDelta * sensitivity) + (((zoom + extraZoom) * sensitivity) * yDelta)
			if (persistentScroll) zoom += delta
			else extraZoom += delta
			updateZoomTime()
			event.cancel()
		}

		onEnable {
			updateZoomTime()
		}

		onDisable {
			extraZoom = 0f
			updateZoomTime()
		}
	}

	private fun updateZoomTime() {
		currentZoom = lerpedZoom
		lastZoomTime = System.currentTimeMillis()
	}

	@JvmStatic
	fun updateCurrentZoom() {
		val target = if (isEnabled) targetZoom else 1f
		lerpedZoom = style.apply(currentZoom, target, zoomProgress)
		if (lerpedZoom == targetZoom) lerpedZoom = targetZoom
	}

	@Suppress("unused")
	private enum class ZoomStyle(
		override val displayName: String,
		val apply: (Float, Float, Float) -> Float,
	) : NamedEnum {
		Instant("Instant", { _, v, _ -> v }),
		EaseOut("Ease Out", { start, end, progress -> start + ((end - start) * (1f - ((1f - progress) * (1f - progress) * (1f - progress)))) }),
		EaseIn("Ease In", { start, end, progress -> start + ((end - start) * (progress * progress * progress)) })
	}
}
