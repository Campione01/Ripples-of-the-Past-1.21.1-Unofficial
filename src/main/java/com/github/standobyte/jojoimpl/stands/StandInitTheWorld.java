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

public class StandInitTheWorld {

	@ApiStatus.Internal
	public static EntityStandType create(ResourceLocation id) {
		return new EntityStandType(
				new StandStats.Builder()
				.power(16.0, 19.0)
				.speed(16.0, 18.5)
				.range(5, 10)
				.durability(16.0, 20.0)
				.precision(12)
				.randomWeight(1)
				.build(),

				new MovesetBuilder()

				.addHumanoidStandStuff()

				.addAbility("punch", ModStandAbilities.PUNCH, punch -> {
					punch.punchImpactSound(ModSoundEvents.THE_WORLD_PUNCH_LIGHT).standCrySound(ModSoundEvents.DIO_MUDA);
				})
				.addAbility("punch2", ModStandAbilities.PUNCH, punch -> {
					punch.punchImpactSound(ModSoundEvents.THE_WORLD_PUNCH_LIGHT).standCrySound(ModSoundEvents.DIO_MUDA);
				})
				.addAbility("barrage", ModStandAbilities.TW_BARRAGE, barrage -> {
					barrage.barrageHitSound(ModSoundEvents.THE_WORLD_PUNCH_BARRAGE).barrageCrySound(ModSoundEvents.THE_WORLD_MUDA_MUDA_MUDA);
				})
				.addAbility("heavy_punch", ModStandAbilities.TW_HEAVY_PUNCH)
				.addAbility("heavy_charged", ModStandAbilities.HEAVY_CHARGED)
				.addAbility("kick", ModStandAbilities.TW_KICK, kick -> {
					kick.initIsFinisher("heavy_punch");
					kick.resolveLevelToUnlock(1);
				})
				.addAbility("grab", ModStandAbilities.GRAB)
				.addAbility("grab_throw", ModStandAbilities.GRAB_THROW)
				.addAbility("grab_punch", ModStandAbilities.PUNCH, punch -> {
					punch.punchImpactSound(ModSoundEvents.THE_WORLD_PUNCH_LIGHT).standCrySound(ModSoundEvents.DIO_MUDA);
					punch.initIsGrabVariation();
				})
				.addAbility("grab_barrage", ModStandAbilities.TW_BARRAGE, barrage -> {
					barrage.barrageHitSound(ModSoundEvents.THE_WORLD_PUNCH_BARRAGE).barrageCrySound(ModSoundEvents.THE_WORLD_MUDA_MUDA_MUDA);
					barrage.initIsGrabVariation();
				})
				.addAbility("grab_heavy_punch", ModStandAbilities.TW_HEAVY_PUNCH, punch -> {
					punch.initIsGrabVariation();
				})
				.addAbility("grab_kick", ModStandAbilities.TW_KICK, kick -> {
					kick.initIsGrabVariation();
					kick.initIsFinisher("grab_heavy_punch");
				})
				.addAbility("ts_punch", ModStandAbilities.TW_TS_PUNCH)
				.addAbility("guard", ModStandAbilities.GUARD)
				.addAbility("time_stop", ModStandAbilities.TIME_STOP, ability -> {
					ability.setIgnoresPerformerStun();
				})
				.addAbility("time_stop_blink", ModStandAbilities.TIME_STOP_BLINK, ability -> {
					ability.setTeleportBehindEntity();
				})


				.makeControlScheme("hotbar")
					.bind("punch", InputMethod.CLICK, InputKey.LMB)
					.bind("barrage", InputMethod.HOLD, InputKey.LMB)
					.bind("heavy_punch", InputMethod.CLICK, InputKey.RMB)
					.bind("heavy_charged", InputMethod.HOLD, MovesetBuilder.DEFAULT_CHARGED_HEAVY_INPUT)
					.bind("grab", InputMethod.CLICK, MovesetBuilder.DEFAULT_GRAB_INPUT)
					.bind("grab_throw", InputMethod.HOLD, MovesetBuilder.DEFAULT_GRAB_THROW_INPUT)
					.bind("guard", InputMethod.HOLD, InputKey.RMB)
					.makeHotbar(0, USE_SPECIAL, SWITCH_SPECIAL)
					.addToHotbar("ts_punch", 0, InputMethod.CLICK)
					.addToHotbar("time_stop", 0, InputMethod.HOLD)
				.finalizeControlScheme()


				.makeControlScheme("keybinds")
					.bind("guard", InputMethod.HOLD, InputKey.RMB)
					.bind("punch", InputMethod.CLICK, InputKey.LMB)
					.bind("barrage", InputMethod.HOLD, InputKey.LMB)
					.bind("heavy_punch", InputMethod.CLICK, InputKey.RMB)
					.bind("heavy_charged", InputMethod.HOLD, MovesetBuilder.DEFAULT_CHARGED_HEAVY_INPUT)
					.bind("grab", InputMethod.CLICK, MovesetBuilder.DEFAULT_GRAB_INPUT)
					.bind("grab_throw", InputMethod.HOLD, MovesetBuilder.DEFAULT_GRAB_THROW_INPUT)
				.finalizeControlScheme()


				.addSkill(StandUnlockableSkill.startingAbility("punch"))
				.addSkill(StandUnlockableSkill.startingAbility("barrage"))
				.addSkill(StandUnlockableSkill.startingAbility("heavy_punch"))
				.addSkill(StandUnlockableSkill.startingAbility("heavy_charged").prerequisiteSkill("heavy_punch"))
				.addSkill(StandUnlockableSkill.startingAbility("grab"))
				.addSkill(StandUnlockableSkill.unlockableAbility("grab_throw", 100)
						.withAbility("grab_punch", "grab_barrage", "grab_heavy_punch", "grab_kick")
						.prerequisiteSkill("grab"))
				.addSkill(StandUnlockableSkill.unlockableAbility("kick", 150).prerequisiteSkill("heavy_punch"))
				.addSkill(StandUnlockableSkill.unlockableAbility("ts_punch", 6000).prerequisiteSkill("time_stop"))
				.addSkill(StandUnlockableSkill.unlockableAbility("time_stop", 5000))
				.addHumanoidStandSkills()

				, id)
			.summonShout(() -> ModSoundEvents.DIO_THE_WORLD)
			.summonSound(() -> ModSoundEvents.THE_WORLD_SUMMON)
			.unsummonSound(() -> ModSoundEvents.THE_WORLD_UNSUMMON)
			.ost(() -> ModSoundEvents.THE_WORLD_OST);
	}
}
