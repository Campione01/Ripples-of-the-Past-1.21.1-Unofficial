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

/**
 * Echoes ACT1. The 1.16 donor never shipped this form (its inventory records ACT1 as absent); it exists in the
 * port because the author delivered a Blockbench model and clips for it on 2026-09-16. The moveset is the
 * smallest one that exercises those clips - the tail whip (punch), the sweep (heavy punch), the core guard and
 * the evolution into ACT2 - and the stats are deliberately below ACT2's. It is not in the arrow pool; it is
 * reached by ACT2's degrade action or by command.
 */
public final class EchoesAct1Stand {
    private EchoesAct1Stand() {}

    public static EntityStandType create(ResourceLocation id) {
        MovesetBuilder moveset = new MovesetBuilder()
                .addHumanoidStandStuff()
                .addAbility("punch", ModStandAbilities.PUNCH)
                .addAbility("heavy_punch", ModStandAbilities.HEAVY_PUNCH)
                .addAbility("guard", ModStandAbilities.GUARD)
                .addAbility("evolve_to_act2", EchoesAbilities.EVOLVE_TO_ACT2)
                .makeControlScheme("hotbar")
                    .bind("punch", InputMethod.CLICK, InputKey.LMB)
                    .bind("heavy_punch", InputMethod.CLICK, InputKey.RMB)
                    .bind("guard", InputMethod.HOLD, InputKey.RMB)
                    .makeHotbar(0, USE_SPECIAL, SWITCH_SPECIAL)
                    .addToHotbar("evolve_to_act2", 0, InputMethod.CLICK)
                .finalizeControlScheme()
                .makeControlScheme("keybinds")
                    .bind("punch", InputMethod.CLICK, InputKey.LMB)
                    .bind("heavy_punch", InputMethod.CLICK, InputKey.RMB)
                    .bind("guard", InputMethod.HOLD, InputKey.RMB)
                .finalizeControlScheme()
                .addSkill(StandUnlockableSkill.startingAbility("punch"))
                .addSkill(StandUnlockableSkill.startingAbility("heavy_punch"))
                .addSkill(StandUnlockableSkill.startingAbility(
                        "evolve_to_act2"))
                .addHumanoidStandSkills();

        return new EntityStandType(
                new StandStats.Builder()
                        .power(6.0, 6.0)
                        .speed(8.0, 8.0)
                        .range(10.0, 20.0)
                        .durability(6.0, 6.0)
                        .precision(6.0)
                        .randomWeight(0.0D)
                        .build(),
                moveset,
                EchoesEntities.ACT1.get(),
                id)
                .standControlType(StandControlType.LONG_DISTANCE_OPERATION)
                .manualControl(true)
                .distanceStrengthDecay(false)
                .standDimensions(0.4F, 1.2F);
    }
}
