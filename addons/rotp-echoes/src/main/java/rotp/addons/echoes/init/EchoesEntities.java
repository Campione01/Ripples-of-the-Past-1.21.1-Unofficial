package rotp.addons.echoes.init;

import rotp.addons.echoes.RotpEchoes;
import rotp.addons.echoes.stand.EchoesStandEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class EchoesEntities {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, RotpEchoes.MOD_ID);

    public static final DeferredHolder<
            EntityType<?>, EntityType<EchoesStandEntity>> ACT1 =
            ENTITY_TYPES.register(
                    "echoes_act1",
                    key -> EntityType.Builder.<EchoesStandEntity>of(
                                    EchoesStandEntity::new,
                                    MobCategory.MISC)
                            .noSave()
                            .noSummon()
                            .sized(0.4F, 1.2F)
                            .clientTrackingRange(32)
                            .updateInterval(2)
                            .build(key.toString()));

    public static final DeferredHolder<
            EntityType<?>, EntityType<EchoesStandEntity>> ACT2 =
            ENTITY_TYPES.register(
                    "echoes_act2",
                    key -> EntityType.Builder.<EchoesStandEntity>of(
                                    EchoesStandEntity::new,
                                    MobCategory.MISC)
                            .noSave()
                            .noSummon()
                            .sized(0.4F, 1.25F)
                            .clientTrackingRange(32)
                            .updateInterval(2)
                            .build(key.toString()));

    public static final DeferredHolder<
            EntityType<?>, EntityType<EchoesStandEntity>> ACT3 =
            ENTITY_TYPES.register(
                    "echoes_act3",
                    key -> EntityType.Builder.<EchoesStandEntity>of(
                                    EchoesStandEntity::new,
                                    MobCategory.MISC)
                            .noSave()
                            .noSummon()
                            .sized(0.5F, 1.5F)
                            .clientTrackingRange(32)
                            .updateInterval(2)
                            .build(key.toString()));

    private EchoesEntities() {}
}
