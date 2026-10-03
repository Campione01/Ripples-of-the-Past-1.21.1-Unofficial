package rotp.core.powersystem.standpower.entity;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import rotp.core.PacketsRegister;
import rotp.core.network.s2c.KnockbackResTickPacket;

import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.neoforged.neoforge.network.connection.ConnectionType;

/**
 * 1.16 NoKnockbackOnBlocking.cancelHurtSound: a hit the guard blocked leaves the user no hurt sound, on the server
 * (LivingEntityMixin.playHurtSound) and on the user's own client, which KnockbackResTickPacket tells (LivingEntityClMixin).
 * 1.16 resolveOnHurtEvent (HIGHEST) counted the hit before blockDamage (HIGH) cut it. The sounds and the Resolve run
 * in StandGuardHurtSoundGameTests; the wiring is pinned here.
 */
public final class StandGuardHurtSoundSmokeTest {
	private static final String NO_KNOCKBACK =
			"src/main/java/rotp/core/powersystem/standpower/entity/NoKnockbackOnBlocking.java";
	private static final String MIXIN = "src/main/java/rotp/core/mixin/damage/LivingEntityMixin.java";
	private static final String PACKETS = "src/main/java/rotp/core/PacketsRegister.java";
	private static final String PACKET = "src/main/java/rotp/core/network/s2c/KnockbackResTickPacket.java";
	private static final String USER_GUARD =
			"src/main/java/rotp/core/powersystem/standpower/entity/StandUserGuard.java";
	private static final String RESOLVE = "src/main/java/rotp/core/mechanics/resolve/ResolveCounter.java";

	private StandGuardHurtSoundSmokeTest() {}

	public static void run() {
		verifyHurtSoundCancel();
		verifyClientPacket();
		verifyResolveBeforeTheCut();
	}

	private static void verifyHurtSoundCancel() {
		String noKnockback = compact(source(NO_KNOCKBACK));
		requireInOrder(noKnockback,
				"publicstaticvoidsetOneTickKbRes(LivingEntityentity){if(entity.level().isClientSide()){return;}"
						+ "if(addModifier(entity)){PENDING.add(entity);}"
						+ "PacketDistributor.sendToPlayersTrackingEntityAndSelf(entity,newKnockbackResTickPacket(entity.getId()));",
				"publicstaticvoidsetOneTickKbResClient(LivingEntityentity){"
						+ "if(entity.level().isClientSide()&&addModifier(entity)){PENDING_CLIENT.add(entity);}}",
				"publicstaticbooleancancelHurtSound(LivingEntityentity){"
						+ "return!(entityinstanceofStandEntity)&&hasOneTickKbRes(entity);}",
				"Set<LivingEntity>pending=entity.level().isClientSide()?PENDING_CLIENT:PENDING;");

		String mixin = compact(source(MIXIN));
		requireInOrder(mixin,
				"@Inject(method=\"playHurtSound\",at=@At(\"HEAD\"),cancellable=true)",
				"elseif(NoKnockbackOnBlocking.cancelHurtSound((LivingEntity)(Entity)this)){ci.cancel();}",
				"@WrapOperation(method=\"handleDamageEvent\",at=@At(value=\"INVOKE\",target=\"Lnet/minecraft/world/entity/"
						+ "LivingEntity;getHurtSound(Lnet/minecraft/world/damagesource/DamageSource;)\"+\"Lnet/minecraft/sounds/SoundEvent;\"))",
				"if(NoKnockbackOnBlocking.cancelHurtSound(entity)){returnnull;}returnoriginal.call(entity,source);");
	}

	private static void verifyClientPacket() {
		check(Integer.parseInt(PacketsRegister.NETWORK_PROTOCOL_VERSION) >= 7,
				"the clientbound knockbackrestick payload needs core protocol v7 or later");
		requireInOrder(compact(source(PACKETS)),
				"PayloadRegistrarregistrar=event.registrar(NETWORK_PROTOCOL_VERSION);",
				"registerPacket(registrar,PayloadRegistrar::playToClient,newKnockbackResTickPacket.Handler("
						+ "JojoMod.resLoc(\"knockbackrestick\")));");
		requireInOrder(compact(source(PACKET)),
				"if(entityinstanceofLivingEntityliving){NoKnockbackOnBlocking.setOneTickKbResClient(living);}");

		RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY, ConnectionType.NEOFORGE);
		try {
			KnockbackResTickPacket.Handler.STREAM_CODEC.encode(buf, new KnockbackResTickPacket(1234567));
			KnockbackResTickPacket decoded = KnockbackResTickPacket.Handler.STREAM_CODEC.decode(buf);
			check(decoded.entityId() == 1234567 && buf.readableBytes() == 0,
					"knockbackrestick must carry the entity id and nothing else");
		}
		finally {
			buf.release();
		}
	}

	/*
	 * 1.16 counted the LivingHurtEvent amount (past a shield; in the hurt cooldown only the excess over lastHurt) plus
	 * the guard's later cut. The port counts at the ARMOR reduction, where getNewDamage() is that amount, and adds back
	 * what the guard took in either path.
	 */
	private static void verifyResolveBeforeTheCut() {
		String guard = compact(source(USER_GUARD));
		requireInOrder(guard,
				// hurt cooldown: the cut of the excess over lastHurt
				"event.addReductionModifier(DamageContainer.Reduction.INVULNERABILITY,(container,lastHurt)->{"
						+ "floatamount=container.getNewDamage();floatexcess=Math.max(amount-lastHurt,0);"
						+ "floatleft=cut.apply(excess);",
				"if(excess-left>0){GUARD_CUTS.merge(container,excess-left,Float::sum);}returnamount-left;",
				// normal hit: the cut the event amount shows
				"floatremoved=amount-reduced;if(removed>0){event.setAmount(reduced);"
						+ "GUARD_CUTS.merge(event.getContainer(),removed,Float::sum);}",
				"publicstaticfloatguardCut(DamageContainercontainer){if(GUARD_CUTS.isEmpty()){return0;}"
						+ "Floatcut=GUARD_CUTS.get(container);returncut!=null?cut:0;}",
				"if(!GUARD_CUTS.isEmpty()){GUARD_CUTS.clear();}");
		int merges = occurrences(guard, "GUARD_CUTS.merge(");
		check(merges == 2, "the guard cut must be recorded once in the hurt-cooldown path and once in the normal path, found "
				+ merges + " GUARD_CUTS.merge calls");

		String resolve = compact(source(RESOLVE));
		requireInOrder(resolve,
				"@SubscribeEvent(priority=EventPriority.LOW)publicstaticvoidonAttack(LivingIncomingDamageEventevent){"
						+ "LivingEntitytarget=event.getEntity();DamageSourcedmgSource=event.getSource();"
						+ "if(target.is(dmgSource.getEntity())||!target.isAlive())return;"
						+ "event.addReductionModifier(DamageContainer.Reduction.ARMOR,(container,reduction)->{"
						+ "countHit(target,dmgSource,container.getNewDamage()+StandUserGuard.guardCut(container));"
						+ "returnreduction;});}",
				"privatestaticvoidcountHit(LivingEntitytarget,DamageSourcedmgSource,floatdmgAmount){"
						+ "floatpoints=dmgAmount;");
		int counts = occurrences(resolve, "countHit(");
		check(counts == 2, "Resolve must be counted only where the hit lands (one countHit call), found "
				+ (counts - 1) + " calls");
	}

	private static void requireInOrder(String text, String... tokens) {
		int from = 0;
		for (String token : tokens) {
			int at = text.indexOf(token, from);
			if (at < 0) {
				int anywhere = text.indexOf(token);
				check(false, "blocked-hit sound or Resolve source " + (anywhere >= 0
						? "reordered (found at " + anywhere + ", before the previous token ending at " + from + ")"
						: "lost") + ": " + token);
			}
			from = at + token.length();
		}
	}

	private static int occurrences(String text, String token) {
		int count = 0;
		for (int at = text.indexOf(token); at >= 0; at = text.indexOf(token, at + token.length())) {
			count++;
		}
		return count;
	}

	private static String compact(String source) {
		return source
				.replaceAll("(?s)/\\*.*?\\*/", "")
				.replaceAll("//[^\\n]*", "")
				.replaceAll("\\s+", "");
	}

	private static String source(String path) {
		try {
			return Files.readString(Path.of(path));
		}
		catch (IOException exception) {
			throw new AssertionError("Could not read " + path, exception);
		}
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
