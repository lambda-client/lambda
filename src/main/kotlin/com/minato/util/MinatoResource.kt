
@file:Suppress("unused")

package com.minato.util

import com.minato.Minato
import java.awt.image.BufferedImage
import java.io.FileNotFoundException
import java.io.InputStream
import java.net.URL
import javax.imageio.ImageIO

typealias MinatoResource = String

val MinatoResource.stream: InputStream
    get() = Minato::class.java.getResourceAsStream("/assets/minato/$this")
        ?: throw FileNotFoundException("File \"/assets/minato/$this\" not found")

val MinatoResource.text: String
    get() = Minato::class.java.getResourceAsStream("/assets/minato/$this")?.readAllBytes()?.decodeToString()
        ?: throw FileNotFoundException("File \"/assets/minato/$this\" not found")

val MinatoResource.url: URL
    get() = Minato::class.java.getResource("/assets/minato/$this")
        ?: throw FileNotFoundException("File \"/assets/minato/$this\" not found")

fun MinatoResource.readImage(): BufferedImage = ImageIO.read(this.stream)
