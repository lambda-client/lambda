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

package com.lambda.module.modules.debug

import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.util.Communication.info
import com.lambda.util.KeyCode
import net.minecraft.block.Blocks
import net.minecraft.util.math.BlockPos
import java.awt.Color

object SettingTest : Module(
    name = "Setting Test",
    defaultTags = setOf(ModuleTag.DEBUG)
) {
    // CharSetting
    private val charSetting by setting("Character Setting", 'A')

    // String
    private val stringSetting by setting("String Setting", "Default String")

    // Comparable
    private val booleanSetting by setting("Boolean Setting", true)
    private val enumSetting by setting("Enum Setting", ExampleEnum.VALUE_ONE)

    // Numeric
    private val doubleSetting by setting("Double Setting", 3.14159, 0.0..100.0, 0.1)
    private val floatSetting by setting("Float Setting", 3.14f, 0.0f..100.0f, 0.1f)
    private val integerSetting by setting("Integer Setting", 42, 0..1000)
    private val longSetting by setting("Long Setting", 100000L, 0L..1000000L, 1000L)

    // Collections
    private val stringList by setting("String List", listOf("Hello", "World"))
    private val stringSet by setting("String Set", setOf("Apple", "Banana"))
    private val stringMap by setting("String Map", mapOf("Key1" to "Value1", "Key2" to "Value2"))

    // Complex
    private val blockPosSetting by setting("Block Position", BlockPos(0, 0, 0))
    private val blockSetting by setting("Block Setting", Blocks.OBSIDIAN)
    private val colorSetting by setting("Color Setting", Color.GREEN)
    private val keyBindSetting by setting("Key Bind Setting", KeyCode.T)

    // Complex collections
    private val blockPosSet by setting("Block Position Set", setOf(BlockPos(0, 0, 0)))
    private val blockList by setting("Block List", listOf(Blocks.OBSIDIAN))
    private val colorMap by setting("Color Map", mapOf("Primary" to Color.GREEN))
    private val keyBindSet by setting("Key Bind Set", setOf(KeyCode.T))

    // Other
    private val unitSetting by setting("Unit Test", { this@SettingTest.info("Unit setting") })

    enum class ExampleEnum {
        VALUE_ONE,
        VALUE_TWO,
        VALUE_THREE
    }
}
