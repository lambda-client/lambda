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

package com.lambda.graphics.gl

import org.lwjgl.opengl.GL30C.GL_BLEND
import org.lwjgl.opengl.GL30C.GL_CULL_FACE
import org.lwjgl.opengl.GL30C.GL_DEPTH_TEST
import org.lwjgl.opengl.GL30C.GL_LINE_SMOOTH
import org.lwjgl.opengl.GL30C.GL_ONE_MINUS_SRC_ALPHA
import org.lwjgl.opengl.GL30C.GL_SRC_ALPHA
import org.lwjgl.opengl.GL30C.glBlendFunc
import org.lwjgl.opengl.GL30C.glBlendFuncSeparate
import org.lwjgl.opengl.GL30C.glDepthMask
import org.lwjgl.opengl.GL30C.glDisable
import org.lwjgl.opengl.GL30C.glEnable
import org.lwjgl.opengl.GL30C.glLineWidth

// ToDo: Migrate particle system so we can remove this
object GlStateUtils {
	private var depthTestState = true
	private var blendState = false
	private var cullState = true

	fun setupGL(block: () -> Unit) {
		val savedDepthTest = depthTestState
		val savedBlend = blendState
		val savedCull = cullState

		glDepthMask(false)
		lineSmooth(true)

		depthTest(false)
		blend(true)
		cull(false)

		block()

		glDepthMask(true)
		lineSmooth(false)

		depthTest(savedDepthTest)
		blend(savedBlend)
		cull(savedCull)
	}

	fun withDepth(maskWrite: Boolean = false, block: () -> Unit) {
		depthTest(true)
		if (maskWrite) glDepthMask(true)
		block()
		if (maskWrite) glDepthMask(false)
		depthTest(false)
	}

	fun withFaceCulling(block: () -> Unit) {
		cull(true)
		block()
		cull(false)
	}

	fun withLineWidth(width: Double, block: () -> Unit) {
		glLineWidth(width.toFloat())
		block()
		glLineWidth(1f)
	}

	fun withBlendFunc(
		sfactorRGB: Int, dfactorRGB: Int,
		sfactorAlpha: Int = sfactorRGB, dfactorAlpha: Int = dfactorRGB,
		block: () -> Unit
	) {
		glBlendFuncSeparate(sfactorRGB, dfactorRGB, sfactorAlpha, dfactorAlpha)
		block()
		glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)
	}

	@JvmStatic
	fun capSet(id: Int, flag: Boolean) {
		val field = when (id) {
			GL_DEPTH_TEST -> ::depthTestState
			GL_BLEND -> ::blendState
			GL_CULL_FACE -> ::cullState
			else -> return
		}

		field.set(flag)
	}

	private fun blend(flag: Boolean) {
		if (flag) {
			glEnable(GL_BLEND)
			glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)
		} else glDisable(GL_BLEND)
	}

	private fun cull(flag: Boolean) {
		if (flag) glEnable(GL_CULL_FACE)
		else glDisable(GL_CULL_FACE)
	}

	private fun depthTest(flag: Boolean) {
		if (flag) glEnable(GL_DEPTH_TEST)
		else glDisable(GL_DEPTH_TEST)
	}

	private fun lineSmooth(flag: Boolean) {
		if (flag) glEnable(GL_LINE_SMOOTH)
		else glDisable(GL_LINE_SMOOTH)
	}
}
