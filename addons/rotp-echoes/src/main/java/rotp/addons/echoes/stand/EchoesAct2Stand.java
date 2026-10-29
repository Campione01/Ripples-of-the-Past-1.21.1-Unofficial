package rotp.addons.echoes.stand;

import static rotp.core.init.power.ModStands.SWITCH_SPECIAL;
import static rotp.core.init.power.ModStands.USE_SPECIAL;

import rotp.core.init.power.ModStandAbilities;
import rotp.core.powersystem.MovesetBuilder;
import rotp.core.powersystem.ability.controls.InputKey;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.standpower.StandStats;
import rotp.core.powersystem.standpower.StandUnlockableSkill;
import rotp.core.powersystem.standpower.entity.EntityStandType;
import rotp.core.powersystem.standpower.entity.StandControlType;

import rotp.addons.echoes.init.EchoesAbilities;
import rotp.addons.echoes.init.EchoesEntities;
import net.minecraft.resources.ResourceLocation;

public final class EchoesAct2Stand {
    private EchoesAct2Stand() {}

    public static EntityStandType create(ResourceLocation id) {
        MovesetBuilder moveset = new MovesetBuilder()
                .addHumanoidStandStuff()
                .addAbility("act2_punch", ModStandAbilities.PUNCH)
                .addAbility(
                        "act2_heavy_punch",
                        ModStandAbilities.HEAVY_PUNCH)
                .addAbility("act2_guard", ModStandAbilities.GUARD)
                .addAbility("act2_finisher", EchoesAbilities.ACT2_FINISHER)
                // 092 Resolve gate: ACT2 evolves at level 1
                .addAbility(
                        "evolve_to_act3",
                        EchoesAbilities.EVOLVE_TO_ACT3,
                        ability -> ability.resolveLevelToUnlock(1))
                .addAbility("degrade_to_act1", EchoesAbilities.DEGRADE_TO_ACT1)
                .makeControlScheme("hotbar")
                    .bind("act2_punch", InputMethod.CLICK, InputKey.LMB)
                    .bind(
                            "act2_heavy_punch",
                            InputMethod.CLICK,
                            InputKey.RMB)
                    .bind("act2_guard", InputMethod.HOLD, InputKey.RMB)
                    .makeHotbar(0, USE_SPECIAL, SWITCH_SPECIAL)
                    .addToHotbar("act2_finisher", 0, InputMethod.CLICK)
                    .addToHotbar("evolve_to_act3", 0, InputMethod.CLICK)
                    .addToHotbar("degrade_to_act1", 0, InputMethod.CLICK)
                .finalizeControlScheme()
                .makeControlScheme("keybinds")
                    .bind("act2_punch", InputMethod.CLICK, InputKey.LMB)
                    .bind(
                            "act2_heavy_punch",
                            InputMethod.CLICK,
                            InputKey.RMB)
                    .bind("act2_guard", InputMethod.HOLD, InputKey.RMB)
                .finalizeControlScheme()
                .addSkill(StandUnlockableSkill.startingAbility("act2_punch"))
                .addSkill(StandUnlockableSkill.startingAbility(
                        "act2_heavy_punch"))
                .addSkill(StandUnlockableSkill.startingAbility(
                        "act2_finisher"))
                .addSkill(StandUnlockableSkill.startingAbility(
                        "evolve_to_act3"))
                .addSkill(StandUnlockableSkill.startingAbility(
                        "degrade_to_act1"))
                .addHumanoidStandSkills();

        return new EntityStandType(
                new StandStats.Builder()
                        .power(14.0, 14.0)
                        .speed(12.0, 12.0)
                        .range(10.0, 20.0)
                        .durability(10.0, 10.0)
                        .precision(10.0)
                        .randomWeight(2.0D)
                        .build(),
                moveset,
                EchoesEntities.ACT2.get(),
                id)
                .standControlType(StandControlType.LONG_DISTANCE_OPERATION)
                .manualControl(true)
                .distanceStrengthDecay(false)
                .standDimensions(0.4F, 1.25F);
    }
}
