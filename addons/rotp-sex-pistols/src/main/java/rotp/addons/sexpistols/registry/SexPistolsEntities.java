package rotp.addons.sexpistols.registry;

import java.util.List;

import rotp.addons.sexpistols.SexPistolsIds;
import rotp.addons.sexpistols.content.revolver.entity.RevolverBulletEntity;
import rotp.addons.sexpistols.content.sexpistols.SexPistolsEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class SexPistolsEntities {
    public static final float MEMBER_WIDTH = 0.35F;
    public static final float MEMBER_HEIGHT = 0.7F;

    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(
                    Registries.ENTITY_TYPE, SexPistolsIds.MOD_ID);

    public static final DeferredHolder<
            EntityType<?>, EntityType<RevolverBulletEntity>>
            REVOLVER_BULLET = ENTITY_TYPES.register(
                    SexPistolsIds.REVOLVER_BULLET_PATH,
                    key -> EntityType.Builder
                            .<RevolverBulletEntity>of(
                                    (type, level) ->
                                            new RevolverBulletEntity(
                                                    type,
                                                    level),
                                    MobCategory.MISC)
                            .noSummon()
                            .sized(0.25F, 0.25F)
                            .clientTrackingRange(64)
                            .updateInterval(1)
                            .build(key.toString()));

    public static final DeferredHolder<
            EntityType<?>, EntityType<SexPistolsEntity>> SEX_PISTOLS_NO_1 =
            member(1);
    public static final DeferredHolder<
            EntityType<?>, EntityType<SexPistolsEntity>> SEX_PISTOLS_NO_2 =
            member(2);
    public static final DeferredHolder<
            EntityType<?>, EntityType<SexPistolsEntity>> SEX_PISTOLS_NO_3 =
            member(3);
    public static final DeferredHolder<
            EntityType<?>, EntityType<SexPistolsEntity>> SEX_PISTOLS_NO_5 =
            member(5);
    public static final DeferredHolder<
            EntityType<?>, EntityType<SexPistolsEntity>> SEX_PISTOLS_NO_6 =
            member(6);
    public static final DeferredHolder<
            EntityType<?>, EntityType<SexPistolsEntity>> SEX_PISTOLS_NO_7 =
            member(7);

    private static final List<DeferredHolder<
            EntityType<?>, EntityType<SexPistolsEntity>>> MEMBERS = List.of(
                    SEX_PISTOLS_NO_1,
                    SEX_PISTOLS_NO_2,
                    SEX_PISTOLS_NO_3,
                    SEX_PISTOLS_NO_5,
                    SEX_PISTOLS_NO_6,
                    SEX_PISTOLS_NO_7);

    private SexPistolsEntities() {}

    public static List<DeferredHolder<
            EntityType<?>, EntityType<SexPistolsEntity>>> members() {
        return MEMBERS;
    }

    public static int slotForNumber(int number) {
        for (int slot = 0;
                slot < SexPistolsIds.SEX_PISTOLS_NUMBERS.length;
                slot++) {
            if (SexPistolsIds.SEX_PISTOLS_NUMBERS[slot] == number) {
                return slot;
            }
        }
        return -1;
    }

    public static int numberFor(EntityType<?> type) {
        for (int slot = 0; slot < MEMBERS.size(); slot++) {
            if (MEMBERS.get(slot).get() == type) {
                return SexPistolsIds.SEX_PISTOLS_NUMBERS[slot];
            }
        }
        throw new IllegalArgumentException(
                "Unknown Sex Pistols entity type: " + type);
    }

    public static EntityType<SexPistolsEntity> typeForNumber(int number) {
        int slot = slotForNumber(number);
        if (slot < 0) {
            throw new IllegalArgumentException(
                    "Unknown Sex Pistols number: " + number);
        }
        return MEMBERS.get(slot).get();
    }

    public static void register(IEventBus modEventBus) {
        ENTITY_TYPES.register(modEventBus);
    }

    private static DeferredHolder<
            EntityType<?>, EntityType<SexPistolsEntity>> member(int number) {
        return ENTITY_TYPES.register(
                "sex_pistols_" + number,
                key -> EntityType.Builder.of(
                                SexPistolsEntity::new,
                                MobCategory.MISC)
                        .noSave()
                        .noSummon()
                        .sized(MEMBER_WIDTH, MEMBER_HEIGHT)
                        .clientTrackingRange(64)
                        .updateInterval(2)
                        .build(key.toString()));
    }
}
