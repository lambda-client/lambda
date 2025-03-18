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


fun buildShaderSource(path: String): Pair<String, String> {
    val shader = ParsedShader(path)

    val vertexShader = StringBuilder().appendLine(HEADER)
    val fragmentShader = StringBuilder().appendLine(HEADER)
    val shaders = setOf(vertexShader, fragmentShader)

    /* Build attribs for vertex shader */
    shader.attribs.apply {
        if (isNotEmpty()) vertexShader.appendLine()

        forEachIndexed { i, attribute ->
            vertexShader.appendLine(
                "layout (location = $i) in ${attribute.type} ${attribute.name};"
            )
        }
    }

    /* Build uniforms */
    shader.uniforms.apply {
        val vertex = mutableSetOf<String>()
        val fragment = mutableSetOf<String>()

        vertex.add("uniform mat4 u_ProjModel;")
        forEach { uniform ->
            val line = "uniform ${uniform.type} ${uniform.name};"

            when (uniform.flag?.trim()) {
                "vertex" -> vertex += line
                "fragment" -> fragment += line
                else -> {
                    vertex += line; fragment += line
                }
            }
        }

        vertex.apply {
            vertexShader.appendLine()
            forEach { vertexShader.appendLine(it) }
        }

        fragment.apply {
            if (isNotEmpty()) fragmentShader.appendLine()
            forEach { fragmentShader.appendLine(it) }
        }
    }

    /* Does "v_TexCoord = uv" in the vertex shader for you */
    val autoAssignment = StringBuilder()

    /* Build in/out vars */
    shader.exported.apply {
        if (isNotEmpty()) shaders.forEach(StringBuilder::appendLine)

        // Add gl_Position = u_ProjModel * pos; by default
        val hasPosAttribute = shader.attribs.any { it.name == "pos" && it.type == "vec4" }
        val glPosImplemented = any { it.name == "gl_Position" }
        if (hasPosAttribute && !glPosImplemented) {
            autoAssignment.appendLine("    gl_Position = u_ProjModel * pos;")
        }

        forEach { export ->
            if (export.type != "core") {
                vertexShader.appendLine("out ${export.type} ${export.name};")
                fragmentShader.appendLine("in ${export.type} ${export.name};")
            }

            export.flag?.let { expr -> /* Add assignment if present */
                autoAssignment.appendLine("    ${export.name} = ${expr.trim()};")
            }
        }

        fragmentShader.appendLine()
        fragmentShader.appendLine("out vec4 color;")
    }

    /* Build definitions */
    shader.definitions.apply {
        if (isNotEmpty()) fragmentShader.appendLine()

        forEach { definition ->
            fragmentShader.appendLine("#define ${definition.name} ${definition.type}")
        }
    }

    /* Build methods */
    shaders.forEach(StringBuilder::appendLine)
    val methods = shader.methods

    val vertexMain = methods.takeMain("vertex").apply {
        code += autoAssignment.trim()
    }.construct("main")
    vertexShader.appendLine(vertexMain)

    val fragmentMain = methods.takeMain("fragment").construct("main")
    methods.forEach { fragmentShader.appendLine(it.construct()) }
    fragmentShader.appendLine(fragmentMain)

    return vertexShader.toString().trimEnd() to fragmentShader.toString().trimEnd()
}

private fun MutableSet<ParsedShader.Method>.takeMain(name: String) = firstOrNull {
    it.name == name && it.returnType == "void" && it.parameters.isEmpty()
}.apply(this::remove) ?: ParsedShader.Method(name, "void", "", "")


private const val HEADER = "#version 330 core"
