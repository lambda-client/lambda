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

package com.lambda.util.text

import net.minecraft.entity.Entity
import net.minecraft.entity.EntityType
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NbtCompound
import net.minecraft.nbt.NbtOps
import net.minecraft.text.ClickEvent
import net.minecraft.text.ClickEvent.ChangePage
import net.minecraft.text.ClickEvent.CopyToClipboard
import net.minecraft.text.ClickEvent.OpenFile
import net.minecraft.text.ClickEvent.OpenUrl
import net.minecraft.text.ClickEvent.RunCommand
import net.minecraft.text.ClickEvent.SuggestCommand
import net.minecraft.text.HoverEvent
import net.minecraft.text.HoverEvent.ShowEntity
import net.minecraft.text.HoverEvent.ShowItem
import net.minecraft.text.HoverEvent.ShowText
import net.minecraft.text.Text
import java.io.File
import java.net.URI
import java.nio.file.Path
import java.util.*

/**
 * Container object for [ClickEvent] builders.
 */
object ClickEvents {
    /**
     * Builds a [ClickEvent] to open a [url].
     *
     * A URL (Uniform Resource Locator) is a specific type of URI (Uniform Resource Identifier)
     * that provides the location of a resource on the internet, while a URI can identify a
     * resource by name or location, including both URLs and URNs (Uniform Resource Names).
     *
     * @param url The url string to open
     * @return The click event to open the target [url]
     */
    fun openUrl(url: String) = OpenUrl(URI.create(url))

    /**
     * Builds a [ClickEvent] to open a [uri].
     *
     * A URL (Uniform Resource Locator) is a specific type of URI (Uniform Resource Identifier)
     * that provides the location of a resource on the internet, while a URI can identify a
     * resource by name or location, including both URLs and URNs (Uniform Resource Names).
     *
     * @param uri The uri to open
     * @return The click event to open the target [uri]
     */
    fun openUrl(uri: URI) = OpenUrl(uri)

    /**
     * Builds a [ClickEvent] to open a file at a specified [path].
     *
     * @param path The path of the file to open
     * @return The click event to open the target file
     */
    fun openFile(path: String) = OpenFile(path)

    /**
     * Builds a [ClickEvent] to open a file at a specified [path].
     *
     * @param path The path of the file to open
     * @return The click event to open the target file
     */
    fun openFile(path: Path) = OpenFile(path)

    /**
     * Builds a [ClickEvent] to open a [file].
     *
     * @param file The [file] to open
     * @return The click event to open the target file
     */
    fun openFile(file: File) = OpenFile(file)

    /**
     * Builds a [ClickEvent] run a given [command].
     *
     * @param command The command to suggest
     * @return The click event to run the target [command]
     */
    fun runCommand(command: String) = RunCommand(command)

    /**
     * Builds a [ClickEvent] to suggest a [command].
     *
     * @param command The command to suggest
     * @return The click event to suggest a given command
     */
    fun suggestCommand(command: String) = SuggestCommand(command)

    /**
     * Builds a [ClickEvent] to change a page to the given [page].
     *
     * @param page The page to change too
     * @return The click event to change the page to the provided [page]
     */
    fun changePage(page: Int) = ChangePage(page)

    /**
     * Builds a [ClickEvent] to copy [toCopy] to the clipboard.
     *
     * @param toCopy The text to copy
     * @return The click event to copy the provided text
     */
    fun copyToClipboard(string: String) = CopyToClipboard(string)
}

/**
 * Container object for [HoverEvent] builders.
 */
object HoverEvents {
    /**
     * Creates a [HoverEvent] showing the given [itemStack].
     *
     * @see HoverEvent.Action.SHOW_ITEM
     */
    fun showItem(itemStack: ItemStack) = ShowItem(itemStack)

    /**
     * Creates a [HoverEvent] showing an [ItemStack] created from the given [NBT Compound][nbt].
     *
     * @see HoverEvent.Action.SHOW_ITEM
     */
    fun showItem(nbt: NbtCompound): HoverEvent {
        // ToDo: Might not work because of registries
        val stack = ItemStack.CODEC.parse(NbtOps.INSTANCE, nbt)
            .orThrow

        return ShowItem(stack)
    }

    /**
     * Creates a [HoverEvent] showing an [Entity] of a specified [type][entityType]
     * with the specified [uuid] and an optional [name].
     *
     * @see HoverEvent.Action.SHOW_ENTITY
     */
    fun showEntity(entityType: EntityType<*>, uuid: UUID, name: Text? = null) =
        ShowEntity(HoverEvent.EntityContent(entityType, uuid, name))

    /**
     * Creates a [HoverEvent] showing specified [text].
     *
     * @author NoComment1105
     */
    fun showText(text: Text) = ShowText(text)
}
