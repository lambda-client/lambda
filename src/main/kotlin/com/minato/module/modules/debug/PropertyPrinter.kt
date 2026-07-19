
package com.minato.module.modules.debug

import com.minato.module.Module
import com.minato.module.tag.ModuleTag
import com.minato.util.FolderRegistry
import com.minato.util.extension.resolveFile
import net.minecraft.block.Block
import net.minecraft.block.Blocks

@Suppress("unused")
object PropertyPrinter : Module(
    name = "PropertyPrinter",
    description = "Prints all properties coupled with all the states that use them into a text file",
    tag = ModuleTag.DEBUG,
) {
    init {
        onEnable {
            val file = FolderRegistry.minato.resolve("property-print").resolveFile("property-print.txt")
            file.parentFile.mkdirs()
            file.writeText("")
            StateInfo.propertyFields.forEach properties@{ property ->
                file.appendText("${property.value.name}\n")
                Blocks::class.java.declaredFields.forEach blocks@{ field ->
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
