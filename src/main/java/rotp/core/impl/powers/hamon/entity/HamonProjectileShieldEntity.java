package rotp.core.impl.powers.hamon.entity;

import javax.annotation.Nullable;

import rotp.core.client.sound.HamonSparksLoopSound;
import rotp.core.customobjects.entity_projectile.ModdedProjectileEntity;
import rotp.core.impl.powers.hamon.HamonData;
import rotp.core.impl.powers.hamon.HamonUtil;
import rotp.core.impl.powers.hamon.abilities.HamonProjectileShieldAbility;
import rotp.core.init.ModEntityTypes;
import rotp.core.init.ModParticles;
import rotp.core.util.functions.MathUtil;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.entity.IEntityWithComplexSpawn;

public class HamonProjectileShieldEntity extends Entity implements IEntityWithComplexSpawn {
	private static final int REFRESH_LIFETIME = 10;
	private int ownerId = -1;
	@Nullable private LivingEntity owner;
	private float shieldWidth = 8.0F;
	private float shieldHeight = 4.0F;

	public HamonProjectileShieldEntity(Level level, LivingEntity owner, float width, float height) {
		this(ModEntityTypes.HAMON_PROJECTILE_SHIELD.get(), level);
		setOwner(owner);
		// 1.16 startedHolding: the facing is taken from the user once, when the shield is raised
		setYRot(owner.getYRot());
		setXRot(owner.getXRot());
		this.shieldWidth = width;
		this.shieldHeight = height;
		updateShieldPos();
	}

	public HamonProjectileShieldEntity(EntityType<?> type, Level level) {
		super(type, level);
		setNoGravity(true);
	}

	public void refresh(LivingEntity owner, float width, float height) {
		setOwner(owner);
		this.shieldWidth = width;
		this.shieldHeight = height;
		this.tickCount = 0;
		updateShieldPos();
	}

	private void setOwner(@Nullable LivingEntity owner) {
		this.owner = owner;
		this.ownerId = owner != null ? owner.getId() : -1;
	}

	@Nullable
	public LivingEntity getOwnerEntity() {
		if (owner == null && ownerId >= 0) {
			Entity entity = level().getEntity(ownerId);
			if (entity instanceof LivingEntity living) {
				owner = living;
			}
		}
		return owner;
	}

	public float getShieldWidth() {
		return shieldWidth;
	}

	public float getShieldHeight() {
		return shieldHeight;
	}

	@Override
	public void tick() {
		super.tick();
		LivingEntity owner = getOwnerEntity();
		if (owner == null || !owner.isAlive()) {
			if (!level().isClientSide()) {
				discard();
			}
			return;
		}
		// 1.16 removed the shield as soon as its user stopped holding Projectile Shield
		HamonData hamon = level().isClientSide() ? null : HamonProjectileShieldAbility.getHoldingShieldHamon(owner);
		if (!level().isClientSide() && hamon == null) {
			discard();
			return;
		}
		updateShieldPos();
		if (!level().isClientSide()) {
			deflectProjectiles(owner, hamon);
			if (isAlive() && tickCount > REFRESH_LIFETIME) {
				discard();
			}
		}
		else {
			// 1.16: sparks over the whole plane and a crackle at its center
			for (int i = sparkParticleCount(); i > 0; i--) {
				Vec3 pos = randomPlanePoint(random);
				level().addParticle(ModParticles.HAMON_SPARK.get(), pos.x, pos.y, pos.z, 0.0D, 0.0D, 0.0D);
			}
			HamonSparksLoopSound.playSparkSound(this, position(), 1.0F);
		}
	}

	// 1.16 client tick: (int) (width * height * 0.1) sparks per tick
	public int sparkParticleCount() {
		return (int) (shieldWidth * shieldHeight * 0.1F);
	}

	// 1.16 PlaneRectangle.getUniformRandomPos: a uniform point of the drawn plane, which is centered on position()
	public Vec3 randomPlanePoint(RandomSource rand) {
		Vec3 facing = Vec3.directionFromRotation(getXRot(), getYRot()).normalize();
		Vec3 upAxis = Math.abs(facing.y) > 0.99D ? new Vec3(0.0D, 0.0D, 1.0D) : new Vec3(0.0D, 1.0D, 0.0D);
		Vec3 planeRight = upAxis.cross(facing).normalize();
		Vec3 planeUp = facing.cross(planeRight).normalize();
		return position()
				.add(planeRight.scale((rand.nextDouble() - 0.5D) * shieldWidth))
				.add(planeUp.scale((rand.nextDouble() - 0.5D) * shieldHeight));
	}

	private void updateShieldPos() {
		LivingEntity owner = getOwnerEntity();
		if (owner == null) {
			return;
		}
		// 1.16: follows the user's position but keeps its own facing; the plane center is user mid-height + 2 forward
		Vec3 offset = new Vec3(0.0D, 0.0D, 2.0D)
				.xRot(-getXRot() * MathUtil.DEG_TO_RAD)
				.yRot(-getYRot() * MathUtil.DEG_TO_RAD);
		setPos(owner.getX() + offset.x, owner.getY(0.5D) + offset.y, owner.getZ() + offset.z);
	}

	// 1.16 tick: only projectiles crossing the shield toward its user this tick, and not about to hit a block
	private void deflectProjectiles(LivingEntity owner, HamonData hamon) {
		Vec3 center = position();
		Vec3 normal = Vec3.directionFromRotation(getXRot(), getYRot()).normalize();
		Vec3 upRef = Math.abs(normal.y) > 0.99D ? new Vec3(0.0D, 0.0D, 1.0D) : new Vec3(0.0D, 1.0D, 0.0D);
		Vec3 right = upRef.cross(normal).normalize();
		Vec3 up = normal.cross(right).normalize();
		AABB searchArea = getBoundingBox().inflate(24.0D);
		for (Projectile projectile : level().getEntitiesOfClass(Projectile.class, searchArea, Entity::isAlive)) {
			if (!isAlive()) {
				return;
			}
			Vec3 intersection = crossesShield(projectile, center, normal, right, up);
			if (intersection == null) {
				continue;
			}
			HitResult hit = ProjectileUtil.getHitResultOnMoveVector(projectile, target -> target != this
					&& !target.isSpectator() && target.isAlive() && !target.is(projectile.getOwner()));
			if (hit.getType() != HitResult.Type.BLOCK) {
				deflectProjectile(projectile, intersection, owner, hamon);
			}
		}
	}

	// 1.16 PlaneRectangle.projectileIsPassing: in front of the shield now, behind it after this tick's move, inside its bounds.
	// Returns the point where the projectile passes the plane, or null.
	@Nullable
	private Vec3 crossesShield(Projectile projectile, Vec3 center, Vec3 normal, Vec3 right, Vec3 up) {
		Vec3 motion = projectile.getDeltaMovement();
		Vec3 cur = projectile.position();
		double projCur = cur.subtract(center).dot(normal);
		double projNext = cur.add(motion).subtract(center).dot(normal);
		if (!(projCur > 0.0D && projNext <= 0.0D)) {
			return null;
		}
		Vec3 intersection = cur.add(motion.scale(projCur / (projCur - projNext)));
		Vec3 fromCenter = intersection.subtract(center);
		return Math.abs(fromCenter.dot(right)) <= shieldWidth * 0.5D && Math.abs(fromCenter.dot(up)) <= shieldHeight * 0.5D
				? intersection : null;
	}

	// 1.16 deflectProjectile: speed * 20 energy per projectile and Control points; no energy breaks the shield
	private void deflectProjectile(Projectile projectile, Vec3 intersection, LivingEntity owner, HamonData hamon) {
		if (projectile instanceof ModdedProjectileEntity moddedProjectile && !moddedProjectile.canBeDeflected(this)) {
			return;
		}
		// 1.16: a spark burst and crackle where the projectile hits the plane, with or without energy
		HamonUtil.emitHamonSparkParticles(level(), owner instanceof Player playerOwner ? playerOwner : null, intersection, 5.0F);
		float energyCost = (float) projectile.getDeltaMovement().length() * 20.0F;
		boolean creative = owner instanceof Player player && player.getAbilities().instabuild;
		if (creative || hamon.hasEnergy(energyCost, owner)) {
			// 1.16 JojoModUtil.deflectProjectile: full reverse, then one move
			projectile.setDeltaMovement(projectile.getDeltaMovement().reverse());
			projectile.move(MoverType.SELF, projectile.getDeltaMovement());
			projectile.hurtMarked = true;
			if (projectile instanceof ModdedProjectileEntity moddedProjectile) {
				moddedProjectile.setIsDeflected(projectile.getDeltaMovement(), projectile.position());
			}
		}
		if (creative || hamon.consumeEnergy(energyCost, owner)) {
			hamon.hamonPointsFromAction(HamonData.HamonStat.CONTROL, energyCost);
		}
		else {
			hamon.setEnergy(0.0F);
			discard();
		}
		hamon.syncOnUpdate(owner);
	}

	@Override
	public boolean isPickable() {
		return false;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
	}

	@Override
	protected void readAdditionalSaveData(CompoundTag nbt) {
		shieldWidth = nbt.getFloat("Width");
		shieldHeight = nbt.getFloat("Height");
		ownerId = nbt.getInt("OwnerId");
	}

	@Override
	protected void addAdditionalSaveData(CompoundTag nbt) {
		nbt.putFloat("Width", shieldWidth);
		nbt.putFloat("Height", shieldHeight);
		nbt.putInt("OwnerId", ownerId);
	}

	@Override
	public void writeSpawnData(RegistryFriendlyByteBuf buffer) {
		buffer.writeFloat(shieldWidth);
		buffer.writeFloat(shieldHeight);
		buffer.writeVarInt(ownerId);
	}

	@Override
	public void readSpawnData(RegistryFriendlyByteBuf additionalData) {
		shieldWidth = additionalData.readFloat();
		shieldHeight = additionalData.readFloat();
		ownerId = additionalData.readVarInt();
		owner = null;
	}
}
