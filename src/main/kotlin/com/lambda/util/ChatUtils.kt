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

	val fancyToAscii = mapOf(
		'！' to '!',
		'＂' to '"',
		'＃' to '#',
		'＄' to '$',
		'％' to '%',
		'＆' to '&',
		'＇' to '\'',
		'（' to '(',
		'）' to ')',
		'＊' to '*',
		'＋' to '+',
		'，' to ',',
		'－' to '-',
		'．' to '.',
		'／' to '/',
		'＼' to '\\',
		'｜' to '|',
		'：' to ':',
		'；' to ';',
		'＜' to '<',
		'＝' to '=',
		'＞' to '>',
		'？' to '?',
		'＠' to '@',
		'［' to '[',
		'］' to ']',
		'｛' to '{',
		'｝' to '}',
		'～' to '~',
		'＾' to '^',
		'＿' to '_',
		'｀' to '`',
		'０' to '0',
		'１' to '1',
		'２' to '2',
		'３' to '3',
		'４' to '4',
		'５' to '5',
		'６' to '6',
		'７' to '7',
		'８' to '8',
		'９' to '9',
		'Ａ' to 'A',
		'Ｂ' to 'B',
		'C' to 'C',
		'Ｄ' to 'D',
		'Ｅ' to 'E',
		'Ｆ' to 'F',
		'Ｇ' to 'G',
		'Ｈ' to 'H',
		'Ｉ' to 'I',
		'Ｊ' to 'J',
		'Ｋ' to 'K',
		'Ｌ' to 'L',
		'Ｍ' to 'M',
		'Ｎ' to 'N',
		'Ｏ' to 'O',
		'Ｐ' to 'P',
		'Ｑ' to 'Q',
		'Ｒ' to 'R',
		'Ｓ' to 'S',
		'Ｔ' to 'T',
		'Ｕ' to 'U',
		'Ｖ' to 'V',
		'Ｗ' to 'W',
		'Ｘ' to 'X',
		'Ｙ' to 'Y',
		'Ｚ' to 'Z',
		'ᴀ' to 'a',
		'ʙ' to 'b',
		'c' to 'c',
		'ᴅ' to 'd',
		'ᴇ' to 'e',
		'ꜰ' to 'f',
		'ɢ' to 'g',
		'ʜ' to 'h',
		'ɪ' to 'i',
		'ᴊ' to 'j',
		'ᴋ' to 'k',
		'ʟ' to 'l',
		'ᴍ' to 'm',
		'ɴ' to 'n',
		'ᴏ' to 'o',
		'ᴩ' to 'p',
		'q' to 'q',
		'ʀ' to 'r',
		'ꜱ' to 's',
		'ᴛ' to 't',
		'ᴜ' to 'u',
		'ᴠ' to 'v',
		'ᴡ' to 'w',
		'x' to 'x',
		'y' to 'y',
		'ᴢ' to 'z',
		'ａ' to 'a',
		'ｂ' to 'b',
		'ｃ' to 'c',
		'ｄ' to 'd',
		'ｅ' to 'e',
		'ｆ' to 'f',
		'ｇ' to 'g',
		'ｈ' to 'h',
		'ｉ' to 'i',
		'ｊ' to 'j',
		'ｋ' to 'k',
		'ｌ' to 'l',
		'ｍ' to 'm',
		'ｎ' to 'n',
		'ｏ' to 'o',
		'ｐ' to 'p',
		'ｑ' to 'q',
		'ｒ' to 'r',
		'ｓ' to 's',
		'ｔ' to 't',
		'ｕ' to 'u',
		'ｖ' to 'v',
		'ｗ' to 'w',
		'ｘ' to 'x',
		'ｙ' to 'y',
		'ｚ' to 'z',
	)
	val asciiToFancy = fancyToAscii.entries.associate { (key, value) -> value to key }
	val asciiToLeet = mapOf('a' to '4', 'e' to '3', 'g' to '6', 'l' to '1', 'i' to '1', 'o' to '0', 's' to '$', 't' to '7')

	val String.toFancy get() = buildString { this@toFancy.forEach { append(asciiToFancy.getOrDefault(it, it)) } }
	val String.toAscii get() = buildString { this@toAscii.forEach { append(fancyToAscii.getOrDefault(it, it)) } }
 	val String.toLeet get() = buildString { this@toLeet.forEach { append(asciiToLeet.getOrDefault(it, it)) } }
	val String.toGreen get() = ">$this"
	val String.toBlue get() = "`$this"

	val String.toUwu get() =
		replace("my", "mai")
			.replace("friend", "fwend")
			.replace("small", "smol")
			.replace("cute", "cyute")
			.replace("very", "vewy")
			.replace("ove", "uv")
			.replace("no", "nu")
			.replace("you", "yew")
			.replace("the", "da")
			.replace("is", "ish")
			.replace('r', 'w')
			.replace("ve", "v")
			.replace('l', 'w')
}