package com.lambda.client.util.graphics.font

import java.awt.Font

enum class Style(val code: String, val codeChar: Char, val fontPath: String, val styleConst: Int) {
    REGULAR("§r", 'r', "/assets/fonts/FiraSans-Regular.ttf", Font.PLAIN),
    BOLD("§l", 'l', "/assets/fonts/FiraSans-Bold.ttf", Font.BOLD),
    ITALIC("§o", 'o', "/assets/fonts/FiraSans-Italic.ttf", Font.ITALIC)
}