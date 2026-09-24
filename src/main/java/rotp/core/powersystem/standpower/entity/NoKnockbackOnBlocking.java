package rotp.core.powersystem.standpower.entity;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

import org.jetbrains.annotations.ApiStatus;

import rotp.core.core.JojoMod;
import rotp.core.network.s2c.KnockbackResTickPacket;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 1.16 NoKnockbackOnBlocking: a hit the guard blocked gives the Stand and its user knockback resistance 1 until
 * their next tick, so neither staggers, and the user makes no hurt sound (cancelHurtSound). The knockback is dealt
 * on the server; the tracking clients get the modifier through KnockbackResTickPacket, as in 1.16, because a player
 * hears their own hurt sound from their own client.
 */
@EventBusSubscriber(modid = JojoMod.MOD_ID)
public final class NoKnockbackOnBlocking {
	public static final ResourceLocation ONE_TICK_KB_RES_ID = JojoMod.resLoc("stand_block_knockback_resistance");
	// the entities whose next tick removes the modifier: one set per side, each used by its own thread only
	private static final Set<LivingEntity> PENDING = Collections.newSetFromMap(new WeakHashMap<>());
	private static final Set<LivingEntity> PENDING_CLIENT = Collections.newSetFromMap(new WeakHashMap<>());

	private NoKnockbackOnBlocking() {}

	public static void setOneTickKbRes(LivingEntity entity) {
		if (entity.level().isClientSide()) {
			return;
		}
		if (addModifier(entity)) {
			PENDING.add(entity);
		}
		PacketDistributor.sendToPlayersTrackingEntityAndSelf(entity, new KnockbackResTickPacket(entity.getId()));
		if (entity instanceof StandEntity stand) {
			LivingEntity user = stand.getUser();
			if (user != null && user != entity) {
				setOneTickKbRes(user);
			}
		}
	}

	/** Client side of 1.16 KnockbackResTickPacket. */
	@ApiStatus.Internal
	public static void setOneTickKbResClient(LivingEntity entity) {
		if (entity.level().isClientSide() && addModifier(entity)) {
			PENDING_CLIENT.add(entity);
		}
	}

	private static boolean addModifier(LivingEntity entity) {
		AttributeInstance kbRes = entity.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
		if (kbRes == null) {
			return false;
		}
		if (!kbRes.hasModifier(ONE_TICK_KB_RES_ID)) {
			kbRes.addTransientModifier(new AttributeModifier(ONE_TICK_KB_RES_ID, 1, AttributeModifier.Operation.ADD_VALUE));
		}
		return true;
	}

	public static boolean hasOneTickKbRes(LivingEntity entity) {
		AttributeInstance kbRes = entity.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
		return kbRes != null && kbRes.hasModifier(ONE_TICK_KB_RES_ID);
	}

	/**
	 * 1.16 cancelHurtSound: an entity whose hit the guard blocked this tick makes no hurt sound, on the server
	 * (playHurtSound) or on its client (handleDamageEvent). A Stand still makes its own.
	 */
	public static boolean cancelHurtSound(LivingEntity entity) {
		return !(entity instanceof StandEntity) && hasOneTickKbRes(entity);
	}

	// 1.16 removed it on the entity's next LivingUpdateEvent, on either side
	@SubscribeEvent
	public static void onEntityTick(EntityTickEvent.Pre event) {
		Entity entity = event.getEntity();
		Set<LivingEntity> pending = entity.level().isClientSide() ? PENDING_CLIENT : PENDING;
		if (pending.isEmpty() || !(entity instanceof LivingEntity living) || !pending.remove(living)) {
			return;
		}
		AttributeInstance kbRes = living.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
		if (kbRes != null) {
			kbRes.removeModifier(ONE_TICK_KB_RES_ID);
		}
	}
}
