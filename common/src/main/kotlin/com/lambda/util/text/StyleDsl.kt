/*
 * Copyright 2023 The Quilt Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

@file:Suppress("MemberVisibilityCanBePrivate")

package com.lambda.util.text

import net.minecraft.text.*
import net.minecraft.util.Identifier
import java.awt.Color

/**
 * Marks objects as being part the Style Builder DSL.
 */
@DslMarker
annotation class StyleDsl

/**
 * A mutable style object that transforms to Minecraft [styles][Style].
 */
@StyleDsl
class StyleBuilder {
    /**
     * A [Color] to apply to the text.
     */
    var color: Color? = null
        set(value) {
            if (field != value) {
                cachedStyle = null
            }
            field = value
        }

    /**
     * Whether to obfuscate the text or not.
     */
    var obfuscated: Boolean? = null
        set(value) {
            if (field != value) {
                cachedStyle = null
            }
            field = value
        }

    /**
     * Whether to format the text in bold or not.
     */
    var bold: Boolean? = null
        set(value) {
            if (field != value) {
                cachedStyle = null
            }
            field = value
        }

    /**
     * Whether to format the text in italics or not.
     */
    var italic: Boolean? = null
        set(value) {
            if (field != value) {
                cachedStyle = null
            }
            field = value
        }

    /**
     * Whether to format the text with a strikethrough or not.
     */
    var strikethrough: Boolean? = null
        set(value) {
            if (field != value) {
                cachedStyle = null
            }
            field = value
        }

    /**
     * Whether to format the text with an underline or not.
     */
    var underlined: Boolean? = null
        set(value) {
            if (field != value) {
                cachedStyle = null
            }
            field = value
        }

    /**
     * A [HoverEvent] to apply to the text.
     */
    var hoverEvent: HoverEvent? = null
        set(value) {
            if (field != value) {
                cachedStyle = null
            }
            field = value
        }

    /**
     * A [ClickEvent] to apply to the text.
     */
    var clickEvent: ClickEvent? = null
        set(value) {
            if (field != value) {
                cachedStyle = null
            }
            field = value
        }

    /**
     * An insertion inserted when a piece of text clicked while shift key is down in the chat HUD to apply to the text.
     */
    var insertion: String? = null
        set(value) {
            if (field != value) {
                cachedStyle = null
            }
            field = value
        }

    /**
     * An [Identifier] for the Minecraft font that would like to be used.
     */
    var font: Identifier? = null
        set(value) {
            if (field != value) {
                cachedStyle = null
            }
            field = value
        }

    private var cachedStyle: Style? = Style.EMPTY

    /**
     * Converts 3 RGB int values to a [Color].
     *
     * @param red The red channel of the color
     * @param green The green channel of the color
     * @param blue The blue channel of the color
     */
    fun color(red: Int, green: Int, blue: Int) {
        this.color = Color(red, green, blue)
    }

    /**
     * Converts 3 RGB float values to a [Color].
     *
     * @param red The red channel of the color
     * @param green The green channel of the color
     * @param blue The blue channel of the color
     */
    fun color(red: Float, green: Float, blue: Float) {
        this.color = Color(red, green, blue)
    }

    /**
     * Converts 3 RGB double values to a [Color].
     *
     * @param red The red channel of the color
     * @param green The green channel of the color
     * @param blue The blue channel of the color
     */
    fun color(red: Double, green: Double, blue: Double) {
        color(red.toFloat(), green.toFloat(), blue.toFloat())
    }

    /**
     * Converts a single RGB value to a [Color].
     *
     * @param rgb The RGB value to convert
     */
    fun color(rgb: Int) {
        this.color = Color(rgb)
    }

    /**
     * Converts a hexadecimal string color to a [Color].
     *
     * @param colorCode The color to convert.
     */
    fun color(colorCode: String) {
        this.color = Color(colorCode.toColor())
    }

    /**
     * Converts a string color to an [Int].
     *
     * @return The [Int] converted color
     */
    @Suppress("MagicNumber")
    private fun String.toColor(): Int {
        var color = when {
            startsWith("#") -> substring(1)
            startsWith("0x") -> substring(2)
            matches("^\\d+$".toRegex()) -> this
            else -> error("value is not a valid color")
        }
        if (color.length == 3) {
            color = color.split("").joinToString("") {
                "$it$it"
            }
        }

        return color.toInt(16)
    }

    /**
     * Copies all non-null properties from a given [base] [Style]
     * into this builder.
     */
    fun copyFrom(base: Style) {
        color = base.color?.let { Color(it.rgb) } ?: color
        bold = base.isBold
        italic = base.isItalic
        strikethrough = base.strikethrough
        underlined = base.underlined
        clickEvent = base.clickEvent
        hoverEvent = base.hoverEvent
        insertion = base.insertion
        font = base.font
    }

    /**
     * Creates a new style while avoiding creating a new instance of the empty style.
     *
     * Both helpful to avoid multiple instances of the style being created,
     * and to enable optimizations in Minecraft code that use reference equality to [Style.EMPTY].
     */
    private fun createNewStyle(): Style {
        if (color == null && bold == null && italic == null && underlined == null && strikethrough == null &&
            obfuscated == null && clickEvent == null && hoverEvent == null && insertion == null && font == null
        ) {
            return Style.EMPTY
        }

        return Style(
            color?.let { TextColor.fromRgb(it.rgb) },
            bold,
            italic,
            underlined,
            strikethrough,
            obfuscated,
            clickEvent,
            hoverEvent,
            insertion,
            font
        )
    }

    /**
     * Builds a [Style] based on properties set on the builder.
     *
     * If called repeatedly with no property changes, will return
     * a cached value.
     *
     * @author Cypher121
     */
    fun buildStyle(): Style {
        return cachedStyle ?: createNewStyle().also { cachedStyle = it }
    }

    /**
     * Applies the [styles][StyleBuilder] to the [text].
     *
     * @param text The text to apply a [style][StyleBuilder] to
     */
    fun applyTo(text: MutableText): MutableText {
        return text.setStyle(buildStyle())
    }
}

/**
 * Build a [Style] using the given [action] to set [StyleBuilder] properties.
 */
@StyleDsl
inline fun buildStyle(action: StyleBuilder.() -> Unit): Style {
    return StyleBuilder().apply(action).buildStyle()
}
