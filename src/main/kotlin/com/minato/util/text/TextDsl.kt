
package com.minato.util.text

import com.minato.module.modules.client.Client
import net.minecraft.text.ClickEvent
import net.minecraft.text.HoverEvent
import net.minecraft.text.MutableText
import net.minecraft.text.NbtDataSource
import net.minecraft.text.Style
import net.minecraft.text.StyleSpriteSource
import net.minecraft.text.Text
import java.awt.Color
import java.util.*

/**
 * Marks objects as being part of the Text Builder DSL.
 */
@DslMarker
annotation class TextDsl

/**
 * A builder for a styled [Text] instance. Can be built into text with [build].
 *
 * @see [buildText]
 */
@TextDsl
class TextBuilder {
    /**
     * Current text state of the builder.
     */
    val text: MutableText = Text.empty()

    /**
     * Current style state of the builder to be applied with [styleAndAppend].
     */
    val style: StyleBuilder = StyleBuilder()

    /**
     * Applies the given [action] while setting the [StyleBuilder] property
     * described by [getProp] and [setProp] to [newValue] for the duration of the action.
     *
     * The value is then reset to its original state.
     */
    inline fun <T> withProp(
        newValue: T,
        getProp: StyleBuilder.() -> T,
        setProp: StyleBuilder.(T) -> Unit,
        action: TextBuilder.() -> Unit,
    ) {
        val oldValue = getProp(style)
        setProp(style, newValue)

        action()

        setProp(style, oldValue)
    }

    /**
     * Applies the current [style] of the builder to the given [text]
     * and appends it to the builder's result.
     */
    @TextDsl
    fun styleAndAppend(text: MutableText) {
        this.text.append(style.applyTo(text))
    }

    /**
     * Returns the [Text] result of this builder.
     */
    fun build(): Text {
        return text
    }
}

/**
 * Builds a [Text] instance by configuring a [TextBuilder] with the given [action].
 */
@TextDsl
inline fun buildText(action: TextBuilder.() -> Unit): Text {
    return TextBuilder().apply(action).build()
}

/**
 * Adds a translatable text.
 *
 * @param value The translation key of the text.
 * @param args Any arguments to apply to the translatable text. Can be left
 * empty for no arguments.
 * @see StyleBuilder for action
 */
@TextDsl
fun TextBuilder.translatable(value: String, vararg args: Any) {
    styleAndAppend(Text.translatable(value, *args))
}

/**
 * Adds a literal text.
 *
 * @param value The text.
 * @see StyleBuilder for action
 */
@TextDsl
fun TextBuilder.literal(value: String) {
    styleAndAppend(Text.literal(value))
}

/**
 * Adds a literal text.
 *
 * @param value The text.
 * @see StyleBuilder for action
 */
@TextDsl
fun TextBuilder.literal(color: Color = Color.WHITE, value: String) {
    color(color) {
        literal(value)
    }
}

/**
 * Adds a mutable key bind text.
 *
 * @param key The key of the Key bind
 * @see StyleBuilder for action
 */
@TextDsl
fun TextBuilder.keybind(key: String) {
    styleAndAppend(Text.keybind(key))
}


@TextDsl
fun TextBuilder.nbt(
    pathPattern: String,
    interpreting: Boolean,
    separator: Optional<Text>,
    nbt: NbtDataSource,
) {
    styleAndAppend(
        Text.nbt(
            pathPattern,
            interpreting,
            separator,
            nbt
        )
    )
}

/**
 * Adds a pre-existing [Text] instance.
 *
 * @param value The text to add
 * @see StyleBuilder for action
 */
@TextDsl
fun TextBuilder.text(value: Text) {
    styleAndAppend(value.copy())
}

/**
 * Adds a scoreboard.
 *
 * @param name The name to add to the scoreboard
 * @param objective The objective of the scoreboard
 * @see StyleBuilder for action
 */
@TextDsl
fun TextBuilder.scoreboard(name: String, objective: String) {
    styleAndAppend(Text.score(name, objective))
}

/**
 * Adds a resolvable entity selector.
 *
 * @param separator the optional separator if there are multiple matches issued from the selector
 * @param selector the selector
 * @see StyleBuilder for action
 */
@TextDsl
fun TextBuilder.selector(selector: String, separator: Optional<Text>) {
    // TODO: fix this
    //styleAndAppend(Text.selector(selector, separator))
}

/**
 * Adds a mutable empty text.
 *
 * @see StyleBuilder for action
 */
@TextDsl
fun TextBuilder.empty() {
    styleAndAppend(Text.empty())
}

/**
 * Applies the [TextBuilder] [action] with [color] set to the provided value.
 */
@TextDsl
inline fun TextBuilder.color(color: Color?, action: TextBuilder.() -> Unit) {
    withProp(color, { this.color }, { this.color = it }, action)
}

@TextDsl
fun TextBuilder.highlighted(value: String) {
    color(Client.highlightColor) {
        literal(value)
    }
}

/**
 * Applies the [TextBuilder] [action] with [bold] set
 * to the provided value (or enabled if no value given).
 */
@TextDsl
inline fun TextBuilder.bold(bold: Boolean? = true, action: TextBuilder.() -> Unit) {
    withProp(bold, { this.bold }, { this.bold = it }, action)
}

/**
 * Applies the [TextBuilder] [action] with [italic] set
 * to the provided value (or enabled if no value given).
 */
@TextDsl
inline fun TextBuilder.italic(italic: Boolean? = true, action: TextBuilder.() -> Unit) {
    withProp(italic, { this.italic }, { this.italic = it }, action)
}

/**
 * Applies the [TextBuilder] [action] with [underlined] set
 * to the provided value (or enabled if no value given).
 */
@TextDsl
inline fun TextBuilder.underlined(underlined: Boolean? = true, action: TextBuilder.() -> Unit) {
    withProp(underlined, { this.underlined }, { this.underlined = it }, action)
}

/**
 * Applies the [TextBuilder] [action] with [strikethrough] set
 * to the provided value (or enabled if no value given).
 */
@TextDsl
inline fun TextBuilder.strikethrough(strikethrough: Boolean? = true, action: TextBuilder.() -> Unit) {
    withProp(strikethrough, { this.strikethrough }, { this.strikethrough = it }, action)
}

/**
 * Applies the [TextBuilder] [action] with [obfuscated] set
 * to the provided value (or enabled if no value given).
 */
@TextDsl
inline fun TextBuilder.obfuscated(obfuscated: Boolean? = true, action: TextBuilder.() -> Unit) {
    withProp(obfuscated, { this.obfuscated }, { this.obfuscated = it }, action)
}

/**
 * Applies the [TextBuilder] [action] with [clickEvent] set to the provided value.
 */
@TextDsl
inline fun TextBuilder.clickEvent(clickEvent: ClickEvent?, action: TextBuilder.() -> Unit) {
    withProp(clickEvent, { this.clickEvent }, { this.clickEvent = it }, action)
}

/**
 * Applies the [TextBuilder] [action] with [hoverEvent] set to the provided value.
 */
@TextDsl
inline fun TextBuilder.hoverEvent(hoverEvent: HoverEvent?, action: TextBuilder.() -> Unit) {
    withProp(hoverEvent, { this.hoverEvent }, { this.hoverEvent = it }, action)
}

/**
 * Applies the [TextBuilder] [action] with [insertion] set to the provided value.
 */
@TextDsl
inline fun TextBuilder.insertion(insertion: String?, action: TextBuilder.() -> Unit) {
    withProp(insertion, { this.insertion }, { this.insertion = it }, action)
}

/**
 * Applies the [TextBuilder] [action] with [font] set to the provided value.
 */
@TextDsl
inline fun TextBuilder.font(font: StyleSpriteSource?, action: TextBuilder.() -> Unit) {
    withProp(font, { this.font }, { this.font = it }, action)
}

/**
 * Applies the [TextBuilder] [action] with the selected style properties changed.
 */
@TextDsl
fun TextBuilder.styled(
    color: Color? = style.color,
    bold: Boolean? = style.bold,
    italic: Boolean? = style.italic,
    underlined: Boolean? = style.underlined,
    strikethrough: Boolean? = style.strikethrough,
    obfuscated: Boolean? = style.obfuscated,
    clickEvent: ClickEvent? = style.clickEvent,
    hoverEvent: HoverEvent? = style.hoverEvent,
    insertion: String? = style.insertion,
    font: StyleSpriteSource? = style.font,
    action: TextBuilder.() -> Unit,
) {
    color(color) {
        bold(bold) {
            italic(italic) {
                underlined(underlined) {
                    strikethrough(strikethrough) {
                        obfuscated(obfuscated) {
                            clickEvent(clickEvent) {
                                hoverEvent(hoverEvent) {
                                    insertion(insertion) {
                                        font(font) {
                                            action()
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Applies the [TextBuilder] [action] using the given [style]'s properties.
 *
 * Similar to [Style.withParent], properties that are set to null on the style will
 * not be changed from those currently set on the builder.
 */
@TextDsl
fun TextBuilder.styled(style: Style, action: TextBuilder.() -> Unit) {
    styled(
        style.color?.let { Color(it.rgb) } ?: this.style.color,
        style.isBold,
        style.isItalic,
        style.isUnderlined,
        style.isStrikethrough,
        style.isObfuscated,
        style.clickEvent,
        style.hoverEvent,
        style.insertion,
        style.font,
        action
    )
}
