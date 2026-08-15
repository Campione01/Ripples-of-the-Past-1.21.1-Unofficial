package com.github.standobyte.jojoimpl.stands;

import static com.github.standobyte.jojo.init.power.ModStands.SWITCH_SPECIAL;
import static com.github.standobyte.jojo.init.power.ModStands.USE_SPECIAL;

import org.jetbrains.annotations.ApiStatus;

import com.github.standobyte.jojo.init.ModSoundEvents;
import com.github.standobyte.jojo.init.ModEntityTypes;
import com.github.standobyte.jojo.init.power.ModStandAbilities;
import com.github.standobyte.jojo.powersystem.MovesetBuilder;
import com.github.standobyte.jojo.powersystem.ability.controls.InputKey;
import com.github.standobyte.jojo.powersystem.ability.controls.InputMethod;
import com.github.standobyte.jojo.powersystem.standpower.StandStats;
import com.github.standobyte.jojo.powersystem.standpower.StandUnlockableSkill;
import com.github.standobyte.jojo.powersystem.standpower.entity.StandControlType;
import com.github.standobyte.jojo.powersystem.standpower.entity.EntityStandType;

import net.minecraft.resources.ResourceLocation;

public class StandInitHierophantGreen {

	@ApiStatus.Internal
	public static EntityStandType create(ResourceLocation id) {
		return new EntityStandType(
				new StandStats.Builder()
				.power(8.0, 9.0)
				.speed(10.0, 12.0)
				.range(50, 100)
				.durability(8.0, 10.0)
				.precision(8.0, 10.0)
				.build(),

				new MovesetBuilder()

				.addHumanoidStandStuff()

				.addAbility("puppet", ModStandAbilities.HG_PUPPET)
				.addAbility("guard", ModStandAbilities.GUARD)
				.addAbility("emerald_splash", ModStandAbilities.HG_EMERALD_SPLASH, ability -> {
					ability.resolveLevelToUnlock(1);
				})
				.addAbility("emerald_splash_concentrated", ModStandAbilities.HG_EMERALD_SPLASH_CONCENTRATED)
				.addAbility("string_attack", ModStandAbilities.HG_STRING_ATTACK)
				.addAbility("string_bind", ModStandAbilities.HG_STRING_BIND)
				.addAbility("grapple", ModStandAbilities.HG_GRAPPLE, ability -> {
					ability.resolveLevelToUnlock(2);
				})
				.addAbility("grapple_entity", ModStandAbilities.HG_GRAPPLE_ENTITY, ability -> {
					ability.resolveLevelToUnlock(2);
				})
				.addAbility("barrier", ModStandAbilities.HG_BARRIER, ability -> {
					ability.resolveLevelToUnlock(3);
				})


				.makeControlScheme("hotbar")
					.bind("guard", InputMethod.HOLD, InputKey.RMB)
					.bind("string_attack", InputMethod.CLICK, InputKey.LMB)
					.bind("emerald_splash", InputMethod.HOLD, InputKey.LMB)
					.makeHotbar(0, USE_SPECIAL, SWITCH_SPECIAL)
					.addToHotbar("puppet", 0, InputMethod.CLICK)
					.addToHotbar("string_bind", 0, InputMethod.CLICK)
					.addToHotbar("grapple", 0, InputMethod.HOLD)
					.addToHotbar("grapple_entity", 0, InputMethod.HOLD)
					.addToHotbar("barrier", 0, InputMethod.CLICK)
				.finalizeControlScheme()

				.makeControlScheme("keybinds")
					.bind("guard", InputMethod.HOLD, InputKey.RMB)
					.bind("string_attack", InputMethod.CLICK, InputKey.LMB)
					.bind("emerald_splash", InputMethod.HOLD, InputKey.LMB)
				.finalizeControlScheme()

				.addSkill(StandUnlockableSkill.startingAbility("guard"))
				.addSkill(StandUnlockableSkill.unlockableAbility("emerald_splash", 150))
				.addSkill(StandUnlockableSkill.unlockableAbility("puppet", 500).setIncomplete())
				.addSkill(new StandUnlockableSkill("emerald_splash_concentrated")
						.withAbility("emerald_splash_concentrated")
						.setHidden()
						.prerequisiteSkill("emerald_splash"))
				.addSkill(StandUnlockableSkill.startingAbility("string_attack").withAbility("string_bind"))
				.addSkill(StandUnlockableSkill.unlockableAbility("grapple", 250).withAbility("grapple_entity").prerequisiteSkill("string_attack"))
				.addSkill(StandUnlockableSkill.unlockableAbility("barrier", 300).prerequisiteSkill("grapple"))
				.addHumanoidStandSkills()

				, ModEntityTypes.HIEROPHANT_GREEN_STAND.get(), id,
				StandControlType.LONG_DISTANCE_OPERATION,
				50, 100, true, false)
			.summonShout(() -> ModSoundEvents.KAKYOIN_HIEROPHANT_GREEN)
			.summonSound(() -> ModSoundEvents.HIEROPHANT_GREEN_SUMMON)
			.unsummonSound(() -> ModSoundEvents.HIEROPHANT_GREEN_UNSUMMON)
			.ost(() -> ModSoundEvents.HIEROPHANT_GREEN_OST);
	}
}
