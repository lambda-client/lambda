package me.zeroeightsix.kami.util;

import net.minecraft.util.math.Vec3d;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class VectorUtil {
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
