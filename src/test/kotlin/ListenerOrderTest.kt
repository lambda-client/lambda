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

import com.lambda.event.Event
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.Listener
import com.lambda.event.listener.UnsafeListener
import java.util.concurrent.ConcurrentSkipListSet
import kotlin.test.Test
import kotlin.test.assertEquals

class ListenerOrderTest {
	@Test
	fun `listeners of equal priority run in the order they were created`() {
		val first = UnsafeListener<TickEvent.Pre>({ 0 }, this) {}
		val second = UnsafeListener<TickEvent.Pre>({ 0 }, this) {}
		val third = UnsafeListener<TickEvent.Pre>({ 0 }, this) {}
		val dispatch = ConcurrentSkipListSet<Listener<out Event>>(Listener.comparator.reversed())
		dispatch += listOf(third, first, second)
		assertEquals(listOf(first, second, third), dispatch.toList())
	}

	@Test
	fun `a higher priority still runs before an earlier listener`() {
		val early = UnsafeListener<TickEvent.Pre>({ 0 }, this) {}
		val urgent = UnsafeListener<TickEvent.Pre>({ 5 }, this) {}
		val dispatch = ConcurrentSkipListSet<Listener<out Event>>(Listener.comparator.reversed())
		dispatch += listOf(early, urgent)
		assertEquals(listOf(urgent, early), dispatch.toList())
	}

	@Test
	fun `tick stages are listed in the order the client runs them`() {
		assertEquals(
			listOf(
				TickEvent.Pre, TickEvent.Network.Pre, TickEvent.Network.Post, TickEvent.Input.Pre, TickEvent.Input.Post,
				TickEvent.Player.Pre, TickEvent.Player.Post, TickEvent.Sound.Pre, TickEvent.Sound.Post, TickEvent.Post,
			),
			TickEvent.ALL_STAGES,
		)
		assertEquals(TickEvent.ALL_STAGES.subList(0, 6), TickEvent.ROTATION_STAGES)
	}

	@Test
	fun `every tick stage has its own name`() {
		val names = (TickEvent.ALL_STAGES + TickEvent.Render.Pre + TickEvent.Render.Post).map(TickEvent::toString)
		assertEquals(names.distinct(), names)
		assertEquals("Input.Post", TickEvent.Input.Post.toString())
		assertEquals("Post", TickEvent.Post.toString())
	}
}
