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

@file:Suppress("unused")

package com.lambda.util

import com.lambda.Lambda
import java.awt.image.BufferedImage
import java.io.FileNotFoundException
import java.io.InputStream
import java.net.URL
import javax.imageio.ImageIO

typealias LambdaResource = String

val LambdaResource.stream: InputStream
    get() = Lambda::class.java.getResourceAsStream("/assets/lambda/$this")
        ?: throw FileNotFoundException("File \"/assets/lambda/$this\" not found")

val LambdaResource.text: String
    get() = Lambda::class.java.getResourceAsStream("/assets/lambda/$this")?.readAllBytes()?.decodeToString()
        ?: throw FileNotFoundException("File \"/assets/lambda/$this\" not found")

val LambdaResource.url: URL
    get() = Lambda::class.java.getResource("/assets/lambda/$this")
        ?: throw FileNotFoundException("File \"/assets/lambda/$this\" not found")

fun LambdaResource.readImage(): BufferedImage = ImageIO.read(this.stream)
