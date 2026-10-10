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

import com.lambda.config.Config
import com.lambda.config.ConfigBlock
import com.lambda.config.Group
import com.lambda.config.Tab
import com.lambda.config.blocks.ScreenLineSettings
import com.lambda.config.blocks.ScreenTextSettings
import com.lambda.config.blocks.WorldLineSettings
import com.lambda.config.blocks.WorldTextSettings
import com.lambda.module.Module
import com.lambda.module.ModuleTag

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

    private const val TEST_CONFIG_BLOCK_TAB = "Test Config Block Tab"
    private const val TEXT_CONFIG_BLOCK_GROUP = "Test Config Block Group"
    @Tab(TEST_CONFIG_BLOCK_TAB) @Group(TEXT_CONFIG_BLOCK_GROUP) private val testConfigBlock by configBlock(TestConfigBlock(this))
    @Tab(TEST_CONFIG_BLOCK_TAB) private val testValue1 by property(1)

    @Tab(WORLD_LINE_TAB) private val worldLineConfig by configBlock(WorldLineSettings(this))
    @Tab(SCREEN_LINE_TAB) private val screenLineConfig by configBlock(ScreenLineSettings(this))
    @Tab(WORLD_TEXT_TAB) private val worldTextConfig by configBlock(WorldTextSettings(this))
    @Tab(SCREEN_TEXT_TAB) private val textConfig by configBlock(ScreenTextSettings(this))
}

class TestConfigBlock(override val c: Config) : ConfigBlock {
    var testValue2 by c.property(2)
    val testValue3 by c.property { 3 }
}
