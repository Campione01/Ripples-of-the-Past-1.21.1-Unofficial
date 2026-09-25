package rotp.core.impl.stands.goldexperience;

import java.util.ArrayList;
import java.util.List;

import rotp.core.impl.powers.hamon.HamonData;
import rotp.core.impl.powers.pillarman.PillarmanData;
import rotp.core.impl.powers.vampirism.VampirismData;
import rotp.core.impl.powers.zombie.ZombieData;
import rotp.core.init.ModStatusEffects;
import rotp.core.init.power.ModPlayerPowers;
import rotp.core.powersystem.playerpower.PlayerPower;
import rotp.core.powersystem.standpower.StandPower;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * 1.16 ClientEventHandler.hudRenderEntityGEDetectorData lines for the Life Detector entity looked at: HP, player power
 * energy, Stand stamina and Resolve (full under the Resolve effect), as whole percents. No client classes here.
 */
public final class GoldExperienceLifeDetectorReadout {
	public enum ReadoutKind { HEALTH, ENERGY, STAMINA, RESOLVE }

	public record ReadoutLine(ReadoutKind kind, int percent) {}

	private GoldExperienceLifeDetectorReadout() {}

	public static List<ReadoutLine> readout(Entity entity) {
		List<ReadoutLine> lines = new ArrayList<>();
		// 1.16 left the soul line as a TODO, so a soul shows nothing
		if (!(entity instanceof LivingEntity living)) {
			return lines;
		}
		if (living.getMaxHealth() > 0) {
			lines.add(new ReadoutLine(ReadoutKind.HEALTH, percent(living.getHealth() / living.getMaxHealth())));
		}
		float energyRatio = energyRatio(living);
		if (energyRatio >= 0) {
			lines.add(new ReadoutLine(ReadoutKind.ENERGY, percent(energyRatio)));
		}
		StandPower stand = StandPower.get(living);
		if (stand != null && stand.hasPower()) {
			if (stand.usesStamina() && stand.getMaxStamina() > 0) {
				lines.add(new ReadoutLine(ReadoutKind.STAMINA, percent(stand.getStamina() / stand.getMaxStamina())));
			}
			float maxResolve = stand.usesResolve() ? stand.resolveCounter.getMaxResolveValue(stand) : 0;
			if (maxResolve > 0) {
				float resolveRatio = living.hasEffect(ModStatusEffects.RESOLVE) ? 1 : stand.resolveCounter.getResolveValue() / maxResolve;
				lines.add(new ReadoutLine(ReadoutKind.RESOLVE, percent(resolveRatio)));
			}
		}
		return lines;
	}

	private static int percent(float ratio) {
		return (int) (ratio * 100);
	}

	// ratio of the player power's energy (blood for vampires), or -1 without one
	private static float energyRatio(LivingEntity living) {
		PlayerPower power = PlayerPower.get(living);
		if (power == null || !power.hasPower()) {
			return -1;
		}
		var type = power.getPowerType();
		float energy;
		float maxEnergy;
		if (type == ModPlayerPowers.HAMON.get()) {
			HamonData data = power.getCurTypeData(ModPlayerPowers.HAMON).orElse(null);
			if (data == null) return -1;
			energy = data.getEnergy();
			maxEnergy = data.getMaxEnergy();
		}
		else if (type == ModPlayerPowers.VAMPIRISM.get()) {
			VampirismData data = power.getCurTypeData(ModPlayerPowers.VAMPIRISM).orElse(null);
			if (data == null) return -1;
			energy = data.getBloodLevel();
			maxEnergy = data.getMaxBlood(living);
		}
		else if (type == ModPlayerPowers.ZOMBIE.get()) {
			ZombieData data = power.getCurTypeData(ModPlayerPowers.ZOMBIE).orElse(null);
			if (data == null) return -1;
			energy = data.getEnergy();
			maxEnergy = data.getMaxEnergy(living);
		}
		else if (type == ModPlayerPowers.PILLAR_MAN.get()) {
			PillarmanData data = power.getCurTypeData(ModPlayerPowers.PILLAR_MAN).orElse(null);
			if (data == null) return -1;
			energy = data.getEnergy();
			maxEnergy = data.getMaxEnergy(living);
		}
		else {
			return -1;
		}
		// 1.16 NonStandPower.getMaxEnergy never went below 1
		return energy / Math.max(maxEnergy, 1);
	}
}
