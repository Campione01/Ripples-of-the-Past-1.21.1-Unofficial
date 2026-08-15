package com.github.standobyte.jojoimpl.stands;

import static com.github.standobyte.jojo.init.power.ModStands.SWITCH_SPECIAL;
import static com.github.standobyte.jojo.init.power.ModStands.USE_SPECIAL;

import org.jetbrains.annotations.ApiStatus;

import com.github.standobyte.jojo.init.ModSoundEvents;
import com.github.standobyte.jojo.init.power.ModStandAbilities;
import com.github.standobyte.jojo.powersystem.MovesetBuilder;
import com.github.standobyte.jojo.powersystem.ability.controls.InputKey;
import com.github.standobyte.jojo.powersystem.ability.controls.InputMethod;
import com.github.standobyte.jojo.powersystem.entityaction.ActionPhase;
import com.github.standobyte.jojo.powersystem.standpower.StandStats;
import com.github.standobyte.jojo.powersystem.standpower.StandUnlockableSkill;
import com.github.standobyte.jojo.powersystem.standpower.entity.StandControlType;
import com.github.standobyte.jojo.powersystem.standpower.entity.EntityStandType;

import net.minecraft.resources.ResourceLocation;

public class StandInitStarPlatinum {

	@ApiStatus.Internal
	public static EntityStandType create(ResourceLocation id) {
		return new EntityStandType(
				new StandStats.Builder()
				.power(16.0, 18.5)
				.speed(16.0, 19.0)
				.range(1, 2)
				.durability(16.0, 20.0)
				.precision(20)
				.randomWeight(1)
				.build(),

				new MovesetBuilder()

				.addHumanoidStandStuff()

				.addAbility("punch", ModStandAbilities.PUNCH, punch -> {
					punch.punchImpactSound(ModSoundEvents.STAR_PLATINUM_PUNCH_LIGHT).standCrySound(ModSoundEvents.STAR_PLATINUM_ORA);
				})
				.addAbility("punch2", ModStandAbilities.PUNCH, punch -> {
					punch.punchImpactSound(ModSoundEvents.STAR_PLATINUM_PUNCH_LIGHT).standCrySound(ModSoundEvents.STAR_PLATINUM_ORA);
				})
				.addAbility("punch3", ModStandAbilities.PUNCH, punch -> {
					punch.punchImpactSound(ModSoundEvents.STAR_PLATINUM_PUNCH_LIGHT).standCrySound(ModSoundEvents.STAR_PLATINUM_ORA);
				})
				.addAbility("punch4", ModStandAbilities.PUNCH, punch -> {
					punch.punchImpactSound(ModSoundEvents.STAR_PLATINUM_PUNCH_LIGHT).standCrySound(ModSoundEvents.STAR_PLATINUM_ORA);
					punch.setDefaultPhaseLength(ActionPhase.WINDUP, 5);
				})

				.addAbility("barrage", ModStandAbilities.BARRAGE, barrage -> {
					barrage.barrageHitSound(ModSoundEvents.STAR_PLATINUM_PUNCH_BARRAGE).barrageCrySound(ModSoundEvents.STAR_PLATINUM_ORA_RUSH);
				})

				.addAbility("heavy_punch", ModStandAbilities.HEAVY_PUNCH, punch -> {
					punch.heavyPunchImpactSound(ModSoundEvents.STAR_PLATINUM_PUNCH_HEAVY).heavyPunchCrySound(ModSoundEvents.STAR_PLATINUM_ORA_LONG);
				})
				.addAbility("heavy_charged", ModStandAbilities.HEAVY_CHARGED)
				.addAbility("finisher_uppercut", ModStandAbilities.HEAVY_UPPERCUT, punch -> {
					punch.heavyPunchImpactSound(ModSoundEvents.STAR_PLATINUM_PUNCH_HEAVY).heavyPunchCrySound(ModSoundEvents.STAR_PLATINUM_ORA_LONG);
					punch.initIsFinisher();
					punch.resolveLevelToUnlock(1);
				})
				.addAbility("grab", ModStandAbilities.GRAB)
				.addAbility("grab_throw", ModStandAbilities.GRAB_THROW)
				.addAbility("grab_punch", ModStandAbilities.PUNCH, punch -> {
					punch.punchImpactSound(ModSoundEvents.STAR_PLATINUM_PUNCH_LIGHT).standCrySound(ModSoundEvents.STAR_PLATINUM_ORA);
					punch.initIsGrabVariation();
				})
				.addAbility("grab_barrage", ModStandAbilities.BARRAGE, barrage -> {
					barrage.barrageHitSound(ModSoundEvents.STAR_PLATINUM_PUNCH_BARRAGE).barrageCrySound(ModSoundEvents.STAR_PLATINUM_ORA_RUSH);
					barrage.initIsGrabVariation();
				})
				.addAbility("grab_heavy_punch", ModStandAbilities.HEAVY_PUNCH, punch -> {
					punch.heavyPunchImpactSound(ModSoundEvents.STAR_PLATINUM_PUNCH_HEAVY).heavyPunchCrySound(ModSoundEvents.STAR_PLATINUM_ORA_LONG);
					punch.initIsGrabVariation();
				})
				.addAbility("grab_uppercut", ModStandAbilities.HEAVY_UPPERCUT, punch -> {
					punch.heavyPunchImpactSound(ModSoundEvents.STAR_PLATINUM_PUNCH_HEAVY).heavyPunchCrySound(ModSoundEvents.STAR_PLATINUM_ORA_LONG);
					punch.initIsGrabVariation();
					punch.initIsFinisher("grab_heavy_punch");
				})

				.addAbility("guard", ModStandAbilities.GUARD)
				.addAbility("bearing_shot", ModStandAbilities.BEARING_SHOT)

				.addAbility("enhanced_eyesight", ModStandAbilities.SP_EYESIGHT, ability -> {
					ability.setIgnoresPerformerStun();
				})
				.addAbility("star_finger", ModStandAbilities.SP_STAR_FINGER, ability -> {
					ability.setIgnoresPerformerStun();
					ability.resolveLevelToUnlock(3);
				})
				.addAbility("inhale", ModStandAbilities.SP_INHALE, ability -> {
					ability.setIgnoresPerformerStun();
					ability.resolveLevelToUnlock(2);
				})
				.addAbility("time_stop", ModStandAbilities.TIME_STOP, ability -> {
					ability.setIgnoresPerformerStun();
				})
				.addAbility("time_stop_blink", ModStandAbilities.TIME_STOP_BLINK)


				.makeControlScheme("hotbar")
					.bind("punch", InputMethod.CLICK, InputKey.LMB)
					.bind("barrage", InputMethod.HOLD, InputKey.LMB)
					.bind("heavy_punch", InputMethod.CLICK, InputKey.RMB)
					.bind("heavy_charged", InputMethod.HOLD, MovesetBuilder.DEFAULT_CHARGED_HEAVY_INPUT)
					.bind("grab", InputMethod.CLICK, MovesetBuilder.DEFAULT_GRAB_INPUT)
					.bind("grab_throw", InputMethod.HOLD, MovesetBuilder.DEFAULT_GRAB_THROW_INPUT)
					.bind("guard", InputMethod.HOLD, InputKey.RMB)
					.makeHotbar(0, USE_SPECIAL, SWITCH_SPECIAL)
					.addToHotbar("star_finger", 0, InputMethod.CLICK)
					.addToHotbar("bearing_shot", 0, InputMethod.HOLD)
					.addToHotbar("enhanced_eyesight", 0, InputMethod.HOLD)
					.addToHotbar("inhale", 0, InputMethod.HOLD)
					.addToHotbar("time_stop", 0, InputMethod.HOLD)
				.finalizeControlScheme()


				.makeControlScheme("keybinds")
					.bind("punch", InputMethod.CLICK, InputKey.LMB)
					.bind("barrage", InputMethod.HOLD, InputKey.LMB)
					.bind("heavy_punch", InputMethod.CLICK, InputKey.RMB)
					.bind("heavy_charged", InputMethod.HOLD, MovesetBuilder.DEFAULT_CHARGED_HEAVY_INPUT)
					.bind("grab", InputMethod.CLICK, MovesetBuilder.DEFAULT_GRAB_INPUT)
					.bind("grab_throw", InputMethod.HOLD, MovesetBuilder.DEFAULT_GRAB_THROW_INPUT)
					.bind("guard", InputMethod.HOLD, InputKey.RMB)
				.finalizeControlScheme()


				.addSkill(StandUnlockableSkill.startingAbility("punch"))
				.addSkill(StandUnlockableSkill.startingAbility("barrage"))
				.addSkill(StandUnlockableSkill.startingAbility("heavy_punch"))
				.addSkill(StandUnlockableSkill.startingAbility("heavy_charged").prerequisiteSkill("heavy_punch"))
				.addSkill(StandUnlockableSkill.startingAbility("guard"))
				.addSkill(StandUnlockableSkill.startingAbility("grab"))
				.addSkill(StandUnlockableSkill.unlockableAbility("grab_throw", 100).withAbility("grab_punch", "grab_barrage", "grab_heavy_punch", "grab_uppercut").prerequisiteSkill("grab"))
				.addSkill(new StandUnlockableSkill("uppercut")
						.setExpToUnlock(150)
						.withAbility("finisher_uppercut")
						.prerequisiteSkill("heavy_punch"))

				.addSkill(StandUnlockableSkill.startingAbility("enhanced_eyesight"))
				.addSkill(StandUnlockableSkill.unlockableAbility("star_finger", 250))
				.addSkill(StandUnlockableSkill.unlockableAbility("inhale", 150))
				.addSkill(StandUnlockableSkill.unlockableAbility("time_stop", 5000))

				.addHumanoidStandSkills()

				, id,
				StandControlType.CLOSE_RANGE_DIRECT,
				1, 2, true, true)
			.summonShout(() -> ModSoundEvents.JOTARO_STAR_PLATINUM)
			.summonSound(() -> ModSoundEvents.STAR_PLATINUM_SUMMON)
			.unsummonSound(() -> ModSoundEvents.STAR_PLATINUM_UNSUMMON)
			.ost(() -> ModSoundEvents.STAR_PLATINUM_OST)
			/* This is to make it appear first in the list of Stardust Crusaders Stands in the creative tab, as the protagonist's Stand */
			.init(stand -> stand.discStoryPartPriority = 0);
	}
}
