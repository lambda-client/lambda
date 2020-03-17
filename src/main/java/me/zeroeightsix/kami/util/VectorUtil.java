package me.zeroeightsix.kami.util;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class VectorUtil {
    public static BlockPos Vec3dToBlockPos(Vec3d vec) {
        return new BlockPos(vec.x, vec.y, vec.z);
    }

    public static List<BlockPos> Vec3dToBlockPos(List<Vec3d> vecList) {
        List<BlockPos> returnList = null;
        for (Vec3d v : vecList) returnList.add(new BlockPos(v.x, v.y, v.z));
        return returnList;
    }

    public static Vec3d BlockPosToVec3d(BlockPos blockpos) {
        return new Vec3d(blockpos.x, blockpos.y, blockpos.z);
    }

    public static List<Vec3d> BlockPosToVec3d(List<BlockPos> blockposList) {
        List<Vec3d> returnList = null;
        for (BlockPos v : blockposList) returnList.add(new Vec3d(v.x, v.y, v.z));
        return returnList;
    }
    
    public static Vec3d advanceVec(Vec3d startVec, Vec3d destinationVec, double distance) {
        Vec3d advanceDirection = destinationVec.subtract(startVec).normalize();
        if (destinationVec.distanceTo(startVec) < distance) return destinationVec;
        return advanceDirection.scale(distance);
    }
    
    public static List<Vec3d> getVectorsInArea(Vec3d pos1, Vec3d pos2) {
        int x1 = (int) Math.round(pos1.x);
        int x2 = (int) Math.round(pos2.x);
        int y1 = (int) Math.round(pos1.y);
        int y2 = (int) Math.round(pos2.y);
        int z1 = (int) Math.round(pos1.z);
        int z2 = (int) Math.round(pos2.z);
        
        Vec3d intPos1 = new Vec3d(x1, y1, z1);
        Vec3d intPos2 = new Vec3d(x2, y2, z2);
        
        if (intPos1 == intPos2) {
            return Collections.singletonList(intPos1);
        }

        List<Vec3d> returnVectors = Arrays.asList(intPos1, intPos2);

        for (int x = x1; x < x2; x++) {
            for (int y = y1; y < y2; y++) {
                for (int z = z1; z < z2; z++) {
                    returnVectors.add(new Vec3d(x, y, z));
                }
                for (int z = z1; z > z2; z--) {
                    returnVectors.add(new Vec3d(x, y, z));
                }
                if (z1 == z2) {
                    int z = z1;
                    returnVectors.add(new Vec3d(x, y, z));
                }
            }
            for (int y = y1; y > y2; y--) {
                for (int z = z1; z < z2; z++) {
                    returnVectors.add(new Vec3d(x, y, z));
                }
                for (int z = z1; z > z2; z--) {
                    returnVectors.add(new Vec3d(x, y, z));
                }
                if (z1 == z2) {
                    int z = z1;
                    returnVectors.add(new Vec3d(x, y, z));
                }
            }
            if (y1 == y2) {
                int y = y1;

                for (int z = z1; z < z2; z++) {
                    returnVectors.add(new Vec3d(x, y, z));
                }
                for (int z = z1; z > z2; z--) {
                    returnVectors.add(new Vec3d(x, y, z));
                }
                if (z1 == z2) {
                    int z = z1;
                    returnVectors.add(new Vec3d(x, y, z));
                }
            }
        }
        for (int x = x1; x > x2; x--) {
            for (int y = y1; y < y2; y++) {
                for (int z = z1; z < z2; z++) {
                    returnVectors.add(new Vec3d(x, y, z));
                }
                for (int z = z1; z > z2; z--) {
                    returnVectors.add(new Vec3d(x, y, z));
                }
                if (z1 == z2) {
                    int z = z1;
                    returnVectors.add(new Vec3d(x, y, z));
                }
            }
            for (int y = y1; y > y2; y--) {
                for (int z = z1; z < z2; z++) {
                    returnVectors.add(new Vec3d(x, y, z));
                }
                for (int z = z1; z > z2; z--) {
                    returnVectors.add(new Vec3d(x, y, z));
                }
                if (z1 == z2) {
                    int z = z1;
                    returnVectors.add(new Vec3d(x, y, z));
                }
            }
            if (y1 == y2) {
                int y = y1;

                for (int z = z1; z < z2; z++) {
                    returnVectors.add(new Vec3d(x, y, z));
                }
                for (int z = z1; z > z2; z--) {
                    returnVectors.add(new Vec3d(x, y, z));
                }
                if (z1 == z2) {
                    int z = z1;
                    returnVectors.add(new Vec3d(x, y, z));
                }
            }
        }
        if (x1 == x2) {
            int x = x1;

            for (int y = y1; y < y2; y++) {
                for (int z = z1; z < z2; z++) {
                    returnVectors.add(new Vec3d(x, y, z));
                }
                for (int z = z1; z > z2; z--) {
                    returnVectors.add(new Vec3d(x, y, z));
                }
            }
            for (int y = y1; y > y2; y--) {
                for (int z = z1; z < z2; z++) {
                    returnVectors.add(new Vec3d(x, y, z));
                }
                for (int z = z1; z > z2; z--) {
                    returnVectors.add(new Vec3d(x, y, z));
                }
            }
            if (y1 == y2) {
                int y = y1;

                for (int z = z1; z < z2; z++) {
                    returnVectors.add(new Vec3d(x, y, z));
                }
                for (int z = z1; z > z2; z--) {
                    returnVectors.add(new Vec3d(x, y, z));
                }
                if (z1 == z2) {
                    int z = z1;
                    returnVectors.add(new Vec3d(x, y, z));
                }
            }
        }

        return returnVectors;
    }
}
