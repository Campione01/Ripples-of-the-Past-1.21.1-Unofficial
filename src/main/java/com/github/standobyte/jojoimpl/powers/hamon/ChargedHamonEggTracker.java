package com.github.standobyte.jojoimpl.powers.hamon;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.Optional;
import java.util.Queue;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ThrownEgg;

public class ChargedHamonEggTracker {
	private final Queue<ThrownEgg> chargedEggs = new LinkedList<>();

	public void addChargedEggEntity(ThrownEgg egg) {
		tick();
		chargedEggs.add(egg);
	}

	public Optional<ThrownEgg> eggChargingChicken(Entity chicken) {
		if (chargedEggs.isEmpty()) {
			return Optional.empty();
		}
		return chargedEggs.stream()
				.filter(egg -> egg.getBoundingBox().intersects(chicken.getBoundingBox()))
				.findFirst();
	}

	public void tick() {
		Iterator<ThrownEgg> iterator = chargedEggs.iterator();
		while (iterator.hasNext()) {
			ThrownEgg egg = iterator.next();
			if (!egg.isAlive()) {
				iterator.remove();
			}
		}
	}
}
