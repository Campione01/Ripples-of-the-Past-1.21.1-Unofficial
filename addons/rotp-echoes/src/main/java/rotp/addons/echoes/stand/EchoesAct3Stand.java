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
import rotp.addons.echoes.init.EchoesSounds;
import net.minecraft.resources.ResourceLocation;

public final class EchoesAct3Stand {
    private EchoesAct3Stand() {}

    public static EntityStandType create(ResourceLocation id) {
        MovesetBuilder moveset = new MovesetBuilder()
                .addHumanoidStandStuff()
                .addAbility("punch", ModStandAbilities.PUNCH)
                // 092: ACT3 voice is the barrage cry; start voice and
                // silent hits live in EchoesAct3BarrageAbility
                .addAbility(
                        "barrage",
                        EchoesAbilities.ACT3_BARRAGE,
                        barrage -> barrage.barrageCrySound(
                                EchoesSounds.BARRAGE_HIT_SOUND))
                // 1.16 donor: heavy punch uses its own heavy_attack impact sound
                .addAbility(
                        "heavy_punch",
                        ModStandAbilities.HEAVY_PUNCH,
                        heavy -> heavy.heavyPunchImpactSound(
                                EchoesSounds.HEAVY_ATTACK))
                .addAbility(
                        "heavy_charged",
                        ModStandAbilities.HEAVY_CHARGED)
                .addAbility("guard", ModStandAbilities.GUARD)
                // 092 Resolve gates: projectile 4, infuse 3, hand freeze 1
                .addAbility(
                        "gravity_projectile",
                        EchoesAbilities.GRAVITY_PROJECTILE,
                        ability -> ability.resolveLevelToUnlock(4))
                .addAbility("freeze", EchoesAbilities.FREEZE)
                .addAbility(
                        "freeze_self",
                        EchoesAbilities.FREEZE_SELF)
                .addAbility(
                        "gravity_infuse",
                        EchoesAbilities.GRAVITY_INFUSE,
                        ability -> ability.resolveLevelToUnlock(3))
                .addAbility(
                        "hand_freeze",
                        EchoesAbilities.HAND_FREEZE,
                        ability -> ability.resolveLevelToUnlock(1))
                // The donor registers these above its normal Resolve cap.
                .addAbility("finisher", EchoesAbilities.ACT3_FINISHER,
                        ability -> ability.resolveLevelToUnlock(5))
                .addAbility("degrade_to_act2", EchoesAbilities.DEGRADE_TO_ACT2,
                        ability -> ability.resolveLevelToUnlock(6))
                .makeControlScheme("hotbar")
                    .bind("punch", InputMethod.CLICK, InputKey.LMB)
                    .bind("barrage", InputMethod.HOLD, InputKey.LMB)
                    .bind("heavy_punch", InputMethod.CLICK, InputKey.RMB)
                    .bind(
                            "heavy_charged",
                            InputMethod.HOLD,
                            MovesetBuilder.DEFAULT_CHARGED_HEAVY_INPUT)
                    .bind("guard", InputMethod.HOLD, InputKey.RMB)
                    .makeHotbar(0, USE_SPECIAL, SWITCH_SPECIAL)
                    .addToHotbar(
                            "gravity_projectile",
                            0,
                            InputMethod.CLICK)
                    .addToHotbar("degrade_to_act2", 0, InputMethod.CLICK)
                    .addToHotbar("freeze", 0, InputMethod.CLICK)
                    .addToHotbar(
                            "freeze_self",
                            0,
                            InputMethod.CLICK)
                    .addToHotbar(
                            "gravity_infuse",
                            0,
                            InputMethod.CLICK)
                    .addToHotbar("hand_freeze", 0, InputMethod.CLICK)
                    .addToHotbar("finisher", 0, InputMethod.CLICK)
                .finalizeControlScheme()
                .makeControlScheme("keybinds")
                    .bind("punch", InputMethod.CLICK, InputKey.LMB)
                    .bind("barrage", InputMethod.HOLD, InputKey.LMB)
                    .bind("heavy_punch", InputMethod.CLICK, InputKey.RMB)
                    .bind(
                            "heavy_charged",
                            InputMethod.HOLD,
                            MovesetBuilder.DEFAULT_CHARGED_HEAVY_INPUT)
                    .bind("guard", InputMethod.HOLD, InputKey.RMB)
                .finalizeControlScheme()
                .addSkill(StandUnlockableSkill.startingAbility("punch"))
                .addSkill(StandUnlockableSkill.startingAbility("barrage"))
                .addSkill(StandUnlockableSkill.startingAbility("heavy_punch"))
                .addSkill(StandUnlockableSkill.startingAbility(
                                "heavy_charged")
                        .prerequisiteSkill("heavy_punch"))
                .addSkill(StandUnlockableSkill.startingAbility(
                        "gravity_projectile"))
                .addSkill(StandUnlockableSkill.startingAbility("freeze"))
                .addSkill(StandUnlockableSkill.startingAbility(
                        "freeze_self"))
                .addSkill(StandUnlockableSkill.startingAbility(
                        "gravity_infuse"))
                .addSkill(StandUnlockableSkill.startingAbility("hand_freeze"))
                .addSkill(StandUnlockableSkill.startingAbility("finisher"))
                .addSkill(StandUnlockableSkill.startingAbility(
                        "degrade_to_act2"))
                .addHumanoidStandSkills();

        return new EntityStandType(
                new StandStats.Builder()
                        .power(11.0, 11.0)
                        .speed(11.0, 11.0)
                        .range(5.0, 10.0)
                        .durability(11.0, 11.0)
                        .precision(8.0)
                        .randomWeight(0.0D)
                        .build(),
                moveset,
                EchoesEntities.ACT3.get(),
                id)
                .standControlType(StandControlType.CLOSE_RANGE_DIRECT)
                .manualControl(true)
                .distanceStrengthDecay(true)
                .standDimensions(0.5F, 1.5F)
                .summonShout(() -> EchoesSounds.SUMMON_VOICE_ACT3);
    }
}
