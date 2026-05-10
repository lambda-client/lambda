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

package com.lambda.module.modules.debug

import com.lambda.config.Tab
import com.lambda.config.settings.blocks.ScreenLineSettings
import com.lambda.config.settings.blocks.ScreenTextSettings
import com.lambda.config.settings.blocks.WorldLineSettings
import com.lambda.config.settings.blocks.WorldTextSettings
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag

@Suppress("unused")
object SettingsTestModule : Module(
    name = "SettingsTestModule",
    description = "Test module for Line and Text Config Settings",
    tag = ModuleTag.Debug
) {
    private const val WorldLineTab = "World Line"
    private const val ScreenLineTab = "Screen Line"
    private const val WorldTextTab = "World Text"
    private const val ScreenTextTab = "Screen Text"

    @Tab(WorldLineTab) private val worldLineConfig by settingBlock(WorldLineSettings(this))
    @Tab(ScreenLineTab) private val screenLineConfig by settingBlock(ScreenLineSettings(this))
    @Tab(WorldTextTab) private val worldTextConfig by settingBlock(WorldTextSettings(this))
    @Tab(ScreenTextTab) private val textConfig by settingBlock(ScreenTextSettings(this))

//    private val renderer = ImmediateRenderer("SettingsTestRenderer")

//    init {
//        listen<RenderEvent.Render> {
//            renderer.tick()
//            renderer.shapes {
//                val startPos = lerp(mc.tickDelta, player.prevPos, player.pos).offset(Direction.NORTH, 3.0)
//
//                // Render line using config
//                lineGradient(
//                    startPos,
//                    lineConfig.startColor,
//                    startPos.offset(Direction.EAST, 3.0),
//                    lineConfig.endColor,
//                    lineConfig.lineWidth,
//                    lineConfig.getDashStyle()
//                )
//
//                // Render text using config
//                worldText(
//                    "Configured Text",
//                    startPos.add(0.0, 1.0, 0.0),
//                    style = textConfig.getSDFStyle()
//                )
//            }
//            renderer.render()
//        }
//
//        onDisable { renderer.close() }
//    }
}
