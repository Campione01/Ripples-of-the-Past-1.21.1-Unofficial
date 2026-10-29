package rotp.addons.kraftwork.entity;

import java.util.Optional;
import java.util.UUID;

import javax.annotation.Nullable;

import rotp.core.init.ModStatusEffects;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.util.functions.DamageUtil;

import rotp.addons.kraftwork.KraftWorkSpec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.BannerPatternItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.PotionItem;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.InfestedBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;

public final class KraftWorkItemProjectile
        extends AbstractArrow {
    private static final double DEFAULT_BASE_DAMAGE = 2.0D / 6.0D;
    private static final String CONTROLLER_TAG = "Controller";
    private static final String ITEM_TAG = "Item";
    private static final String REMOVE_TAG = "Remove";
    private static final String REMOVE_AFTER_TAG =
            "RemoveAfterHitting";
    private static final String BENEFICIAL_TAG = "Beneficial";
    private static final String RELEASED_ENERGY_TAG =
            "ReleasedEnergy";
    private static final String FOOD_DAMAGE_VERSION_TAG = "FoodDamageVersion";
    private static final String ITEM_DAMAGE_VERSION_TAG = "ItemDamageVersion";
    private static final String WEAPON_DAMAGE_VERSION_TAG = "WeaponDamageVersion";

    private static final EntityDataAccessor<Optional<UUID>>
            CONTROLLER_UUID = SynchedEntityData.defineId(
                    KraftWorkItemProjectile.class,
                    EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Integer>
            CONTROLLER_ENTITY_ID = SynchedEntityData.defineId(
                    KraftWorkItemProjectile.class,
                    EntityDataSerializers.INT);
    private static final EntityDataAccessor<ItemStack> ITEM =
            SynchedEntityData.defineId(
                    KraftWorkItemProjectile.class,
                    EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<Boolean>
            REMOVE_ON_ENTITY_HIT = SynchedEntityData.defineId(
                    KraftWorkItemProjectile.class,
                    EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean>
            REMOVE_AFTER_HIT = SynchedEntityData.defineId(
                    KraftWorkItemProjectile.class,
                    EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> BENEFICIAL =
            SynchedEntityData.defineId(
                    KraftWorkItemProjectile.class,
                    EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer>
            RELEASED_ENERGY = SynchedEntityData.defineId(
                    KraftWorkItemProjectile.class,
                    EntityDataSerializers.INT);

    public KraftWorkItemProjectile(
            EntityType<? extends KraftWorkItemProjectile> type,
            Level level) {
        super(type, level);
        pickup = Pickup.DISALLOWED;
        setBaseDamage(DEFAULT_BASE_DAMAGE);
    }

    public void initialize(
            LivingEntity owner,
            @Nullable StandEntity controller,
            ItemStack item) {
        ItemStack projectileItem = item.copyWithCount(1);
        setOwner(owner);
        setItem(projectileItem);
        if (usesWeaponDamage(projectileItem)) {
            setBaseDamage(weaponBaseDamage(projectileItem));
        } else if (usesResourceDamage(projectileItem)) {
            setBaseDamage(DEFAULT_BASE_DAMAGE
                    * kineticDamageMultiplier());
        }
        if (controller != null) {
            entityData.set(
                    CONTROLLER_UUID,
                    Optional.of(controller.getUUID()));
            entityData.set(
                    CONTROLLER_ENTITY_ID,
                    controller.getId());
        }
        boolean recoverable = isRecoverable(projectileItem);
        entityData.set(REMOVE_ON_ENTITY_HIT, !recoverable);
        entityData.set(
                REMOVE_AFTER_HIT,
                isConsumedOnImpact(projectileItem));
        entityData.set(
                BENEFICIAL,
                projectileItem.is(Items.TOTEM_OF_UNDYING));
        pickup = recoverable
                ? Pickup.ALLOWED
                : Pickup.DISALLOWED;
    }

    public void setItem(ItemStack item) {
        ItemStack projectileItem = item.copyWithCount(1);
        entityData.set(ITEM, projectileItem);
        setPickupItemStack(projectileItem.copy());
    }

    public ItemStack getItem() {
        return entityData.get(ITEM);
    }

    public boolean willBeRemovedOnEntityHit() {
        return entityData.get(REMOVE_ON_ENTITY_HIT);
    }

    public boolean removeAfterHitting() {
        return entityData.get(REMOVE_AFTER_HIT);
    }

    public boolean isBeneficial() {
        return entityData.get(BENEFICIAL);
    }

    public void setReleasedEnergy(int energy) {
        entityData.set(RELEASED_ENERGY, Math.max(0, energy));
    }

    public int releasedEnergy() {
        return entityData.get(RELEASED_ENERGY);
    }

    public double kineticDamageMultiplier() {
        ItemStack item = getItem();
        if (item.isEmpty()) {
            return 0.0D;
        }
        if (item.has(DataComponents.FOOD)) {
            return foodDamageMultiplier(item);
        }
        if (item.getItem() instanceof PotionItem
                || item.getItem() instanceof SpawnEggItem) {
            return 0.0D;
        }
        if (isHalfDamageResource(item)) {
            return 0.5D;
        }
        if (!(item.getItem() instanceof BlockItem)
                && (isWeightlessResource(item)
                        || item.getItem() instanceof BannerPatternItem)) {
            return 0.0D;
        }
        return 1.0D;
    }

    private static boolean usesResourceDamage(ItemStack item) {
        return !(item.getItem() instanceof TieredItem)
                && !(item.getItem() instanceof ArmorItem);
    }

    private static boolean usesWeaponDamage(ItemStack item) {
        return item.getItem() instanceof SwordItem
                || item.getItem() instanceof DiggerItem
                        && !(item.getItem() instanceof HoeItem);
    }

    private static double weaponBaseDamage(ItemStack item) {
        ItemAttributeModifiers modifiers = item.getItem().getDefaultInstance()
                .getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS,
                        ItemAttributeModifiers.EMPTY);
        double damage = 0.0D;
        boolean found = false;
        for (ItemAttributeModifiers.Entry entry : modifiers.modifiers()) {
            if (entry.attribute().equals(Attributes.ATTACK_DAMAGE)
                    && entry.slot().test(EquipmentSlot.MAINHAND)
                    && entry.modifier().operation() == AttributeModifier.Operation.ADD_VALUE) {
                damage += entry.modifier().amount();
                found = true;
            }
        }
        if (!found) {
            return DEFAULT_BASE_DAMAGE;
        }
        if (item.getItem() instanceof SwordItem
                || item.getItem() instanceof AxeItem) {
            damage -= 1.0D;
        }
        return Math.max(0.0D, damage) / 6.0D;
    }

    private static boolean isHalfDamageResource(ItemStack item) {
        return item.is(Items.GLISTERING_MELON_SLICE)
                || item.is(Items.BOOK)
                || item.is(Items.WRITABLE_BOOK)
                || item.is(Items.WRITTEN_BOOK)
                || item.is(Items.ENCHANTED_BOOK)
                || item.is(Items.KNOWLEDGE_BOOK)
                || item.is(Items.SHULKER_SHELL)
                || item.is(Items.NAUTILUS_SHELL)
                || item.is(Items.TURTLE_SCUTE)
                || item.is(Items.TURTLE_HELMET)
                || item.is(Items.OAK_SAPLING)
                || item.is(Items.SPRUCE_SAPLING)
                || item.is(Items.BIRCH_SAPLING)
                || item.is(Items.JUNGLE_SAPLING)
                || item.is(Items.ACACIA_SAPLING)
                || item.is(Items.DARK_OAK_SAPLING)
                || item.is(Items.DRAGON_EGG);
    }

    private static boolean isWeightlessResource(ItemStack item) {
        return item.is(Items.STRING)
                || item.is(Items.FEATHER)
                || item.is(Items.WHEAT)
                || item.is(Items.LEATHER)
                || item.is(Items.CLAY_BALL)
                || item.is(Items.PAPER)
                || item.is(Items.SLIME_BALL)
                || item.is(Items.MAP)
                || item.is(Items.RABBIT_HIDE)
                || item.is(Items.RABBIT_FOOT)
                || item.is(Items.INK_SAC)
                || item.is(Items.FIREWORK_STAR)
                || item.is(Items.PHANTOM_MEMBRANE)
                || item.is(Items.GHAST_TEAR)
                || item.is(Items.GLASS_BOTTLE)
                || item.is(Items.MAGMA_CREAM);
    }

    private static double foodDamageMultiplier(ItemStack item) {
        if (item.is(Items.APPLE)
                || item.is(Items.GOLDEN_APPLE)
                || item.is(Items.ENCHANTED_GOLDEN_APPLE)
                || item.is(Items.MELON_SLICE)
                || item.is(Items.GOLDEN_CARROT)
                || item.is(Items.POTATO)
                || item.is(Items.BAKED_POTATO)
                || item.is(Items.POISONOUS_POTATO)) {
            return 0.5D;
        }
        if (item.is(Items.MUSHROOM_STEW)
                || item.is(Items.RABBIT_STEW)
                || item.is(Items.BEETROOT_SOUP)
                || item.is(Items.SUSPICIOUS_STEW)) {
            return 1.0D;
        }
        return 0.0D;
    }

    // placed with Kraft Work not out, as 1.16 placed every item
    public boolean hasController() {
        return entityData.get(CONTROLLER_UUID).isPresent();
    }

    @Nullable
    public StandEntity resolveController() {
        int entityId = entityData.get(CONTROLLER_ENTITY_ID);
        Entity byId = entityId >= 0
                ? level().getEntity(entityId)
                : null;
        if (byId instanceof StandEntity stand) {
            return stand;
        }
        UUID uuid = entityData.get(CONTROLLER_UUID).orElse(null);
        if (uuid != null
                && level() instanceof ServerLevel serverLevel) {
            Entity byUuid = serverLevel.getEntity(uuid);
            if (byUuid instanceof StandEntity stand) {
                entityData.set(
                        CONTROLLER_ENTITY_ID, stand.getId());
                return stand;
            }
        }
        return null;
    }

    @Override
    protected ItemStack getDefaultPickupItem() {
        return getItem().copyWithCount(1);
    }

    @Override
    protected double getDefaultGravity() {
        return 0.05D;
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        if (level().isClientSide()) {
            return;
        }
        Entity target = result.getEntity();
        double speed = getDeltaMovement().length();
        float damage = (float) Math.ceil(
                speed * Math.max(0.0D, getBaseDamage()));
        if (damage > 0.0F) {
            int previousFireTicks = target.getRemainingFireTicks();
            boolean fireCarrier = applyItemHitIgnition(target);
            if (target.hurt(
                    damageSources().thrown(this, getOwner()),
                    damage)) {
                applyItemHitEffects(target);
            }
            else if (fireCarrier) {
                target.setRemainingFireTicks(previousFireTicks);
            }
        }
        if (removeAfterHitting()) {
            handleImpact(target.blockPosition(), null);
            discard();
        }
        else if (willBeRemovedOnEntityHit()) {
            discard();
        }
        else {
            setDeltaMovement(
                    getDeltaMovement().scale(0.75D));
        }
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        if (!level().isClientSide()) {
            handleImpact(result.getBlockPos(), result);
            if (removeAfterHitting()) {
                discard();
                return;
            }
        }
        super.onHitBlock(result);
    }

    private boolean applyItemHitIgnition(Entity target) {
        ItemStack stack = getItem();
        if (stack.is(Items.FIRE_CHARGE)
                || stack.is(Items.LAVA_BUCKET)
                || stack.is(Items.MAGMA_CREAM)
                || stack.is(Items.BLAZE_POWDER)) {
            if (target instanceof StandEntity) {
                DamageUtil.setOnFire(target, 80, true);
            }
            else {
                target.setRemainingFireTicks(
                        Math.max(target.getRemainingFireTicks(), 80));
            }
            return true;
        }
        int fireAspect = stack.getEnchantmentLevel(
                registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                        .getOrThrow(Enchantments.FIRE_ASPECT));
        if (stack.is(Items.TORCH)
                || stack.is(Items.SOUL_TORCH)
                || stack.is(Items.CAMPFIRE)
                || stack.is(Items.SOUL_CAMPFIRE)
                || stack.is(Items.MAGMA_BLOCK)
                || fireAspect > 0) {
            DamageUtil.setOnFire(
                    target, 80 * Math.max(1, fireAspect), true);
            return true;
        }
        return false;
    }

    private void applyItemHitEffects(Entity target) {
        ItemStack stack = getItem();
        if (stack.is(Items.BELL)) {
            target.playSound(
                    SoundEvents.NOTE_BLOCK_BELL.value(),
                    5.0F,
                    0.5F + random.nextFloat() * 1.5F);
        }
        if (target instanceof LivingEntity living
                && releasedEnergy() >= 100
                && isGlass(stack)
                && random.nextFloat() < 0.25F) {
            living.addEffect(new MobEffectInstance(
                    ModStatusEffects.BLEEDING,
                    100,
                    0,
                    false,
                    false,
                    true));
        }
    }

    private void handleImpact(
            BlockPos pos,
            @Nullable BlockHitResult hit) {
        ItemStack stack = getItem();
        if (stack.is(Items.TNT)) {
            level().explode(
                    this,
                    getX(),
                    getY(),
                    getZ(),
                    6.0F,
                    Level.ExplosionInteraction.TNT);
        }
        else if (stack.getItem() instanceof SpawnEggItem egg
                && level() instanceof ServerLevel serverLevel) {
            egg.getType(stack).spawn(
                    serverLevel,
                    stack,
                    null,
                    pos,
                    MobSpawnType.SPAWN_EGG,
                    false,
                    false);
        }
        else if (stack.getItem() instanceof BucketItem bucket
                && hit != null) {
            bucket.emptyContents(
                    null, level(), pos, hit, stack);
            spawnAtLocation(Items.BUCKET);
        }
        else if (stack.getItem() instanceof BlockItem blockItem
                && blockItem.getBlock() instanceof InfestedBlock
                && level() instanceof ServerLevel serverLevel) {
            EntityType.SILVERFISH.spawn(
                    serverLevel,
                    pos.above(),
                    MobSpawnType.TRIGGERED);
        }
    }

    private static boolean isRecoverable(ItemStack stack) {
        return stack.isDamageableItem()
                || stack.is(Items.TRIDENT)
                || stack.is(Items.BOW)
                || stack.is(Items.CROSSBOW)
                || stack.is(Items.SHIELD)
                || stack.is(Items.TOTEM_OF_UNDYING);
    }

    private static boolean isConsumedOnImpact(ItemStack stack) {
        return stack.has(DataComponents.FOOD)
                || stack.getItem() instanceof PotionItem
                || stack.getItem() instanceof BucketItem
                || stack.getItem() instanceof SpawnEggItem
                || stack.is(Items.TNT);
    }

    private static boolean isGlass(ItemStack stack) {
        return stack.is(Items.GLASS)
                || stack.is(Items.GLASS_PANE)
                || stack.is(Items.TINTED_GLASS)
                || stack.is(Items.WHITE_STAINED_GLASS)
                || stack.is(Items.BLACK_STAINED_GLASS);
    }

    @Override
    protected void defineSynchedData(
            SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(CONTROLLER_UUID, Optional.empty());
        builder.define(CONTROLLER_ENTITY_ID, -1);
        builder.define(ITEM, ItemStack.EMPTY);
        builder.define(REMOVE_ON_ENTITY_HIT, true);
        builder.define(REMOVE_AFTER_HIT, false);
        builder.define(BENEFICIAL, false);
        builder.define(RELEASED_ENERGY, 0);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        if (getPickupItemStackOrigin().isEmpty()
                && !getItem().isEmpty()) {
            setPickupItemStack(getItem().copyWithCount(1));
        }
        super.addAdditionalSaveData(tag);
        entityData.get(CONTROLLER_UUID).ifPresent(
                uuid -> tag.putUUID(CONTROLLER_TAG, uuid));
        if (!getItem().isEmpty()) {
            tag.put(ITEM_TAG, getItem().save(registryAccess()));
        }
        tag.putBoolean(
                REMOVE_TAG,
                entityData.get(REMOVE_ON_ENTITY_HIT));
        tag.putBoolean(
                REMOVE_AFTER_TAG,
                entityData.get(REMOVE_AFTER_HIT));
        tag.putBoolean(
                BENEFICIAL_TAG,
                entityData.get(BENEFICIAL));
        tag.putInt(
                RELEASED_ENERGY_TAG,
                entityData.get(RELEASED_ENERGY));
        if (getItem().has(DataComponents.FOOD)) {
            tag.putInt(FOOD_DAMAGE_VERSION_TAG, 1);
        }
        tag.putInt(ITEM_DAMAGE_VERSION_TAG, 1);
        if (usesWeaponDamage(getItem())) {
            tag.putInt(WEAPON_DAMAGE_VERSION_TAG, 1);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(
                CONTROLLER_UUID,
                tag.hasUUID(CONTROLLER_TAG)
                        ? Optional.of(tag.getUUID(CONTROLLER_TAG))
                        : Optional.empty());
        entityData.set(CONTROLLER_ENTITY_ID, -1);
        if (tag.contains(ITEM_TAG)) {
            setItem(ItemStack.parseOptional(
                    registryAccess(),
                    tag.getCompound(ITEM_TAG)));
        }
        entityData.set(
                REMOVE_ON_ENTITY_HIT,
                tag.getBoolean(REMOVE_TAG));
        entityData.set(
                REMOVE_AFTER_HIT,
                tag.getBoolean(REMOVE_AFTER_TAG));
        entityData.set(
                BENEFICIAL,
                tag.getBoolean(BENEFICIAL_TAG));
        entityData.set(
                RELEASED_ENERGY,
                Math.max(0, tag.getInt(RELEASED_ENERGY_TAG)));
        // Only the unmarked legacy default is reconstructible. Preserve
        // custom damage and damage accumulated across subsequent releases.
        if (usesWeaponDamage(getItem())
                && tag.getInt(WEAPON_DAMAGE_VERSION_TAG) < 1
                && getBaseDamage() == DEFAULT_BASE_DAMAGE) {
            setBaseDamage(weaponBaseDamage(getItem())
                    + KraftWorkSpec.ARROW_DAMAGE_PER_CHARGE * releasedEnergy()
                            * kineticDamageMultiplier());
        } else if (usesResourceDamage(getItem())
                && tag.getInt(ITEM_DAMAGE_VERSION_TAG) < 1
                && (!getItem().has(DataComponents.FOOD)
                        || tag.getInt(FOOD_DAMAGE_VERSION_TAG) < 1)
                && getBaseDamage() == DEFAULT_BASE_DAMAGE) {
            setBaseDamage((DEFAULT_BASE_DAMAGE
                    + KraftWorkSpec.ARROW_DAMAGE_PER_CHARGE * releasedEnergy())
                    * kineticDamageMultiplier());
        }
        pickup = willBeRemovedOnEntityHit()
                ? Pickup.DISALLOWED
                : Pickup.ALLOWED;
    }
}
