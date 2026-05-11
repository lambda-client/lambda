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

@file:Suppress("MemberVisibilityCanBePrivate")

package com.lambda.util.text

import net.minecraft.text.ClickEvent
import net.minecraft.text.HoverEvent
import net.minecraft.text.MutableText
import net.minecraft.text.Style
import net.minecraft.text.StyleSpriteSource
import net.minecraft.text.TextColor
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
     * A shadow [Color] to apply to the text.
     */
    var shadowColor: Color? = null
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
     * A [StyleSpriteSource] for the Minecraft font that would like to be used.
     */
    var font: StyleSpriteSource? = null
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
     * Converts 3 RGB int values to a shadow [Color].
     *
     * @param red The red channel of the color
     * @param green The green channel of the color
     * @param blue The blue channel of the color
     */
    fun shadowColor(red: Int, green: Int, blue: Int) {
        this.shadowColor = Color(red, green, blue)
    }

    /**
     * Converts 3 RGB float values to a shadow [Color].
     *
     * @param red The red channel of the color
     * @param green The green channel of the color
     * @param blue The blue channel of the color
     */
    fun shadowColor(red: Float, green: Float, blue: Float) {
        this.shadowColor = Color(red, green, blue)
    }

    /**
     * Converts 3 RGB double values to a shadow [Color].
     *
     * @param red The red channel of the color
     * @param green The green channel of the color
     * @param blue The blue channel of the color
     */
    fun shadowColor(red: Double, green: Double, blue: Double) {
        shadowColor(red.toFloat(), green.toFloat(), blue.toFloat())
    }

    /**
     * Converts a single RGB value to a shadow [Color].
     *
     * @param rgb The RGB value to convert
     */
    fun shadowColor(rgb: Int) {
        this.shadowColor = Color(rgb)
    }

    /**
     * Converts a hexadecimal string color to a shadow [Color].
     *
     * @param colorCode The color to convert.
     */
    fun shadowColor(colorCode: String) {
        this.shadowColor = Color(colorCode.toColor())
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
            shadowColor?.rgb,
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
