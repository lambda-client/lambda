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

import com.lambda.event.events.MouseEvent
import com.lambda.event.events.RenderEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import java.lang.Math.clamp

object Zoom : Module(
    "Zoom",
    "Zooms the current view",
    ModuleTag.RENDER
) {
    override val disableOnRelease by setting("Disable On Release", true)
    private var zoom by setting("Zoom", 2f, 1f..10f, 0.1f)
    private val style by setting("Style", ZoomStyle.EaseOut)
    private val animationDuration by setting("Animation Duration", 1f, 0.1f..10f, 0.1f) { style != ZoomStyle.Instant }
    private val scroll by setting("Scroll", true)
    private val persistentScroll by setting("Persistent Scroll", false) { scroll }
    private val sensitivity by setting("Sensitivity", 0.4f, 0.1f..1f, 0.1f) { scroll }
    @JvmStatic val smoothMovement by setting("Smooth Movement", false)

    private var extraZoom = 0f
        set(value) {
            field = value.coerceAtLeast(-zoom + 1)
        }
    @JvmStatic val targetZoom: Float
        get() = zoom + extraZoom

    @JvmStatic var currentZoom = 1f; private set
    private var lastZoomTime = 1L
    private val zoomProgress
        get() =
            clamp((System.currentTimeMillis() - lastZoomTime) / (animationDuration * 1000).toDouble(), 0.0, 1.0).toFloat()

    init {
        listen<MouseEvent.Scroll> { event ->
            val yDelta = event.delta.y.toFloat()
            val delta = (yDelta * sensitivity) + (((zoom + extraZoom) * sensitivity) * yDelta)
            if (persistentScroll) zoom += delta
            else extraZoom += delta
            updateZoomTime()
            event.cancel()
        }

        listen<RenderEvent.Render>(alwaysListen = true) {
            updateCurrentZoom()
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
        lastZoomTime = System.currentTimeMillis()
    }

    @JvmStatic
    fun updateCurrentZoom() {
        val target = if (isEnabled) targetZoom else 1f
        if (currentZoom == target) return
        currentZoom = when (style) {
            ZoomStyle.Instant -> target
            ZoomStyle.EaseOut -> easeOut(currentZoom, target, zoomProgress)
            ZoomStyle.EaseIn -> easeIn(currentZoom, target, zoomProgress)
        }
    }

    private fun easeOut(start: Float, end: Float, progress: Float): Float {
        val easedT = 1f - (1f - progress) * (1f - progress)
        return start + ((end - start) * easedT)
    }

    private fun easeIn(start: Float, end: Float, progress: Float): Float {
        val easedT = progress * progress
        return start + ((end - start) * easedT)
    }

    private enum class ZoomStyle {
        Instant,
        EaseOut,
        EaseIn
    }
}