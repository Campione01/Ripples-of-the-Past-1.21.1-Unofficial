package com.github.standobyte.jojo.api.control;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import com.github.standobyte.jojo.api.control.ControlledEntityCombatLeases.AcquireResult;
import com.github.standobyte.jojo.api.control.ControlledEntityCombatLeases.AcquireStatus;
import com.github.standobyte.jojo.api.control.ControlledEntityCombatLeases.AttackOrigin;
import com.github.standobyte.jojo.api.control.ControlledEntityCombatLeases.Lease;
import com.github.standobyte.jojo.api.control.ControlledEntityCombatLeases.LeaseEndpoint;
import com.github.standobyte.jojo.api.control.ControlledEntityCombatLeases.OriginKind;
import com.github.standobyte.jojo.api.control.ControlledEntityCombatLeases.OriginAdapter;
import com.github.standobyte.jojo.api.control.ControlledEntityCombatLeases.Owner;
import com.github.standobyte.jojo.api.control.ControlledEntityCombatLeases.ReleaseStatus;

import net.minecraft.resources.ResourceLocation;

public final class ControlledEntityCombatLeasesSmokeTest {
	private ControlledEntityCombatLeasesSmokeTest() {}

	public static void run() {
		ControlledEntityCombatLeases.resetForTests();
		Owner first = ControlledEntityCombatLeases.register(id("first"));
		Owner second = ControlledEntityCombatLeases.register(id("second"));
		expectIllegalState(() ->
				ControlledEntityCombatLeases.register(id("first")));

		UUID subjectId = UUID.randomUUID();
		UUID forbiddenId = UUID.randomUUID();
		UUID issuerId = UUID.randomUUID();
		AtomicBoolean subjectActive = new AtomicBoolean(true);
		AtomicBoolean forbiddenActive = new AtomicBoolean(true);
		AtomicBoolean issuerActive = new AtomicBoolean(true);
		LeaseEndpoint subject = endpoint(subjectId, subjectActive);
		LeaseEndpoint forbidden = endpoint(forbiddenId, forbiddenActive);
		LeaseEndpoint issuer = endpoint(issuerId, issuerActive);

		UUID firstId = UUID.randomUUID();
		AcquireResult acquired = first.acquireForTests(
				subject,
				forbidden,
				issuer,
				firstId,
				AttackOriginScope.SELF_AND_SUMMONED_STAND);
		check(acquired.status() == AcquireStatus.ACQUIRED
						&& acquired.lease() != null,
				"combat lease was not acquired");
		Lease firstLease = acquired.lease();
		AcquireResult duplicate = first.acquireForTests(
				subject,
				forbidden,
				issuer,
				firstId,
				AttackOriginScope.SELF_AND_SUMMONED_STAND);
		check(duplicate.status() == AcquireStatus.ALREADY_ACTIVE
						&& duplicate.lease() == firstLease,
				"identical combat reapplication was not idempotent");
		AcquireResult conflict = first.acquireForTests(
				subject,
				endpoint(UUID.randomUUID(), new AtomicBoolean(true)),
				issuer,
				firstId,
				AttackOriginScope.SELF_AND_SUMMONED_STAND);
		check(conflict.status() == AcquireStatus.CONFLICT
						&& conflict.lease() == null,
				"mismatched combat lease ID was not rejected");

		check(blocks(
						forbiddenId,
						new AttackOrigin(subjectId, OriginKind.SELF)),
				"direct subject attack was not blocked");
		check(blocks(
						forbiddenId,
						new AttackOrigin(
								subjectId,
								OriginKind.SUMMONED_STAND)),
				"summoned Stand attack was not blocked");
		check(!blocks(
						subjectId,
						new AttackOrigin(forbiddenId, OriginKind.SELF)),
				"reverse attack was incorrectly blocked");
		check(!blocks(
						UUID.randomUUID(),
						new AttackOrigin(subjectId, OriginKind.SELF)),
				"unleased target attack was incorrectly blocked");
		assertIndirectAttribution(subjectId, forbiddenId);

		UUID secondId = UUID.randomUUID();
		Lease secondLease = first.acquireForTests(
				subject,
				forbidden,
				issuer,
				secondId,
				AttackOriginScope.SELF_AND_SUMMONED_STAND)
				.lease();
		check(first.release(firstLease) == ReleaseStatus.RELEASED
						&& blocks(
								forbiddenId,
								new AttackOrigin(
										subjectId,
										OriginKind.SELF)),
				"independent same-pair lease was released too early");
		check(second.release(secondLease) == ReleaseStatus.WRONG_OWNER
						&& secondLease.isActive(),
				"foreign owner released a combat token");
		check(first.release(secondLease) == ReleaseStatus.RELEASED
						&& first.release(secondLease)
								== ReleaseStatus.ALREADY_RELEASED
						&& !blocks(
								forbiddenId,
								new AttackOrigin(
										subjectId,
										OriginKind.SELF)),
				"combat release was not idempotent");

		Lease selfOnly = first.acquireForTests(
				subject,
				forbidden,
				issuer,
				UUID.randomUUID(),
				AttackOriginScope.SELF_ONLY).lease();
		check(!blocks(
						forbiddenId,
						new AttackOrigin(
								subjectId,
								OriginKind.SUMMONED_STAND)),
				"SELF_ONLY unexpectedly covered a Stand");
		check(blocks(
						forbiddenId,
						new AttackOrigin(subjectId, OriginKind.SELF)),
				"SELF_ONLY did not cover the subject");
		first.release(selfOnly);

		assertEndpointInvalidation(
				first,
				subjectId,
				forbiddenId,
				issuerId,
				subjectActive,
				forbiddenActive,
				issuerActive);
		assertExplicitLifecycleRelease(
				first, subjectId, forbiddenId, issuerId);
		verifyRuntimeCallPaths();
		ControlledEntityCombatLeases.resetForTests();
	}

	private static void assertEndpointInvalidation(
			Owner owner,
			UUID subjectId,
			UUID forbiddenId,
			UUID issuerId,
			AtomicBoolean subjectActive,
			AtomicBoolean forbiddenActive,
			AtomicBoolean issuerActive) {
		AtomicBoolean[] states = {
				subjectActive, forbiddenActive, issuerActive
		};
		for (int invalid = 0; invalid < states.length; invalid++) {
			for (AtomicBoolean state : states) {
				state.set(true);
			}
			Lease lease = owner.acquireForTests(
					endpoint(subjectId, subjectActive),
					endpoint(forbiddenId, forbiddenActive),
					endpoint(issuerId, issuerActive),
					UUID.randomUUID(),
					AttackOriginScope.SELF_AND_SUMMONED_STAND)
					.lease();
			states[invalid].set(false);
			check(ControlledEntityCombatLeases
							.activeLeaseCountForTests() == 0
							&& !lease.isActive(),
					"subject/forbidden/issuer invalidation leaked a lease");
		}
		for (AtomicBoolean state : states) {
			state.set(true);
		}
	}

	private static void assertIndirectAttribution(
			UUID subjectId, UUID forbiddenId) {
		FakeOriginNode living = new FakeOriginNode(
				false,
				null,
				new AttackOrigin(subjectId, OriginKind.SELF));
		FakeOriginNode projectile = new FakeOriginNode(
				true, living, null);
		AttackOrigin indirect = ControlledEntityCombatLeases
				.resolveAttackOriginsForTests(
						FakeOriginNode.ADAPTER, projectile)
				.getFirst();
		check(indirect.kind() == OriginKind.SELF
						&& blocks(forbiddenId, indirect),
				"projectile true-owner attribution was not blocked");

		FakeOriginNode stand = new FakeOriginNode(
				false,
				null,
				new AttackOrigin(
						subjectId, OriginKind.SUMMONED_STAND));
		FakeOriginNode standProjectile = new FakeOriginNode(
				true, stand, null);
		AttackOrigin indirectStand = ControlledEntityCombatLeases
				.resolveAttackOriginsForTests(
						FakeOriginNode.ADAPTER, standProjectile)
				.getFirst();
		check(indirectStand.kind() == OriginKind.SUMMONED_STAND
						&& blocks(forbiddenId, indirectStand),
				"projectile Stand-owner attribution was not blocked");
	}

	private static void assertExplicitLifecycleRelease(
			Owner owner,
			UUID subjectId,
			UUID forbiddenId,
			UUID issuerId) {
		Lease lease = owner.acquireForTests(
				endpoint(subjectId, new AtomicBoolean(true)),
				endpoint(forbiddenId, new AtomicBoolean(true)),
				endpoint(issuerId, new AtomicBoolean(true)),
				UUID.randomUUID(),
				AttackOriginScope.SELF_AND_SUMMONED_STAND).lease();
		ControlledEntityCombatLeases.releaseInvolvingForTests(issuerId);
		check(!lease.isActive()
						&& ControlledEntityCombatLeases
								.activeLeaseCountForTests() == 0,
				"explicit lifecycle release did not invalidate token");
	}

	private static void verifyRuntimeCallPaths() {
		Path root = Path.of(System.getProperty("user.dir"));
		String source = read(root.resolve(
				"src/main/java/com/github/standobyte/jojo/api/control/"
				+ "ControlledEntityCombatLeases.java"));
		for (String token : new String[] {
				"AttackEntityEvent",
				"LivingIncomingDamageEvent",
				"source.getDirectEntity()",
				"source.getEntity()",
				"instanceof Projectile",
				"projectile.getOwner()",
				"instanceof StandEntity",
				"stand.getUser()",
				"power.getSummonedStandEntity()",
				"== stand",
				"PlayerLoggedOutEvent",
				"PlayerChangedDimensionEvent",
				"EntityLeaveLevelEvent",
				"LivingDeathEvent"
		}) {
			check(source.contains(token),
					"combat runtime path missing: " + token);
		}
	}

	private static LeaseEndpoint endpoint(
			UUID id, AtomicBoolean active) {
		return ControlledEntityCombatLeases.endpointForTests(
				id, active::get);
	}

	private static boolean blocks(
			UUID target, AttackOrigin... origins) {
		return ControlledEntityCombatLeases.shouldBlockForTests(
				target, origins);
	}

	private static ResourceLocation id(String path) {
		return ResourceLocation.fromNamespaceAndPath(
				"rotp_test", path);
	}

	private static String read(Path path) {
		try {
			return Files.readString(path);
		}
		catch (IOException error) {
			throw new AssertionError("failed to read " + path, error);
		}
	}

	private static void expectIllegalState(Runnable action) {
		try {
			action.run();
		}
		catch (IllegalStateException expected) {
			return;
		}
		throw new AssertionError(
				"duplicate combat lease owner was accepted");
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}

	private record FakeOriginNode(
			boolean projectile,
			FakeOriginNode owner,
			AttackOrigin terminal) {
		private static final OriginAdapter<FakeOriginNode> ADAPTER =
				new OriginAdapter<>() {
					@Override
					public boolean isProjectile(FakeOriginNode node) {
						return node.projectile;
					}

					@Override
					public FakeOriginNode projectileOwner(
							FakeOriginNode node) {
						return node.owner;
					}

					@Override
					public AttackOrigin terminalOrigin(
							FakeOriginNode node) {
						return node.terminal;
					}
				};
	}
}
