/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.lambda.pathing.rl

/**
 * Fixed-width big-endian protocol shared by the Kotlin simulator server and
 * Python Gymnasium client. Keeping it tiny avoids JSON parsing in every
 * Minecraft tick while still leaving the two runtimes independently testable.
 */
object RlBridgeProtocol {
    const val MAGIC = 0x4E4C524C // "NLRL"
    const val VERSION = 3

    const val RESET: Int = 1
    const val STEP: Int = 2
    const val CLOSE: Int = 3
}
