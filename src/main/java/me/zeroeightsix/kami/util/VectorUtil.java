package me.zeroeightsix.kami.util;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.List;

public class VectorUtil {

    public static List<Vec3d> getVecsInArea(Vec3d pos1, Vec3d pos2) {
        List<Vec3d> returnList = null;

        int minX = (int) Math.round(Math.min(pos1.x, pos2.x));
        int maxX = (int) Math.round(Math.max(pos1.x, pos2.x));

        int minY = (int) Math.round(Math.min(pos1.y, pos2.y));
        int maxY = (int) Math.round(Math.max(pos1.y, pos2.y));

        int minZ = (int) Math.round(Math.min(pos1.z, pos2.z));
        int maxZ = (int) Math.round(Math.max(pos1.z, pos2.z));

        for (int x = minX; x < maxX; x++) {
            for (int y = minY; y < maxY; y++) {
                for (int z = minZ; z < maxZ; z++) { returnList.add(new Vec3d(x, y, z)); }
            }
        }

        return returnList;
    }

    public static BlockPos convertType(Vec3d vec3d) { return new BlockPos(vec3d.x, vec3d.y, vec3d.z); }
    public static Vec3d convertType(BlockPos blockpos) { return new Vec3d(blockpos.x, blockpos.y, blockpos.z); }
}
