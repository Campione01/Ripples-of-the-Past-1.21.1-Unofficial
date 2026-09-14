package rotp.core.api.gravity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.jetbrains.annotations.ApiStatus;

import rotp.core.core.JojoMod;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.subsystems.directional_gravity.DirectionalGravityRuntime;

import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.attachment.AttachmentSyncHandler;
import net.neoforged.neoforge.attachment.IAttachmentHolder;

@ApiStatus.Internal
public final class DirectionalGravityData {
	public static final AttachmentSyncHandler<DirectionalGravityData> SYNC_HANDLER =
			new AttachmentSyncHandler<>() {
		@Override
		public void write(RegistryFriendlyByteBuf buffer, DirectionalGravityData data,
				boolean initialSync) {
			AuthoritativeFrame frame = data.currentFrame();
			buffer.writeVarLong(frame.revision());
			buffer.writeEnum(frame.direction());
			writeVector(buffer, frame.position());
			writeVector(buffer, frame.worldVelocity());
		}

		@Override
		public DirectionalGravityData read(IAttachmentHolder holder, RegistryFriendlyByteBuf buffer,
				DirectionalGravityData previousValue) {
			AuthoritativeFrame frame = readFrame(buffer);
			if (!(holder instanceof LivingEntity living) || !living.level().isClientSide()) {
				throw new IllegalArgumentException("Directional gravity sync requires a client living entity");
			}
			// Keep provider bindings and install the first data object before refreshing its geometry.
			DirectionalGravityData data = previousValue != null ? previousValue
					: living.getData(ModDataAttachmentTypes.DIRECTIONAL_GRAVITY.get());
			Direction previousDirection = data.appliedDirection;
			if (data.acceptAuthoritativeFrame(frame)) {
				Vec3 position = frame.position();
				living.lerpTo(position.x, position.y, position.z,
						living.getYRot(), living.getXRot(), 0);
				living.moveTo(position.x, position.y, position.z);
				living.refreshDimensions();
				living.setDeltaMovement(frame.worldVelocity());
				if (previousDirection != frame.direction()) {
					DirectionalGravityApi.clearPreviousSupport(living);
				}
			}
			return data;
		}
	};

	private final LivingEntity owner;
	private final Map<ResourceLocation, Binding> bindings = new HashMap<>();
	private List<Candidate> snapshot = List.of();
	private Direction appliedDirection = Direction.DOWN;
	private long appliedRevision;
	private boolean hasAuthoritativeFrame;

	public DirectionalGravityData() {
		this(null);
	}

	public DirectionalGravityData(LivingEntity owner) {
		this.owner = owner;
	}

	boolean bind(ResourceLocation sourceId, int priority,
			DirectionalGravitySource source) {
		Binding replacement = new Binding(priority, source);
		Binding previous = bindings.put(sourceId, replacement);
		publishSnapshot();
		return !replacement.sameDefinition(previous);
	}

	Direction bindAndResolve(Entity entity, ResourceLocation sourceId,
			int priority, DirectionalGravitySource source) {
		Binding replacement = new Binding(priority, source);
		Binding previous = bindings.put(sourceId, replacement);
		publishSnapshot();
		try {
			Direction replacementDirection =
					resolveStrict(entity, replacement);
			return resolve(
					entity, replacement, replacementDirection);
		}
		catch (RuntimeException | Error failure) {
			if (bindings.get(sourceId) == replacement) {
				if (previous != null) {
					bindings.put(sourceId, previous);
				}
				else {
					bindings.remove(sourceId);
				}
				publishSnapshot();
			}
			throw failure;
		}
	}

	boolean unbind(ResourceLocation sourceId,
			DirectionalGravitySource source) {
		Binding binding = bindings.get(sourceId);
		if (binding == null || binding.source() != source) {
			return false;
		}
		bindings.remove(sourceId);
		publishSnapshot();
		return true;
	}

	boolean contains(ResourceLocation sourceId,
			DirectionalGravitySource source) {
		Binding binding = bindings.get(sourceId);
		return binding != null && binding.source() == source;
	}

	boolean reactivate(ResourceLocation sourceId,
			DirectionalGravitySource source) {
		Binding binding = bindings.get(sourceId);
		if (binding == null || binding.source() != source) {
			return false;
		}
		binding.reactivate();
		return true;
	}

	Direction appliedDirection() {
		return appliedDirection;
	}

	boolean updateAppliedDirection(Direction direction) {
		if (appliedDirection == direction) {
			return false;
		}
		appliedDirection = direction;
		appliedRevision = Math.incrementExact(appliedRevision);
		return true;
	}

	AuthoritativeFrame currentFrame() {
		LivingEntity entity = Objects.requireNonNull(owner, "Directional gravity attachment owner");
		Vec3 velocity = entity.getDeltaMovement();
		if (DirectionalGravityRuntime.isLocalFrame(entity)
				&& !DirectionalGravityRuntime.isWorldMoveAdapterActive(entity)) {
			velocity = DirectionalGravityTransforms.toWorld(
					DirectionalGravityRuntime.localFrameDirection(entity), velocity);
		}
		// Initial tracking must use the holder's current position, never a cached transition anchor.
		return new AuthoritativeFrame(appliedRevision, appliedDirection, entity.position(), velocity);
	}

	boolean acceptAuthoritativeFrame(AuthoritativeFrame frame) {
		if (hasAuthoritativeFrame && frame.revision() <= appliedRevision) {
			return false;
		}
		appliedRevision = frame.revision();
		appliedDirection = frame.direction();
		hasAuthoritativeFrame = true;
		return true;
	}

	static AuthoritativeFrame readFrame(RegistryFriendlyByteBuf buffer) {
		return new AuthoritativeFrame(buffer.readVarLong(), buffer.readEnum(Direction.class),
				readVector(buffer), readVector(buffer));
	}

	private static void writeVector(RegistryFriendlyByteBuf buffer, Vec3 vector) {
		buffer.writeDouble(vector.x);
		buffer.writeDouble(vector.y);
		buffer.writeDouble(vector.z);
	}

	private static Vec3 readVector(RegistryFriendlyByteBuf buffer) {
		return new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble());
	}

	record AuthoritativeFrame(long revision, Direction direction, Vec3 position, Vec3 worldVelocity) {
		AuthoritativeFrame {
			Objects.requireNonNull(direction);
			Objects.requireNonNull(position);
			Objects.requireNonNull(worldVelocity);
			if (revision < 0 || !finite(position) || !finite(worldVelocity)) {
				throw new IllegalArgumentException("Invalid authoritative gravity frame");
			}
		}

		private static boolean finite(Vec3 vector) {
			return Double.isFinite(vector.x) && Double.isFinite(vector.y) && Double.isFinite(vector.z);
		}
	}

	Direction resolve(Entity entity) {
		return resolve(entity, null, Direction.DOWN);
	}

	private Direction resolve(Entity entity,
			Binding preResolvedBinding,
			Direction preResolvedDirection) {
		ResourceLocation winningId = null;
		Binding winner = null;
		Direction winningDirection = Direction.DOWN;
		for (Candidate candidate : snapshot) {
			ResourceLocation sourceId = candidate.sourceId();
			Binding binding = candidate.binding();
			if (bindings.get(sourceId) != binding
					|| binding.quarantined()) {
				continue;
			}
			Direction direction;
			if (binding == preResolvedBinding) {
				direction = preResolvedDirection;
			}
			else {
				try {
					direction = resolveStrict(entity, binding);
				}
				catch (RuntimeException failure) {
					if (bindings.get(sourceId) == binding
							&& binding.quarantine()) {
						logRuntimeFailure(
								sourceId, entity, binding, failure);
					}
					continue;
				}
			}
			if (bindings.get(sourceId) != binding) {
				continue;
			}
			binding.recordSuccess();
			if (direction == Direction.DOWN) {
				continue;
			}
			if (winner == null
					|| binding.priority() > winner.priority()
					|| binding.priority() == winner.priority()
							&& sourceId.compareTo(winningId) < 0) {
				winningId = sourceId;
				winner = binding;
				winningDirection = direction;
			}
		}
		return winningDirection;
	}

	private void publishSnapshot() {
		snapshot = bindings.entrySet().stream()
				.map(entry -> new Candidate(
						entry.getKey(), entry.getValue()))
				.toList();
	}

	private static Direction resolveStrict(
			Entity entity, Binding binding) {
		return Objects.requireNonNullElse(
				binding.source().gravityDirection(entity),
				Direction.DOWN);
	}

	private static void logRuntimeFailure(
			ResourceLocation sourceId,
			Entity entity,
			Binding binding,
			RuntimeException failure) {
		if (!binding.markFailureLogged()) {
			return;
		}
		JojoMod.getLogger().error(
				"Directional gravity source {} failed for {}; "
						+ "its binding is quarantined until "
						+ "directionChanged or rebind.",
				sourceId,
				entity != null
						? entity.getStringUUID()
						: "<unknown entity>",
				failure);
	}

	private record Candidate(
			ResourceLocation sourceId, Binding binding) {}

	private static final class Binding {
		private final int priority;
		private final DirectionalGravitySource source;
		private boolean quarantined;
		private boolean failureLogged;

		private Binding(int priority,
				DirectionalGravitySource source) {
			this.priority = priority;
			this.source = Objects.requireNonNull(source);
		}

		private int priority() {
			return priority;
		}

		private DirectionalGravitySource source() {
			return source;
		}

		private boolean quarantined() {
			return quarantined;
		}

		private boolean quarantine() {
			if (quarantined) {
				return false;
			}
			quarantined = true;
			return true;
		}

		private void reactivate() {
			quarantined = false;
		}

		private boolean markFailureLogged() {
			if (failureLogged) {
				return false;
			}
			failureLogged = true;
			return true;
		}

		private void recordSuccess() {
			failureLogged = false;
		}

		private boolean sameDefinition(Binding other) {
			return other != null
					&& priority == other.priority
					&& source.equals(other.source);
		}
	}
}
