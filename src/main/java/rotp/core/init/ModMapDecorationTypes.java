package rotp.core.init;

import rotp.core.core.JojoMod;

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
    // 1.16 meteorite map: icon textures/map/meteorite.png, map color 0x6d6bb9
    public static final DeferredHolder<MapDecorationType, MapDecorationType> METEORITE =
            MAP_DECORATION_TYPES.register("meteorite",
                    () -> new MapDecorationType(JojoMod.resLoc("meteorite"), true, 0x6d6bb9, true, false));

    private ModMapDecorationTypes() {}
}
