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
import com.lambda.util.NamedEnum
import net.minecraft.block.Blocks
import net.minecraft.util.math.BlockPos
import java.awt.Color

object SettingTest : Module(
    name = "SettingTest",
    tag = ModuleTag.DEBUG,
) {
    private enum class Group(override val displayName: String) : NamedEnum {
        GENERIC("Generic"),
        NUMERIC("Numeric"),
        COLLECTIONS("Collections"),
        COMPLEX("Complex"),
        FUNCTIONAL("Functional"),
    }

    private enum class SubGroup(override val displayName: String) : NamedEnum {
        Foo("Foo"),
        Bar("Bar")
    }

    // CharSetting
    private val charSetting by setting("Character Setting", 'A').group(Group.GENERIC)

    // String
    private val stringSetting by setting("String Setting", "Default String").group(Group.GENERIC, SubGroup.Foo)

    // Comparable
    private val booleanSetting by setting("Boolean Setting", true).group(Group.GENERIC, SubGroup.Bar)
    private val enumSetting by setting("Enum Setting", ExampleEnum.VALUE_ONE).group(Group.GENERIC)

    // Numeric
    private val doubleSetting by setting("Double Setting", 3.14159, 0.0..100.0, 0.1, unit = " crumbs").group(Group.NUMERIC)
    private val floatSetting by setting("Float Setting", 3.14f, 0.0f..100.0f, 0.1f, unit = " pies").group(Group.NUMERIC)
    private val integerSetting by setting("Integer Setting", 42, 0..1000, unit = " apples").group(Group.NUMERIC)
    private val longSetting by setting("Long Setting", 100000L, 0L..1000000L, 1000L, unit = " pizzas").group(Group.NUMERIC)

    // Collections
    private val stringList by setting("String List", listOf("Hello", "World"), listOf("Hello", "World")).group(Group.COLLECTIONS)
    private val stringSet by setting("String Set", setOf("Apple", "Banana"), setOf("Apple", "Banana")).group(Group.COLLECTIONS)
    private val stringMap by setting("String Map", mapOf("Key1" to "Value1", "Key2" to "Value2")).group(Group.COLLECTIONS)

    // Complex
    private val blockPosSetting by setting("Block Position", BlockPos(0, 0, 0)).group(Group.COMPLEX)
    private val blockSetting by setting("Block Setting", Blocks.OBSIDIAN).group(Group.COMPLEX)
    private val colorSetting by setting("Color Setting", Color.GREEN).group(Group.COMPLEX)
    private val keyBindSetting by setting("Key Bind Setting", KeyCode.T).group(Group.COMPLEX)

    // Complex collections
    private val blockPosSet by setting("Block Position Set", setOf(BlockPos(0, 0, 0)), setOf(BlockPos(0, 0, 0))).group(Group.COMPLEX)
    private val blockList by setting("Block List", listOf(Blocks.OBSIDIAN), listOf(Blocks.OBSIDIAN)).group(Group.COMPLEX)
    private val colorMap by setting("Color Map", mapOf("Primary" to Color.GREEN)).group(Group.COMPLEX)
    private val keyBindSet by setting("Key Bind Set", setOf(KeyCode.T), setOf(KeyCode.T)).group(Group.COMPLEX)

    // Other
    private val unitSetting by setting("Unit Test", { this@SettingTest.info("Unit setting") }).group(Group.FUNCTIONAL)

    enum class ExampleEnum {
        VALUE_ONE,
        VALUE_TWO,
        VALUE_THREE
    }
}
