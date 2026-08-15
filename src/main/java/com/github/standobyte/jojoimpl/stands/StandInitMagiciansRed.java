package com.github.standobyte.jojoimpl.stands;

import static com.github.standobyte.jojo.init.power.ModStands.SWITCH_SPECIAL;
import static com.github.standobyte.jojo.init.power.ModStands.USE_SPECIAL;

import org.jetbrains.annotations.ApiStatus;

import com.github.standobyte.jojo.init.ModSoundEvents;
import com.github.standobyte.jojo.init.power.ModStandAbilities;
import com.github.standobyte.jojo.powersystem.MovesetBuilder;
import com.github.standobyte.jojo.powersystem.ability.controls.InputKey;
import com.github.standobyte.jojo.powersystem.ability.controls.InputMethod;
import com.github.standobyte.jojo.powersystem.standpower.StandStats;
import com.github.standobyte.jojo.powersystem.standpower.StandUnlockableSkill;
import com.github.standobyte.jojo.powersystem.standpower.entity.StandControlType;
import com.github.standobyte.jojo.powersystem.standpower.entity.EntityStandType;

import net.minecraft.resources.ResourceLocation;

public class StandInitMagiciansRed {

	@ApiStatus.Internal
	public static EntityStandType create(ResourceLocation id) {
		return new EntityStandType(
				new StandStats.Builder()
				.power(11.0, 13.0)
				.speed(11.0, 12.0)
				.range(5, 10)
				.durability(12.0, 13.0)
				.precision(8.0, 9.0)
				.build(),

				new MovesetBuilder()

				.addHumanoidStandStuff()

				.addAbility("punch", ModStandAbilities.PUNCH, punch -> {
					punch.punchImpactSound(ModSoundEvents.MAGICIANS_RED_PUNCH_LIGHT);
				})
				.addAbility("heavy_punch", ModStandAbilities.HEAVY_PUNCH, punch -> {
					punch.heavyPunchImpactSound(ModSoundEvents.MAGICIANS_RED_PUNCH_HEAVY);
				})
				.addAbility("heavy_charged", ModStandAbilities.HEAVY_CHARGED)
				.addAbility("kick", ModStandAbilities.MR_KICK, kick -> {
					kick.initIsFinisher("heavy_punch");
					kick.resolveLevelToUnlock(1);
				})
				.addAbility("guard", ModStandAbilities.GUARD)
				.addAbility("flame_burst", ModStandAbilities.MR_FLAME_BURST)
				.addAbility("fireball", ModStandAbilities.MR_FIREBALL, ability -> {
					ability.resolveLevelToUnlock(2);
				})
				.addAbility("crossfire_hurricane", ModStandAbilities.MR_CROSSFIRE_HURRICANE, ability -> {
					ability.resolveLevelToUnlock(4);
				})
				.addAbility("crossfire_hurricane_special", ModStandAbilities.MR_CROSSFIRE_HURRICANE_SPECIAL)
				.addAbility("red_bind", ModStandAbilities.MR_RED_BIND, ability -> {
					ability.resolveLevelToUnlock(1);
				})
				.addAbility("mr_detector", ModStandAbilities.MR_DETECTOR, ability -> {
					ability.resolveLevelToUnlock(3);
				})


				.makeControlScheme("hotbar")
					.bind("guard", InputMethod.HOLD, InputKey.RMB)
					.bind("punch", InputMethod.CLICK, InputKey.LMB)
					.bind("heavy_punch", InputMethod.CLICK, InputKey.RMB)
					.bind("heavy_charged", InputMethod.HOLD, MovesetBuilder.DEFAULT_CHARGED_HEAVY_INPUT)
					.makeHotbar(0, USE_SPECIAL, SWITCH_SPECIAL)
					.addToHotbar("flame_burst", 0, InputMethod.HOLD)
					.addToHotbar("fireball", 0, InputMethod.CLICK)
					.addToHotbar("crossfire_hurricane", 0, InputMethod.HOLD)
					.addToHotbar("crossfire_hurricane_special", 0, InputMethod.HOLD)
					.addToHotbar("red_bind", 0, InputMethod.HOLD)
					.addToHotbar("mr_detector", 0, InputMethod.CLICK)
				.finalizeControlScheme()


				.makeControlScheme("keybinds")
					.bind("guard", InputMethod.HOLD, InputKey.RMB)
					.bind("punch", InputMethod.CLICK, InputKey.LMB)
					.bind("heavy_punch", InputMethod.CLICK, InputKey.RMB)
					.bind("heavy_charged", InputMethod.HOLD, MovesetBuilder.DEFAULT_CHARGED_HEAVY_INPUT)
				.finalizeControlScheme()


				.addSkill(StandUnlockableSkill.startingAbility("punch"))
				.addSkill(StandUnlockableSkill.startingAbility("heavy_punch"))
				.addSkill(StandUnlockableSkill.startingAbility("heavy_charged").prerequisiteSkill("heavy_punch"))
				.addSkill(StandUnlockableSkill.unlockableAbility("kick", 150).prerequisiteSkill("heavy_punch"))
				.addSkill(StandUnlockableSkill.startingAbility("guard"))
				.addSkill(StandUnlockableSkill.startingAbility("flame_burst"))
				.addSkill(StandUnlockableSkill.unlockableAbility("fireball", 250).prerequisiteSkill("flame_burst"))
				.addSkill(StandUnlockableSkill.unlockableAbility("crossfire_hurricane", 400).prerequisiteSkill("fireball"))
				.addSkill(new StandUnlockableSkill("crossfire_hurricane_special")
						.withAbility("crossfire_hurricane_special")
						.setHidden()
						.prerequisiteSkill("crossfire_hurricane"))
				.addSkill(StandUnlockableSkill.unlockableAbility("red_bind", 150))
				.addSkill(StandUnlockableSkill.unlockableAbility("mr_detector", 300))
				.addHumanoidStandSkills()

				, id,
				StandControlType.CLOSE_RANGE_DIRECT,
				5, 10, true, true)
			.summonShout(() -> ModSoundEvents.AVDOL_MAGICIANS_RED)
			.summonSound(() -> ModSoundEvents.MAGICIANS_RED_SUMMON)
			.unsummonSound(() -> ModSoundEvents.MAGICIANS_RED_UNSUMMON)
			.ost(() -> ModSoundEvents.MAGICIANS_RED_OST);
	}
}
