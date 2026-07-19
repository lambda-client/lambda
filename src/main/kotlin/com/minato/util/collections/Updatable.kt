
package com.minato.util.collections

class Updatable<T>(private val initializer: () -> T) {
	private var backingField: T = initializer()

	val value: T
		get() = backingField

	fun update() {
		backingField = initializer()
	}
}

fun <T> updatable(initializer: () -> T) = Updatable(initializer)