
package com.minato.config.blocks

import com.minato.config.Config
import com.minato.config.ConfigBlock

class FormatterSettings(override val c: Config) : FormatterConfig, ConfigBlock {
    val localeEnum by c.setting("Locale", FormatterConfig.Locales.US, "The regional formatting used for numbers")
    override val locale get() = localeEnum.locale

    val sep by c.setting("Separator", FormatterConfig.TupleSeparator.Comma, "Separator for string serialization of tuple data structures")
    val customSep by c.setting("Custom Separator", "") { sep == FormatterConfig.TupleSeparator.Custom }
    override val separator get() = if (sep == FormatterConfig.TupleSeparator.Custom) customSep else sep.separator

    val tupleGroup by c.setting("Tuple Prefix", FormatterConfig.TupleGrouping.Parentheses)
    override val prefix get() = tupleGroup.prefix
    override val postfix get() = tupleGroup.postfix

    val floatingPrecision by c.setting("Floating Precision", 3, 0..6, 1, "Precision for floating point numbers")
    override val precision get() = floatingPrecision

    val timeFormat by c.setting("Time Format", FormatterConfig.Time.IsoDateTime)
    override val format get() = timeFormat.format
}