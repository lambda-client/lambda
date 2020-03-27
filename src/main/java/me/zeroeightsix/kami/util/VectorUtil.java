package me.zeroeightsix.kami.util;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class VectorUtil {

    public static double getDistance(Vec3d vecA, Vec3d vecB) {
        return MathHelper.sqrt((vecA.x - vecB.x) * (vecA.x - vecB.x) + (vecA.y - vecB.y) * (vecA.y - vecB.y) + (vecA.z - vecB.z) * (vecA.z - vecB.z));
    }

    public static List<Vec3d> extendVec(Vec3d startVec, Vec3d destinationVec, int steps) {
        List<Vec3d> returnList = new ArrayList<>();
        double stepDistance = getDistance(startVec, destinationVec) / steps;

        for (int i = 0; i < Math.max(steps, 1) + 1; i++) {
            returnList.add(advanceVec(startVec, destinationVec, stepDistance * i));
        }

        return returnList;
    }

    public static Vec3d advanceVec(Vec3d startVec, Vec3d destinationVec, double distance) {
        Vec3d advanceDirection = destinationVec.subtract(startVec).normalize();
        if (destinationVec.distanceTo(startVec) < distance) return destinationVec;
        return advanceDirection.scale(distance);
    }

    public static List<BlockPos> getIntPositionsInArea(Vec3d pos1, Vec3d pos2) {
        int minX = (int) Math.round(Math.min(pos1.x, pos2.x));
        int maxX = (int) Math.round(Math.max(pos1.x, pos2.x));

        int minY = (int) Math.round(Math.min(pos1.y, pos2.y));
        int maxY = (int) Math.round(Math.max(pos1.y, pos2.y));

        int minZ = (int) Math.round(Math.min(pos1.z, pos2.z));
        int maxZ = (int) Math.round(Math.max(pos1.z, pos2.z));

        List<BlockPos> returnList = Arrays.asList(new BlockPos[(maxX - minX) * (maxY-minY) * (maxZ-minZ)]);

        for (int x = minX; x < maxX; x++) {
            for (int y = minY; y < maxY; y++) {
                for (int z = minZ; z < maxZ; z++) { returnList.add(new BlockPos(x, y, z)); }
            }
        }

        return returnList;
    }

    public static List<BlockPos> getIntPositionsInArea(BlockPos pos1, BlockPos pos2) {
        int minX = (int) Math.round(Math.min(pos1.x, pos2.x));
        int maxX = (int) Math.round(Math.max(pos1.x, pos2.x));

        int minY = (int) Math.round(Math.min(pos1.y, pos2.y));
        int maxY = (int) Math.round(Math.max(pos1.y, pos2.y));

        int minZ = (int) Math.round(Math.min(pos1.z, pos2.z));
        int maxZ = (int) Math.round(Math.max(pos1.z, pos2.z));

        List<BlockPos> returnList = Arrays.asList(new BlockPos[(maxX - minX) * (maxY-minY) * (maxZ-minZ)]);

        for (int x = minX; x < maxX; x++) {
            for (int y = minY; y < maxY; y++) {
                for (int z = minZ; z < maxZ; z++) { returnList.add(new BlockPos(x, y, z)); }
            }
        }

        return returnList;
    }
}
