package rotp.addons.sexpistols.registry;

import static rotp.core.init.power.ModStands.SWITCH_SPECIAL;
import static rotp.core.init.power.ModStands.USE_SPECIAL;

import rotp.core.core.JojoRegistries;
import rotp.core.powersystem.MovesetBuilder;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.standpower.StandStats;
import rotp.core.powersystem.standpower.StandUnlockableSkill;
import rotp.core.powersystem.standpower.type.StandType;

import rotp.addons.sexpistols.SexPistolsIds;
import rotp.addons.sexpistols.content.sexpistols.SexPistolsStandType;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class SexPistolsStands {
    public static final String CONTROL_TOPOLOGY = "group";
    public static final String MANIFESTATION = "swarm-unit";
    public static final String COMBAT_TOPOLOGY =
            "remote-scout-command";
    public static final String INPUT_PROFILE =
            "non-humanoid-ranged-exception";

    public static final DeferredRegister<StandType> STAND_TYPES =
            DeferredRegister.create(
                    JojoRegistries.DEFAULT_STANDS_REG,
                    SexPistolsIds.MOD_ID);

    public static final DeferredHolder<
            StandType, SexPistolsStandType> SEX_PISTOLS =
            STAND_TYPES.register(
                    SexPistolsIds.SEX_PISTOLS_PATH,
                    SexPistolsStands::createSexPistols);

    private SexPistolsStands() {}

    public static void register(IEventBus modEventBus) {
        STAND_TYPES.register(modEventBus);
    }

    private static SexPistolsStandType createSexPistols(
            ResourceLocation id) {
        String move = SexPistolsIds.SEX_PISTOLS_MOVE_SELECTED_PATH;
        String select = SexPistolsIds.SEX_PISTOLS_SELECT_PATH;
        String targetMode =
                SexPistolsIds.SEX_PISTOLS_TARGET_MODE_PATH;
        String transferOrder =
                SexPistolsIds.SEX_PISTOLS_TRANSFER_ORDER_PATH;
        String standReload =
                SexPistolsIds.SEX_PISTOLS_STAND_RELOAD_PATH;
        String quickReload =
                SexPistolsIds.SEX_PISTOLS_QUICK_RELOAD_PATH;
        String recall =
                SexPistolsIds.SEX_PISTOLS_RECALL_PATH;
        String trajectory =
                SexPistolsIds
                        .SEX_PISTOLS_TRAJECTORY_VISION_PATH;
        String piercing =
                SexPistolsIds.SEX_PISTOLS_PIERCING_SHOT_PATH;
        String splitting =
                SexPistolsIds.SEX_PISTOLS_SPLITTING_SHOT_PATH;
        String encirclement =
                SexPistolsIds.SEX_PISTOLS_ENCIRCLEMENT_PATH;
        MovesetBuilder moveset = new MovesetBuilder()
                .addAbility(
                        select,
                        SexPistolsAbilities
                                .SEX_PISTOLS_SELECT)
                .addAbility(
                        targetMode,
                        SexPistolsAbilities
                                .SEX_PISTOLS_TARGET_MODE)
                .addAbility(
                        transferOrder,
                        SexPistolsAbilities
                                .SEX_PISTOLS_TRANSFER_ORDER)
                .addAbility(
                        move,
                        SexPistolsAbilities
                                .SEX_PISTOLS_MOVE_SELECTED)
                .addAbility(
                        standReload,
                        SexPistolsAbilities
                                .SEX_PISTOLS_STAND_RELOAD)
                .addAbility(
                        quickReload,
                        SexPistolsAbilities
                                .SEX_PISTOLS_QUICK_RELOAD)
                .addAbility(
                        recall,
                        SexPistolsAbilities
                                .SEX_PISTOLS_RECALL)
                .addAbility(
                        trajectory,
                        SexPistolsAbilities
                                .SEX_PISTOLS_TRAJECTORY_VISION)
                .addAbility(
                        piercing,
                        SexPistolsAbilities
                                .SEX_PISTOLS_PIERCING_SHOT)
                .addAbility(
                        splitting,
                        SexPistolsAbilities
                                .SEX_PISTOLS_SPLITTING_SHOT)
                .addAbility(
                        encirclement,
                        SexPistolsAbilities
                                .SEX_PISTOLS_ENCIRCLEMENT);

        moveset.makeControlScheme("hotbar")
                .makeHotbar(
                        0, USE_SPECIAL, SWITCH_SPECIAL)
                // 1.16 sex_pistols_move_selected was a holdType action
                .addToHotbar(
                        move, 0, InputMethod.HOLD)
                .addToHotbar(
                        select, 0, InputMethod.CLICK)
                .addToHotbar(
                        targetMode, 0, InputMethod.CLICK)
                .addToHotbar(
                        transferOrder, 0, InputMethod.CLICK)
                .addToHotbar(
                        standReload, 0, InputMethod.CLICK)
                .addToHotbar(
                        quickReload, 0, InputMethod.CLICK)
                .addToHotbar(
                        recall, 0, InputMethod.CLICK)
                .addToHotbar(
                        trajectory, 0, InputMethod.CLICK)
                .addToHotbar(
                        piercing, 0, InputMethod.CLICK)
                .addToHotbar(
                        splitting, 0, InputMethod.CLICK)
                .addToHotbar(
                        encirclement, 0, InputMethod.CLICK)
                .finalizeControlScheme();

        moveset.addSkill(
                StandUnlockableSkill.unlockableAbility(
                        move, 100));
        moveset.addSkill(
                StandUnlockableSkill.unlockableAbility(
                        select, 0));
        moveset.addSkill(
                StandUnlockableSkill.unlockableAbility(
                        targetMode, 200));
        moveset.addSkill(
                StandUnlockableSkill.unlockableAbility(
                        transferOrder, 0));
        moveset.addSkill(
                StandUnlockableSkill.unlockableAbility(
                        standReload, 0));
        moveset.addSkill(
                StandUnlockableSkill.unlockableAbility(
                        quickReload, 100));
        moveset.addSkill(
                StandUnlockableSkill.unlockableAbility(
                        recall, 0));
        moveset.addSkill(
                StandUnlockableSkill.unlockableAbility(
                        trajectory, 0));
        moveset.addSkill(
                StandUnlockableSkill.unlockableAbility(
                        piercing, 200));
        moveset.addSkill(
                StandUnlockableSkill.unlockableAbility(
                        splitting, 300));
        moveset.addSkill(
                StandUnlockableSkill.unlockableAbility(
                        encirclement, 400));

        return new SexPistolsStandType(
                new StandStats.Builder()
                        .power(2.0D)
                        .speed(8.0D, 14.0D)
                        .range(40.0D, 40.0D)
                        .durability(12.0D, 16.0D)
                        .precision(16.0D, 20.0D)
                        .build(),
                moveset,
                id)
                .summonShout(SexPistolsSounds::randomMistaSummon)
                .summonSound(
                        () -> SexPistolsSounds
                                .SEX_PISTOLS_SUMMON)
                .unsummonSound(
                        () -> SexPistolsSounds
                                .SEX_PISTOLS_UNSUMMON);
    }

}
