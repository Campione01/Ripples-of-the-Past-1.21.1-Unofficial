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

public class StandInitGoldExperience {

	@ApiStatus.Internal
	public static EntityStandType create(ResourceLocation id) {
		return new EntityStandType(
				new StandStats.Builder()
				.power(9.0, 10.0)
				.speed(14.0, 15.0)
				.range(2, 4)
				.durability(7)
				.precision(10)
				.build(),

				new MovesetBuilder()

				.addHumanoidStandStuff()

				.addAbility("punch", ModStandAbilities.PUNCH, punch -> {
					punch.punchImpactSound(ModSoundEvents.GOLD_EXPERIENCE_PUNCH_LIGHT).standCrySound(ModSoundEvents.GOLD_EXPERIENCE_MUDA);
				})
				.addAbility("punch2", ModStandAbilities.PUNCH, punch -> {
					punch.punchImpactSound(ModSoundEvents.GOLD_EXPERIENCE_PUNCH_LIGHT).standCrySound(ModSoundEvents.GOLD_EXPERIENCE_MUDA);
				})
				.addAbility("barrage", ModStandAbilities.BARRAGE, barrage -> {
					barrage.barrageHitSound(ModSoundEvents.GOLD_EXPERIENCE_PUNCH_BARRAGE).barrageCrySound(ModSoundEvents.GOLD_EXPERIENCE_MUDA_RUSH);
				})
				.addAbility("heavy_punch", ModStandAbilities.GE_HEAVY_PUNCH, punch -> {
					punch.heavyPunchImpactSound(ModSoundEvents.GOLD_EXPERIENCE_PUNCH_HEAVY)
					.heavyPunchCrySound(ModSoundEvents.GOLD_EXPERIENCE_MUDA_LONG)
					.heavyPunchPerformSound(ModSoundEvents.GOLD_EXPERIENCE_PUNCH_HEAVY_EXTRA);
				})
				.addAbility("heavy_charged", ModStandAbilities.HEAVY_CHARGED)
				.addAbility("guard", ModStandAbilities.GUARD)
				.addAbility("choose_lifeform", ModStandAbilities.GE_CHOOSE_LIFEFORM)
				.addAbility("create_lifeform", ModStandAbilities.GE_CREATE_LIFEFORM)
				.addAbility("mark_item", ModStandAbilities.GE_MARK_ITEM)
				.addAbility("bone_meal", ModStandAbilities.GE_BONE_MEAL)
				.addAbility("life_detector", ModStandAbilities.GE_LIFE_DETECTOR, ability -> {
					ability.resolveLevelToUnlock(2);
				})
				.addAbility("heal", ModStandAbilities.GE_HEAL, ability -> {
					ability.resolveLevelToUnlock(3);
				})
				.addAbility("healing_item", ModStandAbilities.GE_HEALING_ITEM, ability -> {
					ability.resolveLevelToUnlock(3);
				})
				.addAbility("heal_other", ModStandAbilities.GE_HEAL_OTHER, ability -> {
					ability.resolveLevelToUnlock(3);
				})
				.addAbility("lifeshot", ModStandAbilities.GE_ENTITY_LIFESHOT)
				.addAbility("lifeshot_punch", ModStandAbilities.GE_LIFESHOT_PUNCH, punch -> {
					punch.heavyPunchImpactSound(ModSoundEvents.GOLD_EXPERIENCE_PUNCH_HEAVY)
					.heavyPunchCrySound(ModSoundEvents.GOLD_EXPERIENCE_MUDA_LONG)
					.heavyPunchPerformSound(ModSoundEvents.GOLD_EXPERIENCE_PUNCH_HEAVY_EXTRA);
					punch.initIsFinisher("heavy_punch");
					punch.resolveLevelToUnlock(1);
				})
				.addAbility("tooth_lifeform", ModStandAbilities.GE_TOOTH_LIFEFORM)
				.addAbility("revert_lifeform", ModStandAbilities.GE_REVERT_LIFEFORM)


				.makeControlScheme("hotbar")
					.bind("guard", InputMethod.HOLD, InputKey.RMB)
					.bind("punch", InputMethod.CLICK, InputKey.LMB)
					.bind("barrage", InputMethod.HOLD, InputKey.LMB)
					.bind("heavy_punch", InputMethod.CLICK, InputKey.RMB)
					.bind("heavy_charged", InputMethod.HOLD, InputKey.RMB.withModifier(InputKey.Modifier.CONTROL))
					.makeHotbar(0, USE_SPECIAL, SWITCH_SPECIAL)
					.addToHotbar("choose_lifeform", 0, InputMethod.CLICK)
					.addToHotbar("create_lifeform", 0, InputMethod.CLICK)
					.addToHotbar("revert_lifeform", 0, InputMethod.CLICK)
					.addToHotbar("mark_item", 0, InputMethod.CLICK)
					.addToHotbar("bone_meal", 0, InputMethod.CLICK)
					.addToHotbar("life_detector", 0, InputMethod.HOLD)
					.addToHotbar("heal", 0, InputMethod.CLICK)
					.addToHotbar("heal_other", 0, InputMethod.CLICK)
					.addToHotbar("healing_item", 0, InputMethod.CLICK)
				.finalizeControlScheme()


				.makeControlScheme("keybinds")
					.bind("guard", InputMethod.HOLD, InputKey.RMB)
					.bind("punch", InputMethod.CLICK, InputKey.LMB)
					.bind("barrage", InputMethod.HOLD, InputKey.LMB)
					.bind("heavy_punch", InputMethod.CLICK, InputKey.RMB)
					.bind("heavy_charged", InputMethod.HOLD, InputKey.RMB.withModifier(InputKey.Modifier.CONTROL))
				.finalizeControlScheme()


				.addSkill(StandUnlockableSkill.startingAbility("punch"))
				.addSkill(StandUnlockableSkill.startingAbility("barrage"))
				.addSkill(StandUnlockableSkill.startingAbility("heavy_punch").withAbility("tooth_lifeform"))
				.addSkill(StandUnlockableSkill.startingAbility("heavy_charged").prerequisiteSkill("heavy_punch"))
				.addSkill(StandUnlockableSkill.startingAbility("choose_lifeform"))
				.addSkill(StandUnlockableSkill.startingAbility("create_lifeform").withAbility("revert_lifeform"))
				.addSkill(StandUnlockableSkill.startingAbility("mark_item"))
				.addSkill(StandUnlockableSkill.startingAbility("bone_meal"))
				.addSkill(StandUnlockableSkill.unlockableAbility("life_detector", 200))
				.addSkill(StandUnlockableSkill.unlockableAbility("heal", 250))
				.addSkill(StandUnlockableSkill.unlockableAbility("heal_other", 300).withAbility("healing_item").prerequisiteSkill("heal"))
				.addSkill(StandUnlockableSkill.unlockableAbility("lifeshot", 150).withAbility("lifeshot_punch").prerequisiteSkill("heavy_punch"))
				.addHumanoidStandSkills()

				, id)
			.summonShout(() -> ModSoundEvents.GIORNO_GOLD_EXPERIENCE)
			.summonSound(() -> ModSoundEvents.GOLD_EXPERIENCE_SUMMON)
			.unsummonSound(() -> ModSoundEvents.GOLD_EXPERIENCE_UNSUMMON)
			.ost(() -> ModSoundEvents.GOLD_EXPERIENCE_OST);
	}
}
