package rotp.addons.badcompany;

import static rotp.core.init.power.ModStands.SWITCH_SPECIAL;
import static rotp.core.init.power.ModStands.USE_SPECIAL;

import rotp.core.api.RotpAddonApi;
import rotp.core.core.JojoRegistries;
import rotp.core.init.ModSoundEvents;
import rotp.core.init.power.ModStandAbilities;
import rotp.core.powersystem.MovesetBuilder;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.standpower.StandStats;
import rotp.core.powersystem.standpower.StandUnlockableSkill;
import rotp.core.powersystem.standpower.entity.StandControlType;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.util.sound.OstSoundList;

import rotp.addons.badcompany.ability.BadCompanyCommandAbility;
import rotp.addons.badcompany.ability.BadCompanyCommandAbility.Command;
import rotp.addons.badcompany.ability.ToMeAbility;
import rotp.addons.badcompany.effect.BadCompanyHoldEffect;
import rotp.addons.badcompany.entity.BadCompanyBulletEntity;
import rotp.addons.badcompany.entity.BadCompanyHelicopterEntity;
import rotp.addons.badcompany.entity.BadCompanyMineEntity;
import rotp.addons.badcompany.entity.BadCompanyMissileEntity;
import rotp.addons.badcompany.entity.BadCompanySoldierEntity;
import rotp.addons.badcompany.entity.BadCompanyStandEntity;
import rotp.addons.badcompany.entity.BadCompanyTankEntity;
import rotp.addons.badcompany.network.BadCompanySelectionPayloads;
import rotp.addons.badcompany.stand.BadCompanyStandType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(BadCompanyAddon.MOD_ID)
public final class BadCompanyAddon {
    public static final String MOD_ID = "rotp_zbc";
    public static final String TO_ME_NAME = "to_me";
    private static final int REQUIRED_CORE_ABI = 1;

    public static final DeferredRegister<AbilityType<?>> ABILITY_TYPES =
            DeferredRegister.create(
                    JojoRegistries.ABILITY_TYPES_REG, MOD_ID);
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, MOD_ID);
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(Registries.ITEM, MOD_ID);
    public static final DeferredRegister<MobEffect> MOB_EFFECTS =
            DeferredRegister.create(Registries.MOB_EFFECT, MOD_ID);
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(Registries.SOUND_EVENT, MOD_ID);
    public static final DeferredRegister<StandType> STAND_TYPES =
            DeferredRegister.create(
                    JojoRegistries.DEFAULT_STANDS_REG, MOD_ID);

    public static final DeferredHolder<
            AbilityType<?>, AbilityType<BadCompanyCommandAbility>>
            SELECT_UNITS = command("units", Command.SELECT_UNITS);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<BadCompanyCommandAbility>>
            CLEAR_TARGET = command("shoot", Command.CLEAR_TARGET);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<ToMeAbility>> TO_ME =
            ABILITY_TYPES.register(
                    TO_ME_NAME,
                    key -> new AbilityType<>(key, ToMeAbility::new));
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<BadCompanyCommandAbility>>
            SELECT_FORMATION =
                    command("random", Command.SELECT_FORMATION);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<BadCompanyCommandAbility>>
            ORDER_WAIT = command("order_wait", Command.ORDER_WAIT);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<BadCompanyCommandAbility>>
            ORDER_NO_WAIT =
                    command("order_no_wait", Command.ORDER_NO_WAIT);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<BadCompanyCommandAbility>>
            PUT_MINES = command("put_mines", Command.PUT_MINES);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<BadCompanyCommandAbility>>
            EXPLODE_MINES =
                    command("explode_mines", Command.EXPLODE_MINES);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<BadCompanyCommandAbility>>
            SET_TARGET = command("target", Command.SET_TARGET);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<BadCompanyCommandAbility>>
            MISSILE_MODE =
                    command("missile", Command.MISSILE_MODE);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<BadCompanyCommandAbility>>
            GO_TO_BLOCK =
                    command("go_block", Command.GO_TO_BLOCK);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<BadCompanyCommandAbility>>
            STAY_CLOSE = command("close", Command.STAY_CLOSE);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<BadCompanyCommandAbility>>
            STOP_STAY_CLOSE =
                    command("closent", Command.STOP_STAY_CLOSE);
    public static final DeferredHolder<
            AbilityType<?>, AbilityType<BadCompanyCommandAbility>>
            SUMMON_UNITS =
                    command("summon", Command.SUMMON_UNITS);

    public static final DeferredHolder<
            EntityType<?>, EntityType<BadCompanyStandEntity>>
            BAD_COMPANY_ENTITY = entity(
                    "bad_company",
                    EntityType.Builder
                            .<BadCompanyStandEntity>of(
                                    BadCompanyStandEntity::new,
                                    MobCategory.MISC)
                            .noSave()
                            .noSummon()
                            .sized(0.065F, 0.195F));
    public static final DeferredHolder<
            EntityType<?>, EntityType<BadCompanySoldierEntity>>
            BAD_SOLDIER = entity(
                    "bad_soldier",
                    EntityType.Builder
                            .<BadCompanySoldierEntity>of(
                                    BadCompanySoldierEntity::new,
                                    MobCategory.MISC)
                            .noSave()
                            .noSummon()
                            .sized(0.2F, 0.6F));
    public static final DeferredHolder<
            EntityType<?>, EntityType<BadCompanyTankEntity>>
            BAD_TANK = entity(
                    "bad_tank",
                    EntityType.Builder
                            .<BadCompanyTankEntity>of(
                                    BadCompanyTankEntity::new,
                                    MobCategory.MISC)
                            .noSave()
                            .noSummon()
                            .sized(1.0F, 0.5F));
    public static final DeferredHolder<
            EntityType<?>, EntityType<BadCompanyHelicopterEntity>>
            BAD_HELICOPTER = entity(
                    "bad_helicopter",
                    EntityType.Builder
                            .<BadCompanyHelicopterEntity>of(
                                    BadCompanyHelicopterEntity::new,
                                    MobCategory.MISC)
                            .noSave()
                            .noSummon()
                            .sized(1.0F, 0.4F));
    public static final DeferredHolder<
            EntityType<?>, EntityType<BadCompanyBulletEntity>>
            BAD_BULLET = entity(
                    "bad_bullet",
                    EntityType.Builder
                            .<BadCompanyBulletEntity>of(
                                    BadCompanyBulletEntity::new,
                                    MobCategory.MISC)
                            .noSave()
                            .noSummon()
                            .sized(0.2F, 0.6F));
    public static final DeferredHolder<
            EntityType<?>, EntityType<BadCompanyMissileEntity>>
            BAD_MISSILE = entity(
                    "bad_missile",
                    EntityType.Builder
                            .<BadCompanyMissileEntity>of(
                                    BadCompanyMissileEntity::new,
                                    MobCategory.MISC)
                            .noSave()
                            .noSummon()
                            .sized(0.2F, 0.6F));
    public static final DeferredHolder<
            EntityType<?>, EntityType<BadCompanyMineEntity>>
            BAD_MINE = entity(
                    "bad_mine",
                    EntityType.Builder
                            .<BadCompanyMineEntity>of(
                                    BadCompanyMineEntity::new,
                                    MobCategory.MISC)
                            .noSummon()
                            .sized(0.2F, 0.0416F));

    public static final DeferredHolder<Item, Item> BAD_GUN =
            ITEMS.register(
                    "bad_gun",
                    () -> new Item(
                            new Item.Properties().stacksTo(1)));
    public static final DeferredHolder<MobEffect, BadCompanyHoldEffect>
            SAT = MOB_EFFECTS.register(
                    "sat", BadCompanyHoldEffect::new);

    public static final DeferredHolder<SoundEvent, SoundEvent>
            VOID = registerSound("void");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            KEICHO_BAD_COMPANY =
                    registerSound("keicho_bad_company");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            SOLDIER_STEP = registerSound("bc_soldier_step");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            SOLDIER_SHOOT = registerSound("bc_soldier_shoot");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            SOLDIER_BARRAGE = registerSound("bc_barrage");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            TANK_SHOOT = registerSound("bc_tank_shoot");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            TANK_HURT = registerSound("bc_tank_hurt");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            TANK_STEP = registerSound("bc_tank_step");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            TANK_LIVING = registerSound("bc_tank_living");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            HELICOPTER = registerSound("bc_copter");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            BAD_COMPANY_OST_EVENT =
                    registerSound("bad_company_ost");
    public static final OstSoundList BAD_COMPANY_OST =
            new OstSoundList(id("bad_company_ost"), SOUND_EVENTS);

    public static final DeferredHolder<StandType, BadCompanyStandType>
            BAD_COMPANY = STAND_TYPES.register(
                    "bad_company", BadCompanyAddon::createStand);

    public BadCompanyAddon(IEventBus modEventBus) {
        if (!RotpAddonApi.supportsAbi(REQUIRED_CORE_ABI)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi
                                .FEATURE_ABILITY_RESOURCE_NAMESPACE_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi.FEATURE_STAND_VISUAL_CONTEXT_V1)) {
            throw new IllegalStateException(
                    "RotP Bad Company requires ROTP addon ABI 1 with "
                            + "namespaced ability resources and Stand "
                            + "visual contexts.");
        }

        ABILITY_TYPES.register(modEventBus);
        ENTITY_TYPES.register(modEventBus);
        ITEMS.register(modEventBus);
        MOB_EFFECTS.register(modEventBus);
        SOUND_EVENTS.register(modEventBus);
        STAND_TYPES.register(modEventBus);
        modEventBus.addListener(BadCompanySelectionPayloads::register);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    private static DeferredHolder<
            AbilityType<?>, AbilityType<BadCompanyCommandAbility>>
            command(String name, Command command) {
        DeferredHolder<
                AbilityType<?>, AbilityType<BadCompanyCommandAbility>>
                holder = ABILITY_TYPES.register(
                        name,
                        key -> new AbilityType<>(
                                key,
                                (type, abilityId) ->
                                        new BadCompanyCommandAbility(
                                                type,
                                                abilityId,
                                                command)));
        return holder;
    }

    private static <T extends net.minecraft.world.entity.Entity>
            DeferredHolder<EntityType<?>, EntityType<T>> entity(
                    String name,
                    EntityType.Builder<T> builder) {
        return ENTITY_TYPES.register(
                name,
                key -> builder
                        .clientTrackingRange(32)
                        .updateInterval(2)
                        .build(key.toString()));
    }

    private static DeferredHolder<SoundEvent, SoundEvent>
            registerSound(String path) {
        return SOUND_EVENTS.register(
                path, SoundEvent::createVariableRangeEvent);
    }

    private static BadCompanyStandType createStand(
            ResourceLocation id) {
        MovesetBuilder moveset = new MovesetBuilder();
        moveset.addAbility(
                        "units", SELECT_UNITS)
                .addAbility("shoot", CLEAR_TARGET)
                .addAbility(TO_ME_NAME, TO_ME)
                .addAbility("random", SELECT_FORMATION)
                .addAbility("order_wait", ORDER_WAIT)
                .addAbility("order_no_wait", ORDER_NO_WAIT)
                .addAbility("put_mines", PUT_MINES)
                .addAbility("explode_mines", EXPLODE_MINES)
                .addAbility("target", SET_TARGET)
                .addAbility("missile", MISSILE_MODE)
                .addAbility("go_block", GO_TO_BLOCK)
                .addAbility("close", STAY_CLOSE)
                .addAbility("closent", STOP_STAY_CLOSE)
                .addAbility("summon", SUMMON_UNITS)
                .addAbility(
                        "manual_control",
                        ModStandAbilities.MANUAL_CONTROL,
                        ability -> ability.resolveLevelToUnlock(4));
        var controls = moveset.makeControlScheme("hotbar")
                .makeHotbar(0, USE_SPECIAL, SWITCH_SPECIAL);
        for (String name : commandWheelOrder()) {
            controls.addToHotbar(
                    name,
                    0,
                    TO_ME_NAME.equals(name)
                            ? InputMethod.HOLD
                            : InputMethod.CLICK);
        }
        controls.finalizeControlScheme();
        for (String name : commandWheelOrder()) {
            moveset.addSkill(
                    StandUnlockableSkill.startingAbility(name));
        }
        moveset.addSkill(
                StandUnlockableSkill.startingAbility("manual_control"));
        return new BadCompanyStandType(
                new StandStats.Builder()
                        .power(13.0D)
                        .speed(12.0D)
                        // 1.16 InitStands registers range 10 (stats rank C); only the
                        // coordinator's getMaxRange override widens its boundary to 60.
                        .range(10.0D, 10.0D)
                        .durability(13.0D)
                        .precision(10.0D)
                        .randomWeight(2.0D)
                        .build(),
                moveset,
                BAD_COMPANY_ENTITY.get(),
                id)
                .standControlType(StandControlType.COLONY)
                .manualControl(true)
                .distanceStrengthDecay(false)
                .standDimensions(0.065F, 0.195F)
                .summonShout(() -> KEICHO_BAD_COMPANY)
                .summonSound(() -> ModSoundEvents.STAND_SUMMON)
                .unsummonSound(() -> ModSoundEvents.STAND_UNSUMMON)
                .ost(() -> BAD_COMPANY_OST);
    }

    private static String[] commandWheelOrder() {
        return new String[] {
                "units",
                "random",
                "summon",
                "order_wait",
                "order_no_wait",
                "close",
                "closent",
                "go_block",
                "target",
                "shoot",
                "put_mines",
                "explode_mines",
                "missile",
                TO_ME_NAME
        };
    }
}
