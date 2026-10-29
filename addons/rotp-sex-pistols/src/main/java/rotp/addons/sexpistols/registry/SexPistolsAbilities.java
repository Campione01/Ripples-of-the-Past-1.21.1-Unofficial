package rotp.addons.sexpistols.registry;

import rotp.core.core.JojoRegistries;
import rotp.core.powersystem.ability.AbilityType;

import rotp.addons.sexpistols.SexPistolsIds;
import rotp.addons.sexpistols.content.sexpistols.SexPistolsCycleCommandAbility;
import rotp.addons.sexpistols.content.sexpistols.SexPistolsMoveSelectedAbility;
import rotp.addons.sexpistols.content.sexpistols.SexPistolsRecallAbility;
import rotp.addons.sexpistols.content.sexpistols.SexPistolsReloadAbility;
import rotp.addons.sexpistols.content.sexpistols.SexPistolsSpecialShotAbility;
import rotp.addons.sexpistols.content.sexpistols.SexPistolsTrajectoryVisionAbility;
import rotp.addons.sexpistols.content.revolver.state.RevolverSpecialChambers.ShotType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class SexPistolsAbilities {
    public static final DeferredRegister<AbilityType<?>> ABILITY_TYPES =
            DeferredRegister.create(
                    JojoRegistries.ABILITY_TYPES_REG,
                    SexPistolsIds.MOD_ID);

    public static final DeferredHolder<
            AbilityType<?>,
            AbilityType<SexPistolsMoveSelectedAbility>>
            SEX_PISTOLS_MOVE_SELECTED = ABILITY_TYPES.register(
                    SexPistolsIds.SEX_PISTOLS_MOVE_SELECTED_PATH,
                    key -> new AbilityType<>(
                            key,
                            SexPistolsMoveSelectedAbility::new));

    public static final DeferredHolder<
            AbilityType<?>,
            AbilityType<SexPistolsCycleCommandAbility>>
            SEX_PISTOLS_SELECT = cycleCommand(
                    SexPistolsIds.SEX_PISTOLS_SELECT_PATH,
                    SexPistolsCycleCommandAbility.Command.SELECT);
    public static final DeferredHolder<
            AbilityType<?>,
            AbilityType<SexPistolsCycleCommandAbility>>
            SEX_PISTOLS_TARGET_MODE = cycleCommand(
                    SexPistolsIds.SEX_PISTOLS_TARGET_MODE_PATH,
                    SexPistolsCycleCommandAbility.Command.TARGET_MODE);
    public static final DeferredHolder<
            AbilityType<?>,
            AbilityType<SexPistolsCycleCommandAbility>>
            SEX_PISTOLS_TRANSFER_ORDER = cycleCommand(
                    SexPistolsIds.SEX_PISTOLS_TRANSFER_ORDER_PATH,
                    SexPistolsCycleCommandAbility.Command.TRANSFER_ORDER);

    public static final DeferredHolder<
            AbilityType<?>,
            AbilityType<SexPistolsReloadAbility>>
            SEX_PISTOLS_STAND_RELOAD = reload(
                    SexPistolsIds.SEX_PISTOLS_STAND_RELOAD_PATH,
                    false);
    public static final DeferredHolder<
            AbilityType<?>,
            AbilityType<SexPistolsReloadAbility>>
            SEX_PISTOLS_QUICK_RELOAD = reload(
                    SexPistolsIds.SEX_PISTOLS_QUICK_RELOAD_PATH,
                    true);
    public static final DeferredHolder<
            AbilityType<?>,
            AbilityType<SexPistolsRecallAbility>>
            SEX_PISTOLS_RECALL = ABILITY_TYPES.register(
                    SexPistolsIds.SEX_PISTOLS_RECALL_PATH,
                    key -> new AbilityType<>(
                            key,
                            SexPistolsRecallAbility::new));
    public static final DeferredHolder<
            AbilityType<?>,
            AbilityType<SexPistolsTrajectoryVisionAbility>>
            SEX_PISTOLS_TRAJECTORY_VISION =
            ABILITY_TYPES.register(
                    SexPistolsIds
                            .SEX_PISTOLS_TRAJECTORY_VISION_PATH,
                    key -> new AbilityType<>(
                            key,
                            SexPistolsTrajectoryVisionAbility::new));
    public static final DeferredHolder<
            AbilityType<?>,
            AbilityType<SexPistolsSpecialShotAbility>>
            SEX_PISTOLS_ENCIRCLEMENT = specialShot(
                    SexPistolsIds.SEX_PISTOLS_ENCIRCLEMENT_PATH,
                    ShotType.ENCIRCLEMENT);
    public static final DeferredHolder<
            AbilityType<?>,
            AbilityType<SexPistolsSpecialShotAbility>>
            SEX_PISTOLS_PIERCING_SHOT = specialShot(
                    SexPistolsIds.SEX_PISTOLS_PIERCING_SHOT_PATH,
                    ShotType.PIERCING);
    public static final DeferredHolder<
            AbilityType<?>,
            AbilityType<SexPistolsSpecialShotAbility>>
            SEX_PISTOLS_SPLITTING_SHOT = specialShot(
                    SexPistolsIds.SEX_PISTOLS_SPLITTING_SHOT_PATH,
                    ShotType.SPLITTING);

    private SexPistolsAbilities() {}

    public static void register(IEventBus modEventBus) {
        ABILITY_TYPES.register(modEventBus);
    }

    private static DeferredHolder<
            AbilityType<?>,
            AbilityType<SexPistolsCycleCommandAbility>>
            cycleCommand(
                    String path,
                    SexPistolsCycleCommandAbility.Command command) {
        return ABILITY_TYPES.register(
                path,
                key -> new AbilityType<>(
                        key,
                        (type, id) ->
                                new SexPistolsCycleCommandAbility(
                                        type,
                                        id,
                                        command)));
    }

    private static DeferredHolder<
            AbilityType<?>,
            AbilityType<SexPistolsReloadAbility>>
            reload(String path, boolean quickReload) {
        return ABILITY_TYPES.register(
                path,
                key -> new AbilityType<>(
                        key,
                        (type, id) ->
                                new SexPistolsReloadAbility(
                                        type,
                                        id,
                                        quickReload)));
    }

    private static DeferredHolder<
            AbilityType<?>,
            AbilityType<SexPistolsSpecialShotAbility>>
            specialShot(String path, ShotType shotType) {
        return ABILITY_TYPES.register(
                path,
                key -> new AbilityType<>(
                        key,
                        (type, id) ->
                                new SexPistolsSpecialShotAbility(
                                        type,
                                        id,
                                        shotType)));
    }
}
