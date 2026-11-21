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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lambda.gui.component.rememberTexture
import com.lambda.gui.core.observe
import com.lambda.module.HudModule
import com.lambda.module.ModuleTag

@Suppress("unused")
object Watermark : HudModule(
    name = "Watermark",
    tag = ModuleTag.HUD,
    enabledByDefault = true,
) {
    private val scale by setting("Scale", 0.15f, 0.01f..1f, 0.01f)

    @Composable
    override fun Content() {
        val logo = rememberTexture("textures/lambda.png")
        val scale by observe { scale }
        Image(
            logo,
            "Lambda",
            Modifier.size((logo.width * scale).dp, (logo.height * scale).dp)
        )
    }
}
