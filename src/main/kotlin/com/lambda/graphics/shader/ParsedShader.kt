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

package com.lambda.graphics.shader

import com.lambda.util.stream
import org.apache.commons.io.IOUtils

class ParsedShader(path: String) {
    private val text = IOUtils.toString("shaders/$path.glsl".stream, Charsets.UTF_8)

    private val blocks = text.getBlocks()
    private val dependencies = text.getDependencies()

    val methods = text.getMethods()
    val definitions = text.getDefinitions()

    val attribs = blocks.getBlock("attributes")
    val uniforms = blocks.getBlock("uniforms")
    val exported = blocks.getBlock("export")

    init {
        dependencies.forEach { dependencyName ->
            val dependency = ParsedShader("shared/$dependencyName")

            //attribs.addAll(shader.blocks.getBlock("attributes"))
            dependency.attribs.forEach { attrib ->
                check(attribs.any { it.name == attrib.name && it.type == attrib.type }) {
                    "[${path}]: dependency \"$dependencyName\" requires \"${attrib.type} ${attrib.name}\" attribute to be present"
                }
            }

            uniforms.addAll(dependency.uniforms)
            exported.addAll(dependency.exported)

            definitions.addAll(dependency.definitions)
            methods.addAll(dependency.methods.filter {
                it.name != "fragment" && it.name != "vertex"
            })
        }
    }

    data class Field(val type: String, val name: String, val flag: String?)
    data class Method(val name: String, val returnType: String, val parameters: String, var code: String) {
        fun construct(constructedName: String = name): String {
            return "$returnType $constructedName($parameters) {\n    $code\n}\n"
        }
    }

    companion object {
        private val blockRegex = Regex("""\s*(\w+)\s*\{(.*?)}\s*""", RegexOption.DOT_MATCHES_ALL)
        private val methodRegex = Regex(
            """^\s*(\w+)\s+(\w+)\s*\((.*?)\)\s*\{(.*?)}#""",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.MULTILINE)
        )

        private val fieldRegex = Regex("""\s*(\w+(?:\s+\w+)*)\s+(\w+)\s*;\s*(#.*)?""")
        private val includeRegex = Regex("""^#include\s+"([\w-]+)"""", RegexOption.MULTILINE)
        private val defineRegex = Regex("""^#define\s+(\w+)\s+(.+)""", RegexOption.MULTILINE)

        private fun String.getBlocks() = mutableMapOf<String, MutableSet<Field>>().apply {
            blockRegex.findAll(this@getBlocks).forEach { match ->
                val blockName = match.groupValues[1]
                val blockFieldsStr = match.groupValues[2]

                val fields = fieldRegex.findAll(blockFieldsStr).map { fieldMatch ->
                    val type = fieldMatch.groupValues[1]
                    val name = fieldMatch.groupValues[2]

                    val flag = fieldMatch.groupValues[3]
                        .removePrefix("#").trim()
                        .takeIf { it.isNotEmpty() }

                    Field(type, name, flag)
                }.toMutableSet()

                getOrPut(blockName, ::mutableSetOf).addAll(fields)
            }
        }

        private fun String.getMethods() = mutableSetOf<Method>().apply {
            methodRegex.findAll(this@getMethods).forEach { match ->
                val returnType = match.groupValues[1]
                val methodName = match.groupValues[2]
                val parameters = match.groupValues[3]
                val code = match.groupValues[4].trim()

                add(Method(methodName, returnType, parameters, code))
            }
        }

        private fun String.getDependencies() = includeRegex.findAll(this)
            .map { it.groupValues[1] }
            .toSet()

        private fun String.getDefinitions() = mutableSetOf<Field>().apply {
            defineRegex.findAll(this@getDefinitions).forEach { match ->
                add(Field(match.groupValues[2].trim(), match.groupValues[1], null))
            }
        }

        private fun MutableMap<String, MutableSet<Field>>.getBlock(name: String) =
            getOrDefault(name, mutableSetOf())
    }
}
