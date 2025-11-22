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

package com.lambda.config

import kotlinx.coroutines.Job

abstract class DynamicConfiguration : Configuration() {
    private val preLoadListeners = mutableListOf<() -> Unit>()
    private val postLoadListeners = mutableListOf<() -> Unit>()
    private val preSaveListeners = mutableListOf<() -> Unit>()
    private val postSaveListeners = mutableListOf<() -> Unit>()

    override fun tryLoad(): Job {
        preLoadListeners.forEach { it() }
        return super.tryLoad().also { job ->
            job.invokeOnCompletion { postLoadListeners.forEach { it() } }
        }
    }

    override fun trySave(logToChat: Boolean): Job {
        preSaveListeners.forEach { it() }
        return super.trySave(logToChat).also { job ->
            job.invokeOnCompletion { postSaveListeners.forEach { it() } }
        }
    }

    fun onPreLoad(block: () -> Unit) {
        preLoadListeners.add(block)
    }

    fun onPostLoad(block: () -> Unit) {
        postLoadListeners.add(block)
    }

    fun onPreSave(block: () -> Unit) {
        preSaveListeners.add(block)
    }

    fun onPostSave(block: () -> Unit) {
        postSaveListeners.add(block)
    }
}