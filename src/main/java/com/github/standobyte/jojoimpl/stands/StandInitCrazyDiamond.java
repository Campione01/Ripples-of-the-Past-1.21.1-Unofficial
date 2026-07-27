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

public class StandInitCrazyDiamond {

	@ApiStatus.Internal
	public static EntityStandType create(ResourceLocation id) {
		return new EntityStandType(
				new StandStats.Builder()
				.power(14.0, 17.0)
				.speed(14.0, 16.5)
				.range(2, 4)
				.durability(11.0, 13.0)
				.precision(11.0, 12.0)
				.build(),

				new MovesetBuilder()

				.addHumanoidStandStuff()

				.addAbility("punch", ModStandAbilities.PUNCH, punch -> {
					punch.punchImpactSound(ModSoundEvents.CRAZY_DIAMOND_PUNCH_LIGHT).standCrySound(ModSoundEvents.CRAZY_DIAMOND_DORA);
				})
				.addAbility("punch2", ModStandAbilities.PUNCH, punch -> {
					punch.punchImpactSound(ModSoundEvents.CRAZY_DIAMOND_PUNCH_LIGHT).standCrySound(ModSoundEvents.CRAZY_DIAMOND_DORA);
				})
				.addAbility("punch3", ModStandAbilities.PUNCH, punch -> {
					punch.punchImpactSound(ModSoundEvents.CRAZY_DIAMOND_PUNCH_LIGHT).standCrySound(ModSoundEvents.CRAZY_DIAMOND_DORA);
				})
				.addAbility("punch4", ModStandAbilities.PUNCH, punch -> {
					punch.punchImpactSound(ModSoundEvents.CRAZY_DIAMOND_PUNCH_LIGHT).standCrySound(ModSoundEvents.CRAZY_DIAMOND_DORA);
				})
				.addAbility("barrage", ModStandAbilities.BARRAGE, barrage -> {
					barrage.barrageHitSound(ModSoundEvents.CRAZY_DIAMOND_PUNCH_BARRAGE).barrageCrySound(ModSoundEvents.CRAZY_DIAMOND_DORARARA);
				})
				.addAbility("heavy_punch", ModStandAbilities.CD_HEAVY_PUNCH, punch -> {
					punch.heavyPunchImpactSound(ModSoundEvents.CRAZY_DIAMOND_PUNCH_HEAVY).heavyPunchCrySound(ModSoundEvents.CRAZY_DIAMOND_DORA_LONG);
				})
				.addAbility("heavy_charged", ModStandAbilities.HEAVY_CHARGED)
				.addAbility("finisher", ModStandAbilities.HEAVY_PUNCH, punch -> {
					punch.heavyPunchImpactSound(ModSoundEvents.CRAZY_DIAMOND_PUNCH_HEAVY).heavyPunchCrySound(ModSoundEvents.CRAZY_DIAMOND_DORA_LONG);
					punch.initIsFinisher();
					punch.resolveLevelToUnlock(1);
				})
				.addAbility("grab", ModStandAbilities.GRAB)
				.addAbility("grab_throw", ModStandAbilities.GRAB_THROW)
				.addAbility("grab_punch", ModStandAbilities.PUNCH, punch -> {
					punch.punchImpactSound(ModSoundEvents.CRAZY_DIAMOND_PUNCH_LIGHT).standCrySound(ModSoundEvents.CRAZY_DIAMOND_DORA);
					punch.initIsGrabVariation();
				})
				.addAbility("grab_barrage", ModStandAbilities.BARRAGE, barrage -> {
					barrage.barrageHitSound(ModSoundEvents.CRAZY_DIAMOND_PUNCH_BARRAGE).barrageCrySound(ModSoundEvents.CRAZY_DIAMOND_DORARARA);
					barrage.initIsGrabVariation();
				})
				.addAbility("grab_heavy_punch", ModStandAbilities.CD_HEAVY_PUNCH, punch -> {
					punch.heavyPunchImpactSound(ModSoundEvents.CRAZY_DIAMOND_PUNCH_HEAVY).heavyPunchCrySound(ModSoundEvents.CRAZY_DIAMOND_DORA_LONG);
					punch.initIsGrabVariation();
				})
				.addAbility("grab_finisher", ModStandAbilities.HEAVY_PUNCH, punch -> {
					punch.heavyPunchImpactSound(ModSoundEvents.CRAZY_DIAMOND_PUNCH_HEAVY).heavyPunchCrySound(ModSoundEvents.CRAZY_DIAMOND_DORA_LONG);
					punch.initIsGrabVariation();
					punch.initIsFinisher("grab_heavy_punch");
				})

				.addAbility("guard", ModStandAbilities.GUARD)
				.addAbility("bearing_shot", ModStandAbilities.BEARING_SHOT)

				.addAbility("leave_object", ModStandAbilities.CD_LEAVE_OBJECT_ON_PUNCH, ability -> {
					ability.resolveLevelToUnlock(1);
				})
				.addAbility("disfiguring_punch", ModStandAbilities.CD_DISFIGURE_ON_PUNCH, ability -> {
					ability.resolveLevelToUnlock(1);
				})
				.addAbility("fuse_with_rock", ModStandAbilities.CD_ANGELO_ROCK_ON_PUNCH, ability -> {
					ability.resolveLevelToUnlock(2);
				})

				.addAbility("repair_item", ModStandAbilities.CD_REPAIR_ITEM)
				.addAbility("uncraft", ModStandAbilities.CD_UNCRAFT_ITEM)
				.addAbility("heal", ModStandAbilities.CD_HEAL, ability -> {
					ability.resolveLevelToUnlock(1);
				})
				.addAbility("revert_state", ModStandAbilities.CD_REVERT_STATE)
				.addAbility("restore_terrain", ModStandAbilities.CD_RESTORE_TERRAIN, ability -> {
					ability.resolveLevelToUnlock(2);
				})
				.addAbility("block_anchor", ModStandAbilities.CD_ANCHOR_MOVE, ability -> {
					ability.resolveLevelToUnlock(3);
				})
				.addAbility("block_anchor_make", ModStandAbilities.CD_ANCHOR_MAKE, ability -> {
					ability.resolveLevelToUnlock(3);
				})
				.addAbility("block_bullet", ModStandAbilities.CD_BLOCK_BULLET, ability -> {
					ability.resolveLevelToUnlock(4);
				})
				.addAbility("blood_cutter", ModStandAbilities.CD_BLOOD_CUTTER, ability -> {
					ability.resolveLevelToUnlock(4);
				})


				.makeControlScheme("hotbar")
					.bind("punch", InputMethod.CLICK, InputKey.LMB)
					.bind("barrage", InputMethod.HOLD, InputKey.LMB)
					.bind("heavy_punch", InputMethod.CLICK, InputKey.RMB)
					.bind("heavy_charged", InputMethod.HOLD, MovesetBuilder.DEFAULT_CHARGED_HEAVY_INPUT)
					.bind("grab", InputMethod.CLICK, MovesetBuilder.DEFAULT_GRAB_INPUT)
					.bind("grab_throw", InputMethod.HOLD, MovesetBuilder.DEFAULT_GRAB_THROW_INPUT)
					.bind("guard", InputMethod.HOLD, InputKey.RMB)
					.bind("uncraft", InputMethod.HOLD, InputKey.I)
					.makeHotbar(0, USE_SPECIAL, SWITCH_SPECIAL)
					.addToHotbar("block_bullet", 0, InputMethod.CLICK)
					.addToHotbar("blood_cutter", 0, InputMethod.CLICK)
					.addToHotbar("bearing_shot", 0, InputMethod.HOLD)
					.addToHotbar("repair_item", 0, InputMethod.HOLD)
					.addToHotbar("revert_state", 0, InputMethod.HOLD)
					.addToHotbar("uncraft", 0, InputMethod.HOLD)
					.addToHotbar("heal", 0, InputMethod.HOLD)
					.addToHotbar("restore_terrain", 0, InputMethod.HOLD)
					.addToHotbar("block_anchor", 0, InputMethod.HOLD)
					.addToHotbar("block_anchor_make", 0, InputMethod.CLICK)
				.finalizeControlScheme()


				.makeControlScheme("keybinds")
					.bind("punch", InputMethod.CLICK, InputKey.LMB)
					.bind("barrage", InputMethod.HOLD, InputKey.LMB)
					.bind("heavy_punch", InputMethod.CLICK, InputKey.RMB)
					.bind("heavy_charged", InputMethod.HOLD, MovesetBuilder.DEFAULT_CHARGED_HEAVY_INPUT)
					.bind("grab", InputMethod.CLICK, MovesetBuilder.DEFAULT_GRAB_INPUT)
					.bind("grab_throw", InputMethod.HOLD, MovesetBuilder.DEFAULT_GRAB_THROW_INPUT)
					.bind("guard", InputMethod.HOLD, InputKey.RMB)
					.bind("uncraft", InputMethod.HOLD, InputKey.I)
				.finalizeControlScheme()


				.addSkill(StandUnlockableSkill.startingAbility("punch"))
				.addSkill(StandUnlockableSkill.startingAbility("barrage"))
				.addSkill(StandUnlockableSkill.startingAbility("heavy_punch"))
				.addSkill(StandUnlockableSkill.startingAbility("heavy_charged").prerequisiteSkill("heavy_punch"))
				.addSkill(StandUnlockableSkill.startingAbility("guard"))
				.addSkill(StandUnlockableSkill.startingAbility("grab"))
				.addSkill(StandUnlockableSkill.unlockableAbility("grab_throw", 100)
						.withAbility("grab_punch", "grab_barrage", "grab_heavy_punch", "grab_finisher")
						.prerequisiteSkill("grab"))
				.addSkill(StandUnlockableSkill.unlockableAbility("finisher", 100)
						.withAbility("disfiguring_punch", "leave_object")
						.prerequisiteSkill("heavy_punch"))
				
				.addSkill(StandUnlockableSkill.startingAbility("repair_item"))
				.addSkill(new StandUnlockableSkill("revert_state")
						.withAbility("revert_state")
						.setHidden()
						.prerequisiteSkill("repair_item"))
				.addSkill(StandUnlockableSkill.unlockableAbility("uncraft", 0)
						.prerequisiteSkill("repair_item", "revert_state"))

				.addSkill(StandUnlockableSkill.unlockableAbility("heal", 100))

				.addSkill(StandUnlockableSkill.unlockableAbility("restore_terrain", 150).withAbility("fuse_with_rock"))
				.addSkill(StandUnlockableSkill.unlockableAbility("block_anchor", 300)
						.withAbility("block_anchor_make")
						.prerequisiteSkill("restore_terrain"))

				.addSkill(StandUnlockableSkill.unlockableAbility("block_bullet", 400))
				.addSkill(StandUnlockableSkill.unlockableAbility("blood_cutter", 400))

				.addHumanoidStandSkills()

				, id)
			.summonShout(() -> ModSoundEvents.JOSUKE_CRAZY_DIAMOND)
			.summonSound(() -> ModSoundEvents.CRAZY_DIAMOND_SUMMON)
			.unsummonSound(() -> ModSoundEvents.CRAZY_DIAMOND_UNSUMMON)
			.ost(() -> ModSoundEvents.CRAZY_DIAMOND_OST)
			.init(stand -> stand.discStoryPartPriority = 0);
	}
}
