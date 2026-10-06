package pathing

import com.lambda.interaction.managers.rotating.Rotation
import com.lambda.pathing.core.Stance
import com.lambda.pathing.physics.MovementSimulationState
import com.lambda.pathing.physics.PlayerPhysicsProfile
import com.lambda.pathing.search.ValueAnchor
import net.minecraft.util.math.Vec3d
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ValueAnchorContinuationTest {
    @Test
    fun `continuation shares trajectory but resets search bookkeeping`() {
        val state = MovementSimulationState.synthetic(
            profile = PlayerPhysicsProfile.VANILLA,
            position = Vec3d(0.5, 1.0, 0.5), rotation = Rotation(0.0, 0.0), onGround = true,
        )
        val parent = ValueAnchor(state, Stance(0, 1, 0), 0, 0, 0, null, emptyList())
        val anchor = ValueAnchor(state, parent.stance, 12, 2, 4, parent, emptyList(), trace = listOf(state.position)).apply {
            actionsEpoch = 7
            hazardFrame = 8
            pendingSurcharge = 9.0
            legRoot = true
        }

        val copy = anchor.continuation()

        assertNotSame(anchor, copy)
        assertSame(anchor.state, copy.state)
        assertSame(anchor.parent, copy.parent)
        assertSame(anchor.inputs, copy.inputs)
        assertSame(anchor.trace, copy.trace)
        assertEquals(anchor.stance, copy.stance)
        assertEquals(anchor.elapsed, copy.elapsed)
        assertEquals(anchor.collisionEvents, copy.collisionEvents)
        assertEquals(anchor.inputSwitches, copy.inputSwitches)
        assertEquals(anchor.decision, copy.decision)
        assertEquals(-1, copy.actionsEpoch)
        assertNull(copy.hazardFrame)
        assertEquals(0.0, copy.pendingSurcharge)
        assertFalse(copy.legRoot)
        assertTrue(copy.attempted.isEmpty())
        assertNotSame(anchor.attempted, copy.attempted)
    }
}
