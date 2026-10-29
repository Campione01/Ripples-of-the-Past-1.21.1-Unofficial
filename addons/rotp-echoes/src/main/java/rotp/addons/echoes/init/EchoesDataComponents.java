package rotp.addons.echoes.init;

import java.util.function.Supplier;

import com.mojang.serialization.Codec;

import rotp.addons.echoes.RotpEchoes;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class EchoesDataComponents {
    public static final DeferredRegister.DataComponents DATA_COMPONENT_TYPES =
            DeferredRegister.createDataComponents(
                    Registries.DATA_COMPONENT_TYPE, RotpEchoes.MOD_ID);

    public static final Supplier<DataComponentType<Long>>
            GRAVITY_INFUSED_UNTIL =
                    DATA_COMPONENT_TYPES.registerComponentType(
                            "gravity_infused_until",
                            builder -> builder
                                    .persistent(Codec.LONG)
                                    .networkSynchronized(
                                            ByteBufCodecs.VAR_LONG));

    private EchoesDataComponents() {}
}
