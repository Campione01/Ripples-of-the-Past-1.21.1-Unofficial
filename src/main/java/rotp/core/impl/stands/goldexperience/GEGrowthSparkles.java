package rotp.core.impl.stands.goldexperience;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import rotp.core.core.JojoMod;
import rotp.core.init.ModDataAttachmentTypes;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.animal.Animal;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/*
 * 1.16 GoldExperienceBoneMeal.standPerform ran ageUp on every client tracking the Stand: AgeableMob.ageUp set forcedAgeTimer
 * to 40 when it was 0, and the client's AgeableMob.aiStep spawned a HAPPY_VILLAGER every 4 ticks while it counted down.
 * forcedAgeTimer is not synced, so the server counts down for the animal and sends each sparkle to the animal's viewers.
 */
@EventBusSubscriber(modid = JojoMod.MOD_ID)
public class GEGrowthSparkles {
	private static final int SPARKLE_TICKS = 40;
	private static final int SPARKLE_INTERVAL = 4;

	private final ServerLevel level;
	private final Map<Animal, Integer> timers = new LinkedHashMap<>();

	public GEGrowthSparkles(ServerLevel level) {
		this.level = level;
	}

	public static void start(Animal animal) {
		if (animal.level() instanceof ServerLevel level) {
			level.getData(ModDataAttachmentTypes.GE_GROWTH_SPARKLES.get()).timers.putIfAbsent(animal, SPARKLE_TICKS);
		}
	}

	private void tick() {
		Iterator<Map.Entry<Animal, Integer>> iter = timers.entrySet().iterator();
		while (iter.hasNext()) {
			Map.Entry<Animal, Integer> entry = iter.next();
			Animal animal = entry.getKey();
			if (!animal.isAlive() || animal.level() != level) {
				iter.remove();
				continue;
			}
			int timer = entry.getValue();
			if (timer % SPARKLE_INTERVAL == 0) {
				// AgeableMob.aiStep: getRandomX(1.0), getRandomY() + 0.5, getRandomZ(1.0), no motion; a count of 0 keeps
				// the position exact
				RandomSource random = level.getRandom();
				double width = animal.getBbWidth();
				level.sendParticles(ParticleTypes.HAPPY_VILLAGER,
						animal.getX() + (2.0D * random.nextDouble() - 1.0D) * width,
						animal.getY() + random.nextDouble() * animal.getBbHeight() + 0.5D,
						animal.getZ() + (2.0D * random.nextDouble() - 1.0D) * width,
						0, 0.0D, 0.0D, 0.0D, 0.0D);
			}
			if (--timer > 0) {
				entry.setValue(timer);
			}
			else {
				iter.remove();
			}
		}
	}

	@SubscribeEvent
	public static void onLevelTick(LevelTickEvent.Post event) {
		if (event.getLevel() instanceof ServerLevel level) {
			var attachmentType = ModDataAttachmentTypes.GE_GROWTH_SPARKLES.get();
			if (level.hasData(attachmentType)) {
				level.getData(attachmentType).tick();
			}
		}
	}
}
