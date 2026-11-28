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

package com.lambda.module.modules.hud

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lambda.gui.component.rememberTexture
import com.lambda.module.HudModule
import com.lambda.module.ModuleTag

@Suppress("unused")
object Watermark : HudModule(
    name = "Watermark",
    tag = ModuleTag.HUD,
    enabledByDefault = true,
	defaultScale = 0.45f,
	defaultAnchorX = 0.005f,
	defaultAnchorY = 0.0089f,
) {
	private const val LOGO_SCALE = 0.05f

	@Composable
	override fun Content() {
		val logo = rememberTexture("textures/lambda.png")
		Image(
			logo,
			"Lambda",
			Modifier.size((logo.width * LOGO_SCALE).dp, (logo.height * LOGO_SCALE).dp)
		)
    }
}
