package com.github.standobyte.jojoimpl.stands.hierophant;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.github.standobyte.jojo.init.ModSoundEvents;
import com.github.standobyte.jojo.powersystem.standpower.StandPower;
import com.github.standobyte.jojo.powersystem.standpower.StandUtil;
import com.github.standobyte.jojo.powersystem.standpower.entity.StandStatFormulas;
import com.github.standobyte.jojoimpl.stands._entitybase.StandAbilityStamina;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public class HGBarriersNet {
	private static final double SHOOTING_POINTS_GAP = 8;

	private final Map<HGBarrierEntity, ShootingPoints> placedBarriers = new HashMap<>();
	private double lastShotGap = -1;
	private List<Vec3> lastShotPoints = List.of();
	private boolean canShoot;

	public void tick() {
		Iterator<Map.Entry<HGBarrierEntity, ShootingPoints>> iter = placedBarriers.entrySet().iterator();
		while (iter.hasNext()) {
			HGBarrierEntity barrier = iter.next().getKey();
			if (!barrier.isAlive() || barrier.wasRipped()) {
				iter.remove();
				onUpdate();
			}
		}
		canShoot = true;
	}

	public void add(HGBarrierEntity barrier) {
		placedBarriers.put(barrier, generateShootingPoints(barrier));
		onUpdate();
	}

	private ShootingPoints generateShootingPoints(HGBarrierEntity entity) {
		Vec3 posA = entity.position();
		Vec3 posB = entity.getOriginPoint(1.0F);
		Vec3 vecAToB = posB.subtract(posA);

		List<Vec3> shootingPoints = new ArrayList<>();
		if (vecAToB.lengthSqr() <= SHOOTING_POINTS_GAP * SHOOTING_POINTS_GAP * 4) {
			shootingPoints.add(posA.add(vecAToB.scale(0.5)));
		}
		else {
			int steps = Mth.floor(vecAToB.length() / SHOOTING_POINTS_GAP);
			Vec3 nextPoint = posA;
			Vec3 stepVec = vecAToB.normalize().scale(SHOOTING_POINTS_GAP);
			for (int i = 0; i < steps; i++) {
				nextPoint = nextPoint.add(stepVec);
				shootingPoints.add(nextPoint);
			}
		}

		return new ShootingPoints(shootingPoints);
	}

	private void onUpdate() {
		lastShotGap = -1;
		lastShotPoints = List.of();
	}

	public int getSize() {
		return placedBarriers.size();
	}

	public void shootEmeraldsFromBarriers(StandPower standPower, HierophantGreenEntity stand,
			Vec3 targetPos, int tick, double maxEmeralds, float staminaPerEmerald, double minGap, boolean breakBlocks) {
		if (!canShoot || standPower == null) {
			return;
		}
		List<Vec3> shootingPoints = placedBarriers.values().stream()
				.flatMap(points -> points.shootingPoints.stream())
				.collect(Collectors.toCollection(LinkedList::new));
		if (shootingPoints.isEmpty()) {
			return;
		}
		if (lastShotGap != minGap) {
			lastShotPoints = List.copyOf(shootingPoints);
			lastShotGap = minGap;
		}

		Set<Vec3> pointsToShootThisTick = new HashSet<>();
		runFractionalTimes(() -> {
			if (shootingPoints.isEmpty()) {
				return;
			}
			Vec3 point = shootingPoints.stream()
					.min(Comparator.comparingDouble(p -> p.distanceToSqr(targetPos)))
					.orElse(null);
			if (point == null) {
				return;
			}
			pointsToShootThisTick.add(point);
			shootingPoints.removeIf(p -> p.distanceToSqr(point) < minGap * minGap);
		}, maxEmeralds, () -> shootingPoints.isEmpty());

		for (Vec3 point : pointsToShootThisTick) {
			if (!standPower.consumeStamina(StandAbilityStamina.effectiveCost(standPower, staminaPerEmerald))) {
				break;
			}
			runFractionalTimes(() -> shootEmerald(stand, point, targetPos, tick == 0, breakBlocks),
					projectileFireRateScaling(stand, standPower), () -> false);
		}
		canShoot = false;
	}

	private static void runFractionalTimes(Runnable action, double times, BooleanSupplier stop) {
		int wholeTimes = Mth.floor(times);
		for (int i = 0; i < wholeTimes && !stop.getAsBoolean(); i++) {
			action.run();
		}
		double fraction = times - wholeTimes;
		if (fraction > 0 && !stop.getAsBoolean() && Math.random() < fraction) {
			action.run();
		}
	}

	private static double projectileFireRateScaling(HierophantGreenEntity stand, StandPower standPower) {
		double baseSpeed = standPower.getPowerType() != null ? standPower.getPowerType().getStandStats().speed() : 0;
		return baseSpeed > 0 ? stand.getAttackSpeed() / baseSpeed : 1;
	}

	private void shootEmerald(HierophantGreenEntity stand, Vec3 shootingPos, Vec3 targetPos, boolean playSound, boolean breakBlocks) {
		if (!stand.level().isClientSide()) {
			HGEmeraldEntity emerald = new HGEmeraldEntity(stand, stand.level());
			emerald.withStandSkin(stand.getStandType(), stand.getStandSkin());
			emerald.setPos(shootingPos.x, shootingPos.y, shootingPos.z);
			emerald.setBreakBlocks(breakBlocks);
			emerald.setLowerKnockback(true);
			Vec3 shootVec = targetPos.subtract(shootingPos);
			emerald.shoot(shootVec.x, shootVec.y, shootVec.z, 1.5F,
					StandStatFormulas.projectileInaccuracyScaling(stand.getPrecision(), 2.0F));
			emerald.setDamageFactor(0.75F * (float) stand.getAttackDamage() / 8F);
			emerald.setSpeedFactor(emerald.getSpeedFactor() * stand.getAttackSpeed() / 8D);
			stand.level().addFreshEntity(emerald);
			if (playSound) {
				StandUtil.broadcastSound((ServerLevel) stand.level(), shootingPos,
						ModSoundEvents.HIEROPHANT_GREEN_EMERALD_SPLASH, true, stand.getUserPower(),
						stand.getSoundSource(), 1.0F, 1.0F);
			}
		}
	}

	public Stream<Vec3> wasRippedAt() {
		return placedBarriers.keySet().stream()
				.flatMap(barrier -> barrier.wasRippedAt().map(point -> Stream.of(point)).orElse(Stream.empty()));
	}

	public void setStandSkin(Optional<ResourceLocation> standSkin) {
		placedBarriers.keySet().forEach(barrier -> barrier.withStandSkin(standSkin));
	}

	public void discardAll() {
		placedBarriers.keySet().forEach(HGBarrierEntity::discard);
		placedBarriers.clear();
		onUpdate();
	}

	@FunctionalInterface
	private interface BooleanSupplier {
		boolean getAsBoolean();
	}

	private record ShootingPoints(List<Vec3> shootingPoints) {}
}
