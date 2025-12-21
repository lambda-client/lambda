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

package com.lambda.util

object ChatUtils {
	val slurs = sequenceOf("\\bch[i1l]nks?\\b", "\\bc[o0]{2}ns?\\b", "f[a@4](g{1,2}|qq)([e3il1o0]t{1,2}(ry|r[i1l]e)?)?\\b", "\\bk[il1y]k[e3](ry|r[i1l]e)?s?\\b", "\\b(s[a4]nd)?n[ila4o10][gq]{1,2}(l[e3]t|[e3]r|[a4]|n[o0]g)?s?\\b", "\\btr[a4]n{1,2}([il1][e3]|y|[e3]r)s?\\b").map { Regex(it, RegexOption.IGNORE_CASE) }
	val swears = sequenceOf("fuck(er)?", "shit", "cunt", "puss(ie|y)", "bitch", "twat").map { Regex(it, RegexOption.IGNORE_CASE) }
	val sexual = sequenceOf("^cum[s\\$]?$", "cumm?[i1]ng", "h[o0]rny", "mast(e|ur)b(8|ait|ate)").map { Regex(it, RegexOption.IGNORE_CASE) }
	val discord = sequenceOf("(http(s)?:\\/\\/)?(discord)?(\\.)?gg(\\/| ).\\S{1,25}", "(http(s)?:\\/\\/)?(discord)?(\\.)?com\\/invite(\\/| ).\\S{1,25}", "(dsc)?(\\.)?gg(\\/| ).\\S{1,25}").map { Regex(it, RegexOption.IGNORE_CASE) }
	val addresses = sequenceOf("^(?:[0-9]{1,3}\\.){3}[0-9]{1,3}(:\\d{1,5}$)?", "^(\\[?)(\\:\\:)?[0-9a-fA-F]{1,4}(\\:\\:?[0-9a-fA-F]{1,4}){0,7}(\\:\\:)?(\\])?(:\\d{1,5}$)?$").map { Regex(it, RegexOption.IGNORE_CASE) }
	val hex = sequenceOf("\\s([A-Fa-f0-9]+){5,10}$").map { Regex(it) }
	val colors = sequenceOf(">", "`").map { Regex(it) }

	val fancyToAscii = mapOf('ᴀ' to 'a', 'ʙ' to 'b', 'c' to 'c', 'ᴅ' to 'd', 'ᴇ' to 'e', 'ꜰ' to 'f', 'ɢ' to 'g', 'ʜ' to 'h', 'ɪ' to 'i', 'ᴊ' to 'j', 'ᴋ' to 'k', 'ʟ' to 'l', 'ᴍ' to 'm', 'ɴ' to 'n', 'ᴏ' to 'o', 'ᴩ' to 'p', 'q' to 'q', 'ʀ' to 'r', 'ꜱ' to 's', 'ᴛ' to 't', 'ᴜ' to 'u', 'ᴠ' to 'v', 'ᴡ' to 'w', 'x' to 'x', 'y' to 'y', 'ᴢ' to 'z',)

	val String.toAscii get() = buildString { this@toAscii.forEach { append(fancyToAscii.getOrDefault(it, it)) } }
}