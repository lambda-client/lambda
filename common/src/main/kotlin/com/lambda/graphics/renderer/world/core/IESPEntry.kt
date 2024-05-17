package com.lambda.graphics.renderer.world.core

import com.lambda.graphics.renderer.IRenderEntry
import com.lambda.graphics.renderer.world.DirectionMask
import net.minecraft.util.math.Box
import net.minecraft.util.math.Vec3d
import java.awt.Color

interface IESPEntry <T : IESPEntry<T>> : IRenderEntry<T> {
    var color: Color

    interface IBoxEntry <T : IBoxEntry<T>> : IESPEntry<T> {
        var box: Box
        var sides: Int

        interface Filled : IBoxEntry<Filled>
        interface Outline : IBoxEntry<Outline> {
            var outlineMode: DirectionMask.OutlineMode
        }
    }

    interface ITracerEntry : IESPEntry<ITracerEntry> {
        var position: Vec3d
    }
}