package com.github.standobyte.jojo.init;

import com.github.standobyte.jojo.core.JojoMod;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.saveddata.maps.MapDecorationType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModMapDecorationTypes {
    public static final DeferredRegister<MapDecorationType> MAP_DECORATION_TYPES =
            DeferredRegister.create(BuiltInRegistries.MAP_DECORATION_TYPE, JojoMod.MOD_ID);

    public static final DeferredHolder<MapDecorationType, MapDecorationType> PILLARMAN_TEMPLE =
            MAP_DECORATION_TYPES.register("pillarman_temple",
                    () -> new MapDecorationType(JojoMod.resLoc("pillarman_temple"), true, 0x508d50, true, false));

    private ModMapDecorationTypes() {}
}
