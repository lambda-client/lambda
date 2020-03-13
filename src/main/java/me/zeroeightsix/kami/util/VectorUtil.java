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

    public static Vec3d BlockPosToVec3d(BlockPos blockpos) {
        return new Vec3d(blockpos.x, blockpos.y, blockpos.z);
    }
    
    public static Vec3d advanceVec(Vec3d startVec, Vec3d destinationVec, double distance) {
        Vec3d advanceDirection = destinationVec.subtract(startVec).normalize();
        if (destinationVec.distanceTo(startVec) < distance) return destinationVec;
        Vec3d vecAdvancement = advanceDirection.scale(distance);
        return new Vec3d(vecAdvancement.x + startVec.x, vecAdvancement.y + startVec.y, vecAdvancement.z + startVec.z);
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
            calcHoleAddVectors(y1, y2, z1, z2, returnVectors, x);
        }
        for (int x = x1; x > x2; x--) {
            calcHoleAddVectors(y1, y2, z1, z2, returnVectors, x);
        }
        if (x1 == x2) {

            for (int y = y1; y < y2; y++) {
                for (int z = z1; z < z2; z++) {
                    returnVectors.add(new Vec3d(x1, y, z));
                }
                for (int z = z1; z > z2; z--) {
                    returnVectors.add(new Vec3d(x1, y, z));
                }
            }
            for (int y = y1; y > y2; y--) {
                for (int z = z1; z < z2; z++) {
                    returnVectors.add(new Vec3d(x1, y, z));
                }
                for (int z = z1; z > z2; z--) {
                    returnVectors.add(new Vec3d(x1, y, z));
                }
            }
            ifYLevelIsEqual(y1, y2, z1, z2, returnVectors, x1);
        }

        return returnVectors;
    }

    private static void ifYLevelIsEqual(int y1, int y2, int z1, int z2, List<Vec3d> returnVectors, int x) {
        if (y1 == y2) {
            checkNegAndPosZ(y1, z1, z2, returnVectors, x);
        }
    }

    private static void checkNegAndPosZ(int y1, int z1, int z2, List<Vec3d> returnVectors, int x) {
        for (int z = z1; z < z2; z++) {
            returnVectors.add(new Vec3d(x, y1, z));
        }
        for (int z = z1; z > z2; z--) {
            returnVectors.add(new Vec3d(x, y1, z));
        }
        if (z1 == z2) {
            returnVectors.add(new Vec3d(x, y1, z1));
        }
    }

    private static void calcHoleAddVectors(int y1, int y2, int z1, int z2, List<Vec3d> returnVectors, int x) {
        for (int y = y1; y < y2; y++) {
            checkNegAndPosZ(y, z1, z2, returnVectors, x);
        }
        for (int y = y1; y > y2; y--) {
            checkNegAndPosZ(y, z1, z2, returnVectors, x);
        }
        ifYLevelIsEqual(y1, y2, z1, z2, returnVectors, x);
    }
}
