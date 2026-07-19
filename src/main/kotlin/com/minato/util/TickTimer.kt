
package com.minato.util

class TickTimer {
	private var ticks = 0L

	fun tick() {
		ticks++
	}

	fun hasSurpassed(ticks: Int) = this.ticks >= ticks

	fun reset() {
		ticks = 0
	}
}