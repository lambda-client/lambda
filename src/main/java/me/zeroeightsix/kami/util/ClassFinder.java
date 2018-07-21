package me.zeroeightsix.kami.util;

import com.google.common.reflect.ClassPath;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by 086 on 23/08/2017.
 */
public class ClassFinder {

    public static List<Class> generateClassList(String pack) {
        ArrayList<Class> classes = new ArrayList<>();

        final ClassLoader loader = Thread.currentThread().getContextClassLoader();

        try {
            for (final ClassPath.ClassInfo info : ClassPath.from(loader).getTopLevelClasses()) {
                if (info.getName().startsWith(pack + ".")) {
                    final Class<?> clazz = info.load();
                    if (clazz == null) continue;
                    classes.add(clazz);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return classes;
    }

}
