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
import com.github.standobyte.jojo.powersystem.standpower.entity.EntityStandType;

import net.minecraft.resources.ResourceLocation;

public class StandInitSilverChariot {

	@ApiStatus.Internal
	public static EntityStandType create(ResourceLocation id) {
		return new EntityStandType(
				new StandStats.Builder()
				.power(8.0, 9.0)
				.speed(14.0, 17.5)
				.range(2, 10)
				.durability(11.0, 12.0)
				.precision(11.5, 16.0)
				.build(),

				new MovesetBuilder()

				.addHumanoidStandStuff()

				.addAbility("light_attack", ModStandAbilities.SC_LIGHT_ATTACK)
				.addAbility("no_rapier_light_attack", ModStandAbilities.SC_NO_RAPIER_LIGHT_ATTACK)
				.addAbility("melee_barrage", ModStandAbilities.SC_BARRAGE)
				.addAbility("dash_attack", ModStandAbilities.SC_DASH_ATTACK)
				.addAbility("sweeping_attack", ModStandAbilities.SC_SWEEPING_ATTACK, attack -> {
					attack.initIsFinisher("dash_attack");
					attack.resolveLevelToUnlock(1);
				})
				.addAbility("rapier_launch", ModStandAbilities.SC_RAPIER_LAUNCH, ability -> {
					ability.setIgnoresPerformerStun();
					ability.resolveLevelToUnlock(2);
				})
				.addAbility("take_off_armor", ModStandAbilities.SC_TAKE_OFF_ARMOR, ability -> {
					ability.resolveLevelToUnlock(3);
				})
				.addAbility("guard", ModStandAbilities.GUARD)


				.makeControlScheme("hotbar")
					.bind("guard", InputMethod.HOLD, InputKey.RMB)
					.bind("light_attack", InputMethod.CLICK, InputKey.LMB)
					.bind("melee_barrage", InputMethod.HOLD, InputKey.LMB)
					.bind("sweeping_attack", InputMethod.CLICK, InputKey.RMB)
					.makeHotbar(0, USE_SPECIAL, SWITCH_SPECIAL)
					.addToHotbar("dash_attack", 0, InputMethod.CLICK)
					.addToHotbar("rapier_launch", 0, InputMethod.CLICK)
					.addToHotbar("take_off_armor", 0, InputMethod.CLICK)
				.finalizeControlScheme()


				.makeControlScheme("keybinds")
					.bind("guard", InputMethod.HOLD, InputKey.RMB)
					.bind("light_attack", InputMethod.CLICK, InputKey.LMB)
					.bind("melee_barrage", InputMethod.HOLD, InputKey.LMB)
					.bind("sweeping_attack", InputMethod.CLICK, InputKey.RMB)
				.finalizeControlScheme()


				.addSkill(StandUnlockableSkill.startingAbility("light_attack").withAbility("no_rapier_light_attack"))
				.addSkill(StandUnlockableSkill.startingAbility("melee_barrage"))
				.addSkill(StandUnlockableSkill.startingAbility("dash_attack"))
				.addSkill(StandUnlockableSkill.unlockableAbility("sweeping_attack", 200).prerequisiteSkill("dash_attack"))
				.addSkill(StandUnlockableSkill.unlockableAbility("rapier_launch", 250).prerequisiteSkill("dash_attack"))
				.addSkill(StandUnlockableSkill.unlockableAbility("take_off_armor", 300))
				.addHumanoidStandSkills()

				, id)
			.summonShout(() -> ModSoundEvents.POLNAREFF_SILVER_CHARIOT)
			.summonSound(() -> ModSoundEvents.SILVER_CHARIOT_SUMMON)
			.unsummonSound(() -> ModSoundEvents.SILVER_CHARIOT_UNSUMMON)
			.ost(() -> ModSoundEvents.SILVER_CHARIOT_OST);
	}
}
