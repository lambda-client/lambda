package com.github.lunatrius.core.util.math;

import net.minecraft.util.math.BlockPos;

public class MBlockPos extends BlockPos {
    MBlockPos() {
        super(0, 0, 0);
    }

    @Override
    public int getX() {
        return 0;
    }

    @Override
    public int getY() {
        return 0;
    }

    @Override
    public int getZ() {
        return 0;
    }
}