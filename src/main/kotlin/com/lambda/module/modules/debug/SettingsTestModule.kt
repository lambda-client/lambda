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
import com.lambda.config.groups.ScreenLineSettings
import com.lambda.config.groups.ScreenTextSettings
import com.lambda.config.groups.WorldLineSettings
import com.lambda.config.groups.WorldTextSettings
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag

@Suppress("unused")
object SettingsTestModule : Module(
    name = "SettingsTestModule",
    description = "Test module for Line and Text Config Settings",
    tag = ModuleTag.DEBUG
) {
    private const val WORLD_LINE_TAB = "World Line"
    private const val SCREEN_LINE_TAB = "Screen Line"
    private const val WORLD_TEXT_TAB = "World Text"
    private const val SCREEN_TEXT_TAB = "Screen Text"

    @Tab(WORLD_LINE_TAB) private val worldLineConfig = settingBlock(WorldLineSettings(this))
    @Tab(SCREEN_LINE_TAB) private val screenLineConfig = settingBlock(ScreenLineSettings(this))
    @Tab(WORLD_TEXT_TAB) private val worldTextConfig = settingBlock(WorldTextSettings(this))
    @Tab(SCREEN_TEXT_TAB) private val textConfig = settingBlock(ScreenTextSettings(this))

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
