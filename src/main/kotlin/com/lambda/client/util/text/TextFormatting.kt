package com.lambda.client.util.text

import com.lambda.client.util.color.EnumTextColor
import net.minecraft.util.text.TextFormatting
import java.util.*

fun formatValue(value: String) = TextFormatting.GRAY format "[$value]"

fun formatValue(value: Any) = TextFormatting.GRAY format "[$value]"

fun formatValue(value: Int) = TextFormatting.GRAY format "($value)"

infix fun TextFormatting.format(value: Any) = "$this$value${TextFormatting.RESET}"

infix fun EnumTextColor.format(value: Any) = "$this$value${TextFormatting.RESET}"

fun String.capitalize() = replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }