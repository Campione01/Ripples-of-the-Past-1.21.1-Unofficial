package rotp.core.impl.powers.hamon.entity;

import javax.annotation.Nullable;

import rotp.core.init.ModDamageTypes;
import rotp.core.init.power.ModPlayerPowers;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.playerpower.PlayerPower;
import rotp.core.impl.powers.hamon.HamonData;
import rotp.core.impl.powers.hamon.HamonSkillDefinition;
import rotp.core.impl.powers.hamon.HamonUtil;
import rotp.core.impl.powers.hamon.ModHamonSkills;

import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.npc.Npc;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.material.FluidState;
import net.neoforged.neoforge.common.NeoForgeMod;

public class HamonMasterEntity extends Mob implements Npc {

	public HamonMasterEntity(EntityType<? extends HamonMasterEntity> type, Level level) {
		super(type, level);
		PowerClass.PLAYER_POWER.attachGet(this);
	}

	@Override
	public boolean removeWhenFarAway(double distanceFromPlayer) {
		return false;
	}

	@Override
	public boolean requiresCustomPersistence() {
		return true;
	}

	@Override
	public InteractionResult mobInteract(Player player, InteractionHand hand) {
		if (hand == InteractionHand.MAIN_HAND) {
			PlayerPower.getPowerData(this, ModPlayerPowers.HAMON).ifPresent(hamon ->
					HamonUtil.interactWithHamonTeacher(level(), player, this, hamon));
		}
		return super.mobInteract(player, hand);
	}

	@Override
	public boolean canStandOnFluid(FluidState fluidState) {
		return PlayerPower.getPowerData(this, ModPlayerPowers.HAMON)
				.map(hamon -> hamon.isSkillLearned(ModHamonSkills.LIQUID_WALKING.get()))
				.orElse(false);
	}

	@Override
	public void lavaHurt() {}

	@Override
	public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
			MobSpawnType spawnType, @Nullable SpawnGroupData spawnGroupData) {
		addMasterHamon();
		setPersistenceRequired();
		return super.finalizeSpawn(level, difficulty, spawnType, spawnGroupData);
	}

	@Override
	public boolean isInvulnerableTo(DamageSource source) {
		return super.isInvulnerableTo(source) || source.is(ModDamageTypes.HAMON);
	}

	public void addMasterHamon() {
		PlayerPower playerPower = PowerClass.PLAYER_POWER.attachGet(this);
		playerPower.setPowerType(ModPlayerPowers.HAMON.get());
		HamonData hamon = playerPower.getCurTypeData(ModPlayerPowers.HAMON).orElseThrow();
		hamon.setBreathingLevel(HamonData.MAX_BREATHING_LEVEL);
		hamon.setHamonStatPoints(HamonData.HamonStat.STRENGTH, HamonData.MAX_HAMON_POINTS, true, true);
		hamon.setHamonStatPoints(HamonData.HamonStat.CONTROL, HamonData.MAX_HAMON_POINTS, true, true);
		for (HamonSkillDefinition definition : ModHamonSkills.SKILL_DEFINITIONS) {
			if (definition.requiresTeacher()) {
				hamon.learnSkill(ModHamonSkills.skillByName(definition.name()));
			}
		}
	}

	@Override
	protected void registerGoals() {}

	public static AttributeSupplier.Builder createAttributes() {
		return Mob.createMobAttributes()
				.add(Attributes.ATTACK_DAMAGE, 3.0D)
				.add(Attributes.ATTACK_SPEED, 8.0D)
				.add(Attributes.MOVEMENT_SPEED, 0.1D)
				.add(NeoForgeMod.SWIM_SPEED, 2.0D);
	}
}
