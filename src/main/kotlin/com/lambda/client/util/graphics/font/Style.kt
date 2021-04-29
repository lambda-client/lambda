package com.lambda.client.util.graphics.font

import java.awt.Font

@Suppress("UNUSED")
enum class Style(val code: String, val codeChar: Char, val fontPath: String, val styleConst: Int) {
    REGULAR("§r", 'r', "/assets/lambda/fonts/Lato/Lato-Regular.ttf", Font.PLAIN),
    BOLD("§l", 'l', "/assets/lambda/fonts/Lato/Lato-Bold.ttf", Font.BOLD),
    ITALIC("§o", 'o', "/assets/lambda/fonts/Lato/Lato-Italic.ttf", Font.ITALIC)
}