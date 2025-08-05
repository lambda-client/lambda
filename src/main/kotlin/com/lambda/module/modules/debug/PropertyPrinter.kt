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
import com.lambda.util.FolderRegister
import com.lambda.util.extension.resolveFile
import net.minecraft.block.Block
import net.minecraft.block.Blocks

object PropertyPrinter : Module(
    "PropertyPrinter",
    "Prints all properties coupled with all the states that use them into a text file",
    setOf(ModuleTag.DEBUG)
) {
    init {
        onEnable {
            val file = FolderRegister.lambda.resolve("property-print").resolveFile("property-print.txt")
            file.parentFile.mkdirs()
            file.writeText("")
            StateInfo.propertyFields.forEach properties@ { property ->
                file.appendText("${property.value.name}\n")
                Blocks::class.java.declaredFields.forEach blocks@ { field ->
                    field.isAccessible = true
                    val block = field.get(null)
                    if (!Block::class.java.isAssignableFrom(block::class.java)) return@blocks
                    if (property.key in (block as Block).defaultState.properties) {
                        file.appendText("    $block\n")
                    }
                }
                file.appendText("\n\n\n\n\n")
            }
            disable()
        }
    }
}