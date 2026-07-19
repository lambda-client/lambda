
package com.minato.module.modules.debug

import com.minato.config.Config
import com.minato.config.ConfigBlock
import com.minato.config.Group
import com.minato.config.Tab
import com.minato.config.blocks.ScreenLineSettings
import com.minato.config.blocks.ScreenTextSettings
import com.minato.config.blocks.WorldLineSettings
import com.minato.config.blocks.WorldTextSettings
import com.minato.module.Module
import com.minato.module.tag.ModuleTag

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
