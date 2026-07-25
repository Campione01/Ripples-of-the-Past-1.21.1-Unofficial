package com.github.standobyte.v1_21_4_stuff.missingmethods;

import java.util.EnumMap;
import java.util.function.Function;

public class _Util {

    public static <K extends Enum<K>, V> EnumMap<K, V> makeEnumMap(Class<K> enumClass, Function<K, V> valueGetter) {
        EnumMap<K, V> enummap = new EnumMap<>(enumClass);

        for (K k : enumClass.getEnumConstants()) {
            enummap.put(k, valueGetter.apply(k));
        }

        return enummap;
    }
}
