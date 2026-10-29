package rotp.addons.sexpistols.registry;

import java.util.function.Supplier;

import rotp.addons.sexpistols.SexPistolsIds;
import rotp.addons.sexpistols.content.revolver.state.RevolverCooldowns;
import rotp.addons.sexpistols.content.revolver.state.RevolverMemberAttachments;
import rotp.addons.sexpistols.content.revolver.state.RevolverSpecialChambers;
import rotp.addons.sexpistols.content.revolver.state.RevolverState;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class SexPistolsDataComponents {
    public static final DeferredRegister.DataComponents DATA_COMPONENT_TYPES =
            DeferredRegister.createDataComponents(
                    Registries.DATA_COMPONENT_TYPE,
                    SexPistolsIds.MOD_ID);

    public static final Supplier<DataComponentType<RevolverState>>
            REVOLVER_STATE = DATA_COMPONENT_TYPES.registerComponentType(
                    "revolver_state",
                    builder -> builder
                            .persistent(RevolverState.CODEC)
                            .networkSynchronized(
                                    RevolverState.STREAM_CODEC));

    public static final Supplier<DataComponentType<RevolverCooldowns>>
            REVOLVER_COOLDOWNS =
            DATA_COMPONENT_TYPES.registerComponentType(
                    "revolver_cooldowns",
                    builder -> builder
                            .persistent(RevolverCooldowns.CODEC)
                            .networkSynchronized(
                                    RevolverCooldowns.STREAM_CODEC));

    public static final Supplier<DataComponentType<Integer>>
            SEX_PISTOLS_ATTACHMENT_MASK =
            DATA_COMPONENT_TYPES.registerComponentType(
                    "sex_pistols_attachment_mask",
                    builder -> builder
                            .persistent(
                                    com.mojang.serialization.Codec
                                            .intRange(
                                                    0,
                                                    RevolverMemberAttachments
                                                            .MAX_ENCODED))
                            .networkSynchronized(ByteBufCodecs.VAR_INT));

    public static final Supplier<DataComponentType<Integer>>
            REVOLVER_SPECIAL_CHAMBERS =
            DATA_COMPONENT_TYPES.registerComponentType(
                    "revolver_special_chambers",
                    builder -> builder
                            .persistent(
                                    com.mojang.serialization.Codec
                                            .intRange(
                                                    0,
                                                    RevolverSpecialChambers
                                                            .MAX_ENCODED))
                            .networkSynchronized(ByteBufCodecs.VAR_INT));

    public static final Supplier<DataComponentType<Integer>>
            MISTA_HAT_AMMO =
            DATA_COMPONENT_TYPES.registerComponentType(
                    "mista_hat_ammo",
                    builder -> builder
                            .persistent(
                                    com.mojang.serialization.Codec
                                            .intRange(0, 256))
                            .networkSynchronized(ByteBufCodecs.VAR_INT));

    private SexPistolsDataComponents() {}

    public static void register(IEventBus modEventBus) {
        DATA_COMPONENT_TYPES.register(modEventBus);
    }
}
