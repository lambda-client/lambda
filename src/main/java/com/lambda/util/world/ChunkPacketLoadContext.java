/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.lambda.util.world;

/** Client-thread context distinguishing full chunk population from block mutations. */
public final class ChunkPacketLoadContext {
    private static final ThreadLocal<Integer> DEPTH = ThreadLocal.withInitial(() -> 0);

    private ChunkPacketLoadContext() {}

    public static void enter() {
        DEPTH.set(DEPTH.get() + 1);
    }

    public static void exit() {
        int depth = DEPTH.get() - 1;
        if (depth == 0) DEPTH.remove();
        else DEPTH.set(depth);
    }

    public static boolean isActive() {
        return DEPTH.get() > 0;
    }
}
