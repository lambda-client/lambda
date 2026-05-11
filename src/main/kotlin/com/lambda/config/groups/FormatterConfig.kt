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

package com.lambda.config.groups

import com.lambda.config.ISettingGroup
import com.lambda.util.Describable
import com.lambda.util.NamedEnum
import java.time.format.DateTimeFormatter
import java.util.*

interface FormatterConfig : ISettingGroup {
    val locale: Locale
    val separator: String
    val prefix: String
    val postfix: String
    val precision: Int
    val format: DateTimeFormatter

    enum class Locales(
        override val displayName: String,
        override val description: String,
        val locale: Locale,
    ) : NamedEnum, Describable {
        France("France", "Numbers are formatted using a space as the thousands separator and a comma as the decimal separator", Locale.FRANCE),
        Germany("Germany", "Numbers are formatted using a dot as the thousands separator and a comma as the decimal separator", Locale.GERMANY),
        Italy("Italy", "Numbers are formatted using a comma as the thousands separator and a comma as the decimal separator", Locale.ITALY),
        Japan("Japan", "Numbers are formatted using a comma as the thousands separator and a dot as the decimal separator", Locale.JAPAN),
        Korea("Korea", "Numbers are formatted using a comma as the thousands separator and a dot as the decimal separator", Locale.KOREA),
        UK("United Kingdom", "Numbers are formatted using a comma as the thousands separator and a dot as the decimal separator", Locale.UK),
        US("United States", "Numbers are formatted using a comma as the thousands separator and a dot as the decimal separator", Locale.US),
        Canada("Canada", "Numbers are formatted using a comma as the thousands separator and a dot as the decimal separator", Locale.CANADA),
        Quebec("Québec", "Numbers are formatted using a space as the thousands separator and a comma as the decimal separator", Locale.CANADA_FRENCH); // this the best one :3
    }

    enum class Time(
        override val displayName: String,
        override val description: String,
        val format: DateTimeFormatter,
    ) : NamedEnum, Describable {
        IsoLocalDate("ISO-8601 Extended", "The ISO date formatter that formats or parses a date without an offset, such as '2011-12-03'", DateTimeFormatter.ISO_LOCAL_DATE),
        IsoOffsetDate("ISO-8601 Offset", "The ISO date formatter that formats or parses a date with an offset, such as '2011-12-03+01:00'", DateTimeFormatter.ISO_OFFSET_DATE),
        IsoDate("ISO-8601 Date", "The ISO date formatter that formats or parses a date with the offset if available, such as '2011-12-03' or '2011-12-03+01:00'", DateTimeFormatter.ISO_DATE),
        IsoLocalTime("ISO-8601 Local Time", "The ISO time formatter that formats or parses a time without an offset, such as '10:15' or '10:15:30'", DateTimeFormatter.ISO_LOCAL_TIME),
        IsoOffsetTime("ISO-8601 Offset Time", "The ISO time formatter that formats or parses a time with an offset, such as '10:15+01:00' or '10:15:30+01:00'", DateTimeFormatter.ISO_OFFSET_TIME),
        IsoTime("ISO-8601 Time", "The ISO time formatter that formats or parses a time, with the offset if available, such as '10:15', '10:15:30' or '10:15:30+01:00'", DateTimeFormatter.ISO_TIME),
        IsoLocalDateTime("ISO-8601 Local Date Time", "The ISO date-time formatter that formats or parses a date-time without an offset, such as '2011-12-03T10:15:30'", DateTimeFormatter.ISO_LOCAL_DATE_TIME),
        IsoOffsetDateTime("ISO-8601 Offset Date Time", "The ISO date-time formatter that formats or parses a date-time with an offset, such as '2011-12-03T10:15:30+01:00'", DateTimeFormatter.ISO_OFFSET_DATE_TIME),
        IsoZonedDateTime("ISO-8601 Zoned Date Time", "The ISO-like date-time formatter that formats or parses a date-time with offset and zone, such as '2011-12-03T10:15:30+01:00[Europe/Paris]'", DateTimeFormatter.ISO_ZONED_DATE_TIME),
        IsoDateTime("ISO-8601 Date Time", "The ISO-like date-time formatter that formats or parses a date-time with the offset and zone if available, such as '2011-12-03T10:15:30', '2011-12-03T10:15:30+01:00' or '2011-12-03T10:15:30+01:00[Europe/Paris]'", DateTimeFormatter.ISO_DATE_TIME),
        IsoOrdinalDate("ISO-8601 Ordinal Date", "The ISO date formatter that formats or parses the ordinal date without an offset, such as '2012-337'", DateTimeFormatter.ISO_ORDINAL_DATE),
        IsoWeekDate("ISO-8601 Week Date", "The ISO date formatter that formats or parses the week-based date without an offset, such as '2012-W48-6'", DateTimeFormatter.ISO_WEEK_DATE),
        IsoInstant("ISO-8601 Instant", "The ISO instant formatter that formats or parses an instant in UTC, such as '2011-12-03T10:15:30Z'", DateTimeFormatter.ISO_INSTANT),
        BasicIsoDate("ISO 8601", "The ISO date formatter that formats or parses a date without an offset, such as '20111203'", DateTimeFormatter.BASIC_ISO_DATE),
        Rfc1123("RFC 1123", "The RFC-1123 date-time formatter, such as 'Tue, 3 Jun 2008 11:05:30 GMT'", DateTimeFormatter.RFC_1123_DATE_TIME);
    }

    // For context, a tuple is an ordered list of identical value types such as a vec3d which is a tuple of doubles
    enum class TupleSeparator(
        override val displayName: String,
        val separator: String,
    ) : NamedEnum {
        Comma("Comma", ", "),
        Dot("Dot", ". "),
        Semicolon("Semicolon", "; "),
        VerticalBar("Vertical Bar", "| "),
        Space("Space", "  "),
        Custom("Custom", ":3c");
    }

    enum class TupleGrouping(
        override val displayName: String,
        val prefix: String,
        val postfix: String,
    ) : NamedEnum {
        Parentheses("Parenthesis", "(", ")"),
        SquareBrackets("Square Brackets", "[", "]"),
        CurlyBrackets("Curly Brackets", "{", "}"),
        VerticalBar("Vertical Bar", "|", "|");
    }
}