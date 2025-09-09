/*
 * Copyright 2025 Lambda
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

package com.lambda.interaction.request

import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.math.BlockPos

interface LogContext {
    fun toLogContext(): String

    companion object {
        @DslMarker
        private annotation class LogContextDsl

        fun BlockPos.toLogContext(): String {
            val pos = if (this is BlockPos.Mutable) toImmutable() else this
            return buildLogContext {
                value("Block Pos", pos.toShortString())
            }
        }

        fun BlockHitResult.toLogContext() =
            buildLogContext {
                group("Block Hit Result") {
                    value("Side", side)
                    value("Block Pos", blockPos)
                    value("Pos", pos)
                }
            }

        @LogContextDsl
        fun buildLogContext(tabMin: Int = 0, builder: LogContextBuilder.() -> Unit): String =
            LogContextBuilder(tabMin).apply(builder).build()

        private fun LogContextBuilder.build() = logContext

        class LogContextBuilder(val tabMin: Int = 0) {
            var logContext = ""

            private var tabs = tabMin

            @LogContextDsl
            fun sameLine() =
                logContext.replace("\n", "")

            @LogContextDsl
            fun text(text: String) {
                repeat(tabs) {
                    logContext += "\t"
                }
                logContext += "$text\n"
            }

            fun value(name: String, value: Any) {
                text("$name: $value")
            }

            fun value(name: String, value: String) {
                text("$name: $value")
            }

            @LogContextDsl
            fun group(name: String, builder: LogContextBuilder.() -> Unit) {
                text("$name:")
                text(LogContextBuilder(tabs + 1).apply(builder).build())
            }

            @LogContextDsl
            fun pushTab() {
                tabs++
            }

            @LogContextDsl
            fun popTab() {
                tabs--
                if (tabs < tabMin) throw IllegalStateException("Cannot reduce tabs beneath the minimum tab count")
            }
        }
    }
}