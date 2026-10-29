package rotp.addons.kraftwork;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.function.Consumer;

import rotp.core.api.client.render.AbilitySelectionSurface;
import rotp.core.api.client.render.AbilitySelectionVisualQuery;
import rotp.core.customobjects.RoadRollerEntity;
import rotp.core.customobjects.entity_projectile.BladeHatEntity;
import rotp.core.customobjects.entity_projectile.KnifeEntity;
import rotp.core.customobjects.entity_projectile.SpaceRipperStingyEyesEntity;
import rotp.core.customobjects.entity_projectile.TommyGunBulletEntity;
import rotp.core.impl.powers.hamon.HamonData;
import rotp.core.impl.stands.crazydiamond.CrazyDBlockBulletEntity;
import rotp.core.impl.stands.hierophant.HGBarrierEntity;
import rotp.core.impl.stands.starplatinum.SPStarFingerEntity;
import rotp.core.impl.powers.hamon.ModHamonSkills;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.init.ModEntityAttributes;
import rotp.core.init.ModEntityTypes;
import rotp.core.init.ModItems;
import rotp.core.init.ModSoundEvents;
import rotp.core.init.ModStatusEffects;
import rotp.core.init.power.ModPlayerPowers;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.condition.AvailableAbilities;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.playerpower.PlayerPower;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandStatFormulas;
import rotp.core.subsystems.target.ActionTargetRange;
import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;

import rotp.addons.kraftwork.ability.KraftWorkActionAbility;
import rotp.addons.kraftwork.ability.KraftWorkHeavyPunchAbility;
import rotp.addons.kraftwork.entity.KraftWorkBlockMarkerEntity;
import rotp.addons.kraftwork.entity.KraftWorkItemProjectile;
import rotp.addons.kraftwork.entity.KraftWorkProjectileFactory;
import rotp.addons.kraftwork.network.KraftWorkPayloads;
import rotp.addons.kraftwork.state.KraftWorkLockService;
import rotp.addons.kraftwork.state.KraftWorkLockService.ReleaseFilter;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundSoundEntityPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.tags.StructureTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.EyeOfEnder;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.SmallFireball;
import net.minecraft.world.entity.projectile.Snowball;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.attachment.AttachmentSync;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.PlayLevelSoundEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.network.payload.SyncAttachmentsPayload;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

@GameTestHolder(KraftWorkAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class KraftWorkGameTests {
    private static final short PLACE_KEY = 73;
    private static final short ENERGY_KEY = 74;
    private static final short LOCK_KEY = 75;
    private static final short RELEASE_KEY = 76;
    private static final short PUNCH_KEY = 77;
    private static final short BEAT_KEY = 78;
    private static final String CONDITION_MESSAGE =
            "jojo.message.action_condition.";
    private static final String STUN_MESSAGE = CONDITION_MESSAGE + "stun";

    private KraftWorkGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 30)
    public static void freshDonorFireItemsIgniteAfterAcceptedCollision(GameTestHelper helper) {
        TestContext context = prepareKraftWork(helper, "kw-fire-items");
        context.owner().setGameMode(GameType.SURVIVAL);
        List<String> missing = new ArrayList<>();
        try {
            for (Item item : donorFireItems()) {
                FireHit hit = hitFireCarrier(helper, context, new ItemStack(item),
                        null, FireTarget.NORMAL, 0);
                helper.assertTrue(hit.damage() > 0.0F,
                        "Fresh donor item did not reach accepted positive damage: " + item);
                LogUtils.getLogger().info("R577 fresh item={} hit={}", item, hit);
                if (hit.fireTicks() != 80 || !hit.onFire()) {
                    missing.add(item + "=" + hit.fireTicks());
                }
            }
            helper.assertTrue(missing.isEmpty(), "Missing fresh donor fire: " + missing);
        } finally {
            cleanupFireContext(context);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 30)
    public static void fireAspectUsesDonorLevelDurationWithoutShorteningFire(GameTestHelper helper) {
        TestContext context = prepareKraftWork(helper, "kw-fire-aspect");
        context.owner().setGameMode(GameType.SURVIVAL);
        List<String> missing = new ArrayList<>();
        try {
            for (int level = 0; level <= 3; level++) {
                FireHit hit = hitFireCarrier(helper, context,
                        fireAspect(helper, Items.IRON_SWORD, level), null, FireTarget.NORMAL, 0);
                helper.assertTrue(hit.damage() > 0.0F,
                        "The fresh sword did not reach accepted damage at Fire Aspect " + level);
                LogUtils.getLogger().info("R577 fire-aspect={} hit={}", level, hit);
                if (hit.fireTicks() != 80 * level || hit.onFire() != (level > 0)) {
                    missing.add("level" + level + "=" + hit.fireTicks());
                }
            }
            FireHit existing = hitFireCarrier(helper, context,
                    fireAspect(helper, Items.IRON_SWORD, 2), null, FireTarget.NORMAL, 240);
            helper.assertTrue(existing.fireTicks() == 240,
                    "Fire Aspect shortened a longer existing fire duration");
            helper.assertTrue(missing.isEmpty(), "Missing Fire Aspect fire: " + missing);
        } finally {
            cleanupFireContext(context);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 30)
    public static void donorFireRespectsRejectedZeroAndImmuneTargets(GameTestHelper helper) {
        TestContext context = prepareKraftWork(helper, "kw-fire-gates");
        context.owner().setGameMode(GameType.SURVIVAL);
        try {
            List<ItemStack> carriers = new ArrayList<>();
            for (Item item : donorFireItems()) {
                carriers.add(new ItemStack(item));
            }
            carriers.add(fireAspect(helper, Items.IRON_SWORD, 2));
            for (ItemStack item : carriers) {
                for (int existingFire : new int[] { 0, 120 }) {
                    FireHit rejected = hitFireCarrier(helper, context, item.copy(),
                            null, FireTarget.REJECTED, existingFire);
                    helper.assertTrue(rejected.damage() == 0.0F
                                    && rejected.fireTicks() == existingFire,
                            "Rejected hit changed health or fire: " + item + ", " + rejected);
                }
                FireHit immune = hitFireCarrier(helper, context, item.copy(),
                        null, FireTarget.FIRE_IMMUNE, 0);
                helper.assertTrue(immune.damage() > 0.0F && !immune.onFire(),
                        "An intrinsically fire-immune target burned: " + item);
                FireHit noDuration = hitFireCarrier(helper, context, item.copy(),
                        null, FireTarget.ZERO_BURN_DURATION, 0);
                helper.assertTrue(noDuration.damage() > 0.0F && noDuration.fireTicks() == 0
                                && !noDuration.onFire(),
                        "Donor ignition bypassed the vanilla zero burning-time attribute: " + item);
            }
            for (int existingFire : new int[] { 0, 120 }) {
                FireHit zero = hitFireCarrier(helper, context,
                        fireAspect(helper, Items.MAGMA_CREAM, 2), null, FireTarget.NORMAL, existingFire);
                helper.assertTrue(zero.damage() == 0.0F && zero.fireTicks() == existingFire
                                && zero.onFire() == (existingFire > 0),
                        "Zero-damage magma cream changed the original fire timer: " + zero);
            }
        } finally {
            cleanupFireContext(context);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 30)
    public static void legacyExtraFireItemsKeepTheirAcceptedDurationAndRejection(GameTestHelper helper) {
        TestContext context = prepareKraftWork(helper, "kw-fire-extras");
        context.owner().setGameMode(GameType.SURVIVAL);
        try {
            for (Item item : new Item[] { Items.FIRE_CHARGE, Items.LAVA_BUCKET,
                    Items.MAGMA_CREAM, Items.BLAZE_POWDER }) {
                for (int existingFire : new int[] { 0, 240 }) {
                    FireHit accepted = hitFireCarrier(helper, context, new ItemStack(item),
                            1.0D, FireTarget.NORMAL, existingFire);
                    helper.assertTrue(accepted.damage() == 6.0F
                                    && accepted.fireTicks() == Math.max(existingFire, 80),
                            "Saved extra-item carrier changed damage/fire: " + item + ", " + accepted);
                }
                for (int existingFire : new int[] { 0, 120 }) {
                    FireHit rejected = hitFireCarrier(helper, context, new ItemStack(item),
                            1.0D, FireTarget.REJECTED, existingFire);
                    helper.assertTrue(rejected.damage() == 0.0F && rejected.fireTicks() == existingFire,
                            "Rejected legacy extra-item carrier changed fire: " + item + ", " + rejected);
                }
                FireHit enchanted = hitFireCarrier(helper, context,
                        fireAspect(helper, item, 2), 1.0D, FireTarget.NORMAL, 0);
                helper.assertTrue(enchanted.damage() == 6.0F && enchanted.fireTicks() == 80,
                        "Donor additions changed the explicit extra-item 80-tick policy: " + item);
            }
        } finally {
            cleanupFireContext(context);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 30)
    public static void fireCarrierSetsAStandAlightThroughTheStandFirePath(GameTestHelper helper) {
        TestContext context = prepareKraftWork(helper, "kw-fire-stand");
        context.owner().setGameMode(GameType.SURVIVAL);
        TestContext victim = prepareKraftWork(helper, "kw-fire-stand-victim");
        victim.owner().setGameMode(GameType.SURVIVAL);
        try {
            StandEntity target = victim.stand();
            target.setIsPhysicalObject();
            target.igniteForTicks(80);
            helper.assertTrue(target.getRemainingFireTicks() <= 0 && !target.isOnFire(),
                    "A Stand was set alight by ordinary ignition");
            List<String> wrong = new ArrayList<>();
            List<ItemStack> carriers = new ArrayList<>();
            for (Item item : donorFireItems()) {
                carriers.add(new ItemStack(item));
            }
            carriers.add(fireAspect(helper, Items.IRON_SWORD, 2));
            carriers.add(new ItemStack(Items.FIRE_CHARGE));
            carriers.add(new ItemStack(Items.BLAZE_POWDER));
            for (ItemStack item : carriers) {
                int expected = item.is(Items.IRON_SWORD) ? 160 : 80;
                FireHit hit = hitStandWithFireCarrier(helper, context, target, item.copy(),
                        item.is(Items.FIRE_CHARGE) || item.is(Items.BLAZE_POWDER) ? 1.0D : null);
                if (hit.fireTicks() != expected || !hit.onFire()) {
                    wrong.add(item + "=" + hit.fireTicks());
                }
            }
            helper.assertTrue(wrong.isEmpty(),
                    "A burning thrown item did not set the Stand alight: " + wrong);
            FireHit plain = hitStandWithFireCarrier(helper, context, target, new ItemStack(Items.COBBLESTONE), null);
            helper.assertTrue(plain.fireTicks() <= 0 && !plain.onFire(),
                    "A thrown item without fire set the Stand alight: " + plain);
        } finally {
            victim.stand().discard();
            victim.owner().connection.disconnect(Component.literal("Kraft Work fire regression complete"));
            cleanupFireContext(context);
        }
        helper.succeed();
    }

    private static FireHit hitStandWithFireCarrier(
            GameTestHelper helper, TestContext context, StandEntity target, ItemStack item,
            Double savedDamage) {
        KraftWorkItemProjectile projectile = fireCarrier(helper, context, item, savedDamage);
        try {
            Vec3 position = helper.absoluteVec(new Vec3(2, 5, 3));
            target.setPos(position);
            target.setNoGravity(true);
            target.setRemainingFireTicks(0);
            target.invulnerableTime = 0;
            float health = target.getHealth();
            projectile.setPos(position.add(0, 0.5, -2));
            projectile.setNoGravity(true);
            projectile.setDeltaMovement(0, 0, 6);
            projectile.tick();
            helper.assertTrue(target.getHealth() < health,
                    "The thrown item did not hurt the Stand, so the fire path was not reached: " + item);
            FireHit result = new FireHit(health - target.getHealth(),
                    target.getRemainingFireTicks(), target.isOnFire(), 0, 0);
            target.setRemainingFireTicks(0);
            return result;
        } finally {
            projectile.discard();
        }
    }

    @GameTest(template = "empty", timeoutTicks = 30)
    public static void lethalFireCarrierCooksDropsBeforeDeath(GameTestHelper helper) {
        TestContext context = prepareKraftWork(helper, "kw-fire-lethal-loot");
        context.owner().setGameMode(GameType.SURVIVAL);
        try {
            helper.assertTrue(helper.getLevel().getGameRules().getBoolean(GameRules.RULE_DOMOBLOOT),
                    "The lethal collision fixture requires ordinary mob loot");
            FireHit plain = hitFireCarrier(helper, context, new ItemStack(Items.IRON_SWORD),
                    null, FireTarget.LETHAL, 0);
            helper.assertTrue(plain.rawBeef() > 0 && plain.cookedBeef() == 0,
                    "Unlit lethal collision did not produce raw cow loot: " + plain);
            FireHit burning = hitFireCarrier(helper, context, new ItemStack(Items.TORCH),
                    null, FireTarget.LETHAL, 0);
            LogUtils.getLogger().info("R644 lethal cow drops: unlit={} torch={}", plain, burning);
            helper.assertTrue(burning.cookedBeef() > 0 && burning.rawBeef() == 0,
                    "Lethal torch collision generated loot before ignition: " + burning);
        } finally {
            cleanupFireContext(context);
        }
        helper.succeed();
    }

    private static Item[] donorFireItems() {
        return new Item[] { Items.TORCH, Items.SOUL_TORCH, Items.CAMPFIRE,
                Items.SOUL_CAMPFIRE, Items.MAGMA_BLOCK };
    }

    private static ItemStack fireAspect(GameTestHelper helper, Item item, int level) {
        ItemStack stack = new ItemStack(item);
        if (level > 0) {
            stack.enchant(helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                    .getOrThrow(Enchantments.FIRE_ASPECT), level);
        }
        return stack;
    }

    private static FireHit hitFireCarrier(
            GameTestHelper helper, TestContext context, ItemStack item, Double savedDamage,
            FireTarget targetKind, int existingFire) {
        KraftWorkItemProjectile projectile = fireCarrier(helper, context, item, savedDamage);
        Mob target = targetKind == FireTarget.FIRE_IMMUNE
                ? EntityType.BLAZE.create(helper.getLevel()) : EntityType.COW.create(helper.getLevel());
        helper.assertTrue(target != null, "Could not create the real fire-hit target");
        Vec3 position = helper.absoluteVec(new Vec3(2, 5, 3));
        List<ItemEntity> deathDrops = new ArrayList<>();
        try {
            target.setPos(position);
            target.setNoAi(true);
            target.setNoGravity(true);
            target.setInvulnerable(targetKind == FireTarget.REJECTED);
            target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200.0D);
            float initialHealth = targetKind == FireTarget.LETHAL ? 1.0F : 200.0F;
            target.setHealth(initialHealth);
            if (targetKind == FireTarget.ZERO_BURN_DURATION) {
                target.getAttribute(Attributes.BURNING_TIME).setBaseValue(0.0D);
            }
            target.setRemainingFireTicks(existingFire);
            helper.assertTrue(helper.getLevel().addFreshEntity(target), "Could not register fire-hit target");
            List<ItemEntity> existingDrops = targetKind == FireTarget.LETHAL
                    ? helper.getLevel().getEntitiesOfClass(ItemEntity.class, target.getBoundingBox().inflate(2.0D))
                    : List.of();
            double baseDamage = projectile.getBaseDamage();
            projectile.setPos(position.add(0, 0.5, -2));
            projectile.setNoGravity(true);
            projectile.setDeltaMovement(0, 0, 6);
            projectile.tick();
            helper.assertTrue(projectile.isRemoved() || projectile.getDeltaMovement().length() < 5.0D,
                    "Fire test missed its target rather than executing real onHitEntity");
            if (targetKind == FireTarget.LETHAL) {
                for (ItemEntity drop : helper.getLevel().getEntitiesOfClass(
                        ItemEntity.class, target.getBoundingBox().inflate(2.0D))) {
                    if (!existingDrops.contains(drop)) {
                        deathDrops.add(drop);
                    }
                }
                helper.assertTrue(!target.isAlive(), "The lethal collision did not kill its cow");
            }
            float damage = initialHealth - target.getHealth();
            double expectedDamage = targetKind == FireTarget.REJECTED ? 0.0D
                    : Math.min(initialHealth, Math.ceil(6.0D * baseDamage));
            helper.assertTrue(Math.abs(damage - expectedDamage) < 0.001D,
                    "Fire fixture changed kinetic damage: expected=" + expectedDamage + ", actual=" + damage);
            int rawBeef = deathDrops.stream().filter(drop -> drop.getItem().is(Items.BEEF))
                    .mapToInt(drop -> drop.getItem().getCount()).sum();
            int cookedBeef = deathDrops.stream().filter(drop -> drop.getItem().is(Items.COOKED_BEEF))
                    .mapToInt(drop -> drop.getItem().getCount()).sum();
            FireHit result = new FireHit(damage, target.getRemainingFireTicks(), target.isOnFire(), rawBeef, cookedBeef);
            if (targetKind == FireTarget.FIRE_IMMUNE) {
                helper.assertTrue(target.fireImmune(), "The immunity fixture is not fire immune");
                float afterImpact = target.getHealth();
                for (int tick = 0; tick < 20; tick++) {
                    target.baseTick();
                }
                helper.assertTrue(target.getHealth() == afterImpact && !target.isOnFire(),
                        "A fire-immune target took follow-up burning damage");
            }
            return result;
        } finally {
            deathDrops.forEach(ItemEntity::discard);
            projectile.discard();
            target.discard();
        }
    }

    private static KraftWorkItemProjectile fireCarrier(
            GameTestHelper helper, TestContext context, ItemStack item, Double savedDamage) {
        if (savedDamage == null) {
            KraftWorkItemProjectile projectile = placeItem(helper, context, item);
            helper.assertTrue(KraftWorkLockService.releaseTarget(context.owner(), projectile),
                    "The fresh carrier could not leave the production lock/release path");
            return projectile;
        }
        KraftWorkItemProjectile original = new KraftWorkItemProjectile(
                KraftWorkAddon.KW_ITEM.get(), helper.getLevel());
        original.initialize(context.owner(), context.stand(), item);
        original.setBaseDamage(savedDamage);
        CompoundTag saved = new CompoundTag();
        original.saveWithoutId(saved);
        saved.remove("UUID");
        original.discard();
        KraftWorkItemProjectile projectile = new KraftWorkItemProjectile(
                KraftWorkAddon.KW_ITEM.get(), helper.getLevel());
        projectile.load(saved);
        helper.assertTrue(projectile.getBaseDamage() == savedDamage
                        && helper.getLevel().addFreshEntity(projectile),
                "Saved carrier damage or entity registration was lost");
        return projectile;
    }

    private static void cleanupFireContext(TestContext context) {
        context.stand().discard();
        context.owner().connection.disconnect(Component.literal("Kraft Work fire regression complete"));
    }

    private enum FireTarget { NORMAL, REJECTED, FIRE_IMMUNE, ZERO_BURN_DURATION, LETHAL }

    private record FireHit(float damage, int fireTicks, boolean onFire, int rawBeef, int cookedBeef) {}

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void freshMagmaCreamRemainsWeightlessThroughReleaseAndCollision(
            GameTestHelper helper) {
        TestContext context = prepareKraftWork(helper, "kw-magma-fresh");
        context.owner().setGameMode(GameType.SURVIVAL);
        KraftWorkItemProjectile projectile = placeItem(
                helper, context, new ItemStack(Items.MAGMA_CREAM));
        try {
            helper.assertTrue(projectile.kineticDamageMultiplier() == 0.0D
                            && projectile.getBaseDamage() == 0.0D,
                    "Fresh magma cream no longer has the donor zero damage.");
            helper.assertTrue(KraftWorkLockService.addEnergy(projectile)
                            && KraftWorkLockService.releaseTarget(context.owner(), projectile)
                            && projectile.releasedEnergy() > 0
                            && projectile.getBaseDamage() == 0.0D,
                    "Charging/releasing fresh magma cream invented kinetic damage.");
            collideWithCow(helper, projectile, false, 0.0D, false);
        } finally {
            cleanup(context, projectile, "Kraft Work fresh magma test complete");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void savedMagmaDamageAndRejectedHitKeepTheirCompatibilitySemantics(
            GameTestHelper helper) {
        TestContext context = prepareKraftWork(helper, "kw-magma-save");
        context.owner().setGameMode(GameType.SURVIVAL);
        KraftWorkItemProjectile original = placeItem(
                helper, context, new ItemStack(Items.MAGMA_CREAM));
        try {
            for (boolean marked : new boolean[] { false, true }) {
                for (double savedDamage : new double[] { 0.0D, 2.0D / 6.0D, 2.5D }) {
                    for (boolean rejected : new boolean[] { false, true }) {
                        original.setBaseDamage(savedDamage);
                        original.setReleasedEnergy(100);
                        CompoundTag tag = new CompoundTag();
                        original.saveWithoutId(tag);
                        tag.remove("UUID");
                        if (!marked) {
                            tag.remove("ItemDamageVersion");
                        }
                        KraftWorkItemProjectile restored =
                                KraftWorkAddon.KW_ITEM.get().create(helper.getLevel());
                        helper.assertTrue(restored != null, "Could not reload a magma projectile.");
                        restored.load(tag);
                        double expectedDamage = !marked && savedDamage == 2.0D / 6.0D
                                ? 0.0D : savedDamage;
                        helper.assertTrue(restored.getBaseDamage() == expectedDamage
                                        && restored.releasedEnergy() == 100,
                                "Load rewrote marked/custom damage or lost released energy: "
                                        + savedDamage + ", marked=" + marked);
                        helper.assertTrue(helper.getLevel().addFreshEntity(restored),
                                "Could not register the reloaded collision projectile.");
                        try {
                            collideWithCow(helper, restored, rejected, expectedDamage,
                                    !rejected && expectedDamage > 0.0D);
                        } finally {
                            restored.discard();
                        }
                    }
                }
            }
        } finally {
            cleanup(context, original, "Kraft Work saved magma test complete");
        }
        helper.succeed();
    }

    private static void collideWithCow(
            GameTestHelper helper, KraftWorkItemProjectile projectile,
            boolean rejected, double baseDamage, boolean expectFire) {
        Cow target = EntityType.COW.create(helper.getLevel());
        helper.assertTrue(target != null, "Could not create a collision target.");
        Vec3 targetPosition = helper.absoluteVec(new Vec3(2, 5, 3));
        target.setPos(targetPosition);
        target.setNoAi(true);
        target.setNoGravity(true);
        target.setInvulnerable(rejected);
        target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200.0D);
        target.setHealth(200.0F);
        target.clearFire();
        helper.assertTrue(helper.getLevel().addFreshEntity(target),
                "Could not register a collision target.");
        float health = target.getHealth();
        projectile.setPos(targetPosition.add(0, 0.5, -2));
        projectile.setDeltaMovement(0, 0, 6);
        projectile.setNoGravity(true);
        try {
            projectile.tick();
            helper.assertTrue(projectile.isRemoved(),
                    "The live projectile did not collide with the target.");
            double expectedLoss = rejected ? 0.0D : Math.ceil(6.0D * baseDamage);
            helper.assertTrue(Math.abs(health - target.getHealth() - expectedLoss) < 0.001D,
                    "Collision changed expected kinetic damage: expected=" + expectedLoss
                            + ", actual=" + (health - target.getHealth()));
            helper.assertTrue(expectFire ? target.getRemainingFireTicks() == 80
                            : target.getRemainingFireTicks() <= 0,
                    "Collision changed zero-damage, accepted-fire or rejected-fire semantics.");
        } finally {
            target.discard();
        }
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void fixedItemSavesAndLoadsWithVanillaArrowItem(
            GameTestHelper helper) {
        TestContext context = prepareKraftWork(helper, "kraftwork-save");
        KraftWorkItemProjectile projectile =
                placeCobblestone(helper, context);

        helper.assertTrue(
                !projectile.getItem().isEmpty()
                        && !projectile
                                .getPickupItemStackOrigin()
                                .isEmpty(),
                "The fixed item did not initialize both item stacks.");
        CompoundTag firstSave = new CompoundTag();
        projectile.saveWithoutId(firstSave);
        helper.assertTrue(
                firstSave.contains("Item")
                        && firstSave.contains("item"),
                "The fixed item save omitted its custom or vanilla "
                        + "arrow item.");

        KraftWorkItemProjectile restored =
                KraftWorkAddon.KW_ITEM.get().create(helper.getLevel());
        helper.assertTrue(
                restored != null,
                "The fixed item entity could not be recreated.");
        if (restored == null) {
            return;
        }
        restored.load(firstSave);
        helper.assertTrue(
                restored.getItem().is(Items.COBBLESTONE)
                        && restored.getPickupItemStackOrigin()
                                .is(Items.COBBLESTONE),
                "The fixed item NBT round trip desynchronized its "
                        + "custom and vanilla item stacks.");
        restored.saveWithoutId(new CompoundTag());

        cleanup(context, projectile, "Kraft Work save GameTest complete");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void placeInputKeepsDonorIdlePoseAndCreatesFixedItem(
            GameTestHelper helper) {
        TestContext context =
                prepareKraftWork(helper, "kraftwork-place");
        context.owner().setItemSlot(
                EquipmentSlot.OFFHAND,
                new ItemStack(Items.COBBLESTONE));
        Ability ability = context.power().getAbility(
                KraftWorkAddon.PLACE_PROJECTILE);
        helper.assertTrue(
                ability instanceof KraftWorkActionAbility
                        && AbilityInput.withConditionCheck(
                                ability, context.owner()),
                "Production CLICK preflight rejected projectile placement.");

        FriendlyByteBuf input =
                new FriendlyByteBuf(Unpooled.buffer());
        Object pressed;
        try {
            pressed = AbilityInput.keyPress(
                    PLACE_KEY,
                    ability,
                    context.owner(),
                    input,
                    InputMethod.CLICK,
                    0.0F,
                    BufferingState.clickCanBuffer(),
                    ability.abilityId);
        }
        finally {
            input.release();
        }
        // 1.16 Place Projectile was a StandAction its user's power held,
        // performed by Kraft Work while it was out (StandAction.getPerformer)
        EntityActionInstance action =
                LivingComponentAction.getCurEntityAction(
                        context.owner(), PowerClass.STAND);
        helper.assertTrue(
                pressed != null
                        && action instanceof KraftWorkActionAbility
                                .KraftWorkAction
                        && action.getPerformer() == context.stand()
                        && action.getEntityAnim() != null
                        && "idle".equals(action.getEntityAnim().name()),
                "Projectile placement did not preserve the donor idle pose "
                        + "in its user's Stand-power slot, performed by "
                        + "Kraft Work: " + action);

        helper.succeedWhen(() -> {
            List<KraftWorkItemProjectile> projectiles =
                    helper.getLevel().getEntitiesOfClass(
                            KraftWorkItemProjectile.class,
                            new AABB(context.owner().blockPosition())
                                    .inflate(4.0D));
            helper.assertTrue(
                    projectiles.size() == 1,
                    "Production CLICK did not create exactly one fixed item.");
            KraftWorkItemProjectile projectile = projectiles.getFirst();
            helper.assertTrue(
                    LivingComponentAction.getCurEntityAction(
                            context.owner(), PowerClass.STAND) != action,
                    "Projectile placement did not return to idle naturally.");
            helper.assertTrue(
                    KraftWorkLockService.state(projectile).locked()
                            && context.owner().getUUID().equals(
                                    KraftWorkLockService
                                            .state(projectile)
                                            .owner()),
                    "The fixed item lost its production lock state.");
            cleanup(
                    context,
                    projectile,
                    "Kraft Work place GameTest complete");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void energyHoldStartsActionAnimationAndChargesFixedItem(
            GameTestHelper helper) {
        TestContext context =
                prepareKraftWork(helper, "kraftwork-energy");
        KraftWorkItemProjectile projectile =
                placeCobblestone(helper, context);

        context.owner().setYRot(
                context.owner().getYRot() + 180.0F);
        context.owner().setYHeadRot(context.owner().getYRot());
        context.stand().setYRot(context.owner().getYRot());
        context.stand().setYHeadRot(context.owner().getYRot());

        Ability ability = context.power().getAbility(
                KraftWorkAddon.ENERGY_ACCUMULATION);
        helper.assertTrue(
                ability instanceof KraftWorkActionAbility
                        && AbilityInput.withConditionCheck(
                                ability, context.owner()),
                "Targetless production HOLD preflight rejected "
                        + "energy accumulation.");
        var held = AbilityInput.keyPress(
                ENERGY_KEY,
                ability,
                context.owner(),
                new FriendlyByteBuf(Unpooled.buffer()),
                InputMethod.HOLD,
                0.0F,
                BufferingState.clickCanBuffer(),
                ability.abilityId);
        EntityActionInstance action =
                LivingComponentAction.getCurEntityAction(
                        context.stand());
        // 1.16 KraftWorkEnergyAccumulation began with its windup
        helper.assertTrue(
                held != null
                        && action instanceof KraftWorkActionAbility
                                .KraftWorkAction
                        && action.getPhase() == ActionPhase.WINDUP
                        && action.getEntityAnim() != null
                        && KraftWorkAddon.ENERGY_ACCUMULATION.equals(
                                action.getEntityAnim().name()),
                "Production HOLD input did not enter the Kraft Work "
                        + "energy animation.");

        helper.succeedWhen(() -> {
            EntityActionInstance current =
                    LivingComponentAction.getCurEntityAction(
                            context.stand());
            var animation = action.getEntityAnim();
            helper.assertTrue(
                    current == action
                            && !action.isOver()
                            && animation != null
                            && KraftWorkAddon.ENERGY_ACCUMULATION
                                    .equals(animation.name())
                            && KraftWorkLockService.state(projectile)
                                    .energy()
                                    > 0,
                    "The held energy action did not remain animated "
                            + "and charge the nearby fixed item. "
                            + "currentSame=" + (current == action)
                            + ", phase=" + action.getPhase()
                            + ", phaseTick=" + action.getPhaseTick()
                            + ", animation="
                            + (animation != null
                                    ? animation.name()
                                    : null)
                            + ", energy="
                            + KraftWorkLockService.state(projectile)
                                    .energy()
                            + ", locked="
                            + KraftWorkLockService.state(projectile)
                                    .locked()
                            + ", ownerMatch="
                            + context.owner().getUUID().equals(
                                    KraftWorkLockService
                                            .state(projectile)
                                            .owner())
                            + ", ticks(owner/stand/projectile)="
                            + context.owner().tickCount + "/"
                            + context.stand().tickCount + "/"
                            + projectile.tickCount);
            AbilityInput.keyRelease(ENERGY_KEY, context.owner());
            cleanup(
                    context,
                    projectile,
                    "Kraft Work energy GameTest complete");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void effectCleanupPreservesPhysicsOwnership(GameTestHelper helper) {
        TestContext context = prepareKraftWork(helper, "kw-physics");
        KraftWorkItemProjectile projectile = placeCobblestone(helper, context);
        Mob target = helper.spawn(EntityType.ZOMBIE, new BlockPos(2, 2, 1));
        try {
            StandEntity stand = context.stand();
            stand.setNoGravity(true);
            stand.fallDistance = 3.0F;
            stand.addEffect(new MobEffectInstance(MobEffects.GLOWING, 40));
            stand.removeEffect(MobEffects.GLOWING);
            KraftWorkGameEvents.onEffectExpired(new MobEffectEvent.Expired(
                    stand, new MobEffectInstance(MobEffects.GLOWING, 1)));
            KraftWorkLockService.postTick(stand);
            helper.assertTrue(stand.isNoGravity() && stand.fallDistance == 3.0F,
                    "Unrelated effect removal/expiry changed untouched Stand physics.");

            KraftWorkLockService.state(stand);
            stand.removeEffect(MobEffects.GLOWING);
            stand.removeEffect(KraftWorkAddon.LOCKED_POSITION);
            KraftWorkLockService.restoreAfterEffects(stand);
            KraftWorkLockService.postTick(stand);
            helper.assertTrue(stand.isNoGravity() && stand.fallDistance == 3.0F,
                    "An empty lock attachment or absent effect claimed Stand physics.");

            for (boolean original : new boolean[] { false, true }) {
                target.setNoGravity(original);
                target.setNoAi(original);
                target.addEffect(new MobEffectInstance(KraftWorkAddon.LOCKED_MAIN_HAND, 40));
                KraftWorkAddon.LOCKED_MAIN_HAND.get().applyEffectTick(target, 0);
                target.addEffect(new MobEffectInstance(KraftWorkAddon.LOCKED_OFF_HAND, 40));
                KraftWorkGameEvents.onEffectExpired(new MobEffectEvent.Expired(
                        target, target.getEffect(KraftWorkAddon.LOCKED_MAIN_HAND)));
                KraftWorkLockService.postTick(target);
                helper.assertTrue(target.isNoGravity() && target.isNoAi(),
                        "An effect still in the active map restored physics too early.");
                target.removeEffect(KraftWorkAddon.LOCKED_MAIN_HAND);
                KraftWorkLockService.postTick(target);
                helper.assertTrue(target.isNoGravity() && target.isNoAi(),
                        "Removing one of multiple Kraft Work effects ended ownership early.");
                target.removeEffect(KraftWorkAddon.LOCKED_OFF_HAND);
                KraftWorkLockService.postTick(target);
                helper.assertTrue(target.isNoGravity() == original && target.isNoAi() == original,
                        "Effect-only cleanup lost original gravity or AI.");
                target.setNoGravity(!original);
                target.setNoAi(!original);
                KraftWorkLockService.restoreAfterEffects(target);
                helper.assertTrue(target.isNoGravity() != original && target.isNoAi() != original,
                        "An already-consumed effect snapshot was restored again.");

                target.setNoGravity(original);
                target.setNoAi(original);
                KraftWorkLockService.addPositionLockEffect(target, 40);
                KraftWorkAddon.LOCKED_POSITION.get().applyEffectTick(target, 0);
                target.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STICK));
                helper.assertTrue(KraftWorkLockService.lockMainHand(context.owner(), target) > 0,
                        "Could not promote the effect-only target into an owned lock.");
                helper.assertTrue(KraftWorkLockService.state(target).hadNoGravity() == original,
                        "Lock captured temporary effect gravity instead of the original state.");
                helper.assertTrue(KraftWorkLockService.releaseTarget(context.owner(), target)
                                && target.isNoGravity() == original && target.isNoAi() == original,
                        "Immediate owned lock release lost original physics.");
                target.setNoGravity(!original);
                KraftWorkGameEvents.onEffectExpired(new MobEffectEvent.Expired(
                        target, new MobEffectInstance(KraftWorkAddon.LOCKED_MAIN_HAND, 1)));
                KraftWorkLockService.restoreAfterEffects(target);
                KraftWorkLockService.postTick(target);
                helper.assertTrue(target.isNoGravity() != original,
                        "Delayed effect cleanup overwrote physics after authoritative lock release.");

                var flight = KraftWorkLockService.state(projectile);
                flight.lock(context.owner().getUUID(),
                        helper.getLevel().dimension().location().toString(),
                        projectile.position(), Vec3.ZERO, original, false, 0);
                flight.beginRelease(2);
                projectile.setNoGravity(true);
                KraftWorkLockService.postTick(projectile);
                helper.assertTrue(projectile.isNoGravity() && flight.flightTicks() == 1,
                        "Active release flight lost its gravity ownership.");
                KraftWorkLockService.postTick(projectile);
                helper.assertTrue(projectile.isNoGravity() == original && flight.flightTicks() == 0,
                        "Completed release flight did not restore original gravity.");
                projectile.setNoGravity(!original);
                KraftWorkLockService.postTick(projectile);
                helper.assertTrue(projectile.isNoGravity() != original,
                        "Completed flight replayed its cleared snapshot.");
            }
        }
        finally {
            target.discard();
            cleanup(context, projectile, "Kraft Work physics ownership GameTest complete");
        }
        helper.succeed();
    }

    // 1.16 froze an armor stand (bare too) and an eye of ender whole: the
    // stand has no lock effect to lose, and the eye did not tick, so it
    // neither aged out nor flew on until a release freed it. A block lock
    // held only within sqrt(140) of its user.
    @GameTest(template = "empty", timeoutTicks = 140)
    public static void wholeLockHoldsArmorStandAndEyeOfEnder(
            GameTestHelper helper) {
        TestContext context =
                prepareKraftWork(helper, "kw-whole-lock");
        ServerPlayer owner = context.owner();
        ArmorStand armorStand = helper.spawn(
                EntityType.ARMOR_STAND, new BlockPos(1, 2, 3));
        Vec3 eyeStart = Vec3.atCenterOf(
                helper.absolutePos(new BlockPos(3, 3, 1)));
        EyeOfEnder eye = new EyeOfEnder(
                helper.getLevel(), eyeStart.x, eyeStart.y, eyeStart.z);
        eye.setDeltaMovement(0.0D, 0.0D, 0.3D);
        helper.getLevel().addFreshEntity(eye);

        helper.assertTrue(
                KraftWorkLockService.isGuardVehicle(armorStand)
                        && KraftWorkLockService.isGuardVehicle(eye)
                        && !KraftWorkLockService.isLockableNonLiving(eye),
                "The guard or Lock Target kinds drifted from 1.16.");
        helper.assertTrue(
                KraftWorkLockService.lockWhole(owner, armorStand)
                        && KraftWorkLockService.state(armorStand)
                                        .equipmentMask()
                                == 0
                        && KraftWorkLockService.lockWhole(owner, eye),
                "A bare armor stand or an eye of ender was not "
                        + "locked whole.");
        Vec3 standOrigin = armorStand.position();
        Vec3 origin = KraftWorkLockService.state(eye).origin();

        BlockPos feet = BlockPos.containing(owner.position());
        helper.assertTrue(
                KraftWorkBlockMarkerEntity.withinLockRange(
                                owner, feet.south(11))
                        && !KraftWorkBlockMarkerEntity.withinLockRange(
                                owner, feet.south(12)),
                "The block lock range left 1.16's sqrt(140).");

        helper.runAfterDelay(100, () -> {
            helper.assertTrue(
                    armorStand.isAlive()
                            && KraftWorkLockService.state(armorStand)
                                    .locked()
                            && armorStand.position().distanceToSqr(
                                            standOrigin)
                                    < 1.0E-6D,
                    "The whole-locked armor stand was released or moved.");
            helper.assertTrue(
                    eye.isAlive()
                            && KraftWorkLockService.state(eye).locked()
                            && eye.position().distanceToSqr(origin)
                                    < 1.0E-6D,
                    "The locked eye of ender kept ticking: it aged out "
                            + "or moved. alive=" + eye.isAlive()
                            + ", locked="
                            + KraftWorkLockService.state(eye).locked()
                            + ", position=" + eye.position());
            helper.assertTrue(
                    KraftWorkLockService.releaseProjectiles(
                                    owner, ReleaseFilter.ALL)
                                    >= 1
                            && !KraftWorkLockService.state(eye)
                                    .locked(),
                    "Release All did not free the eye of ender.");
            helper.assertTrue(
                    KraftWorkLockService.releaseTarget(owner, armorStand)
                            && !KraftWorkLockService.state(armorStand)
                                    .locked(),
                    "Release Target did not free the armor stand.");
            armorStand.discard();
            eye.discard();
            context.stand().discard();
            owner.connection.disconnect(Component.literal(
                    "Kraft Work whole-lock GameTest complete"));
            helper.succeed();
        });
    }

    // 1.16 KraftWorkReleaseProjectile freed every charged locked
    // projectile, and every locked eye of ender, in the user's box
    // inflated by 1; an uncharged one there, and anything farther, stayed.
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void releaseProjectileFreesEveryChargedLockNearTheUser(
            GameTestHelper helper) {
        TestContext context =
                prepareKraftWork(helper, "kw-release-near");
        ServerPlayer owner = context.owner();
        Vec3 feet = owner.position();
        Snowball side = lockedSnowball(helper, owner,
                feet.add(1.0D, 1.0D, 0.0D), true);
        Snowball overhead = lockedSnowball(helper, owner,
                feet.add(0.0D, 2.5D, 1.0D), true);
        Snowball uncharged = lockedSnowball(helper, owner,
                feet.add(-1.0D, 1.0D, 0.0D), false);
        Snowball far = lockedSnowball(helper, owner,
                feet.add(3.0D, 1.0D, 0.0D), true);
        EyeOfEnder eye = new EyeOfEnder(
                helper.getLevel(), feet.x, feet.y + 1.0D, feet.z - 1.0D);
        helper.getLevel().addFreshEntity(eye);
        helper.assertTrue(
                KraftWorkLockService.lockWhole(owner, eye),
                "The eye of ender was not locked whole.");

        int released = KraftWorkLockService.releaseProjectiles(
                owner, ReleaseFilter.NEARBY);
        helper.assertTrue(
                released == 3
                        && !KraftWorkLockService.state(side).locked()
                        && !KraftWorkLockService.state(overhead).locked()
                        && !KraftWorkLockService.state(eye).locked(),
                "Release Projectile did not free every charged lock "
                        + "within a block of the user: released="
                        + released);
        helper.assertTrue(
                KraftWorkLockService.state(uncharged).locked()
                        && KraftWorkLockService.state(far).locked(),
                "Release Projectile freed an uncharged or distant lock.");

        KraftWorkLockService.releaseProjectiles(owner, ReleaseFilter.ALL);
        for (var entity : List.of(side, overhead, uncharged, far, eye)) {
            entity.discard();
        }
        context.stand().discard();
        owner.connection.disconnect(Component.literal(
                "Kraft Work release-near GameTest complete"));
        helper.succeed();
    }

    private static Snowball lockedSnowball(
            GameTestHelper helper,
            ServerPlayer owner,
            Vec3 position,
            boolean charged) {
        Snowball snowball = new Snowball(
                helper.getLevel(), position.x, position.y, position.z);
        snowball.setDeltaMovement(Vec3.ZERO);
        helper.getLevel().addFreshEntity(snowball);
        helper.assertTrue(
                KraftWorkLockService.lockWhole(owner, snowball)
                        && (!charged
                                || KraftWorkLockService.addEnergy(snowball)),
                "A snowball was not locked"
                        + (charged ? " and charged." : "."));
        return snowball;
    }

    // 1.16 KraftWorkLockTarget refused any target whose PositionLocking
    // was set: a frozen armor stand or an equipment-locked mob could not
    // be taken again, so no charge started.
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void lockTargetRefusesAlreadyLockedTarget(
            GameTestHelper helper) {
        TestContext context =
                prepareKraftWork(helper, "kw-relock");
        ServerPlayer owner = context.owner();
        Ability lockTarget = context.power().getAbility(
                KraftWorkAddon.LOCK_TARGET);
        helper.assertTrue(
                lockTarget instanceof KraftWorkActionAbility,
                "Lock Target is not a Kraft Work action.");
        // The Stand spawns on its user until its first tick; keep it
        // out of the aim ray, inside the test's barrier box: Lock Target
        // measures its 1.16 range from the Stand, quartered without a line
        // of sight.
        context.stand().setPos(
                owner.getX() + 4.0D, owner.getY(), owner.getZ());

        ArmorStand armorStand = helper.spawn(
                EntityType.ARMOR_STAND, new BlockPos(1, 2, 3));
        helper.assertTrue(
                lockTarget.checkSpecificConditions(context.power())
                        .isPositive(),
                "Lock Target refused the unlocked armor stand in view.");
        helper.assertTrue(
                KraftWorkLockService.lockWhole(owner, armorStand),
                "The armor stand was not locked whole.");
        helper.assertTrue(
                !lockTarget.checkSpecificConditions(context.power())
                        .isPositive(),
                "Lock Target accepted an armor stand already locked.");
        armorStand.discard();

        Mob zombie = helper.spawn(
                EntityType.ZOMBIE, new BlockPos(1, 2, 3));
        zombie.setNoAi(true);
        zombie.setItemSlot(
                EquipmentSlot.MAINHAND,
                new ItemStack(Items.IRON_SWORD));
        helper.assertTrue(
                lockTarget.checkSpecificConditions(context.power())
                        .isPositive(),
                "Lock Target refused the armed mob in view.");
        helper.assertTrue(
                KraftWorkLockService.lockEquipment(owner, zombie) > 0,
                "The mob's equipment was not locked.");
        helper.assertTrue(
                !lockTarget.checkSpecificConditions(context.power())
                        .isPositive(),
                "Lock Target accepted a mob already locked.");

        KraftWorkLockService.releaseTarget(owner, zombie);
        zombie.discard();
        context.stand().discard();
        owner.connection.disconnect(Component.literal(
                "Kraft Work relock GameTest complete"));
        helper.succeed();
    }

    // 1.16 kraft_work_lock_target (holdToFire(10, false), no target
    // requirement): tickHeldAction re-ran checkRequirements on the crosshair
    // every held tick, and Action.checkRangeAndTarget left a mob the Stand
    // could not see past 4 blocks of it no target, which KraftWorkLockTarget
    // refused silently. The charge stops and locks nothing; the press is
    // refused.
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void lockTargetChargeStopsWhenTheMobLeavesTheRange(
            GameTestHelper helper) {
        TestContext context =
                prepareKraftWork(helper, "kw-lock-range");
        ServerPlayer owner = context.owner();
        Ability lockTarget = context.power().getAbility(
                KraftWorkAddon.LOCK_TARGET);
        helper.assertTrue(
                lockTarget instanceof KraftWorkActionAbility,
                "Lock Target is not a Kraft Work action.");
        // The Stand stands 4 blocks to the user's side, out of the aim ray;
        // the pillar hides the far mob from it, not the near one.
        context.stand().setPos(
                owner.getX() + 4.0D, owner.getY(), owner.getZ());
        for (int y = 2; y <= 5; y++) {
            helper.setBlock(new BlockPos(4, y, 3), Blocks.STONE);
        }
        Mob zombie = helper.spawn(
                EntityType.ZOMBIE, new BlockPos(1, 2, 3));
        zombie.setNoAi(true);
        zombie.setNoGravity(true);
        zombie.setItemSlot(
                EquipmentSlot.MAINHAND,
                new ItemStack(Items.IRON_SWORD));
        Vec3 near = zombie.position();
        Vec3 far = near.add(0.0D, 0.0D, 3.0D);
        LivingComponentAction userActions =
                LivingComponentAction.getComponent(
                        owner, PowerClass.STAND);
        float stamina = context.power().getStamina();
        try {
            zombie.setPos(far.x, far.y, far.z);
            ConditionCheck farPress =
                    lockTarget.checkSpecificConditions(context.power());
            helper.assertTrue(
                    !farPress.isPositive() && farPress.getWarning() == null,
                    "1.16 refused, silently, a Lock Target pressed at an "
                            + "armed mob the Stand cannot see, past 4 blocks of it.");
            zombie.setPos(near.x, near.y, near.z);
            helper.assertTrue(
                    lockTarget.checkSpecificConditions(context.power())
                            .isPositive(),
                    "Lock Target refused the armed mob 2 blocks away.");
            AbilityInput.keyPress(
                    LOCK_KEY,
                    lockTarget,
                    owner,
                    new FriendlyByteBuf(Unpooled.buffer()),
                    InputMethod.HOLD,
                    0.0F,
                    BufferingState.clickCanBuffer(),
                    lockTarget.abilityId);
            EntityActionInstance charge = userActions.getAction();
            helper.assertTrue(
                    charge != null
                            && charge.ability == lockTarget
                            && charge.getPhase() == ActionPhase.BUTTON_CHARGE,
                    "Lock Target did not start its charge at the mob in "
                            + "its user's Stand-power slot: " + charge);
            userActions.tick();
            userActions.tick();
            helper.assertTrue(
                    !charge.isOver()
                            && charge.getPhase() == ActionPhase.BUTTON_CHARGE,
                    "1.16 kept charging while the mob stayed in range: "
                            + charge.getPhase());
            zombie.setPos(far.x, far.y, far.z);
            userActions.tick();
            helper.assertTrue(
                    charge.isOver()
                            && userActions.getAction() == null,
                    "1.16 tickHeldAction stopped the Lock Target charge "
                            + "once the mob was out of the Stand's sight and 4 blocks: "
                            + charge.getPhase());
            for (int tick = 0; tick < 12; tick++) {
                userActions.tick();
            }
            helper.assertTrue(
                    !(zombie.hasData(KraftWorkAddon.LOCK_STATE)
                            && KraftWorkLockService.state(zombie).locked())
                            && context.power().getStamina() == stamina,
                    "A stopped Lock Target charge still locked the mob or "
                            + "paid: stamina " + stamina + " -> "
                            + context.power().getStamina());

            // A mob that lost its equipment in a held tick ended the hold
            // with tickHeldAction's message; in the tick the charge fired,
            // stopHeldAction(true) ended it unfired without one.
            RecordingPlayer recording = (RecordingPlayer) owner;
            zombie.setPos(near.x, near.y, near.z);
            for (int stripAfter : new int[] {
                    3, KraftWorkSpec.LOCK_TARGET_HOLD_TICKS - 1}) {
                zombie.setItemSlot(EquipmentSlot.MAINHAND,
                        new ItemStack(Items.IRON_SWORD));
                AbilityInput.keyRelease(LOCK_KEY, owner);
                recording.actionBar.clear();
                AbilityInput.keyPress(
                        LOCK_KEY,
                        lockTarget,
                        owner,
                        new FriendlyByteBuf(Unpooled.buffer()),
                        InputMethod.HOLD,
                        0.0F,
                        BufferingState.clickCanBuffer(),
                        lockTarget.abilityId);
                EntityActionInstance held = userActions.getAction();
                for (int tick = 0; tick < stripAfter; tick++) {
                    userActions.tick();
                }
                boolean charging = held != null
                        && held.ability == lockTarget
                        && !held.isOver()
                        && held.getPhase() == ActionPhase.BUTTON_CHARGE;
                zombie.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
                userActions.tick();
                boolean firing = stripAfter
                        == KraftWorkSpec.LOCK_TARGET_HOLD_TICKS - 1;
                List<String> said = recording.conditionMessages();
                List<String> expected = firing
                        ? List.of()
                        : List.of(CONDITION_MESSAGE
                                + "target_without_equipment");
                boolean locked = zombie.hasData(KraftWorkAddon.LOCK_STATE)
                        && KraftWorkLockService.state(zombie).locked();
                helper.assertTrue(
                        charging
                                && held.isOver()
                                && !locked
                                && said.equals(expected)
                                && context.power().getStamina() == stamina,
                        "1.16 ended a Lock Target hold whose mob lost its "
                                + "equipment after " + stripAfter
                                + " held ticks "
                                + (firing
                                        ? "in the tick the charge fired, "
                                                + "unfired and silently"
                                        : "with its message")
                                + ": charging " + charging + ", over "
                                + (held != null && held.isOver())
                                + ", locked " + locked + ", messages "
                                + said + ", stamina " + stamina + " -> "
                                + context.power().getStamina());
            }
        }
        finally {
            AbilityInput.keyRelease(LOCK_KEY, owner);
            zombie.discard();
            context.stand().discard();
            owner.connection.disconnect(Component.literal(
                    "Kraft Work lock range GameTest complete"));
        }
        helper.succeed();
    }

    // 1.16 kraft_work_lock_target was a StandAction (holdToFire(10, false)):
    // PowerBaseImpl.tickHeldAction fired the hold in the 10th tick from the
    // press and the user's power locked the mob in that tick, not in the
    // Stand's next one as a Stand task would.
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void lockTargetLocksTheMobInTheTickItsChargeFires(
            GameTestHelper helper) {
        TestContext context =
                prepareKraftWork(helper, "kw-lock-tick");
        ServerPlayer owner = context.owner();
        Ability lockTarget = context.power().getAbility(
                KraftWorkAddon.LOCK_TARGET);
        helper.assertTrue(
                lockTarget instanceof KraftWorkActionAbility,
                "Lock Target is not a Kraft Work action.");
        context.stand().setPos(
                owner.getX() + 4.0D, owner.getY(), owner.getZ());
        Mob zombie = helper.spawn(
                EntityType.ZOMBIE, new BlockPos(1, 2, 3));
        zombie.setNoAi(true);
        zombie.setNoGravity(true);
        zombie.setItemSlot(
                EquipmentSlot.MAINHAND,
                new ItemStack(Items.IRON_SWORD));
        LivingComponentAction userActions =
                LivingComponentAction.getComponent(
                        owner, PowerClass.STAND);
        try {
            AbilityInput.keyPress(
                    LOCK_KEY,
                    lockTarget,
                    owner,
                    new FriendlyByteBuf(Unpooled.buffer()),
                    InputMethod.HOLD,
                    0.0F,
                    BufferingState.clickCanBuffer(),
                    lockTarget.abilityId);
            EntityActionInstance charge = userActions.getAction();
            helper.assertTrue(
                    charge != null
                            && charge.ability == lockTarget
                            && charge.getPhase() == ActionPhase.BUTTON_CHARGE
                            && charge.getCurPhaseLength()
                                    == KraftWorkSpec.LOCK_TARGET_HOLD_TICKS,
                    "Lock Target did not start its 10-tick charge at the "
                            + "mob in its user's Stand-power slot: " + charge);
            int lockTick = -1;
            for (int tick = 1; tick <= 20 && lockTick < 0; tick++) {
                userActions.tick();
                if (zombie.hasData(KraftWorkAddon.LOCK_STATE)
                        && KraftWorkLockService.state(zombie).locked()) {
                    lockTick = tick;
                }
            }
            helper.assertTrue(
                    lockTick == KraftWorkSpec.LOCK_TARGET_HOLD_TICKS,
                    "1.16 locked the mob in the 10th tick from the Lock "
                            + "Target press, the tick its hold fired: "
                            + "locked in tick " + lockTick);
        }
        finally {
            AbilityInput.keyRelease(LOCK_KEY, owner);
            KraftWorkLockService.releaseTarget(owner, zombie);
            zombie.discard();
            context.stand().discard();
            owner.connection.disconnect(Component.literal(
                    "Kraft Work lock tick GameTest complete"));
        }
        helper.succeed();
    }

    // 1.16 KraftWorkLockTarget.getHoldDurationToFire was 0 with a block under
    // the crosshair, so PowerBaseImpl.onClickAction performed Lock Block at
    // the press instead of holding it: the block is locked in the press's
    // tick, the Stand's first after it, even if the key is let go before.
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void lockBlockLocksAtThePressWithNoChargeAsIn116(
            GameTestHelper helper) {
        TestContext context =
                prepareKraftWork(helper, "kw-lock-block-tick");
        ServerPlayer owner = context.owner();
        context.stand().setPos(
                owner.getX(), owner.getY(), owner.getZ() - 3.0D);
        BlockPos block = new BlockPos(1, 3, 3);
        helper.setBlock(block, Blocks.STONE);
        BlockPos absolute = helper.absolutePos(block);
        Ability lockTarget = context.power().getAbility(
                KraftWorkAddon.LOCK_TARGET);
        helper.assertTrue(
                lockTarget instanceof KraftWorkActionAbility
                        && lockTarget.checkSpecificConditions(
                                        context.power())
                                .isPositive(),
                "Lock Block was not admitted at the stone block.");
        LivingComponentAction userActions =
                LivingComponentAction.getComponent(
                        owner, PowerClass.STAND);
        try {
            AbilityInput.keyPress(
                    LOCK_KEY,
                    lockTarget,
                    owner,
                    new FriendlyByteBuf(Unpooled.buffer()),
                    InputMethod.HOLD,
                    0.0F,
                    BufferingState.clickCanBuffer(),
                    lockTarget.abilityId);
            EntityActionInstance press = userActions.getAction();
            helper.assertTrue(
                    press != null && press.ability == lockTarget,
                    "Lock Block did not start its action in its user's "
                            + "Stand-power slot: " + press);
            ActionPhase pressPhase = press.getPhase();
            // a tap let go before the slot's next tick
            AbilityInput.keyRelease(LOCK_KEY, owner);
            int lockTick = -1;
            for (int tick = 1; tick <= 5 && lockTick < 0; tick++) {
                userActions.tick();
                if (markersOn(helper, absolute) == 1) {
                    lockTick = tick;
                }
            }
            helper.assertTrue(
                    lockTick == 1 && pressPhase == ActionPhase.PERFORM,
                    "1.16 locked the block at the press, with no charge "
                            + "a release could cancel: the press started "
                            + pressPhase + ", locked in tick " + lockTick);
        }
        finally {
            AbilityInput.keyRelease(LOCK_KEY, owner);
            helper.getLevel().getEntitiesOfClass(
                            KraftWorkBlockMarkerEntity.class,
                            new AABB(absolute))
                    .forEach(KraftWorkBlockMarkerEntity::discard);
            context.stand().discard();
            owner.connection.disconnect(Component.literal(
                    "Kraft Work lock block tick GameTest complete"));
        }
        helper.succeed();
    }

    // 1.16 built Lock Target as a StandAction (InitStands :144), which the
    // user's power performed; only a Stand task waited out a new Stand's
    // summon lock (StandEntity.tick :1270-1281). Pressed right after Kraft
    // Work's summon, it runs in its user's Stand-power slot: Lock Block locks
    // in the press's tick and Lock Target's hold fires in the 10th, while the
    // summon fade-in runs on and the rest of the lock still holds back a
    // Stand task that follows.
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void lockTargetIsNotHeldBackByTheSummonLockAsIn116(
            GameTestHelper helper) {
        int summonLock = StandStatFormulas.getSummonLockTicks(
                KraftWorkSpec.SPEED);
        helper.assertTrue(summonLock > 1,
                "Fixture: Kraft Work's summon lock is " + summonLock
                        + " ticks.");

        TestContext blockContext = prepareUnsummonedKraftWork(
                helper, "kw-lock-block-summon");
        ServerPlayer blockOwner = blockContext.owner();
        BlockPos block = new BlockPos(1, 3, 3);
        helper.setBlock(block, Blocks.STONE);
        BlockPos absolute = helper.absolutePos(block);
        StandEntity blockStand = null;
        try {
            blockStand = pressLockTargetJustSummoned(helper, blockContext);
            int lockTick = -1;
            for (int tick = 1; tick <= 10 && lockTick < 0; tick++) {
                tickStandAndAction(blockStand);
                if (markersOn(helper, absolute) == 1) {
                    lockTick = tick;
                }
            }
            helper.assertTrue(lockTick == 1,
                    "1.16 locked the block at a press right after the "
                            + "summon: the new Stand's summon lock held "
                            + "Lock Block back to tick " + lockTick);
            helper.assertTrue(blockStand.getCurStandAction() == null
                            && blockStand.summonLockTicks == summonLock - 1
                            && blockStand.gradualSummonWeaknessTicks == 0,
                    "The rest of the summon lock no longer holds back "
                            + "a following Stand task: lock "
                            + blockStand.summonLockTicks + ", fade "
                            + blockStand.gradualSummonWeaknessTicks
                            + ", expected lock " + (summonLock - 1));
        }
        finally {
            AbilityInput.keyRelease(LOCK_KEY, blockOwner);
            helper.getLevel().getEntitiesOfClass(
                            KraftWorkBlockMarkerEntity.class,
                            new AABB(absolute))
                    .forEach(KraftWorkBlockMarkerEntity::discard);
            helper.setBlock(block, Blocks.AIR);
            if (blockStand != null) {
                blockStand.discard();
            }
            blockOwner.connection.disconnect(Component.literal(
                    "Kraft Work summon lock block GameTest complete"));
        }

        TestContext mobContext = prepareUnsummonedKraftWork(
                helper, "kw-lock-mob-summon");
        ServerPlayer mobOwner = mobContext.owner();
        Mob zombie = helper.spawn(
                EntityType.ZOMBIE, new BlockPos(1, 2, 3));
        zombie.setNoAi(true);
        zombie.setNoGravity(true);
        zombie.setItemSlot(
                EquipmentSlot.MAINHAND,
                new ItemStack(Items.IRON_SWORD));
        StandEntity mobStand = null;
        try {
            mobStand = pressLockTargetJustSummoned(helper, mobContext);
            int lockTick = -1;
            for (int tick = 1; tick <= 20 && lockTick < 0; tick++) {
                tickStandAndAction(mobStand);
                if (tick == 1) {
                    helper.assertTrue(
                            mobStand.summonLockTicks == summonLock - 1
                                    && mobStand.gradualSummonWeaknessTicks
                                            == 0,
                            "1.16 counted the new Stand's summon lock (its "
                                    + "fade-in) on in place under a held "
                                    + "StandAction: lock "
                                    + mobStand.summonLockTicks + ", fade "
                                    + mobStand.gradualSummonWeaknessTicks
                                    + ", expected lock "
                                    + (summonLock - 1) + ", fade 0");
                }
                if (zombie.hasData(KraftWorkAddon.LOCK_STATE)
                        && KraftWorkLockService.state(zombie).locked()) {
                    lockTick = tick;
                }
            }
            helper.assertTrue(
                    lockTick == KraftWorkSpec.LOCK_TARGET_HOLD_TICKS,
                    "1.16 fired a Lock Target hold pressed right after "
                            + "the summon in the 10th tick from the press: "
                            + "the new Stand's summon lock held it to tick "
                            + lockTick);
        }
        finally {
            AbilityInput.keyRelease(LOCK_KEY, mobOwner);
            KraftWorkLockService.releaseTarget(mobOwner, zombie);
            zombie.discard();
            if (mobStand != null) {
                mobStand.discard();
            }
            mobOwner.connection.disconnect(Component.literal(
                    "Kraft Work summon lock mob GameTest complete"));
        }
        helper.succeed();
    }

    private static StandEntity pressLockTargetJustSummoned(
            GameTestHelper helper, TestContext context) {
        Ability lockTarget = context.power().getAbility(
                KraftWorkAddon.LOCK_TARGET);
        helper.assertTrue(
                lockTarget instanceof KraftWorkActionAbility
                        && lockTarget.checkSpecificConditions(
                                        context.power())
                                .isPositive(),
                "Lock Target was not admitted with Kraft Work not out.");
        helper.assertTrue(
                KraftWorkAddon.KRAFT_WORK.get().summon(
                        context.owner(), context.power()),
                "Kraft Work could not be summoned.");
        // The Stand spawns on its user until its first tick, which comes
        // after its user's slot has ticked; keep it out of the aim ray,
        // inside the test's barrier box (the range is the Stand's).
        StandEntity summoned = context.power().getSummonedStandEntity();
        if (summoned != null) {
            summoned.setPos(context.owner().getX() + 4.0D,
                    context.owner().getY(), context.owner().getZ());
        }
        AbilityInput.keyPress(
                LOCK_KEY,
                lockTarget,
                context.owner(),
                new FriendlyByteBuf(Unpooled.buffer()),
                InputMethod.HOLD,
                0.0F,
                BufferingState.clickCanBuffer(),
                lockTarget.abilityId);
        StandEntity stand = context.power().getSummonedStandEntity();
        helper.assertTrue(stand != null && stand.isAddedToLevel()
                        && stand.summonLockTicks
                                + stand.gradualSummonWeaknessTicks
                                == StandStatFormulas.getSummonLockTicks(
                                        KraftWorkSpec.SPEED),
                "Fixture: Kraft Work was not just summoned: " + stand);
        EntityActionInstance press = LivingComponentAction.getCurEntityAction(
                context.owner(), PowerClass.STAND);
        helper.assertTrue(press != null && press.ability == lockTarget
                        && press.getPerformer() == stand
                        && stand.getCurStandAction() == null,
                "The Lock Target press did not go to its user's Stand-power "
                        + "slot, performed by the summoned Kraft Work: "
                        + press + ", Kraft Work's own action "
                        + stand.getCurStandAction());
        return stand;
    }

    // the level's order: the user, which came first, ticks its Stand-power
    // slot (EntityTickEvent.Post); then the Stand's own tick counts its
    // summon lock down and its action ticks
    private static void tickStandAndAction(StandEntity stand) {
        LivingEntity user = stand.getUser();
        if (user != null) {
            LivingComponentAction.getComponent(user, PowerClass.STAND).tick();
        }
        stand.tick();
        LivingComponentAction.getComponent(stand).tick();
    }

    // 1.16 StandEntity.tick :1270-1281 counted a new Stand's summon lock
    // down through a stun and held back only a Stand task meanwhile. A
    // release (a StandAction with ignoresPerformerStun, InitStands :148)
    // pressed as Kraft Work is summoned goes off in the press's tick, the
    // Stand stunned or not, and a punch (a StandEntityLightAttack, :136)
    // pressed after it first ticks in the tick after the lock's own ticks,
    // as energy accumulation (a StandEntityAction, :146) pressed at once does.
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void standTasksWaitOnlyTheSummonLocksOwnTicksThroughAStunAsIn116(
            GameTestHelper helper) {
        int summonLock = StandStatFormulas.getSummonLockTicks(
                KraftWorkSpec.SPEED);
        helper.assertTrue(summonLock > 1,
                "Fixture: Kraft Work's summon lock is " + summonLock
                        + " ticks.");
        List<String> wrong = new ArrayList<>();
        for (boolean stunned : new boolean[] {false, true}) {
            TestContext context = prepareUnsummonedKraftWork(helper,
                    stunned ? "kw-stunned-lock" : "kw-lock-punch");
            ServerPlayer owner = context.owner();
            StandPower power = context.power();
            StandEntity stand = null;
            // a Creative user takes no stun
            owner.setGameMode(GameType.SURVIVAL);
            try {
                Ability release = power.getAbility(KraftWorkAddon.RELEASE_ALL);
                Ability punch = power.getAbility(KraftWorkAddon.PUNCH);
                helper.assertTrue(
                        release instanceof KraftWorkActionAbility releaseAll
                                && releaseAll.ignoresPerformerStun()
                                && punch != null,
                        "Fixture: Kraft Work's release all and punch "
                                + "are missing.");
                helper.assertTrue(
                        KraftWorkAddon.KRAFT_WORK.get().summon(owner, power),
                        "Kraft Work could not be summoned.");
                stand = power.getSummonedStandEntity();
                helper.assertTrue(stand != null && stand.isAddedToLevel()
                                && stand.summonLockTicks == summonLock,
                        "Fixture: Kraft Work was not just summoned: "
                                + stand);
                pressWithKraftWorkOut(helper, owner, RELEASE_KEY, release,
                        InputMethod.CLICK);
                LivingComponentAction userActions =
                        LivingComponentAction.getComponent(
                                owner, PowerClass.STAND);
                EntityActionInstance released = userActions.getAction();
                helper.assertTrue(released != null
                                && released.ability == release
                                && released.getPerformer() == stand,
                        "The release press did not go to its user's "
                                + "Stand-power slot, performed by the summoned "
                                + "Kraft Work: " + released);
                if (stunned) {
                    owner.addEffect(new MobEffectInstance(
                            ModStatusEffects.STUN, 100));
                    helper.assertTrue(ModStatusEffects.isStunned(stand),
                            "Fixture: the user's stun did not stun Kraft "
                                    + "Work.");
                }
                List<Integer> locks = new ArrayList<>();
                List<Integer> fades = new ArrayList<>();
                tickStandAndAction(stand);
                locks.add(stand.summonLockTicks);
                fades.add(stand.gradualSummonWeaknessTicks);
                boolean releasedInTick1 = released.isOver()
                        && userActions.getAction() == null;
                if (stunned) {
                    owner.removeEffect(ModStatusEffects.STUN);
                    stand.removeEffect(ModStatusEffects.STUN);
                    helper.assertTrue(!ModStatusEffects.isStunned(stand),
                            "Fixture: Kraft Work's stun did not end.");
                }
                pressWithKraftWorkOut(helper, owner, PUNCH_KEY, punch,
                        InputMethod.CLICK);
                EntityActionInstance punched = stand.getCurStandAction();
                helper.assertTrue(punched != null
                                && punched.ability == punch,
                        "Kraft Work did not take the punch press: "
                                + punched);
                float ticksAtPress = punched.getFullTicksPassed();
                int firstTick = -1;
                for (int tick = 2; tick <= 10 && firstTick < 0; tick++) {
                    tickStandAndAction(stand);
                    locks.add(stand.summonLockTicks);
                    fades.add(stand.gradualSummonWeaknessTicks);
                    if (stand.getCurStandAction() != punched
                            || punched.getFullTicksPassed()
                                    != ticksAtPress) {
                        firstTick = tick;
                    }
                }
                boolean lockCounted = true;
                for (int i = 0; i < locks.size(); i++) {
                    lockCounted &= locks.get(i)
                                    == Math.max(summonLock - 1 - i, 0)
                            && fades.get(i) == 0;
                }
                if (!releasedInTick1 || firstTick != summonLock + 1
                        || !lockCounted) {
                    wrong.add((stunned
                                    ? "stunned right after the summon"
                                    : "not stunned")
                            + ": the release " + (releasedInTick1
                                    ? "went off in tick 1"
                                    : "still ran after tick 1")
                            + ", the punch first ticked in tick "
                            + firstTick + " (1.16: " + (summonLock + 1)
                            + "), the lock after each tick " + locks
                            + ", the fade " + fades);
                }
            }
            finally {
                AbilityInput.keyRelease(RELEASE_KEY, owner);
                AbilityInput.keyRelease(PUNCH_KEY, owner);
                owner.removeEffect(ModStatusEffects.STUN);
                if (stand != null) {
                    stand.discard();
                }
                owner.connection.disconnect(Component.literal(
                        "Kraft Work stunned summon lock GameTest complete"));
            }
        }
        TestContext energyContext = prepareUnsummonedKraftWork(helper,
                "kw-lock-energy");
        ServerPlayer energyOwner = energyContext.owner();
        StandPower energyPower = energyContext.power();
        StandEntity energyStand = null;
        try {
            Ability energy = energyPower.getAbility(
                    KraftWorkAddon.ENERGY_ACCUMULATION);
            helper.assertTrue(
                    KraftWorkAddon.KRAFT_WORK.get().summon(
                            energyOwner, energyPower),
                    "Kraft Work could not be summoned.");
            energyStand = energyPower.getSummonedStandEntity();
            helper.assertTrue(energyStand != null
                            && energyStand.summonLockTicks == summonLock,
                    "Fixture: Kraft Work was not just summoned: "
                            + energyStand);
            pressWithKraftWorkOut(helper, energyOwner, ENERGY_KEY, energy,
                    InputMethod.HOLD);
            EntityActionInstance charging = energyStand.getCurStandAction();
            helper.assertTrue(charging != null && charging.ability == energy,
                    "Kraft Work did not take the energy accumulation press: "
                            + charging);
            float ticksAtPress = charging.getFullTicksPassed();
            int firstTick = -1;
            for (int tick = 1; tick <= 10 && firstTick < 0; tick++) {
                tickStandAndAction(energyStand);
                if (energyStand.getCurStandAction() != charging
                        || charging.getFullTicksPassed() != ticksAtPress) {
                    firstTick = tick;
                }
            }
            if (firstTick != summonLock + 1) {
                wrong.add("energy accumulation pressed as Kraft Work was "
                        + "summoned first ticked in tick " + firstTick
                        + " (1.16: " + (summonLock + 1) + ")");
            }
        }
        finally {
            AbilityInput.keyRelease(ENERGY_KEY, energyOwner);
            if (energyStand != null) {
                energyStand.discard();
            }
            energyOwner.connection.disconnect(Component.literal(
                    "Kraft Work energy summon lock GameTest complete"));
        }
        helper.assertTrue(wrong.isEmpty(),
                "1.16 counted a new Stand's summon lock through a stun and "
                        + "held back only its tasks, so a punch pressed after "
                        + "a release, or energy accumulation, first ticked "
                        + "in the tick after the lock's own " + summonLock
                        + " ticks: " + String.join("; ", wrong));
        helper.succeed();
    }

    // 1.16 KraftWorkLockArmor was a plain StandAction (InitStands :139), and
    // the follow-up of the heavy punch (getRecoveryFollowup) was given only
    // while canSetFollowUp -> PowerBaseImpl.checkRequirements :263-265 passed:
    // a stunned performer was refused, so the heavy key stayed the heavy
    // punch and a press of Lock Equipment showed the stun message.
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void lockEquipmentFollowupIsNotOfferedToAStunnedStandAsIn116(
            GameTestHelper helper) {
        TestContext context = prepareKraftWork(helper, "kw-lockarmor-stun");
        ServerPlayer owner = context.owner();
        StandPower power = context.power();
        StandEntity stand = context.stand();
        owner.setGameMode(GameType.SURVIVAL);
        Mob zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(1, 2, 3));
        try {
            zombie.setNoAi(true);
            zombie.setItemSlot(EquipmentSlot.HEAD,
                    new ItemStack(Items.IRON_HELMET));
            Ability heavy = power.getAbility(KraftWorkAddon.HEAVY_PUNCH);
            Ability lockArmor = power.getAbility(KraftWorkAddon.LOCK_ARMOR);
            helper.assertTrue(heavy != null && lockArmor != null,
                    "Fixture: Kraft Work's heavy punch or Lock Equipment "
                            + "is missing.");
            BufferingState started = pressResolved(helper, power, owner,
                    KraftWorkAddon.HEAVY_PUNCH, InputMethod.CLICK,
                    PUNCH_KEY);
            AbilityInput.keyRelease(PUNCH_KEY, owner);
            helper.assertTrue(started.isActionSuccess
                            && stand.getCurStandAction()
                                    instanceof KraftWorkHeavyPunchAbility
                                            .KraftWorkHeavyPunch,
                    "Fixture: the heavy punch did not start: "
                            + stand.getCurStandAction());
            helper.assertTrue(
                    heavyKeyReads(power, heavy) == lockArmor
                            && lockArmor.checkConditions(power).isPositive(),
                    "Fixture: the heavy key does not read Lock Equipment "
                            + "over an armoured mob: "
                            + heavyKeyReads(power, heavy));
            // 1.16 StandEntityActionModifier.greenSelection: the selected follow-up is framed green while usable
            ConditionCheck drawn = heavyKeyDrawn(power, heavy, owner);
            helper.assertTrue(lockArmorTint(lockArmor, power, drawn).equals(OptionalInt.of(0xFF00FF00)),
                    "1.16 framed the selected Lock Equipment green while it was usable ("
                            + drawn + "); the port frames it " + lockArmorTint(lockArmor, power, drawn));
            helper.assertTrue(lockArmorTint(heavy, power, ConditionCheck.POSITIVE).isEmpty(),
                    "1.16 framed the selected heavy punch white; the port frames it green");

            owner.addEffect(new MobEffectInstance(
                    ModStatusEffects.STUN, 100));
            helper.assertTrue(ModStatusEffects.isStunned(stand),
                    "Fixture: the user's stun did not stun Kraft Work.");
            ConditionCheck refused = lockArmor.checkConditions(power);
            helper.assertTrue(heavyKeyReads(power, heavy) == heavy,
                    "1.16 gave a stunned Stand no Lock Equipment follow-up "
                            + "(canSetFollowUp), yet the heavy key reads "
                            + heavyKeyReads(power, heavy));
            helper.assertTrue(!refused.isPositive()
                            && refused.getWarning() != null
                            && refused.getWarning().getContents()
                                    instanceof TranslatableContents stun
                            && stun.getKey().equals(STUN_MESSAGE),
                    "1.16 refused a press of Lock Equipment from a stunned "
                            + "Stand with the stun message; the port "
                            + (refused.isPositive() ? "accepted it"
                                    : "answered " + refused.getWarning()));
            helper.assertTrue(lockArmorTint(lockArmor, power, refused).isEmpty(),
                    "1.16 framed a selected Lock Equipment white once it was refused; the port frames it "
                            + lockArmorTint(lockArmor, power, refused));

            owner.removeEffect(ModStatusEffects.STUN);
            stand.removeEffect(ModStatusEffects.STUN);
            helper.assertTrue(heavyKeyReads(power, heavy) == lockArmor,
                    "Once the stun is over the heavy key reads Lock "
                            + "Equipment again: "
                            + heavyKeyReads(power, heavy));
            helper.succeed();
        }
        finally {
            AbilityInput.keyRelease(PUNCH_KEY, owner);
            owner.removeEffect(ModStatusEffects.STUN);
            zombie.discard();
            stand.discard();
            owner.connection.disconnect(Component.literal(
                    "Kraft Work stunned follow-up GameTest complete"));
        }
    }

    // what the power resolves the heavy punch key to now (the test does not
    // tick the power, so its own once-a-tick resolution would be stale)
    private static ConditionCheck heavyKeyDrawn(StandPower power, Ability heavy, ServerPlayer owner) {
        AvailableAbilities moves = new AvailableAbilities();
        moves.update(power, power.getMoveset());
        var key = moves.getContextVariationContainer(heavy);
        return key != null ? key.drawnCondition(owner) : null;
    }

    private static OptionalInt lockArmorTint(Ability ability, StandPower power, ConditionCheck drawn) {
        return KraftWorkHeavyPunchAbility.LockArmorFollowup.selectionTint(new AbilitySelectionVisualQuery(
                ability, power, drawn, AbilitySelectionSurface.HOTBAR_SELECTED));
    }

    private static Ability heavyKeyReads(StandPower power, Ability heavy) {
        AvailableAbilities moves = new AvailableAbilities();
        moves.update(power, power.getMoveset());
        var key = moves.getContextVariationContainer(heavy);
        return key != null ? key.ability : null;
    }

    private static void pressWithKraftWorkOut(GameTestHelper helper,
            ServerPlayer owner, short key, Ability ability,
            InputMethod inputMethod) {
        helper.assertTrue(AbilityInput.withConditionCheck(ability, owner),
                "Fixture: " + ability.abilityId
                        + " was not admitted with Kraft Work out.");
        FriendlyByteBuf input = new FriendlyByteBuf(Unpooled.buffer());
        try {
            AbilityInput.keyPress(key, ability, owner, input,
                    inputMethod, 0.0F, BufferingState.clickCanBuffer(),
                    ability.abilityId);
        }
        finally {
            input.release();
        }
    }

    // 1.16 InitStands :141-154 gave Kraft Work's StandActions no
    // autoSummonStand(): pressed with Kraft Work not out they summoned
    // nothing and the user's power performed them (StandAction.getPerformer
    // :191-193, onClick :263-267), Lock Block at the press, Lock Target's
    // hold in its 10th tick, which a hit stopped, Place Item from the user.
    // Energy accumulation, a StandEntityAction, still summoned the Stand.
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void standActionsLeaveKraftWorkUnsummonedAsIn116(
            GameTestHelper helper) {
        TestContext context = prepareUnsummonedKraftWork(
                helper, "kw-unsummoned");
        ServerPlayer owner = context.owner();
        StandPower power = context.power();
        // its power's actions the user performs: its Stand-power slot
        LivingComponentAction userActions =
                LivingComponentAction.getComponent(owner, PowerClass.STAND);
        Ability lockTarget = power.getAbility(KraftWorkAddon.LOCK_TARGET);
        BlockPos block = new BlockPos(1, 3, 3);
        helper.setBlock(block, Blocks.STONE);
        BlockPos absolute = helper.absolutePos(block);
        Mob zombie = null;
        List<KraftWorkItemProjectile> placed = List.of();
        owner.setGameMode(GameType.SURVIVAL);
        // before the first press: the power checks its moves once a tick
        owner.setItemSlot(EquipmentSlot.OFFHAND,
                new ItemStack(Items.COBBLESTONE));
        try {
            pressUnsummoned(helper, context, LOCK_KEY, lockTarget,
                    InputMethod.HOLD);
            EntityActionInstance press = userActions.getAction();
            helper.assertTrue(power.getSummonedStandEntity() == null
                            && press != null && press.ability == lockTarget
                            && press.getPerformer() == owner,
                    "1.16 performed Lock Block with Kraft Work not out, "
                            + "summoning nothing: Stand "
                            + power.getSummonedStandEntity()
                            + ", user action " + press);
            int lockTick = -1;
            for (int tick = 1; tick <= 5 && lockTick < 0; tick++) {
                userActions.tick();
                if (markersOn(helper, absolute) == 1) {
                    lockTick = tick;
                }
            }
            helper.assertTrue(lockTick == 1
                            && power.getSummonedStandEntity() == null,
                    "1.16's user locked the block at the press with no "
                            + "Stand out: locked in tick " + lockTick);
            AbilityInput.keyRelease(LOCK_KEY, owner);
            helper.getLevel().getEntitiesOfClass(
                            KraftWorkBlockMarkerEntity.class,
                            new AABB(absolute))
                    .forEach(KraftWorkBlockMarkerEntity::discard);
            helper.setBlock(block, Blocks.AIR);

            zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(1, 2, 3));
            zombie.setNoAi(true);
            zombie.setNoGravity(true);
            zombie.setItemSlot(EquipmentSlot.MAINHAND,
                    new ItemStack(Items.IRON_SWORD));
            pressUnsummoned(helper, context, LOCK_KEY, lockTarget,
                    InputMethod.HOLD);
            lockTick = -1;
            for (int tick = 1; tick <= 20 && lockTick < 0; tick++) {
                userActions.tick();
                if (zombie.hasData(KraftWorkAddon.LOCK_STATE)
                        && KraftWorkLockService.state(zombie).locked()) {
                    lockTick = tick;
                }
            }
            helper.assertTrue(
                    lockTick == KraftWorkSpec.LOCK_TARGET_HOLD_TICKS
                            && power.getSummonedStandEntity() == null,
                    "1.16's user fired Lock Target's hold in its 10th tick "
                            + "with no Stand out: locked in tick "
                            + lockTick + ", Stand "
                            + power.getSummonedStandEntity());
            AbilityInput.keyRelease(LOCK_KEY, owner);
            KraftWorkLockService.releaseTarget(owner, zombie);

            pressUnsummoned(helper, context, LOCK_KEY, lockTarget,
                    InputMethod.HOLD);
            for (int tick = 1; tick <= 3; tick++) {
                userActions.tick();
            }
            KraftWorkActionAbility.KraftWorkAction
                    .cancelLockTargetCharge(owner);
            for (int tick = 4; tick <= 12; tick++) {
                userActions.tick();
            }
            helper.assertTrue(
                    !(zombie.hasData(KraftWorkAddon.LOCK_STATE)
                            && KraftWorkLockService.state(zombie).locked()),
                    "1.16's cancelHeldOnGettingAttacked stopped the user's "
                            + "held Lock Target: the mob was locked after "
                            + "the hit");
            AbilityInput.keyRelease(LOCK_KEY, owner);

            pressUnsummoned(helper, context, PLACE_KEY,
                    power.getAbility(KraftWorkAddon.PLACE_PROJECTILE),
                    InputMethod.CLICK);
            for (int tick = 1; tick <= 3; tick++) {
                userActions.tick();
            }
            placed = helper.getLevel().getEntitiesOfClass(
                    KraftWorkItemProjectile.class,
                    new AABB(owner.blockPosition()).inflate(4.0D));
            helper.assertTrue(placed.size() == 1
                            && !placed.getFirst().hasController()
                            && owner.getOffhandItem().isEmpty()
                            && power.getSummonedStandEntity() == null,
                    "1.16's user placed the off-hand item with no Stand "
                            + "out: " + placed.size() + " placed, "
                            + (!placed.isEmpty()
                                            && placed.getFirst()
                                                    .hasController()
                                    ? "controlled by a Stand, "
                                    : "")
                            + "off hand " + owner.getOffhandItem()
                            + ", Stand " + power.getSummonedStandEntity());

            Ability energy = power.getAbility(
                    KraftWorkAddon.ENERGY_ACCUMULATION);
            FriendlyByteBuf input = new FriendlyByteBuf(Unpooled.buffer());
            try {
                AbilityInput.keyPress(ENERGY_KEY, energy, owner, input,
                        InputMethod.HOLD, 0.0F,
                        BufferingState.clickCanBuffer(), energy.abilityId);
            }
            finally {
                input.release();
            }
            helper.assertTrue(power.getSummonedStandEntity() != null,
                    "1.16 summoned Kraft Work for energy accumulation, a "
                            + "StandEntityAction; the press left it out.");
        }
        finally {
            AbilityInput.keyRelease(LOCK_KEY, owner);
            AbilityInput.keyRelease(ENERGY_KEY, owner);
            helper.getLevel().getEntitiesOfClass(
                            KraftWorkBlockMarkerEntity.class,
                            new AABB(absolute))
                    .forEach(KraftWorkBlockMarkerEntity::discard);
            helper.setBlock(block, Blocks.AIR);
            if (zombie != null) {
                KraftWorkLockService.releaseTarget(owner, zombie);
                zombie.discard();
            }
            placed.forEach(KraftWorkItemProjectile::discard);
            StandEntity stand = power.getSummonedStandEntity();
            if (stand != null) {
                stand.discard();
            }
            owner.connection.disconnect(Component.literal(
                    "Kraft Work unsummoned actions GameTest complete"));
        }
        helper.succeed();
    }

    // 1.16's power held and performed Kraft Work's StandActions (InitStands
    // :141-154), never Kraft Work's task: only a StandEntityAction checked
    // the task, so one pressed in its punch's windup went off at once beside
    // it, and one pressed during its held barrage went off and ended the
    // barrage's hold into its recovery (stopHeldAction(false)). Its performer
    // was Kraft Work while out, else the user (StandAction.getPerformer
    // :190-193), whose stun refused it at the press unless it ignored its
    // performer's stun.
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void standActionsGoOffAtOnceBesideKraftWorksTasksAsIn116(
            GameTestHelper helper) {
        List<String> wrong = new ArrayList<>();
        TestContext punchContext = prepareKraftWork(helper, "kw-port-punch");
        try {
            ServerPlayer owner = punchContext.owner();
            StandEntity stand = punchContext.stand();
            StandPower power = punchContext.power();
            pressResolved(helper, power, owner, KraftWorkAddon.PUNCH,
                    InputMethod.CLICK, PUNCH_KEY);
            EntityActionInstance punch = stand.getCurStandAction();
            helper.assertTrue(punch != null
                            && punch.ability == power.getAbility(
                                    KraftWorkAddon.PUNCH)
                            && punch.getPhase() == ActionPhase.WINDUP,
                    "Fixture: Kraft Work's punch is not in its windup: "
                            + punch);
            BufferingState pressed = pressResolved(helper, power, owner,
                    KraftWorkAddon.RELEASE_ALL, InputMethod.CLICK,
                    RELEASE_KEY);
            EntityActionInstance release =
                    LivingComponentAction.getCurEntityAction(
                            owner, PowerClass.STAND);
            if (!pressed.isActionSuccess || pressed.shouldBuffer
                    || release == null
                    || release.ability != power.getAbility(
                            KraftWorkAddon.RELEASE_ALL)
                    || release.getPerformer() != stand
                    || stand.getCurStandAction() != punch
                    || punch.getPhase() != ActionPhase.WINDUP) {
                wrong.add("Release All pressed in the punch's windup: "
                        + "started " + pressed.isActionSuccess
                        + ", buffered " + pressed.shouldBuffer
                        + ", the user's Stand-power action " + release
                        + (release != null
                                ? " performed by " + release.getPerformer()
                                : "")
                        + ", Kraft Work's action " + stand.getCurStandAction()
                        + " in " + punch.getPhase());
            }
        }
        finally {
            AbilityInput.keyRelease(PUNCH_KEY, punchContext.owner());
            AbilityInput.keyRelease(RELEASE_KEY, punchContext.owner());
            punchContext.stand().discard();
            punchContext.owner().connection.disconnect(Component.literal(
                    "Kraft Work port punch GameTest complete"));
        }

        TestContext barrageContext =
                prepareKraftWork(helper, "kw-port-barrage");
        try {
            ServerPlayer owner = barrageContext.owner();
            StandEntity stand = barrageContext.stand();
            StandPower power = barrageContext.power();
            pressResolved(helper, power, owner, KraftWorkAddon.BARRAGE,
                    InputMethod.HOLD, PUNCH_KEY);
            EntityActionInstance barrage = stand.getCurStandAction();
            helper.assertTrue(barrage != null
                            && barrage.getPhase() == ActionPhase.PERFORM
                            && barrage.phasesLength.getFloat(
                                    ActionPhase.RECOVERY) > 0
                            && barrage.isHoldTypeStandAction()
                            && AbilityInput.isHeldByKey(owner, barrage),
                    "Fixture: Kraft Work is not holding a barrage with a "
                            + "recovery: " + barrage + " in "
                            + (barrage != null ? barrage.getPhase() : null));
            LivingComponentAction.getComponent(owner, PowerClass.STAND)
                    .tick();
            LivingComponentAction.getComponent(stand).tick();
            BufferingState pressed = pressResolved(helper, power, owner,
                    KraftWorkAddon.RELEASE_PROJECTILE, InputMethod.CLICK,
                    RELEASE_KEY);
            EntityActionInstance release =
                    LivingComponentAction.getCurEntityAction(
                            owner, PowerClass.STAND);
            if (!pressed.isActionSuccess || pressed.shouldBuffer
                    || release == null
                    || release.ability != power.getAbility(
                            KraftWorkAddon.RELEASE_PROJECTILE)
                    || release.getPerformer() != stand
                    || stand.getCurStandAction() != barrage
                    || barrage.getPhase() != ActionPhase.RECOVERY
                    || AbilityInput.isHeldByKey(owner, barrage)) {
                wrong.add("Release Projectile pressed during the held "
                        + "barrage: started " + pressed.isActionSuccess
                        + ", buffered " + pressed.shouldBuffer
                        + ", the user's Stand-power action " + release
                        + ", the barrage " + barrage.getPhase()
                        + ", held by its key "
                        + AbilityInput.isHeldByKey(owner, barrage));
            }
        }
        finally {
            AbilityInput.keyRelease(PUNCH_KEY, barrageContext.owner());
            AbilityInput.keyRelease(RELEASE_KEY, barrageContext.owner());
            barrageContext.stand().discard();
            barrageContext.owner().connection.disconnect(Component.literal(
                    "Kraft Work port barrage GameTest complete"));
        }

        TestContext stunContext =
                prepareUnsummonedKraftWork(helper, "kw-port-stun");
        ServerPlayer stunOwner = stunContext.owner();
        try {
            StandPower power = stunContext.power();
            stunOwner.setGameMode(GameType.SURVIVAL);
            stunOwner.setItemSlot(EquipmentSlot.OFFHAND,
                    new ItemStack(Items.COBBLESTONE));
            stunOwner.addEffect(new MobEffectInstance(
                    ModStatusEffects.STUN, 100));
            helper.assertTrue(ModStatusEffects.isStunned(stunOwner),
                    "Fixture: the Kraft Work user is not stunned.");
            RecordingPlayer recording = (RecordingPlayer) stunOwner;
            recording.actionBar.clear();
            BufferingState place = pressResolved(helper, power, stunOwner,
                    KraftWorkAddon.PLACE_PROJECTILE, InputMethod.CLICK,
                    PLACE_KEY);
            List<String> placeMessages = recording.conditionMessages();
            EntityActionInstance placed =
                    LivingComponentAction.getCurEntityAction(
                            stunOwner, PowerClass.STAND);
            if (place.isActionSuccess || placed != null
                    || !placeMessages.equals(List.of(STUN_MESSAGE))
                    || stunOwner.getOffhandItem().isEmpty()) {
                wrong.add("Place Projectile pressed by its stunned user with "
                        + "Kraft Work not out: started "
                        + place.isActionSuccess + ", the user's Stand-power "
                        + "action " + placed + ", messages " + placeMessages
                        + " (1.16: refused for its performer's stun, the "
                        + "user)");
            }
            BufferingState release = pressResolved(helper, power, stunOwner,
                    KraftWorkAddon.RELEASE_ALL, InputMethod.CLICK,
                    RELEASE_KEY);
            EntityActionInstance released =
                    LivingComponentAction.getCurEntityAction(
                            stunOwner, PowerClass.STAND);
            if (!release.isActionSuccess || released == null
                    || released.getPerformer() != stunOwner
                    || power.getSummonedStandEntity() != null) {
                wrong.add("Release All, which ignored its performer's "
                        + "stun, pressed by its stunned user with Kraft Work "
                        + "not out: started " + release.isActionSuccess
                        + ", the user's Stand-power action " + released
                        + ", Stand " + power.getSummonedStandEntity());
            }
        }
        finally {
            AbilityInput.keyRelease(PLACE_KEY, stunOwner);
            AbilityInput.keyRelease(RELEASE_KEY, stunOwner);
            stunOwner.removeEffect(ModStatusEffects.STUN);
            LivingComponentAction.getComponent(stunOwner, PowerClass.STAND)
                    .setAction(null, SyncType.NO_SYNC);
            stunOwner.connection.disconnect(Component.literal(
                    "Kraft Work port stun GameTest complete"));
        }
        helper.assertTrue(wrong.isEmpty(),
                "1.16's power performed Kraft Work's StandActions beside "
                        + "Kraft Work's task, with Kraft Work, else the user, "
                        + "as their performer: " + String.join("; ", wrong));
        helper.succeed();
    }

    // 1.16 Action.checkConditions (:140-143) refused, first and without a
    // message, an action whose canBeUsedDuringPlayerAction was false while
    // its user ran a continuous player action such as Hamon Overdrive Beat:
    // Lock Target, Lock Block too (KraftWorkLockTarget :268-270), and Place
    // Projectile (KraftWorkPlaceProjectile :562-564) opted out, and the HUD
    // drew them unavailable; Kraft Work's other StandActions went through
    // (StandAction :138-141). Once the beat was over both went off.
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void lockTargetAndPlaceProjectileAreRefusedDuringAPlayerActionAsIn116(
            GameTestHelper helper) {
        TestContext context = prepareUnsummonedKraftWork(
                helper, "kw-player-action");
        ServerPlayer owner = context.owner();
        StandPower power = context.power();
        RecordingPlayer recording = (RecordingPlayer) owner;
        LivingComponentAction ownActions =
                LivingComponentAction.getComponent(owner);
        LivingComponentAction userActions =
                LivingComponentAction.getComponent(owner, PowerClass.STAND);
        BlockPos block = new BlockPos(1, 3, 3);
        helper.setBlock(block, Blocks.STONE);
        BlockPos absolute = helper.absolutePos(block);
        List<String> wrong = new ArrayList<>();
        owner.setGameMode(GameType.SURVIVAL);
        try {
            PlayerPower hamonPower = PowerClass.PLAYER_POWER.attachGet(owner);
            hamonPower.setPowerType(ModPlayerPowers.HAMON.get());
            HamonData hamon = PlayerPower.getPowerData(
                    owner, ModPlayerPowers.HAMON).orElseThrow();
            hamon.learnSkill(ModHamonSkills.OVERDRIVE.get());
            hamon.setBreathStability(hamon.getMaxBreathStability());
            hamon.setEnergy(hamon.getMaxEnergy());
            Ability lockTarget = power.getAbility(KraftWorkAddon.LOCK_TARGET);
            Ability place = power.getAbility(
                    KraftWorkAddon.PLACE_PROJECTILE);
            helper.assertTrue(lockTarget != null && place != null
                            && lockTarget.checkConditions(power).isPositive(),
                    "Fixture: Lock Block was not admitted at the stone "
                            + "block.");

            // its free off hand: the beat's own condition
            pressResolved(helper, hamonPower, owner, "hamon_beat",
                    InputMethod.CLICK, BEAT_KEY);
            AbilityInput.keyRelease(BEAT_KEY, owner);
            EntityActionInstance beat = ownActions.getAction();
            helper.assertTrue(beat != null && beat.isPlayerAction()
                            && beat.getPhase() == ActionPhase.PERFORM,
                    "Fixture: Hamon Overdrive Beat is not running as a "
                            + "player action: " + beat);
            owner.setItemSlot(EquipmentSlot.OFFHAND,
                    new ItemStack(Items.COBBLESTONE));
            recording.actionBar.clear();

            BufferingState lock = pressResolved(helper, power, owner,
                    KraftWorkAddon.LOCK_TARGET, InputMethod.HOLD, LOCK_KEY);
            AbilityInput.keyRelease(LOCK_KEY, owner);
            BufferingState placed = pressResolved(helper, power, owner,
                    KraftWorkAddon.PLACE_PROJECTILE, InputMethod.CLICK,
                    PLACE_KEY);
            AbilityInput.keyRelease(PLACE_KEY, owner);
            if (lock.isActionSuccess || lock.shouldBuffer
                    || placed.isActionSuccess || placed.shouldBuffer
                    || userActions.getAction() != null
                    || !recording.conditionMessages().isEmpty()
                    || !lockTarget.isHeldBackByHeldAction(owner)
                    || !place.isHeldBackByHeldAction(owner)
                    || owner.getOffhandItem().isEmpty()) {
                wrong.add("Lock Block and Place Projectile pressed during "
                        + "Hamon Overdrive Beat: started "
                        + lock.isActionSuccess + "/" + placed.isActionSuccess
                        + ", buffered " + lock.shouldBuffer + "/"
                        + placed.shouldBuffer + ", the user's Stand-power "
                        + "action " + userActions.getAction() + ", messages "
                        + recording.conditionMessages()
                        + ", drawn unavailable "
                        + lockTarget.isHeldBackByHeldAction(owner) + "/"
                        + place.isHeldBackByHeldAction(owner)
                        + ", off hand " + owner.getOffhandItem()
                        + " (1.16: both refused without a message and "
                        + "drawn unavailable)");
            }

            List<String> drawnUnavailable = new ArrayList<>();
            for (String name : new String[] {
                    KraftWorkAddon.LOCK_SELF,
                    KraftWorkAddon.RELEASE_TARGET,
                    KraftWorkAddon.RELEASE_PROJECTILE,
                    KraftWorkAddon.RELEASE_ALL,
                    KraftWorkAddon.ADVANCED_RELEASE,
                    KraftWorkAddon.BLOCKING_ITEMS,
                    KraftWorkAddon.ENERGY_ACCUMULATION}) {
                Ability other = power.getAbility(name);
                if (other == null || other.isHeldBackByHeldAction(owner)) {
                    drawnUnavailable.add(name);
                }
            }
            boolean blockingBefore =
                    KraftWorkAddon.userState(owner).blockingItems();
            BufferingState blocking = pressResolved(helper, power, owner,
                    KraftWorkAddon.BLOCKING_ITEMS, InputMethod.CLICK,
                    RELEASE_KEY);
            AbilityInput.keyRelease(RELEASE_KEY, owner);
            EntityActionInstance blockingAction = userActions.getAction();
            userActions.tick();
            BufferingState release = pressResolved(helper, power, owner,
                    KraftWorkAddon.RELEASE_ALL, InputMethod.CLICK,
                    RELEASE_KEY);
            AbilityInput.keyRelease(RELEASE_KEY, owner);
            EntityActionInstance releaseAction = userActions.getAction();
            if (!drawnUnavailable.isEmpty() || !blocking.isActionSuccess
                    || blockingAction == null
                    || blockingAction.ability != power.getAbility(
                            KraftWorkAddon.BLOCKING_ITEMS)
                    || KraftWorkAddon.userState(owner).blockingItems()
                            == blockingBefore
                    || !release.isActionSuccess || releaseAction == null
                    || releaseAction.ability != power.getAbility(
                            KraftWorkAddon.RELEASE_ALL)
                    || ownActions.getAction() != beat) {
                wrong.add("Kraft Work's other actions during the beat: drawn "
                        + "unavailable " + drawnUnavailable
                        + ", Blocking Items started "
                        + blocking.isActionSuccess + " (" + blockingAction
                        + "), toggled "
                        + (KraftWorkAddon.userState(owner).blockingItems()
                                != blockingBefore)
                        + ", Release All started " + release.isActionSuccess
                        + " (" + releaseAction + "), the beat running on "
                        + (ownActions.getAction() == beat)
                        + " (1.16: they went through)");
            }

            for (int tick = 0; tick < 20 && (ownActions.getAction() != null
                    || userActions.getAction() != null); tick++) {
                ownActions.tick();
                userActions.tick();
            }
            helper.assertTrue(ownActions.getAction() == null
                            && userActions.getAction() == null,
                    "Fixture: the beat or Release All did not end: "
                            + ownActions.getAction() + ", "
                            + userActions.getAction());
            BufferingState lockAfter = pressResolved(helper, power, owner,
                    KraftWorkAddon.LOCK_TARGET, InputMethod.HOLD, LOCK_KEY);
            AbilityInput.keyRelease(LOCK_KEY, owner);
            userActions.tick();
            BufferingState placeAfter = pressResolved(helper, power, owner,
                    KraftWorkAddon.PLACE_PROJECTILE, InputMethod.CLICK,
                    PLACE_KEY);
            AbilityInput.keyRelease(PLACE_KEY, owner);
            userActions.tick();
            if (!lockAfter.isActionSuccess || markersOn(helper, absolute) != 1
                    || !placeAfter.isActionSuccess
                    || !owner.getOffhandItem().isEmpty()) {
                wrong.add("once the beat was over: Lock Block started "
                        + lockAfter.isActionSuccess + ", markers "
                        + markersOn(helper, absolute)
                        + ", Place Projectile started "
                        + placeAfter.isActionSuccess + ", off hand "
                        + owner.getOffhandItem() + " (1.16: both went off)");
            }
        }
        finally {
            AbilityInput.keyRelease(BEAT_KEY, owner);
            AbilityInput.keyRelease(LOCK_KEY, owner);
            AbilityInput.keyRelease(PLACE_KEY, owner);
            AbilityInput.keyRelease(RELEASE_KEY, owner);
            ownActions.setAction(null, SyncType.NO_SYNC);
            userActions.setAction(null, SyncType.NO_SYNC);
            helper.getLevel().getEntitiesOfClass(
                            KraftWorkBlockMarkerEntity.class,
                            new AABB(absolute))
                    .forEach(KraftWorkBlockMarkerEntity::discard);
            helper.getLevel().getEntitiesOfClass(
                            KraftWorkItemProjectile.class,
                            new AABB(owner.blockPosition()).inflate(4.0D))
                    .forEach(KraftWorkItemProjectile::discard);
            helper.setBlock(block, Blocks.AIR);
            owner.connection.disconnect(Component.literal(
                    "Kraft Work player action GameTest complete"));
        }
        helper.assertTrue(wrong.isEmpty(),
                "1.16 refused Kraft Work's Lock Target and Place Projectile, "
                        + "and only those, during its user's continuous "
                        + "player action: " + String.join("; ", wrong));
        helper.succeed();
    }

    // 1.16 re-ran Action.checkConditions on every held tick (PowerBaseImpl
    // .tickHeldAction :376-381): Lock Target (canBeUsedDuringPlayerAction
    // false, KraftWorkLockTarget :268-270) held when its user starts Hamon
    // Overdrive Beat ends on its next held tick, silently and unfired.
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void heldLockTargetEndsOnceABeatStartsAsIn116(
            GameTestHelper helper) {
        TestContext context = prepareUnsummonedKraftWork(
                helper, "kw-held-beat");
        ServerPlayer owner = context.owner();
        StandPower power = context.power();
        RecordingPlayer recording = (RecordingPlayer) owner;
        LivingComponentAction ownActions =
                LivingComponentAction.getComponent(owner);
        LivingComponentAction userActions =
                LivingComponentAction.getComponent(owner, PowerClass.STAND);
        owner.setGameMode(GameType.SURVIVAL);
        Mob zombie = armedZombie(helper);
        String wrong = null;
        try {
            PlayerPower hamonPower = PowerClass.PLAYER_POWER.attachGet(owner);
            hamonPower.setPowerType(ModPlayerPowers.HAMON.get());
            HamonData hamon = PlayerPower.getPowerData(
                    owner, ModPlayerPowers.HAMON).orElseThrow();
            hamon.learnSkill(ModHamonSkills.OVERDRIVE.get());
            hamon.setBreathStability(hamon.getMaxBreathStability());
            hamon.setEnergy(hamon.getMaxEnergy());
            putInAim(zombie, owner, 2.0D);

            BufferingState lock = pressResolved(helper, power, owner,
                    KraftWorkAddon.LOCK_TARGET, InputMethod.HOLD, LOCK_KEY);
            userActions.tick();
            userActions.tick();
            EntityActionInstance charge = userActions.getAction();
            helper.assertTrue(lock.isActionSuccess && charge != null
                            && charge.getPhase() == ActionPhase.BUTTON_CHARGE
                            && AbilityInput.isHeldByKey(owner, charge)
                            && !isLocked(zombie),
                    "Fixture: Lock Target is not charging at the armed mob: "
                            + charge);
            recording.actionBar.clear();
            pressResolved(helper, hamonPower, owner, "hamon_beat",
                    InputMethod.CLICK, BEAT_KEY);
            AbilityInput.keyRelease(BEAT_KEY, owner);
            EntityActionInstance beat = ownActions.getAction();
            helper.assertTrue(beat != null && beat.isPlayerAction()
                            && beat.getPhase() == ActionPhase.PERFORM,
                    "Fixture: Hamon Overdrive Beat is not running as a "
                            + "player action: " + beat);

            // the beat strikes in its own tick 5: only the Stand-power slot
            // ticks, so the mob stays where Lock Target aims
            userActions.tick();
            boolean endedAtOnce = userActions.getAction() == null
                    || userActions.getAction() != charge
                    || charge.isOver();
            int lockTick = -1;
            for (int tick = 4; tick <= 20 && lockTick < 0; tick++) {
                userActions.tick();
                if (isLocked(zombie)) {
                    lockTick = tick;
                }
            }
            if (!endedAtOnce || lockTick >= 0
                    || !recording.conditionMessages().isEmpty()) {
                wrong = "Lock Target held at an armed mob when Hamon "
                        + "Overdrive Beat started in its second tick: ended "
                        + "on its next held tick " + endedAtOnce
                        + ", locked the mob in tick " + lockTick
                        + ", messages " + recording.conditionMessages()
                        + " (1.16: the hold ended unfired, silently)";
            }
        }
        finally {
            AbilityInput.keyRelease(LOCK_KEY, owner);
            AbilityInput.keyRelease(BEAT_KEY, owner);
            ownActions.setAction(null, SyncType.NO_SYNC);
            userActions.setAction(null, SyncType.NO_SYNC);
            KraftWorkLockService.releaseTarget(owner, zombie);
            zombie.discard();
            owner.connection.disconnect(Component.literal(
                    "Kraft Work held beat GameTest complete"));
        }
        helper.assertTrue(wrong == null, String.valueOf(wrong));
        helper.succeed();
    }

    // Lock Target took its target, and the Lock Block name and icon, from
    // the user's crosshair (KraftWorkLockTarget :116, :168, :287-289,
    // InputHandler.mouseTarget), which never picked the user's own Stand
    // (StandEntity.isPickable :2408-2412) but did pick another user's, which
    // Lock Target refused. Release Target passes the user's own Kraft Work
    // too; 1.16's server ray met it and refused, a difference kept until the
    // owner decides.
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void aimGoesThroughTheUsersOwnKraftWork(
            GameTestHelper helper) {
        List<String> wrong = new ArrayList<>();
        TestContext context = prepareKraftWork(helper, "kw-aim-own");
        ServerPlayer owner = context.owner();
        StandPower power = context.power();
        StandEntity stand = context.stand();
        LivingComponentAction userActions =
                LivingComponentAction.getComponent(owner, PowerClass.STAND);
        Ability lockTarget = power.getAbility(KraftWorkAddon.LOCK_TARGET);
        Ability releaseTarget =
                power.getAbility(KraftWorkAddon.RELEASE_TARGET);
        BlockPos block = new BlockPos(1, 3, 3);
        BlockPos absolute = helper.absolutePos(block);
        Mob zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(1, 2, 3));
        zombie.setNoAi(true);
        zombie.setNoGravity(true);
        zombie.setItemSlot(EquipmentSlot.MAINHAND,
                new ItemStack(Items.IRON_SWORD));
        TestContext other = null;
        try {
            // Kraft Work a block in front of its user, in the aim ray
            stand.setPos(owner.getX(), owner.getY(), owner.getZ() + 1.0D);
            helper.assertTrue(lockTarget instanceof KraftWorkActionAbility
                            && releaseTarget instanceof KraftWorkActionAbility
                            && inAimRay(owner, stand)
                            && inAimRay(owner, zombie),
                    "Fixture: Kraft Work and the mob behind it are not in "
                            + "the user's aim ray.");

            pressResolved(helper, power, owner, KraftWorkAddon.LOCK_TARGET,
                    InputMethod.HOLD, LOCK_KEY);
            EntityActionInstance charge = userActions.getAction();
            int lockTick = -1;
            for (int tick = 1; tick <= 20 && lockTick < 0; tick++) {
                userActions.tick();
                if (isLocked(zombie)) {
                    lockTick = tick;
                }
            }
            if (lockTick != KraftWorkSpec.LOCK_TARGET_HOLD_TICKS) {
                wrong.add("Lock Target held at the armed mob behind Kraft "
                        + "Work: action " + charge + ", locked in tick "
                        + lockTick);
            }
            AbilityInput.keyRelease(LOCK_KEY, owner);
            for (int tick = 0; tick < 3; tick++) {
                userActions.tick();
            }
            if (!isLocked(zombie)) {
                KraftWorkLockService.lockEquipment(owner, zombie);
            }
            pressResolved(helper, power, owner,
                    KraftWorkAddon.RELEASE_TARGET, InputMethod.CLICK,
                    RELEASE_KEY);
            for (int tick = 0; tick < 3; tick++) {
                userActions.tick();
            }
            AbilityInput.keyRelease(RELEASE_KEY, owner);
            if (isLocked(zombie)) {
                wrong.add("Release Target clicked at the locked mob behind "
                        + "Kraft Work left it locked");
            }
            KraftWorkLockService.releaseTarget(owner, zombie);
            zombie.discard();

            helper.setBlock(block, Blocks.STONE);
            String lockSprite = lockTarget.getSpriteName(power);
            String releaseSprite = releaseTarget.getSpriteName(power);
            if (!lockSprite.endsWith("_alt")
                    || !releaseSprite.endsWith("_alt")) {
                wrong.add("the block behind Kraft Work gave Lock / Release "
                        + "Target the icons " + lockSprite + " / "
                        + releaseSprite + ", not Lock / Release Block's");
            }
            pressResolved(helper, power, owner, KraftWorkAddon.LOCK_TARGET,
                    InputMethod.HOLD, LOCK_KEY);
            lockTick = -1;
            for (int tick = 1; tick <= 5 && lockTick < 0; tick++) {
                userActions.tick();
                if (markersOn(helper, absolute) == 1) {
                    lockTick = tick;
                }
            }
            AbilityInput.keyRelease(LOCK_KEY, owner);
            if (lockTick != 1) {
                wrong.add("Lock Block pressed at the block behind Kraft Work "
                        + "locked it in tick " + lockTick);
            }
            for (int tick = 0; tick < 5; tick++) {
                userActions.tick();
            }
            // the power does not tick here to count Release's cooldown down
            power.resetAbilityCooldowns();
            pressResolved(helper, power, owner,
                    KraftWorkAddon.RELEASE_TARGET, InputMethod.CLICK,
                    RELEASE_KEY);
            for (int tick = 0; tick < 3; tick++) {
                userActions.tick();
            }
            AbilityInput.keyRelease(RELEASE_KEY, owner);
            if (lockTick == 1 && markersOn(helper, absolute) != 0) {
                wrong.add("Release Block clicked at the block behind Kraft "
                        + "Work left " + markersOn(helper, absolute)
                        + " lock(s) on it");
            }

            // another user's Kraft Work at the same place stops the aim
            other = prepareKraftWork(helper, "kw-aim-other");
            other.owner().setPos(owner.getX() + 3.0D, owner.getY(),
                    owner.getZ());
            other.stand().setPos(stand.getX(), stand.getY(), stand.getZ());
            ConditionCheck otherStand =
                    lockTarget.checkSpecificConditions(power);
            String otherSprite = lockTarget.getSpriteName(power);
            if (otherStand.isPositive() || otherStand.getWarning() != null
                    || otherSprite.endsWith("_alt")) {
                wrong.add("another user's Kraft Work in front of the block: "
                        + "Lock Target admitted " + otherStand.isPositive()
                        + ", warning " + otherStand.getWarning()
                        + ", icon " + otherSprite
                        + " (1.16 picked it and refused it silently)");
            }
        }
        finally {
            AbilityInput.keyRelease(LOCK_KEY, owner);
            AbilityInput.keyRelease(RELEASE_KEY, owner);
            userActions.setAction(null, SyncType.NO_SYNC);
            KraftWorkLockService.releaseTarget(owner, zombie);
            zombie.discard();
            helper.getLevel().getEntitiesOfClass(
                            KraftWorkBlockMarkerEntity.class,
                            new AABB(absolute))
                    .forEach(KraftWorkBlockMarkerEntity::discard);
            helper.setBlock(block, Blocks.AIR);
            stand.discard();
            if (other != null) {
                other.stand().discard();
                other.owner().connection.disconnect(Component.literal(
                        "Kraft Work other aim GameTest complete"));
            }
            owner.connection.disconnect(Component.literal(
                    "Kraft Work own aim GameTest complete"));
        }
        helper.assertTrue(wrong.isEmpty(),
                "1.16's crosshair went through the user's own Stand to what "
                        + "is behind it: " + String.join("; ", wrong));
        helper.succeed();
    }

    // 1.16's crosshair (InputHandler.pickMouseTarget :697-712, mc.hitResult)
    // came from vanilla GameRenderer.pick, which took only an entity that was
    // no spectator and isPickable: a dropped item or a spectator in the way
    // was not the aim, a dying mob was. In-flight projectiles stay aimable
    // (kept port addition: Lock Target locks them).
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void aimTakesOnlyWhat116sCrosshairPickedAsIn116(
            GameTestHelper helper) {
        List<String> wrong = new ArrayList<>();
        TestContext context = prepareKraftWork(helper, "kw-aim-pick");
        ServerPlayer owner = context.owner();
        StandPower power = context.power();
        StandEntity stand = context.stand();
        ServerLevel level = helper.getLevel();
        LivingComponentAction userActions =
                LivingComponentAction.getComponent(owner, PowerClass.STAND);
        Ability lockTarget = power.getAbility(KraftWorkAddon.LOCK_TARGET);
        BlockPos block = new BlockPos(1, 3, 5);
        Mob zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(1, 2, 4));
        zombie.setNoAi(true);
        zombie.setNoGravity(true);
        zombie.setItemSlot(EquipmentSlot.MAINHAND,
                new ItemStack(Items.IRON_SWORD));
        Vec3 between = owner.getEyePosition().add(0.0D, 0.0D, 1.5D);
        List<Entity> inTheWay = new ArrayList<>();
        ServerPlayer spectator = null;
        try {
            // beside its user, out of the aim ray and in sight of the mob
            stand.setPos(owner.getX() + 1.0D, owner.getY(), owner.getZ());
            helper.assertTrue(lockTarget instanceof KraftWorkActionAbility
                            && inAimRay(owner, zombie)
                            && !inAimRay(owner, stand)
                            && ActionTargetRange.isEntityWithinRange(stand,
                                    zombie,
                                    ActionTargetRange.DEFAULT_ENTITY_RANGE_SQ),
                    "Fixture: the armed mob is not in the user's aim ray "
                            + "and Kraft Work's range, or Kraft Work is in "
                            + "the ray.");

            ItemEntity item = new ItemEntity(level, between.x,
                    between.y - 0.125D, between.z,
                    new ItemStack(Items.COBBLESTONE));
            item.setNoGravity(true);
            item.setNeverPickUp();
            level.addFreshEntity(item);
            inTheWay.add(item);
            helper.assertTrue(inAimRay(owner, item) && !item.isPickable(),
                    "Fixture: the dropped item is not a non-pickable entity "
                            + "in the aim ray.");
            int lockTick = heldLockTargetLockTick(
                    helper, power, owner, userActions, zombie);
            if (lockTick != KraftWorkSpec.LOCK_TARGET_HOLD_TICKS) {
                wrong.add("a dropped item between the user and an armed "
                        + "mob: Lock Target locked the mob in tick "
                        + lockTick);
            }
            KraftWorkLockService.releaseTarget(owner, zombie);
            item.discard();

            spectator = makeCompatibleMockPlayer(helper, "kw-aim-spectator");
            spectator.setGameMode(GameType.SPECTATOR);
            spectator.setPos(between.x, owner.getY(), between.z);
            spectator.setItemSlot(EquipmentSlot.MAINHAND,
                    new ItemStack(Items.IRON_SWORD));
            helper.assertTrue(spectator.isSpectator()
                            && inAimRay(owner, spectator),
                    "Fixture: the spectator is not in the aim ray.");
            lockTick = heldLockTargetLockTick(
                    helper, power, owner, userActions, zombie);
            if (lockTick != KraftWorkSpec.LOCK_TARGET_HOLD_TICKS
                    || isLockedEntity(spectator)) {
                wrong.add("a spectator between the user and an armed mob: "
                        + "Lock Target locked the mob in tick " + lockTick
                        + ", the spectator " + isLockedEntity(spectator));
            }
            KraftWorkLockService.releaseTarget(owner, zombie);
            KraftWorkLockService.releaseTarget(owner, spectator);
            spectator.connection.disconnect(Component.literal(
                    "Kraft Work spectator aim GameTest complete"));
            spectator = null;

            Arrow flying = arrowAt(level, between);
            inTheWay.add(flying);
            helper.assertTrue(inAimRay(owner, flying)
                            && !flying.isPickable(),
                    "Fixture: the in-flight arrow is not a non-pickable "
                            + "entity in the aim ray.");
            lockTick = heldLockTargetLockTick(
                    helper, power, owner, userActions, flying);
            if (lockTick != KraftWorkSpec.LOCK_TARGET_HOLD_TICKS
                    || isLockedEntity(zombie)) {
                wrong.add("an in-flight arrow between the user and an armed "
                        + "mob: Lock Target locked the arrow in tick "
                        + lockTick + ", the mob " + isLockedEntity(zombie));
            }
            KraftWorkLockService.releaseTarget(owner, zombie);
            flying.discard();

            helper.setBlock(block, Blocks.STONE);
            Vec3 zombiePos = zombie.position();
            zombie.setPos(zombiePos.x + 4.0D, zombiePos.y, zombiePos.z);
            helper.assertTrue(lockTarget.getSpriteName(power).endsWith("_alt"),
                    "Fixture: the stone block does not give Lock Block's "
                            + "icon.");
            zombie.setPos(zombiePos.x, zombiePos.y, zombiePos.z);
            zombie.setHealth(0.0F);
            String dyingSprite = lockTarget.getSpriteName(power);
            if (zombie.isAlive() || dyingSprite.endsWith("_alt")) {
                wrong.add("a dying mob in front of a block: alive "
                        + zombie.isAlive() + ", Lock Target's icon "
                        + dyingSprite + " (1.16's crosshair stopped at it)");
            }
        }
        finally {
            AbilityInput.keyRelease(LOCK_KEY, owner);
            userActions.setAction(null, SyncType.NO_SYNC);
            KraftWorkLockService.releaseTarget(owner, zombie);
            zombie.discard();
            inTheWay.forEach(Entity::discard);
            helper.setBlock(block, Blocks.AIR);
            stand.discard();
            if (spectator != null) {
                spectator.connection.disconnect(Component.literal(
                        "Kraft Work spectator aim GameTest complete"));
            }
            owner.connection.disconnect(Component.literal(
                    "Kraft Work pick aim GameTest complete"));
        }
        helper.assertTrue(wrong.isEmpty(),
                "1.16's crosshair took only a pickable non-spectator, and "
                        + "the port adds in-flight projectiles: "
                        + String.join("; ", wrong));
        helper.succeed();
    }

    // 1.16's crosshair never took an arrow (vanilla GameRenderer.pick,
    // isPickable), and the port's addition keeps only projectiles in flight
    // aimable: an arrow stuck in a block beside the aim ray is passed over to
    // the mob behind it.
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void aimPassesOverAStuckArrowAsIn116(
            GameTestHelper helper) {
        TestContext context = prepareKraftWork(helper, "kw-aim-stuck");
        ServerPlayer owner = context.owner();
        StandPower power = context.power();
        StandEntity stand = context.stand();
        ServerLevel level = helper.getLevel();
        LivingComponentAction userActions =
                LivingComponentAction.getComponent(owner, PowerClass.STAND);
        BlockPos block = new BlockPos(2, 3, 3);
        BlockPos absolute = helper.absolutePos(block);
        helper.setBlock(block, Blocks.STONE);
        Mob zombie = armedZombie(helper);
        Arrow stuck = EntityType.ARROW.create(level);
        try {
            helper.assertTrue(stuck != null, "Fixture: no arrow entity.");
            // the aim ray runs 0.15 west of the stone's west face, and the
            // arrow, stuck 0.05 deep in that face, reaches over it
            owner.setPos(absolute.getX() - 0.15D, owner.getY(),
                    owner.getZ());
            stand.setPos(owner.getX() - 1.0D, owner.getY(), owner.getZ());
            putInAim(zombie, owner, 2.7D);
            Vec3 eye = owner.getEyePosition();
            stuck.setPos(absolute.getX() + 0.05D, eye.y - 0.25D,
                    absolute.getZ() + 0.3D);
            stuck.setNoGravity(true);
            level.addFreshEntity(stuck);
            stuck.tick();
            helper.assertTrue(stuck.inGround
                            && inAimRay(owner, stuck)
                            && inAimRay(owner, zombie)
                            && !inAimRay(owner, stand)
                            && new AABB(absolute).clip(eye, eye.add(
                                            owner.getLookAngle().scale(
                                                    KraftWorkSpec.LOCK_RANGE)))
                                    .isEmpty(),
                    "Fixture: the arrow is not stuck in the stone and in the "
                            + "aim ray before the armed mob, or the stone or "
                            + "Kraft Work is in the ray.");
            int lockTick = heldLockTargetLockTick(
                    helper, power, owner, userActions, zombie);
            helper.assertTrue(
                    lockTick == KraftWorkSpec.LOCK_TARGET_HOLD_TICKS
                            && !isLockedEntity(stuck),
                    "1.16's crosshair passed over an arrow stuck in a block "
                            + "to the armed mob behind it: Lock Target "
                            + "locked the mob in tick " + lockTick
                            + ", the stuck arrow " + isLockedEntity(stuck));
        }
        finally {
            AbilityInput.keyRelease(LOCK_KEY, owner);
            userActions.setAction(null, SyncType.NO_SYNC);
            KraftWorkLockService.releaseTarget(owner, zombie);
            zombie.discard();
            if (stuck != null) {
                KraftWorkLockService.releaseTarget(owner, stuck);
                stuck.discard();
            }
            helper.setBlock(block, Blocks.AIR);
            stand.discard();
            owner.connection.disconnect(Component.literal(
                    "Kraft Work stuck arrow aim GameTest complete"));
        }
        helper.succeed();
    }

    // 1.16's crosshair (vanilla GameRenderer.pick, isPickable) never took a
    // placed vanilla projectile hanging in front of its user, nor a placed
    // item on a client (KWItemEntity.isPickable :230-232 read a field only
    // the server set). Release Target's server ray passed the arrow too, but
    // stopped at a placed item that breaks on impact. A knife was pickable
    // (ItemProjectileEntity.isPickable :200-202): both stopped at it, Lock
    // Target refused it and Release Target freed it. Lock Target refused a
    // knife or a blade hat stuck in a block too, and locked nothing behind.
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void aimPassesOverWhatKraftWorkAlreadyHoldsAsIn116(
            GameTestHelper helper) {
        List<String> wrong = new ArrayList<>();
        TestContext context = prepareUnsummonedKraftWork(
                helper, "kw-aim-held");
        ServerPlayer owner = context.owner();
        StandPower power = context.power();
        owner.setGameMode(GameType.SURVIVAL);
        LivingComponentAction userActions =
                LivingComponentAction.getComponent(owner, PowerClass.STAND);
        Ability lockTarget = power.getAbility(KraftWorkAddon.LOCK_TARGET);
        Ability releaseTarget =
                power.getAbility(KraftWorkAddon.RELEASE_TARGET);
        BlockPos block = new BlockPos(1, 3, 4);
        Mob zombie = armedZombie(helper);
        List<AbstractArrow> stuckOnes = new ArrayList<>();
        try {
            putInAim(zombie, owner, 2.0D);
            AbstractArrow arrow = placeAndFindHeld(helper, power, owner,
                    userActions, new ItemStack(Items.ARROW));
            Vec3 arrowPos = arrow.position();
            helper.assertTrue(lockTarget instanceof KraftWorkActionAbility
                            && releaseTarget instanceof KraftWorkActionAbility
                            && inAimRay(owner, arrow)
                            && inAimRay(owner, zombie)
                            && !arrow.isPickable()
                            && !arrow.inGround
                            && arrow.isNoGravity()
                            && arrow.getDeltaMovement().lengthSqr() == 0.0D,
                    "Fixture: the placed arrow is not held still in the "
                            + "aim ray before the armed mob, in flight and "
                            + "without gravity, as its clients see it.");
            int lockTick = heldLockTargetLockTick(
                    helper, power, owner, userActions, zombie);
            if (lockTick != KraftWorkSpec.LOCK_TARGET_HOLD_TICKS
                    || !isLockedEntity(arrow)
                    || !arrow.position().equals(arrowPos)) {
                wrong.add("an arrow placed in front of the user, an armed "
                        + "mob behind it: Lock Target locked the mob in "
                        + "tick " + lockTick + ", the arrow held "
                        + isLockedEntity(arrow) + " at its place "
                        + arrow.position().equals(arrowPos));
            }
            if (!isLockedEntity(zombie)) {
                KraftWorkLockService.lockEquipment(owner, zombie);
            }
            clickReleaseTarget(helper, power, owner, userActions);
            if (isLockedEntity(zombie) || !isLockedEntity(arrow)) {
                wrong.add("Release Target clicked there: the mob still "
                        + "locked " + isLockedEntity(zombie)
                        + ", the arrow held " + isLockedEntity(arrow)
                        + " (1.16's ray passed the arrow)");
            }

            Vec3 zombiePos = zombie.position();
            zombie.setPos(zombiePos.x + 4.0D, zombiePos.y, zombiePos.z);
            helper.setBlock(block, Blocks.STONE);
            String lockSprite = lockTarget.getSpriteName(power);
            String releaseSprite = releaseTarget.getSpriteName(power);
            if (!lockSprite.endsWith("_alt")
                    || !releaseSprite.endsWith("_alt")) {
                wrong.add("a block behind the placed arrow gave Lock / "
                        + "Release Target the icons " + lockSprite + " / "
                        + releaseSprite + ", not Lock / Release Block's");
            }
            helper.setBlock(block, Blocks.AIR);
            zombie.setPos(zombiePos.x, zombiePos.y, zombiePos.z);
            KraftWorkLockService.releaseTarget(owner, zombie);
            KraftWorkLockService.releaseTarget(owner, arrow);
            arrow.discard();

            power.resetAbilityCooldowns();
            AbstractArrow bucket = placeAndFindHeld(helper, power, owner,
                    userActions, new ItemStack(Items.WATER_BUCKET));
            helper.assertTrue(bucket instanceof KraftWorkItemProjectile item
                            && item.removeAfterHitting()
                            && inAimRay(owner, bucket),
                    "Fixture: the placed water bucket is not a held item "
                            + "that breaks on impact, in the aim ray.");
            lockTick = heldLockTargetLockTick(
                    helper, power, owner, userActions, zombie);
            if (lockTick != KraftWorkSpec.LOCK_TARGET_HOLD_TICKS) {
                wrong.add("a water bucket placed in front of the user: Lock "
                        + "Target locked the mob behind it in tick "
                        + lockTick + " (1.16's client never picked a "
                        + "placed item)");
            }
            if (!isLockedEntity(zombie)) {
                KraftWorkLockService.lockEquipment(owner, zombie);
            }
            power.resetAbilityCooldowns();
            clickReleaseTarget(helper, power, owner, userActions);
            if (isLockedEntity(bucket) || !isLockedEntity(zombie)) {
                wrong.add("Release Target clicked at the placed water "
                        + "bucket: the bucket still held "
                        + isLockedEntity(bucket) + ", the mob behind it "
                        + "still locked " + isLockedEntity(zombie)
                        + " (1.16's server ray stopped at the bucket)");
            }
            bucket.discard();

            KraftWorkLockService.releaseTarget(owner, zombie);
            power.setResolveLevel(2);
            power.resetAbilityCooldowns();
            AbstractArrow knife = placeAndFindHeld(helper, power, owner,
                    userActions, new ItemStack(ModItems.KNIFE.get()));
            Vec3 knifePos = knife.position();
            helper.assertTrue(knife instanceof KnifeEntity
                            && inAimRay(owner, knife)
                            && inAimRay(owner, zombie)
                            && !isLockedEntity(zombie),
                    "Fixture: the knife placed at Resolve 2 is not held in "
                            + "the aim ray before the unlocked armed mob.");
            lockTick = heldLockTargetLockTick(
                    helper, power, owner, userActions, zombie);
            if (lockTick >= 0 || isLockedEntity(zombie)
                    || !isLockedEntity(knife)
                    || !knife.position().equals(knifePos)) {
                wrong.add("a knife placed in front of the user: Lock Target "
                        + "locked the mob behind it in tick " + lockTick
                        + ", the knife held " + isLockedEntity(knife)
                        + " at its place " + knife.position().equals(knifePos)
                        + " (1.16's crosshair stopped at the pickable "
                        + "knife, and Lock Target refused it)");
            }
            KraftWorkLockService.lockEquipment(owner, zombie);
            power.resetAbilityCooldowns();
            clickReleaseTarget(helper, power, owner, userActions);
            if (isLockedEntity(knife) || !isLockedEntity(zombie)) {
                wrong.add("Release Target clicked at the placed knife: the "
                        + "knife still held " + isLockedEntity(knife)
                        + ", the mob behind it still locked "
                        + isLockedEntity(zombie) + " (1.16's server ray "
                        + "stopped at the pickable knife and freed it)");
            }

            // a knife or a blade hat stuck in a block's face, unlocked: the
            // aim stops at it as 1.16's crosshair did, and Lock Target
            // refuses it silently (KraftWorkLockTarget :133-134), so neither
            // it nor the block behind it is locked and nothing is paid
            knife.discard();
            KraftWorkLockService.releaseTarget(owner, zombie);
            zombie.setPos(zombie.getX() + 4.0D, zombie.getY(), zombie.getZ());
            helper.setBlock(block, Blocks.STONE);
            BlockPos stone = helper.absolutePos(block);
            RecordingPlayer recording = (RecordingPlayer) owner;
            Vec3 eye = owner.getEyePosition();
            stuckOnes.add(new KnifeEntity(helper.getLevel(), eye.x,
                    eye.y - 0.125D, stone.getZ() + 0.05D,
                    new ItemStack(ModItems.KNIFE.get())));
            stuckOnes.add(new BladeHatEntity(helper.getLevel(), eye.x,
                    eye.y - 0.1875D, stone.getZ() + 0.05D,
                    new ItemStack(ModItems.BLADE_HAT.get())));
            for (AbstractArrow stuck : stuckOnes) {
                stuck.setNoGravity(true);
                helper.getLevel().addFreshEntity(stuck);
                stuck.tick();
                helper.assertTrue(stuck.inGround
                                && inAimRay(owner, stuck)
                                && !inAimRay(owner, zombie)
                                && !isLockedEntity(stuck),
                        "Fixture: the " + stuck.getType().toShortString()
                                + " is not stuck unlocked in the stone's "
                                + "face in the aim ray, or the mob is in it.");
                power.resetAbilityCooldowns();
                power.setStamina(power.getMaxStamina());
                float stamina = power.getStamina();
                int messages = recording.conditionMessages().size();
                lockTick = heldLockTargetLockTick(
                        helper, power, owner, userActions, stuck);
                boolean marked = !helper.getLevel().getEntitiesOfClass(
                                KraftWorkBlockMarkerEntity.class,
                                new AABB(stone))
                        .isEmpty();
                List<String> said = recording.conditionMessages().subList(
                        messages, recording.conditionMessages().size());
                if (lockTick >= 0 || isLockedEntity(stuck) || marked
                        || power.getStamina() != stamina
                        || !said.isEmpty()) {
                    wrong.add("a " + stuck.getType().toShortString()
                            + " stuck in a block in the aim: Lock Target "
                            + "locked it in tick " + lockTick + ", it held "
                            + isLockedEntity(stuck) + ", the block behind it "
                            + "marked " + marked + ", stamina paid "
                            + (stamina - power.getStamina()) + ", messages "
                            + said + " (1.16's crosshair stopped at it and "
                            + "Lock Target refused it silently)");
                }
                KraftWorkLockService.releaseTarget(owner, stuck);
                stuck.discard();
            }
        }
        finally {
            AbilityInput.keyRelease(LOCK_KEY, owner);
            AbilityInput.keyRelease(RELEASE_KEY, owner);
            userActions.setAction(null, SyncType.NO_SYNC);
            stuckOnes.forEach(Entity::discard);
            KraftWorkLockService.releaseTarget(owner, zombie);
            zombie.discard();
            helper.getLevel().getEntitiesOfClass(AbstractArrow.class,
                            owner.getBoundingBox().inflate(4.0D),
                            placed -> placed.getOwner() == owner)
                    .forEach(Entity::discard);
            helper.setBlock(block, Blocks.AIR);
            owner.connection.disconnect(Component.literal(
                    "Kraft Work held projectile aim GameTest complete"));
        }
        helper.assertTrue(wrong.isEmpty(),
                "1.16's aim passed a vanilla projectile Kraft Work already "
                        + "held and stopped at a held or stuck knife: "
                        + String.join("; ", wrong));
        helper.succeed();
    }

    // Place Projectile pressed with the item in the off hand; the one
    // projectile its user placed, held in place
    private static AbstractArrow placeAndFindHeld(GameTestHelper helper,
            StandPower power, ServerPlayer owner,
            LivingComponentAction userActions, ItemStack item) {
        owner.setItemSlot(EquipmentSlot.OFFHAND, item);
        pressResolved(helper, power, owner, KraftWorkAddon.PLACE_PROJECTILE,
                InputMethod.CLICK, PLACE_KEY);
        AbilityInput.keyRelease(PLACE_KEY, owner);
        userActions.tick();
        List<AbstractArrow> held = helper.getLevel().getEntitiesOfClass(
                AbstractArrow.class,
                owner.getBoundingBox().inflate(4.0D),
                placed -> placed.getOwner() == owner
                        && isLockedEntity(placed));
        helper.assertTrue(held.size() == 1 && owner.getOffhandItem().isEmpty(),
                "Fixture: Place Projectile left " + held.size()
                        + " held projectile(s) and the off hand "
                        + owner.getOffhandItem());
        return held.getFirst();
    }

    private static void clickReleaseTarget(GameTestHelper helper,
            StandPower power, ServerPlayer owner,
            LivingComponentAction userActions) {
        pressResolved(helper, power, owner, KraftWorkAddon.RELEASE_TARGET,
                InputMethod.CLICK, RELEASE_KEY);
        for (int tick = 0; tick < 3; tick++) {
            userActions.tick();
        }
        AbilityInput.keyRelease(RELEASE_KEY, owner);
    }

    // vanilla's pick (1.16 ProjectileHelper.getEntityHitResult) and Release
    // Target's ray (JojoModUtil.rayTraceMultipleEntities :168) passed over
    // whatever shared the user's root vehicle: looking down from an armed
    // mount, the aim is the block under it.
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void aimPassesOverTheUsersMountAsIn116(
            GameTestHelper helper) {
        List<String> wrong = new ArrayList<>();
        TestContext context = prepareKraftWork(helper, "kw-aim-mount");
        ServerPlayer owner = context.owner();
        StandPower power = context.power();
        StandEntity stand = context.stand();
        LivingComponentAction userActions =
                LivingComponentAction.getComponent(owner, PowerClass.STAND);
        Ability lockTarget = power.getAbility(KraftWorkAddon.LOCK_TARGET);
        Ability releaseTarget =
                power.getAbility(KraftWorkAddon.RELEASE_TARGET);
        BlockPos block = new BlockPos(1, 1, 1);
        BlockPos absolute = helper.absolutePos(block);
        helper.setBlock(block, Blocks.STONE);
        Mob mount = helper.spawn(EntityType.ZOMBIE, new BlockPos(1, 2, 1));
        mount.setNoAi(true);
        mount.setNoGravity(true);
        mount.setItemSlot(EquipmentSlot.MAINHAND,
                new ItemStack(Items.IRON_SWORD));
        try {
            stand.setPos(owner.getX() + 2.0D, owner.getY(), owner.getZ());
            helper.assertTrue(owner.startRiding(mount, true),
                    "Fixture: the user could not mount the mob.");
            mount.positionRider(owner);
            owner.setXRot(90.0F);
            helper.assertTrue(lockTarget instanceof KraftWorkActionAbility
                            && releaseTarget instanceof KraftWorkActionAbility
                            && owner.getRootVehicle() == mount
                            && inAimRay(owner, mount)
                            && !inAimRay(owner, stand),
                    "Fixture: the user's mount is not under its aim, or "
                            + "Kraft Work is.");
            String lockSprite = lockTarget.getSpriteName(power);
            String releaseSprite = releaseTarget.getSpriteName(power);
            if (!lockSprite.endsWith("_alt")
                    || !releaseSprite.endsWith("_alt")) {
                wrong.add("looking down from an armed mount at a block: Lock "
                        + "/ Release Target's icons " + lockSprite + " / "
                        + releaseSprite + ", not Lock / Release Block's");
            }
            pressResolved(helper, power, owner, KraftWorkAddon.LOCK_TARGET,
                    InputMethod.HOLD, LOCK_KEY);
            int lockTick = -1;
            for (int tick = 1; tick <= 20 && lockTick < 0; tick++) {
                userActions.tick();
                if (markersOn(helper, absolute) == 1) {
                    lockTick = tick;
                }
            }
            AbilityInput.keyRelease(LOCK_KEY, owner);
            if (lockTick != 1 || isLocked(mount)) {
                wrong.add("Lock Target pressed there: the block locked in "
                        + "tick " + lockTick + ", the mount locked "
                        + isLocked(mount));
            }
        }
        finally {
            AbilityInput.keyRelease(LOCK_KEY, owner);
            userActions.setAction(null, SyncType.NO_SYNC);
            owner.stopRiding();
            KraftWorkLockService.releaseTarget(owner, mount);
            mount.discard();
            helper.getLevel().getEntitiesOfClass(
                            KraftWorkBlockMarkerEntity.class,
                            new AABB(absolute))
                    .forEach(KraftWorkBlockMarkerEntity::discard);
            helper.setBlock(block, Blocks.AIR);
            stand.discard();
            owner.connection.disconnect(Component.literal(
                    "Kraft Work mount aim GameTest complete"));
        }
        helper.assertTrue(wrong.isEmpty(),
                "1.16's crosshair and Release Target's ray passed over the "
                        + "user's own mount: " + String.join("; ", wrong));
        helper.succeed();
    }

    // 1.16 KraftWorkLockTarget.perform (:184-244) had no alive check: a
    // dying mob under the crosshair had its equipment locked when the hold
    // fired, and the lock was paid for.
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void lockTargetLocksADyingMobAsIn116(
            GameTestHelper helper) {
        TestContext context = prepareKraftWork(helper, "kw-lock-dying");
        ServerPlayer owner = context.owner();
        StandPower power = context.power();
        StandEntity stand = context.stand();
        LivingComponentAction userActions =
                LivingComponentAction.getComponent(owner, PowerClass.STAND);
        Mob zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(1, 2, 3));
        zombie.setNoAi(true);
        zombie.setNoGravity(true);
        zombie.setItemSlot(EquipmentSlot.MAINHAND,
                new ItemStack(Items.IRON_SWORD));
        owner.setGameMode(GameType.SURVIVAL);
        try {
            stand.setPos(owner.getX() + 1.0D, owner.getY(), owner.getZ());
            zombie.setHealth(0.0F);
            helper.assertTrue(!zombie.isAlive() && !zombie.isRemoved()
                            && inAimRay(owner, zombie)
                            && !inAimRay(owner, stand)
                            && !power.isStaminaInfinite(),
                    "Fixture: the armed mob is not dying in the user's aim, "
                            + "or Kraft Work is in it, or the bar is "
                            + "infinite.");
            float stamina = power.getStamina();
            int lockTick = heldLockTargetLockTick(
                    helper, power, owner, userActions, zombie);
            float paid = stamina - power.getStamina();
            helper.assertTrue(
                    lockTick == KraftWorkSpec.LOCK_TARGET_HOLD_TICKS
                            && KraftWorkLockService.hasLockedMainHand(zombie)
                            && paid == KraftWorkSpec.LOCK_TARGET_STAMINA,
                    "1.16 locked a dying mob's sword when Lock Target's "
                            + "hold fired and charged for it: locked in tick "
                            + lockTick + ", main hand locked "
                            + KraftWorkLockService.hasLockedMainHand(zombie)
                            + ", stamina paid " + paid);
        }
        finally {
            AbilityInput.keyRelease(LOCK_KEY, owner);
            userActions.setAction(null, SyncType.NO_SYNC);
            KraftWorkLockService.releaseTarget(owner, zombie);
            zombie.discard();
            stand.discard();
            owner.connection.disconnect(Component.literal(
                    "Kraft Work dying lock GameTest complete"));
        }
        helper.succeed();
    }

    // 1.16 Lock Target took the crosshair target (InputHandler.mouseTarget):
    // the vanilla pick, a Survival player's entity within 3 blocks and block
    // within 4.5; with Kraft Work out, where that met nothing, the precision
    // ray as far as the user's reach of 5, a hostile mob before another
    // entity (EntityStandType.clientHitResult, StandEntity.precisionRayTrace),
    // or the crosshair line there without the aim assist. Release Target
    // kept its own 15-block ray.
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void lockTargetAimsWithin116sCrosshairReachAsIn116(
            GameTestHelper helper) {
        List<String> wrong = new ArrayList<>();
        TestContext bare = prepareUnsummonedKraftWork(helper, "kw-reach-bare");
        ServerPlayer bareOwner = bare.owner();
        bareOwner.setGameMode(GameType.SURVIVAL);
        Ability bareLock = bare.power().getAbility(KraftWorkAddon.LOCK_TARGET);
        Mob bareZombie = armedZombie(helper);
        try {
            helper.assertTrue(bareLock instanceof KraftWorkActionAbility
                            && bareOwner.entityInteractionRange() == 3.0D
                            && bareOwner.blockInteractionRange() == 4.5D,
                    "Fixture: the user is not a Survival player with 3 and "
                            + "4.5 blocks of reach.");
            putInAim(bareZombie, bareOwner, 2.5D);
            boolean near = bareLock.checkSpecificConditions(bare.power())
                    .isPositive();
            putInAim(bareZombie, bareOwner, 4.0D);
            ConditionCheck far =
                    bareLock.checkSpecificConditions(bare.power());
            if (!near || far.isPositive() || far.getWarning() != null) {
                wrong.add("Kraft Work not out: an armed mob 2.5 blocks away "
                        + "admitted " + near + ", 4 blocks away admitted "
                        + far.isPositive() + ", warning " + far.getWarning()
                        + " (1.16's crosshair reached 3)");
            }
        }
        finally {
            bareZombie.discard();
            bareOwner.connection.disconnect(Component.literal(
                    "Kraft Work bare reach GameTest complete"));
        }

        TestContext context = prepareKraftWork(helper, "kw-reach-out");
        ServerPlayer owner = context.owner();
        StandPower power = context.power();
        StandEntity stand = context.stand();
        owner.setGameMode(GameType.SURVIVAL);
        LivingComponentAction userActions =
                LivingComponentAction.getComponent(owner, PowerClass.STAND);
        Ability lockTarget = power.getAbility(KraftWorkAddon.LOCK_TARGET);
        Ability releaseTarget =
                power.getAbility(KraftWorkAddon.RELEASE_TARGET);
        var settings = owner.getData(
                ModDataAttachmentTypes.PLAYER_BROADCASTED_SETTINGS.get());
        Mob zombie = armedZombie(helper);
        Mob cow = helper.spawn(EntityType.COW, new BlockPos(3, 2, 3));
        cow.setNoAi(true);
        cow.setNoGravity(true);
        BlockPos block = new BlockPos(1, 3, 7);
        try {
            stand.setPos(owner.getX() + 1.0D, owner.getY(), owner.getZ());
            putInAim(zombie, owner, 4.6D);
            helper.assertTrue(lockTarget instanceof KraftWorkActionAbility
                            && settings.standAimAssist
                            && !inAimRay(owner, stand)
                            && inAimRay(owner, zombie)
                            && !inAimRay(owner, cow),
                    "Fixture: the aim assist is off, or the mob is not "
                            + "alone in the user's aim ray.");
            int lockTick = heldLockTargetLockTick(
                    helper, power, owner, userActions, zombie);
            if (lockTick != KraftWorkSpec.LOCK_TARGET_HOLD_TICKS) {
                wrong.add("Kraft Work out, an armed mob 4.6 blocks away: "
                        + "Lock Target locked it in tick " + lockTick
                        + " (1.16's precision ray reached 5)");
            }
            KraftWorkLockService.releaseTarget(owner, zombie);

            putInAim(cow, owner, 3.5D);
            helper.assertTrue(inAimRay(owner, cow),
                    "Fixture: the cow is not in the user's aim ray.");
            lockTick = heldLockTargetLockTick(
                    helper, power, owner, userActions, zombie);
            if (lockTick != KraftWorkSpec.LOCK_TARGET_HOLD_TICKS) {
                wrong.add("Kraft Work out, a cow 3.5 blocks away before the "
                        + "armed mob: Lock Target locked the mob in tick "
                        + lockTick + " (1.16's precision ray put a hostile "
                        + "mob first)");
            }
            KraftWorkLockService.releaseTarget(owner, zombie);
            settings.standAimAssist = false;
            lockTick = heldLockTargetLockTick(
                    helper, power, owner, userActions, zombie);
            settings.standAimAssist = true;
            if (lockTick >= 0) {
                wrong.add("the same without the aim assist: Lock Target "
                        + "locked the mob behind the cow in tick " + lockTick);
            }
            KraftWorkLockService.releaseTarget(owner, zombie);

            cow.setPos(cow.getX() + 4.0D, cow.getY(), cow.getZ());
            putInAim(zombie, owner, 5.5D);
            ConditionCheck far = lockTarget.checkSpecificConditions(power);
            if (far.isPositive() || far.getWarning() != null) {
                wrong.add("Kraft Work out, an armed mob 5.5 blocks away: "
                        + "Lock Target admitted " + far.isPositive()
                        + ", warning " + far.getWarning());
            }

            zombie.setPos(zombie.getX() + 4.0D, zombie.getY(),
                    zombie.getZ());
            helper.setBlock(block, Blocks.STONE);
            String lockSprite = lockTarget.getSpriteName(power);
            String releaseSprite = releaseTarget.getSpriteName(power);
            if (lockSprite.endsWith("_alt")
                    || !releaseSprite.endsWith("_alt")) {
                wrong.add("a block 5.5 blocks away: Lock / Release Target's "
                        + "icons " + lockSprite + " / " + releaseSprite
                        + " (1.16: Lock Target's crosshair did not reach "
                        + "it, Release Target's 15-block ray did)");
            }
        }
        finally {
            settings.standAimAssist = true;
            AbilityInput.keyRelease(LOCK_KEY, owner);
            userActions.setAction(null, SyncType.NO_SYNC);
            KraftWorkLockService.releaseTarget(owner, zombie);
            zombie.discard();
            cow.discard();
            helper.setBlock(block, Blocks.AIR);
            stand.discard();
            owner.connection.disconnect(Component.literal(
                    "Kraft Work reach GameTest complete"));
        }
        helper.assertTrue(wrong.isEmpty(),
                "1.16's Lock Target took the crosshair target, within the "
                        + "vanilla reach, else Kraft Work's precision ray to "
                        + "5 blocks: " + String.join("; ", wrong));
        helper.succeed();
    }

    // 1.16's crosshair was the camera entity's (InputHandler.pickMouseTarget,
    // mc.getCameraEntity()): under manual control Lock Target took the
    // vanilla pick from Kraft Work's eyes, where the user was in the way
    // (Action.checkTarget emptied it), then clientHitResult's precision ray
    // from Kraft Work as far as its own reach of 2.5.
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void manualControlAimsLockTargetFromKraftWorkAsIn116(
            GameTestHelper helper) {
        List<String> wrong = new ArrayList<>();
        TestContext context = prepareKraftWork(helper, "kw-aim-manual");
        ServerPlayer owner = context.owner();
        StandPower power = context.power();
        StandEntity stand = context.stand();
        owner.setGameMode(GameType.SURVIVAL);
        LivingComponentAction userActions =
                LivingComponentAction.getComponent(owner, PowerClass.STAND);
        Ability lockTarget = power.getAbility(KraftWorkAddon.LOCK_TARGET);
        Mob standsMob = armedZombie(helper);
        Mob usersMob = armedZombie(helper);
        try {
            stand.setPos(owner.getX() + 2.0D, owner.getY(), owner.getZ());
            stand.setManuallyControlled(true);
            turnStand(stand, 0.0F);
            putInAim(standsMob, stand, 2.0D);
            putInAim(usersMob, owner, 2.0D);
            helper.assertTrue(lockTarget instanceof KraftWorkActionAbility
                            && stand.isManuallyControlled()
                            && stand.getAttributeValue(
                                            Attributes.BLOCK_INTERACTION_RANGE)
                                    == 2.5D
                            && owner.entityInteractionRange() == 3.0D
                            && inAimRay(stand, standsMob)
                            && !inAimRay(stand, usersMob)
                            && inAimRay(owner, usersMob)
                            && !inAimRay(owner, standsMob)
                            && !inAimRay(owner, stand),
                    "Fixture: Kraft Work is not under manual control with "
                            + "a reach of 2.5, or each armed mob is not in "
                            + "only its own aim ray.");
            int lockTick = heldLockTargetLockTick(
                    helper, power, owner, userActions, standsMob);
            if (lockTick != KraftWorkSpec.LOCK_TARGET_HOLD_TICKS
                    || isLockedEntity(usersMob)) {
                wrong.add("an armed mob 2 blocks before Kraft Work's eyes "
                        + "and one before the user's: Lock Target locked "
                        + "Kraft Work's in tick " + lockTick
                        + ", the user's " + isLockedEntity(usersMob));
            }
            KraftWorkLockService.releaseTarget(owner, standsMob);
            KraftWorkLockService.releaseTarget(owner, usersMob);

            putInAim(standsMob, stand, 4.0D);
            putInAim(usersMob, owner, 4.0D);
            ConditionCheck far = lockTarget.checkSpecificConditions(power);
            if (far.isPositive() || far.getWarning() != null) {
                wrong.add("armed mobs 4 blocks before Kraft Work's and the "
                        + "user's eyes: Lock Target admitted "
                        + far.isPositive() + ", warning " + far.getWarning()
                        + " (1.16: the vanilla pick reached 3, Kraft Work's "
                        + "precision ray 2.5, both from Kraft Work)");
            }
            usersMob.setPos(usersMob.getX() + 8.0D, usersMob.getY(),
                    usersMob.getZ());

            // Kraft Work faces west at its user, an armed mob behind the user
            turnStand(stand, 90.0F);
            standsMob.setPos(owner.getX() - 0.9D, owner.getY(),
                    owner.getZ());
            helper.assertTrue(inAimRay(stand, owner)
                            && inAimRay(stand, standsMob)
                            && standsMob.getBoundingBox().distanceToSqr(
                                            stand.getEyePosition())
                                    < 9.0D,
                    "Fixture: the user and the armed mob behind it are not "
                            + "in Kraft Work's aim ray within 3 blocks.");
            ConditionCheck user = lockTarget.checkSpecificConditions(power);
            lockTick = heldLockTargetLockTick(
                    helper, power, owner, userActions, standsMob);
            if (user.isPositive() || user.getWarning() != null
                    || lockTick >= 0) {
                wrong.add("Kraft Work looking at its user with an armed mob "
                        + "behind: Lock Target admitted " + user.isPositive()
                        + ", warning " + user.getWarning()
                        + ", locked the mob in tick " + lockTick
                        + " (1.16 picked the user and emptied the target)");
            }
        }
        finally {
            AbilityInput.keyRelease(LOCK_KEY, owner);
            userActions.setAction(null, SyncType.NO_SYNC);
            KraftWorkLockService.releaseTarget(owner, standsMob);
            KraftWorkLockService.releaseTarget(owner, usersMob);
            standsMob.discard();
            usersMob.discard();
            stand.setManuallyControlled(false);
            stand.discard();
            owner.connection.disconnect(Component.literal(
                    "Kraft Work manual control aim GameTest complete"));
        }
        helper.assertTrue(wrong.isEmpty(),
                "1.16's Lock Target under manual control aimed from Kraft "
                        + "Work's eyes: " + String.join("; ", wrong));
        helper.succeed();
    }

    // vanilla's pick (Entity.pick) and 1.16 Release Target's ray
    // (JojoModUtil.rayTrace, BlockMode.OUTLINE) stopped at a block's outline:
    // a torch under the crosshair is Lock Block's target and Release Block
    // frees it, and a cobweb hides the mob behind it from the crosshair but
    // not from Release Target's ray, which took an entity first.
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void aimStopsAtABlocksOutlineAsIn116(
            GameTestHelper helper) {
        List<String> wrong = new ArrayList<>();
        TestContext context = prepareKraftWork(helper, "kw-aim-outline");
        ServerPlayer owner = context.owner();
        StandPower power = context.power();
        StandEntity stand = context.stand();
        owner.setGameMode(GameType.SURVIVAL);
        LivingComponentAction userActions =
                LivingComponentAction.getComponent(owner, PowerClass.STAND);
        BlockPos ground = new BlockPos(1, 1, 3);
        BlockPos torch = new BlockPos(1, 2, 3);
        BlockPos cobweb = new BlockPos(1, 3, 3);
        BlockPos torchAbsolute = helper.absolutePos(torch);
        BlockPos groundAbsolute = helper.absolutePos(ground);
        BlockPos cobwebAbsolute = helper.absolutePos(cobweb);
        Mob zombie = armedZombie(helper);
        try {
            stand.setPos(owner.getX() + 2.0D, owner.getY(), owner.getZ());
            helper.setBlock(ground, Blocks.STONE);
            helper.setBlock(torch, Blocks.TORCH);
            Vec3 torchCentre = Vec3.atBottomCenterOf(torchAbsolute)
                    .add(0.0D, 0.3D, 0.0D);
            Vec3 toTorch = torchCentre.subtract(owner.getEyePosition());
            owner.setXRot((float) Math.toDegrees(Math.atan2(-toTorch.y,
                    Math.sqrt(toTorch.x * toTorch.x
                            + toTorch.z * toTorch.z))));
            Vec3 eye = owner.getEyePosition();
            Vec3 end = eye.add(owner.getLookAngle().scale(4.5D));
            helper.assertTrue(helper.getBlockState(torch).is(Blocks.TORCH)
                            && new AABB(torchAbsolute).clip(eye, end)
                                    .isPresent()
                            && new AABB(groundAbsolute).clip(eye, end)
                                    .isPresent()
                            && !inAimRay(owner, stand)
                            && !inAimRay(owner, zombie),
                    "Fixture: the torch on the stone is not under the "
                            + "user's crosshair, or Kraft Work or the mob is.");
            pressResolved(helper, power, owner, KraftWorkAddon.LOCK_TARGET,
                    InputMethod.HOLD, LOCK_KEY);
            int lockTick = -1;
            for (int tick = 1; tick <= 5 && lockTick < 0; tick++) {
                userActions.tick();
                if (markersOn(helper, torchAbsolute) == 1) {
                    lockTick = tick;
                }
            }
            AbilityInput.keyRelease(LOCK_KEY, owner);
            for (int tick = 0; tick < 3; tick++) {
                userActions.tick();
            }
            if (lockTick != 1 || markersOn(helper, groundAbsolute) != 0) {
                wrong.add("a torch under the crosshair: Lock Block locked it "
                        + "in tick " + lockTick + ", the stone under it "
                        + markersOn(helper, groundAbsolute) + " time(s)");
            }
            power.resetAbilityCooldowns();
            pressResolved(helper, power, owner,
                    KraftWorkAddon.RELEASE_TARGET, InputMethod.CLICK,
                    RELEASE_KEY);
            for (int tick = 0; tick < 3; tick++) {
                userActions.tick();
            }
            AbilityInput.keyRelease(RELEASE_KEY, owner);
            if (lockTick == 1 && markersOn(helper, torchAbsolute) != 0) {
                wrong.add("Release Block clicked at the locked torch left "
                        + markersOn(helper, torchAbsolute) + " lock(s) on it");
            }

            owner.setXRot(0.0F);
            helper.setBlock(cobweb, Blocks.COBWEB);
            putInAim(zombie, owner, 2.7D);
            eye = owner.getEyePosition();
            end = eye.add(owner.getLookAngle().scale(4.5D));
            helper.assertTrue(new AABB(cobwebAbsolute).clip(eye, end)
                            .isPresent()
                            && inAimRay(owner, zombie),
                    "Fixture: the cobweb and the armed mob behind it are "
                            + "not in the user's aim ray.");
            power.resetAbilityCooldowns();
            pressResolved(helper, power, owner, KraftWorkAddon.LOCK_TARGET,
                    InputMethod.HOLD, LOCK_KEY);
            int webTick = -1;
            for (int tick = 1; tick <= 20 && webTick < 0
                    && !isLockedEntity(zombie); tick++) {
                userActions.tick();
                if (markersOn(helper, cobwebAbsolute) == 1) {
                    webTick = tick;
                }
            }
            AbilityInput.keyRelease(LOCK_KEY, owner);
            for (int tick = 0; tick < 3; tick++) {
                userActions.tick();
            }
            if (webTick != 1 || isLockedEntity(zombie)) {
                wrong.add("a cobweb before an armed mob: Lock Block locked "
                        + "the cobweb in tick " + webTick + ", the mob "
                        + isLockedEntity(zombie));
            }
            KraftWorkLockService.releaseTarget(owner, zombie);
            KraftWorkLockService.lockEquipment(owner, zombie);
            power.resetAbilityCooldowns();
            pressResolved(helper, power, owner,
                    KraftWorkAddon.RELEASE_TARGET, InputMethod.CLICK,
                    RELEASE_KEY);
            for (int tick = 0; tick < 3; tick++) {
                userActions.tick();
            }
            AbilityInput.keyRelease(RELEASE_KEY, owner);
            if (isLockedEntity(zombie)) {
                wrong.add("Release Target clicked at the locked mob behind "
                        + "the cobweb left it locked");
            }
        }
        finally {
            AbilityInput.keyRelease(LOCK_KEY, owner);
            AbilityInput.keyRelease(RELEASE_KEY, owner);
            userActions.setAction(null, SyncType.NO_SYNC);
            KraftWorkLockService.releaseTarget(owner, zombie);
            zombie.discard();
            for (BlockPos block : List.of(torchAbsolute, groundAbsolute,
                    cobwebAbsolute)) {
                helper.getLevel().getEntitiesOfClass(
                                KraftWorkBlockMarkerEntity.class,
                                new AABB(block))
                        .forEach(KraftWorkBlockMarkerEntity::discard);
            }
            helper.setBlock(cobweb, Blocks.AIR);
            helper.setBlock(torch, Blocks.AIR);
            helper.setBlock(ground, Blocks.AIR);
            stand.discard();
            owner.connection.disconnect(Component.literal(
                    "Kraft Work outline aim GameTest complete"));
        }
        helper.assertTrue(wrong.isEmpty(),
                "1.16's crosshair and Release Target's ray met blocks by "
                        + "their outline: " + String.join("; ", wrong));
        helper.succeed();
    }

    // 1.16 InputHandler.pickMouseTarget (:699-704) put the Stand's precision
    // ray (EntityStandType.clientHitResult) into the crosshair target only
    // while the HUD's current power was the Stand: a Lock Target key pressed
    // with the HUD on another power took the vanilla pick alone. The client
    // tells the server (StandHudPayload); the test records what it sent.
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void lockTargetTakesThePrecisionRayOnlyWithTheStandOnTheHudAsIn116(
            GameTestHelper helper) {
        List<String> wrong = new ArrayList<>();
        TestContext context = prepareKraftWork(helper, "kw-aim-hud");
        ServerPlayer owner = context.owner();
        StandPower power = context.power();
        StandEntity stand = context.stand();
        owner.setGameMode(GameType.SURVIVAL);
        LivingComponentAction userActions =
                LivingComponentAction.getComponent(owner, PowerClass.STAND);
        Ability lockTarget = power.getAbility(KraftWorkAddon.LOCK_TARGET);
        Mob zombie = armedZombie(helper);
        try {
            stand.setPos(owner.getX() + 1.0D, owner.getY(), owner.getZ());
            putInAim(zombie, owner, 4.6D);
            helper.assertTrue(lockTarget instanceof KraftWorkActionAbility
                            && KraftWorkPayloads.standHudShown(owner)
                            && inAimRay(owner, zombie)
                            && !inAimRay(owner, stand),
                    "Fixture: the user's HUD does not start on the Stand, or "
                            + "the armed mob is not alone in its aim ray.");
            KraftWorkPayloads.recordStandHud(owner, false);
            ConditionCheck hidden = lockTarget.checkSpecificConditions(power);
            int hiddenTick = heldLockTargetLockTick(
                    helper, power, owner, userActions, zombie);
            if (hidden.isPositive() || hidden.getWarning() != null
                    || hiddenTick >= 0) {
                wrong.add("the HUD on another power, an armed mob 4.6 blocks "
                        + "away: Lock Target admitted " + hidden.isPositive()
                        + ", warning " + hidden.getWarning()
                        + ", locked it in tick " + hiddenTick
                        + " (1.16: the vanilla pick alone, which reached 3)");
            }
            KraftWorkLockService.releaseTarget(owner, zombie);

            putInAim(zombie, owner, 2.5D);
            int nearTick = heldLockTargetLockTick(
                    helper, power, owner, userActions, zombie);
            if (nearTick != KraftWorkSpec.LOCK_TARGET_HOLD_TICKS) {
                wrong.add("the HUD on another power, an armed mob 2.5 blocks "
                        + "away: Lock Target locked it in tick " + nearTick);
            }
            KraftWorkLockService.releaseTarget(owner, zombie);

            KraftWorkPayloads.recordStandHud(owner, true);
            putInAim(zombie, owner, 4.6D);
            int shownTick = heldLockTargetLockTick(
                    helper, power, owner, userActions, zombie);
            if (shownTick != KraftWorkSpec.LOCK_TARGET_HOLD_TICKS) {
                wrong.add("the HUD back on the Stand, an armed mob 4.6 "
                        + "blocks away: Lock Target locked it in tick "
                        + shownTick + " (1.16's precision ray reached 5)");
            }
        }
        finally {
            KraftWorkPayloads.forgetStandHud(owner);
            AbilityInput.keyRelease(LOCK_KEY, owner);
            userActions.setAction(null, SyncType.NO_SYNC);
            KraftWorkLockService.releaseTarget(owner, zombie);
            zombie.discard();
            stand.discard();
            owner.connection.disconnect(Component.literal(
                    "Kraft Work HUD aim GameTest complete"));
        }
        helper.assertTrue(wrong.isEmpty(),
                "1.16's Lock Target took Kraft Work's precision ray only "
                        + "with the Stand on the HUD: "
                        + String.join("; ", wrong));
        helper.succeed();
    }

    private static void turnStand(StandEntity stand, float yaw) {
        stand.setXRot(0.0F);
        stand.xRotO = 0.0F;
        stand.setYRot(yaw);
        stand.yRotO = yaw;
        stand.setYHeadRot(yaw);
        stand.yHeadRotO = yaw;
    }

    private static Mob armedZombie(GameTestHelper helper) {
        Mob zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(5, 2, 5));
        zombie.setNoAi(true);
        zombie.setNoGravity(true);
        zombie.setItemSlot(EquipmentSlot.MAINHAND,
                new ItemStack(Items.IRON_SWORD));
        return zombie;
    }

    // centred on the user's aim ray (the user faces south), its near side
    // the given distance from the user's eyes
    private static void putInAim(Entity entity, LivingEntity user,
            double distance) {
        Vec3 eye = user.getEyePosition();
        entity.setPos(eye.x, eye.y - entity.getBbHeight() / 2.0D,
                eye.z + distance + entity.getBbWidth() / 2.0D);
    }

    // the arrow's 0.5 high box centred on the aim ray, held where it is
    private static Arrow arrowAt(ServerLevel level, Vec3 ray) {
        Arrow arrow = EntityType.ARROW.create(level);
        if (arrow == null) {
            throw new IllegalStateException("Missing arrow entity");
        }
        arrow.setPos(ray.x, ray.y - 0.25D, ray.z);
        arrow.setNoGravity(true);
        level.addFreshEntity(arrow);
        return arrow;
    }

    // holds Lock Target and gives the tick its lock took the entity, or -1
    private static int heldLockTargetLockTick(GameTestHelper helper,
            StandPower power, ServerPlayer owner,
            LivingComponentAction userActions, Entity entity) {
        pressResolved(helper, power, owner, KraftWorkAddon.LOCK_TARGET,
                InputMethod.HOLD, LOCK_KEY);
        int lockTick = -1;
        for (int tick = 1; tick <= 20 && lockTick < 0; tick++) {
            userActions.tick();
            if (isLockedEntity(entity)) {
                lockTick = tick;
            }
        }
        AbilityInput.keyRelease(LOCK_KEY, owner);
        for (int tick = 0; tick < 3; tick++) {
            userActions.tick();
        }
        return lockTick;
    }

    private static boolean isLockedEntity(Entity entity) {
        return entity.hasData(KraftWorkAddon.LOCK_STATE)
                && KraftWorkLockService.state(entity).locked();
    }

    private static boolean inAimRay(LivingEntity user, Entity entity) {
        Vec3 eye = user.getEyePosition();
        return entity.getBoundingBox().clip(eye, eye.add(
                        user.getLookAngle().scale(KraftWorkSpec.LOCK_RANGE)))
                .isPresent();
    }

    private static boolean isLocked(Mob mob) {
        return mob.hasData(KraftWorkAddon.LOCK_STATE)
                && KraftWorkLockService.state(mob).locked();
    }

    // as ClAbilityInputPacket handles a press (AbilityInput.withConditionCheck,
    // then keyPress), with the condition checked now: the power keeps its
    // checks for the rest of the tick
    private static BufferingState pressResolved(GameTestHelper helper,
            Power<?> power, ServerPlayer user, String name,
            InputMethod inputMethod, short key) {
        BufferingState state = BufferingState.clickCanBuffer();
        Ability ability = power.getAbility(name);
        helper.assertTrue(ability != null, "Fixture: no ability " + name);
        ConditionCheck check = ability.checkConditions(power);
        if (check.isPositive() || inputMethod == InputMethod.HOLD
                && check.shouldContinueHold()) {
            FriendlyByteBuf input = new FriendlyByteBuf(Unpooled.buffer());
            try {
                AbilityInput.keyPress(key, ability, user, input,
                        inputMethod, 0.0F, state, ability.abilityId);
            }
            finally {
                input.release();
            }
        }
        else {
            ConditionCheck.sendActionFailedMessage(ability, check, user);
        }
        return state;
    }

    private static void pressUnsummoned(GameTestHelper helper,
            TestContext context, short key, Ability ability,
            InputMethod inputMethod) {
        helper.assertTrue(ability instanceof KraftWorkActionAbility
                        && AbilityInput.withConditionCheck(
                                ability, context.owner()),
                "Fixture: " + ability.abilityId
                        + " was not admitted with Kraft Work not out.");
        FriendlyByteBuf input = new FriendlyByteBuf(Unpooled.buffer());
        try {
            AbilityInput.keyPress(key, ability, context.owner(), input,
                    inputMethod, 0.0F, BufferingState.clickCanBuffer(),
                    ability.abilityId);
        }
        finally {
            input.release();
        }
    }

    // 1.16 StandAction.onPerform paid the cost and ignored the result: Place
    // Projectile fired on an empty bar, which stayed at 0.
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void placeProjectileFiresOnAnEmptyBar(
            GameTestHelper helper) {
        TestContext context = prepareEmptyBar(helper, "kw-place-empty");
        context.owner().setItemSlot(
                EquipmentSlot.OFFHAND,
                new ItemStack(Items.COBBLESTONE));
        Ability ability = context.power().getAbility(
                KraftWorkAddon.PLACE_PROJECTILE);
        helper.assertTrue(
                ability instanceof KraftWorkActionAbility
                        && AbilityInput.withConditionCheck(
                                ability, context.owner()),
                "1.16 had no stamina check: Place Projectile must be "
                        + "admitted on an empty bar.");
        FriendlyByteBuf input = new FriendlyByteBuf(Unpooled.buffer());
        try {
            AbilityInput.keyPress(
                    PLACE_KEY,
                    ability,
                    context.owner(),
                    input,
                    InputMethod.CLICK,
                    0.0F,
                    BufferingState.clickCanBuffer(),
                    ability.abilityId);
        }
        finally {
            input.release();
        }
        helper.succeedWhen(() -> {
            List<KraftWorkItemProjectile> projectiles =
                    helper.getLevel().getEntitiesOfClass(
                            KraftWorkItemProjectile.class,
                            new AABB(context.owner().blockPosition())
                                    .inflate(4.0D));
            helper.assertTrue(
                    projectiles.size() == 1,
                    "Place Projectile did not place its item on an "
                            + "empty bar.");
            helper.assertTrue(
                    context.owner().getOffhandItem().isEmpty(),
                    "The placed item was not taken from the off hand.");
            cleanup(
                    context,
                    projectiles.getFirst(),
                    "Kraft Work empty-bar place GameTest complete");
        });
    }

    // 1.16 KraftWorkPlaceProjectile.placeProjectile (:255-535) set each kind
    // at its own spot: getY(0.5) + 0.5 high, moved along the view's x and z
    // only, an arrow 0.75, a fire charge 0.8, the blade hat and the trident
    // 0.75 along z alone, the others and a placed item 0.5. Molotovs,
    // clackers and a knife thrown unsneaking from Resolve 3 stayed where
    // their constructors put them, 0.1 below the eyes.
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void placedProjectilesTakeTheirSpotsAsIn116(
            GameTestHelper helper) {
        TestContext context =
                prepareUnsummonedKraftWork(helper, "kw-place-spot");
        ServerPlayer owner = context.owner();
        StandPower power = context.power();
        owner.setYRot(30.0F);
        owner.setYHeadRot(30.0F);
        owner.setXRot(-25.0F);
        owner.setItemSlot(EquipmentSlot.MAINHAND,
                new ItemStack(Items.FLINT_AND_STEEL));
        helper.assertTrue(power.getResolveLevel() >= 3
                        && !owner.isShiftKeyDown(),
                "Fixture: the user is not at Resolve 3 or more, unsneaking.");
        Vec3 view = owner.getViewVector(1.0F);
        double x = owner.getX();
        double z = owner.getZ();
        double y = owner.getY(0.5D) + 0.5D;
        Vec3 half = new Vec3(x + view.x * 0.5D, y, z + view.z * 0.5D);
        Vec3 arrow = new Vec3(x + view.x * 0.75D, y, z + view.z * 0.75D);
        Vec3 fireCharge = new Vec3(x + view.x * 0.3F + view.x * 0.5D, y,
                z + view.z * 0.3F + view.z * 0.5D);
        Vec3 alongZ = new Vec3(x, y, z + view.z * 0.75D);
        Vec3 constructed = new Vec3(x, owner.getEyeY() - 0.1F, z);
        List<String> wrong = new ArrayList<>();
        try {
            checkPlacedSpot(helper, owner, Items.ARROW, arrow, wrong);
            checkPlacedSpot(helper, owner, Items.SPECTRAL_ARROW, arrow,
                    wrong);
            for (Item item : List.of(Items.SPLASH_POTION,
                    Items.LINGERING_POTION, Items.SNOWBALL, Items.EGG,
                    Items.ENDER_PEARL, Items.EXPERIENCE_BOTTLE,
                    Items.FIREWORK_ROCKET, Items.IRON_NUGGET,
                    Items.COBBLESTONE, Items.POTION)) {
                checkPlacedSpot(helper, owner, item, half, wrong);
            }
            checkPlacedSpot(helper, owner, Items.FIRE_CHARGE, fireCharge,
                    wrong);
            checkPlacedSpot(helper, owner, Items.TRIDENT, alongZ, wrong);
            checkPlacedSpot(helper, owner, ModItems.BLADE_HAT.get(), alongZ,
                    wrong);
            checkPlacedSpot(helper, owner, ModItems.MOLOTOV.get(),
                    constructed, wrong);
            checkPlacedSpot(helper, owner, ModItems.CLACKERS.get(),
                    constructed, wrong);
            checkPlacedSpot(helper, owner, ModItems.KNIFE.get(), constructed,
                    wrong);
            owner.setShiftKeyDown(true);
            checkPlacedSpot(helper, owner, ModItems.KNIFE.get(), half, wrong);
            owner.setShiftKeyDown(false);
            power.setResolveLevel(2);
            checkPlacedSpot(helper, owner, ModItems.KNIFE.get(), half, wrong);
        }
        finally {
            owner.connection.disconnect(Component.literal(
                    "Kraft Work placed spot GameTest complete"));
        }
        helper.assertTrue(wrong.isEmpty(),
                "Place Projectile did not put its projectile where 1.16 "
                        + "did: " + String.join("; ", wrong));
        helper.succeed();
    }

    // places one item with the production factory and checks the spot of
    // the one projectile it placed
    private static void checkPlacedSpot(GameTestHelper helper,
            ServerPlayer owner, Item item, Vec3 expected,
            List<String> wrong) {
        String name = BuiltInRegistries.ITEM.getKey(item).getPath()
                + (owner.isShiftKeyDown() ? " (sneaking)" : "")
                + (item == ModItems.KNIFE.get() ? " at Resolve "
                        + StandPower.get(owner).getResolveLevel() : "");
        if (!KraftWorkProjectileFactory.place(helper.getLevel(), owner, null,
                new ItemStack(item))) {
            wrong.add(name + " was not placed");
            return;
        }
        List<Projectile> placed = helper.getLevel().getEntitiesOfClass(
                Projectile.class, owner.getBoundingBox().inflate(4.0D),
                projectile -> projectile.getOwner() == owner);
        if (placed.size() != 1) {
            wrong.add(name + " left " + placed.size() + " projectiles");
        }
        else if (placed.getFirst().position().distanceTo(expected)
                > 1.0E-6D) {
            wrong.add(name + " (" + placed.getFirst().getClass()
                    .getSimpleName() + ") at " + placed.getFirst().position()
                    + ", 1.16 " + expected);
        }
        placed.forEach(Entity::discard);
    }

    // 1.16 projectileList (KraftWorkPlaceProjectile :196-199) took throwable
    // potions only, clackers only unenchanted and a molotov only with flint
    // and steel in the main hand; anything else was placed as a KWItemEntity
    // (:486-538). A drinkable potion (weakItems, removeOnImpact :233-234)
    // dealt nothing, broke on its first hit without a splash, and Release
    // Beneficial left it (KraftWorkReleaseBeneficial :69-85: of the placed
    // items only a totem).
    @GameTest(template = "empty", timeoutTicks = 60)
    public static void itemsOffTheProjectileListArePlacedAsItemsAsIn116(
            GameTestHelper helper) {
        TestContext context =
                prepareUnsummonedKraftWork(helper, "kw-place-potion");
        ServerPlayer owner = context.owner();
        StandPower power = context.power();
        owner.setGameMode(GameType.SURVIVAL);
        Ability place = power.getAbility(KraftWorkAddon.PLACE_PROJECTILE);
        LivingComponentAction userActions =
                LivingComponentAction.getComponent(owner, PowerClass.STAND);
        List<String> wrong = new ArrayList<>();
        ItemStack clackers = new ItemStack(ModItems.CLACKERS.get());
        clackers.enchant(helper.getLevel().registryAccess()
                .lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(Enchantments.UNBREAKING), 1);
        for (ItemStack item : List.of(clackers,
                new ItemStack(ModItems.MOLOTOV.get()))) {
            owner.setItemSlot(EquipmentSlot.OFFHAND, item.copy());
            helper.assertTrue(place.getSpriteName(power).endsWith("_item"),
                    "Fixture: " + item + " in the off hand does not read "
                            + "Place Item.");
            helper.assertTrue(KraftWorkProjectileFactory.place(
                            helper.getLevel(), owner, null, item),
                    "Fixture: " + item + " was not placed.");
            List<Projectile> placed = helper.getLevel().getEntitiesOfClass(
                    Projectile.class, owner.getBoundingBox().inflate(4.0D),
                    projectile -> projectile.getOwner() == owner);
            if (placed.size() != 1
                    || !(placed.getFirst()
                            instanceof KraftWorkItemProjectile held)
                    || !held.getItem().is(item.getItem())) {
                wrong.add(item + " placed as " + placed.stream()
                        .map(entity -> entity.getClass().getSimpleName())
                        .toList());
            }
            placed.forEach(Entity::discard);
        }

        // the potion lands on this block, 0.4 below its spot
        Vec3 start = owner.position();
        owner.setPos(start.x, start.y, start.z + 0.2D);
        helper.setBlock(new BlockPos(1, 2, 2), Blocks.STONE);
        owner.setItemSlot(EquipmentSlot.OFFHAND, PotionContents
                .createItemStack(Items.POTION, Potions.SLOWNESS));
        helper.assertTrue(place.getSpriteName(power).endsWith("_item"),
                "Fixture: a drinkable potion in the off hand does not read "
                        + "Place Item.");
        pressResolved(helper, power, owner, KraftWorkAddon.PLACE_PROJECTILE,
                InputMethod.CLICK, PLACE_KEY);
        AbilityInput.keyRelease(PLACE_KEY, owner);
        userActions.tick();
        List<Projectile> placed = helper.getLevel().getEntitiesOfClass(
                Projectile.class, owner.getBoundingBox().inflate(4.0D),
                projectile -> projectile.getOwner() == owner);
        if (placed.size() != 1
                || !(placed.getFirst()
                        instanceof KraftWorkItemProjectile potion)
                || !potion.getItem().is(Items.POTION)
                || !isLockedEntity(potion)
                || !owner.getOffhandItem().isEmpty()) {
            placed.forEach(Entity::discard);
            owner.connection.disconnect(Component.literal(
                    "Kraft Work placed potion GameTest complete"));
            helper.fail("Place Projectile with a drinkable potion placed "
                    + placed.stream().map(entity -> entity.getClass()
                            .getSimpleName()).toList()
                    + ", the off hand " + owner.getOffhandItem()
                    + " (1.16: one held item, KWItemEntity); "
                    + String.join("; ", wrong));
            return;
        }
        if (!potion.removeAfterHitting()
                || potion.getBaseDamage() != 0.0D
                || potion.isBeneficial()) {
            wrong.add("the placed potion breaks on impact "
                    + potion.removeAfterHitting() + ", base damage "
                    + potion.getBaseDamage() + ", beneficial "
                    + potion.isBeneficial());
        }
        int beneficial = KraftWorkLockService.releaseProjectiles(
                owner, ReleaseFilter.BENEFICIAL);
        if (beneficial != 0 || !isLockedEntity(potion)) {
            wrong.add("Release Beneficial freed " + beneficial
                    + ", the potion still held " + isLockedEntity(potion));
        }
        KraftWorkLockService.releaseTarget(owner, potion);
        helper.succeedWhen(() -> {
            helper.assertTrue(potion.isRemoved(),
                    "The released potion has not broken yet.");
            List<AreaEffectCloud> clouds =
                    helper.getLevel().getEntitiesOfClass(
                            AreaEffectCloud.class,
                            owner.getBoundingBox().inflate(6.0D));
            List<String> found = new ArrayList<>(wrong);
            if (owner.hasEffect(MobEffects.MOVEMENT_SLOWDOWN)
                    || !clouds.isEmpty()) {
                found.add("the potion splashed as it broke: the user "
                        + "slowed " + owner.hasEffect(
                                MobEffects.MOVEMENT_SLOWDOWN)
                        + ", clouds " + clouds.size());
            }
            helper.assertTrue(found.isEmpty(),
                    "1.16 placed an item off its projectileList as a "
                            + "plain item: " + String.join("; ", found));
            owner.connection.disconnect(Component.literal(
                    "Kraft Work placed potion GameTest complete"));
        });
    }

    // 1.16 KraftWorkPlaceProjectile :287-318: from Resolve 3 an unsneaking
    // user threw min(count, 8) knives at once, the first with inaccuracy 1
    // and the rest 16, each flying one tick of its own (flightTicks 1)
    // before it held, and an entity met in that tick stopped a knife unhurt
    // where it began; KNIVES_THROW (KNIFE_THROW for one) at volume 0, a
    // cooldown of 3 a knife, the stack shrunk by them all (:558) and 10
    // stamina each (getStaminaCost :566-574). Sneaking, or below Resolve 3,
    // one knife held at once.
    @GameTest(template = "empty", timeoutTicks = 40)
    public static void knivesFromResolve3AreThrownSeveralAtOnceAsIn116(
            GameTestHelper helper) {
        TestContext context =
                prepareUnsummonedKraftWork(helper, "kw-place-knives");
        ServerPlayer owner = context.owner();
        StandPower power = context.power();
        owner.setGameMode(GameType.SURVIVAL);
        LivingComponentAction userActions =
                LivingComponentAction.getComponent(owner, PowerClass.STAND);
        Item knifeItem = ModItems.KNIFE.get();
        List<String> wrong = new ArrayList<>();
        List<PlayLevelSoundEvent.AtPosition> sounds = new ArrayList<>();
        Consumer<PlayLevelSoundEvent.AtPosition> listener = event -> {
            if (event.getSound() != null
                    && (event.getSound().value()
                                    == ModSoundEvents.KNIFE_THROW.get()
                            || event.getSound().value()
                                    == ModSoundEvents.KNIVES_THROW.get())
                    && event.getPosition().distanceToSqr(
                            owner.position()) < 1.0E-6D) {
                sounds.add(event);
            }
        };
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true,
                PlayLevelSoundEvent.AtPosition.class, listener);
        helper.assertTrue(power.getResolveLevel() >= 3
                        && !owner.isShiftKeyDown()
                        && !power.isStaminaInfinite(),
                "Fixture: the user is not at Resolve 3 or more, unsneaking, "
                        + "on a finite bar.");
        Vec3 view = owner.getViewVector(1.0F);
        Vec3 half = new Vec3(owner.getX() + view.x * 0.5D,
                owner.getY(0.5D) + 0.5D, owner.getZ() + view.z * 0.5D);
        Vec3 constructed = new Vec3(owner.getX(), owner.getEyeY() - 0.1F,
                owner.getZ());
        List<KnifeEntity> thrown = new ArrayList<>();
        Mob[] blocker = new Mob[1];
        Runnable cleanup = () -> {
            NeoForge.EVENT_BUS.unregister(listener);
            helper.getLevel().getEntitiesOfClass(KnifeEntity.class,
                            owner.getBoundingBox().inflate(6.0D),
                            knife -> knife.getOwner() == owner)
                    .forEach(Entity::discard);
            if (blocker[0] != null) {
                blocker[0].discard();
            }
            owner.connection.disconnect(Component.literal(
                    "Kraft Work knives GameTest complete"));
        };
        helper.startSequence().thenExecute(() -> {
            owner.setShiftKeyDown(true);
            checkOneKnife(helper, power, owner, userActions, sounds, half,
                    "sneaking at Resolve " + power.getResolveLevel(), wrong);
            owner.setShiftKeyDown(false);
            int resolve = power.getResolveLevel();
            power.setResolveLevel(2);
            checkOneKnife(helper, power, owner, userActions, sounds, half,
                    "at Resolve 2", wrong);
            power.setResolveLevel(resolve);

            sounds.clear();
            thrown.addAll(pressPlaceKnives(helper, power, owner,
                    userActions, 10));
            float paid = power.getMaxStamina() - power.getStamina();
            if (thrown.size() != 8
                    || owner.getOffhandItem().getCount() != 2
                    || cooldownTicks(owner, knifeItem) != 24
                    || paid != 80.0F) {
                wrong.add("10 knives unsneaking at Resolve "
                        + power.getResolveLevel() + ": " + thrown.size()
                        + " placed, " + owner.getOffhandItem().getCount()
                        + " left, cooldown " + cooldownTicks(owner, knifeItem)
                        + ", stamina paid " + paid
                        + " (1.16: 8, 2, 24 and 80)");
            }
            for (KnifeEntity knife : thrown) {
                if (!isLockedEntity(knife)
                        || knife.position().distanceTo(constructed) > 1.0E-6D
                        || knife.getDeltaMovement().length() < 1.0D) {
                    wrong.add("a knife thrown in flight at "
                            + knife.position() + ", held "
                            + isLockedEntity(knife) + ", motion "
                            + knife.getDeltaMovement() + " (1.16: at "
                            + constructed + ", still moving)");
                    break;
                }
            }
            if (sounds.size() != 1
                    || sounds.getFirst().getSound().value()
                            != ModSoundEvents.KNIVES_THROW.get()
                    || sounds.getFirst().getSource() != SoundSource.PLAYERS
                    || sounds.getFirst().getOriginalVolume() != 0.0F
                    || sounds.getFirst().getOriginalPitch() < 0.4F / 1.2F
                    || sounds.getFirst().getOriginalPitch() > 0.4F / 0.8F) {
                wrong.add("the throw of 8 knives played " + sounds.stream()
                        .map(sound -> sound.getSound().value().getLocation()
                                + " " + sound.getSource() + " volume "
                                + sound.getOriginalVolume() + " pitch "
                                + sound.getOriginalPitch())
                        .toList() + " (1.16: one knives_throw, PLAYERS, "
                        + "volume 0, pitch 1/3 to 1/2)");
            }
        }).thenExecuteAfter(3, () -> {
            thrown.sort(java.util.Comparator.comparingInt(Entity::getId));
            int spread = 0;
            for (KnifeEntity knife : thrown) {
                Vec3 moved = knife.position().subtract(constructed);
                double angle = Math.toDegrees(Math.acos(Mth.clamp(
                        moved.normalize().dot(view), -1.0D, 1.0D)));
                if (!isLockedEntity(knife) || knife.isRemoved()
                        || moved.length() < 1.0D || moved.length() > 2.0D
                        || knife.getDeltaMovement().lengthSqr() != 0.0D
                        || !knife.isNoGravity()
                        || knife == thrown.getFirst() && angle > 3.0D) {
                    wrong.add("a knife 3 ticks after the throw moved "
                            + moved.length() + " at " + angle
                            + " degrees from the view, held "
                            + isLockedEntity(knife) + ", motion "
                            + knife.getDeltaMovement() + " (1.16: one tick "
                            + "of flight, about 1.5, then held still; the "
                            + "first within 3 degrees)");
                    break;
                }
                if (angle > 3.0D) {
                    spread++;
                }
                knife.getPersistentData().putDouble("kw_test_x",
                        knife.getX());
                knife.getPersistentData().putDouble("kw_test_z",
                        knife.getZ());
            }
            if (spread == 0) {
                wrong.add("no knife after the first spread more than 3 "
                        + "degrees (1.16: inaccuracy 16)");
            }
        }).thenExecuteAfter(2, () -> {
            for (KnifeEntity knife : thrown) {
                if (knife.getX() != knife.getPersistentData()
                                .getDouble("kw_test_x")
                        || knife.getZ() != knife.getPersistentData()
                                .getDouble("kw_test_z")) {
                    wrong.add("a held knife kept moving after its flight");
                    break;
                }
                KraftWorkLockService.releaseTarget(owner, knife);
                knife.discard();
            }
            owner.getCooldowns().removeCooldown(knifeItem);
            power.resetAbilityCooldowns();
            power.setStamina(power.getMaxStamina());
            blocker[0] = helper.spawn(EntityType.COW,
                    new BlockPos(1, 2, 1));
            blocker[0].setNoAi(true);
            blocker[0].setNoGravity(true);
            putInAim(blocker[0], owner, 0.4D);
            thrown.clear();
            thrown.addAll(pressPlaceKnives(helper, power, owner,
                    userActions, 2));
        }).thenExecuteAfter(3, () -> {
            for (KnifeEntity knife : thrown) {
                if (!isLockedEntity(knife) || knife.isRemoved()
                        || knife.position().distanceTo(constructed)
                                > 1.0E-6D) {
                    wrong.add("a knife thrown at a mob 0.4 ahead is at "
                            + knife.position() + ", held "
                            + isLockedEntity(knife) + " (1.16: stopped "
                            + "where it began, " + constructed + ")");
                    break;
                }
            }
            if (thrown.size() != 2
                    || blocker[0].getHealth() < blocker[0].getMaxHealth()) {
                wrong.add(thrown.size() + " knives thrown at a mob in "
                        + "front, its health " + blocker[0].getHealth()
                        + " (1.16: 2, the mob unhurt)");
            }
            cleanup.run();
            helper.assertTrue(wrong.isEmpty(),
                    "1.16 threw several knives at once from Resolve 3: "
                            + String.join("; ", wrong));
        }).thenSucceed();
    }

    // one press of Place Projectile with a stack of knives in the off hand;
    // the knives it placed
    private static List<KnifeEntity> pressPlaceKnives(GameTestHelper helper,
            StandPower power, ServerPlayer owner,
            LivingComponentAction userActions, int count) {
        owner.setItemSlot(EquipmentSlot.OFFHAND,
                new ItemStack(ModItems.KNIFE.get(), count));
        pressResolved(helper, power, owner, KraftWorkAddon.PLACE_PROJECTILE,
                InputMethod.CLICK, PLACE_KEY);
        AbilityInput.keyRelease(PLACE_KEY, owner);
        userActions.tick();
        return helper.getLevel().getEntitiesOfClass(KnifeEntity.class,
                owner.getBoundingBox().inflate(4.0D),
                knife -> knife.getOwner() == owner);
    }

    // sneaking, or below Resolve 3: one knife held at once at its spot
    private static void checkOneKnife(GameTestHelper helper,
            StandPower power, ServerPlayer owner,
            LivingComponentAction userActions,
            List<PlayLevelSoundEvent.AtPosition> sounds, Vec3 spot,
            String row, List<String> wrong) {
        Item knifeItem = ModItems.KNIFE.get();
        owner.getCooldowns().removeCooldown(knifeItem);
        power.resetAbilityCooldowns();
        power.setStamina(power.getMaxStamina());
        sounds.clear();
        List<KnifeEntity> placed =
                pressPlaceKnives(helper, power, owner, userActions, 10);
        float paid = power.getMaxStamina() - power.getStamina();
        if (placed.size() != 1
                || owner.getOffhandItem().getCount() != 9
                || cooldownTicks(owner, knifeItem) != 3
                || paid != 10.0F
                || !sounds.isEmpty()
                || !isLockedEntity(placed.getFirst())
                || placed.getFirst().position().distanceTo(spot) > 1.0E-6D
                || placed.getFirst().getDeltaMovement().lengthSqr() != 0.0D
                || KraftWorkLockService.state(placed.getFirst())
                        .holdDelay() != 0) {
            wrong.add("10 knives " + row + ": " + placed.size()
                    + " placed, " + owner.getOffhandItem().getCount()
                    + " left, cooldown " + cooldownTicks(owner, knifeItem)
                    + ", stamina paid " + paid + ", sounds " + sounds.size()
                    + (placed.isEmpty() ? "" : ", held at "
                            + placed.getFirst().position() + " moving "
                            + placed.getFirst().getDeltaMovement())
                    + " (1.16: one knife held at once at " + spot
                    + ", 9 left, cooldown 3, stamina 10, no sound)");
        }
        placed.forEach(knife -> {
            KraftWorkLockService.releaseTarget(owner, knife);
            knife.discard();
        });
        owner.getCooldowns().removeCooldown(knifeItem);
        power.resetAbilityCooldowns();
        power.setStamina(power.getMaxStamina());
    }

    // the length of the item's cooldown, read in the tick it began
    private static int cooldownTicks(ServerPlayer player, Item item) {
        if (!player.getCooldowns().isOnCooldown(item)) {
            return 0;
        }
        float left = player.getCooldowns().getCooldownPercent(item, 1.0F);
        return Math.round(1.0F / (1.0F - left));
    }

    // 1.16 KraftWorkPlaceProjectile :260, :304, :340, :355 divided a placed
    // arrow's, knife's, blade hat's and trident's base damage by 6; a placed
    // item (KWItemEntity, :530) had its own, divided once
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void placedProjectilesHitWithASixthOfTheirBaseDamageAsIn116(
            GameTestHelper helper) {
        TestContext context =
                prepareUnsummonedKraftWork(helper, "kw-place-damage");
        ServerPlayer owner = context.owner();
        ServerLevel level = helper.getLevel();
        context.power().setResolveLevel(2);
        List<String> wrong = new ArrayList<>();
        try {
            ItemStack hat = new ItemStack(ModItems.BLADE_HAT.get());
            ItemStack trident = new ItemStack(Items.TRIDENT);
            ItemStack knife = new ItemStack(ModItems.KNIFE.get());
            ItemStack cobblestone = new ItemStack(Items.COBBLESTONE);
            KraftWorkItemProjectile item = new KraftWorkItemProjectile(
                    KraftWorkAddon.KW_ITEM.get(), level);
            item.initialize(owner, null, cobblestone.copy());
            checkPlacedDamage(helper, owner, new ItemStack(Items.ARROW),
                    ((ArrowItem) Items.ARROW).createArrow(level,
                            new ItemStack(Items.ARROW), owner, null)
                            .getBaseDamage() / 6.0D, wrong);
            checkPlacedDamage(helper, owner,
                    new ItemStack(Items.SPECTRAL_ARROW),
                    ((ArrowItem) Items.SPECTRAL_ARROW).createArrow(level,
                            new ItemStack(Items.SPECTRAL_ARROW), owner, null)
                            .getBaseDamage() / 6.0D, wrong);
            checkPlacedDamage(helper, owner, knife,
                    new KnifeEntity(level, owner, knife.copy())
                            .getBaseDamage() / 6.0D, wrong);
            checkPlacedDamage(helper, owner, hat,
                    new BladeHatEntity(level, owner, hat.copy())
                            .getBaseDamage() / 6.0D, wrong);
            checkPlacedDamage(helper, owner, trident,
                    new ThrownTrident(level, owner, trident.copy())
                            .getBaseDamage() / 6.0D, wrong);
            checkPlacedDamage(helper, owner, cobblestone,
                    item.getBaseDamage(), wrong);
        }
        finally {
            owner.connection.disconnect(Component.literal(
                    "Kraft Work placed damage GameTest complete"));
        }
        helper.assertTrue(wrong.isEmpty(),
                "1.16 placed these with a sixth of their base damage: "
                        + String.join("; ", wrong));
        helper.succeed();
    }

    // 1.16 KraftWorkPlaceProjectile set each item's own cooldown (3; 5 for a
    // trident :363, 10 for a fire charge or a firework :399, :422, 20 for an
    // ender pearl :438, none for clackers :402-411) and counted a use of an
    // ender pearl, snowball, egg, trident or molotov (:328, :364, :439, :454,
    // :469)
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void placedItemsTakeTheirOwnCooldownAndUseAsIn116(
            GameTestHelper helper) {
        TestContext context =
                prepareUnsummonedKraftWork(helper, "kw-place-cooldown");
        ServerPlayer owner = context.owner();
        context.power().setResolveLevel(2);
        owner.setItemSlot(EquipmentSlot.MAINHAND,
                new ItemStack(Items.FLINT_AND_STEEL));
        List<String> wrong = new ArrayList<>();
        Object[][] rows = {
                {Items.ARROW, 3, false},
                {Items.SPLASH_POTION, 3, false},
                {ModItems.KNIFE.get(), 3, false},
                {ModItems.MOLOTOV.get(), 3, true},
                {ModItems.BLADE_HAT.get(), 3, false},
                {Items.TRIDENT, 5, true},
                {Items.IRON_NUGGET, 3, false},
                {Items.FIRE_CHARGE, 10, false},
                {ModItems.CLACKERS.get(), 0, false},
                {Items.FIREWORK_ROCKET, 10, false},
                {Items.ENDER_PEARL, 20, true},
                {Items.SNOWBALL, 3, true},
                {Items.EGG, 3, true},
                {Items.EXPERIENCE_BOTTLE, 3, false},
                {Items.COBBLESTONE, 3, false}};
        try {
            for (Object[] row : rows) {
                Item item = (Item) row[0];
                int used = owner.getStats().getValue(
                        Stats.ITEM_USED.get(item));
                owner.getCooldowns().removeCooldown(item);
                boolean placed = KraftWorkProjectileFactory.place(
                        helper.getLevel(), owner, null, new ItemStack(item));
                int cooldown = cooldownTicks(owner, item);
                int counted = owner.getStats().getValue(
                        Stats.ITEM_USED.get(item)) - used;
                if (!placed || cooldown != (int) row[1]
                        || counted != ((boolean) row[2] ? 1 : 0)) {
                    wrong.add(BuiltInRegistries.ITEM.getKey(item).getPath()
                            + " placed " + placed + ", cooldown " + cooldown
                            + ", uses counted " + counted + " (1.16: "
                            + row[1] + ", " + ((boolean) row[2] ? 1 : 0)
                            + ")");
                }
                helper.getLevel().getEntitiesOfClass(Projectile.class,
                                owner.getBoundingBox().inflate(4.0D),
                                projectile -> projectile.getOwner() == owner)
                        .forEach(Entity::discard);
            }
        }
        finally {
            owner.connection.disconnect(Component.literal(
                    "Kraft Work placed cooldown GameTest complete"));
        }
        helper.assertTrue(wrong.isEmpty(),
                "1.16 gave each placed item its own cooldown and counted "
                        + "its use: " + String.join("; ", wrong));
        helper.succeed();
    }

    // 1.16 KraftWorkReleaseBeneficial.BeneficialItems (:69-85): a thrown
    // potion was beneficial by its first effect, one with none was not; a
    // placed totem was. Release Projectiles R and NR left what it took
    // (KraftWorkReleaseProjectilesR :59, NR :59).
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void releaseBeneficialTakesAPotionByItsFirstEffectAsIn116(
            GameTestHelper helper) {
        TestContext context =
                prepareUnsummonedKraftWork(helper, "kw-release-beneficial");
        ServerPlayer owner = context.owner();
        List<String> wrong = new ArrayList<>();
        List<Entity> placed = new ArrayList<>();
        try {
            for (ItemStack stack : List.of(
                    PotionContents.createItemStack(Items.SPLASH_POTION,
                            Potions.HEALING),
                    PotionContents.createItemStack(Items.SPLASH_POTION,
                            Potions.POISON),
                    PotionContents.createItemStack(Items.LINGERING_POTION,
                            Potions.WATER),
                    PotionContents.createItemStack(Items.SPLASH_POTION,
                            Potions.TURTLE_MASTER),
                    new ItemStack(Items.TOTEM_OF_UNDYING))) {
                helper.assertTrue(KraftWorkProjectileFactory.place(
                                helper.getLevel(), owner, null, stack),
                        "Fixture: " + stack + " was not placed.");
                placed.addAll(helper.getLevel().getEntitiesOfClass(
                        Projectile.class, owner.getBoundingBox().inflate(4.0D),
                        projectile -> projectile.getOwner() == owner
                                && !placed.contains(projectile)));
            }
            helper.assertTrue(placed.size() == 5
                            && placed.stream().allMatch(
                                    KraftWorkGameTests::isLockedEntity),
                    "Fixture: the five placed projectiles are not held.");
            int beneficial = KraftWorkLockService.releaseProjectiles(
                    owner, ReleaseFilter.BENEFICIAL);
            List<Boolean> held = placed.stream()
                    .map(KraftWorkGameTests::isLockedEntity).toList();
            if (beneficial != 2 || !held.equals(
                    List.of(false, true, true, true, false))) {
                wrong.add("Release Beneficial freed " + beneficial
                        + ", still held (healing, poison, water, turtle "
                        + "master, totem) " + held + " (1.16: 2, [false, "
                        + "true, true, true, false])");
            }
            int notReady = KraftWorkLockService.releaseProjectiles(
                    owner, ReleaseFilter.NOT_READY);
            if (notReady != 3 || placed.stream()
                    .anyMatch(KraftWorkGameTests::isLockedEntity)) {
                wrong.add("Release Projectiles NR then freed " + notReady
                        + " (1.16: the poison, the water and the turtle "
                        + "master, 3)");
            }
        }
        finally {
            placed.forEach(Entity::discard);
            owner.connection.disconnect(Component.literal(
                    "Kraft Work release beneficial GameTest complete"));
        }
        helper.assertTrue(wrong.isEmpty(),
                "1.16 took a thrown potion as beneficial by its first "
                        + "effect: " + String.join("; ", wrong));
        helper.succeed();
    }

    // 1.16 KraftWorkPlaceProjectile :350-369: a trident with Riptide went
    // only in water or rain, else nothing was placed and the off hand still
    // lost it (:557-559), which the owner changed (2026-10-24): it keeps it;
    // a placed trident took a use (:351). A molotov
    // (:319-335) took a use of the flint and steel in the main hand and
    // played MOLOTOV_THROW (0.5, 0.4 / (rand * 0.4 + 0.8)).
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void tridentAndMolotovTakeWhat116TookAsIn116(
            GameTestHelper helper) {
        TestContext context =
                prepareUnsummonedKraftWork(helper, "kw-place-trident");
        ServerPlayer owner = context.owner();
        StandPower power = context.power();
        owner.setGameMode(GameType.SURVIVAL);
        ServerLevel level = helper.getLevel();
        LivingComponentAction userActions =
                LivingComponentAction.getComponent(owner, PowerClass.STAND);
        List<String> wrong = new ArrayList<>();
        List<PlayLevelSoundEvent.AtPosition> sounds = new ArrayList<>();
        Consumer<PlayLevelSoundEvent.AtPosition> listener = event -> {
            if (event.getSound() != null && event.getSound().value()
                            == ModSoundEvents.MOLOTOV_THROW.get()
                    && event.getPosition().distanceToSqr(
                            owner.position()) < 1.0E-6D) {
                sounds.add(event);
            }
        };
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true,
                PlayLevelSoundEvent.AtPosition.class, listener);
        BlockPos feet = owner.blockPosition();
        try {
            ItemStack riptide = new ItemStack(Items.TRIDENT);
            riptide.enchant(level.registryAccess()
                    .lookupOrThrow(Registries.ENCHANTMENT)
                    .getOrThrow(Enchantments.RIPTIDE), 1);
            helper.assertTrue(!owner.isInWaterOrRain(),
                    "Fixture: the user is in water or rain.");
            owner.setItemSlot(EquipmentSlot.OFFHAND, riptide.copy());
            float stamina = power.getStamina();
            pressResolved(helper, power, owner,
                    KraftWorkAddon.PLACE_PROJECTILE, InputMethod.CLICK,
                    PLACE_KEY);
            AbilityInput.keyRelease(PLACE_KEY, owner);
            userActions.tick();
            List<ThrownTrident> tridents = level.getEntitiesOfClass(
                    ThrownTrident.class, owner.getBoundingBox().inflate(4.0D),
                    trident -> trident.getOwner() == owner);
            // the owner's call (2026-10-24): the trident stays in the off
            // hand; the press still pays and cools down as 1.16's did
            ItemStack kept = owner.getOffhandItem();
            if (!tridents.isEmpty() || !kept.is(Items.TRIDENT)
                    || kept.getCount() != 1 || kept.getDamageValue() != 0
                    || owner.getCooldowns().isOnCooldown(Items.TRIDENT)
                    || stamina - power.getStamina()
                            != KraftWorkSpec.PLACE_STAMINA
                    || !power.isAbilityOnCooldown(
                            KraftWorkAddon.PLACE_PROJECTILE)) {
                wrong.add("a Riptide trident placed out of water and rain: "
                        + tridents.size() + " placed, the off hand " + kept
                        + " (damage " + kept.getDamageValue() + "), cooldown "
                        + owner.getCooldowns().isOnCooldown(Items.TRIDENT)
                        + ", stamina paid " + (stamina - power.getStamina())
                        + ", Place Projectile cooling down "
                        + power.isAbilityOnCooldown(
                                KraftWorkAddon.PLACE_PROJECTILE)
                        + " (the owner's call: none placed, the trident kept, "
                        + "no item cooldown; 1.16's press paid "
                        + KraftWorkSpec.PLACE_STAMINA + " and cooled down)");
            }
            tridents.forEach(Entity::discard);

            // a trident without Riptide still goes and is taken
            power.resetAbilityCooldowns();
            owner.setItemSlot(EquipmentSlot.OFFHAND,
                    new ItemStack(Items.TRIDENT));
            pressResolved(helper, power, owner,
                    KraftWorkAddon.PLACE_PROJECTILE, InputMethod.CLICK,
                    PLACE_KEY);
            AbilityInput.keyRelease(PLACE_KEY, owner);
            userActions.tick();
            tridents = level.getEntitiesOfClass(
                    ThrownTrident.class, owner.getBoundingBox().inflate(4.0D),
                    trident -> trident.getOwner() == owner);
            if (tridents.size() != 1 || !owner.getOffhandItem().isEmpty()
                    || cooldownTicks(owner, Items.TRIDENT) != 5) {
                wrong.add("a trident pressed out of water: " + tridents.size()
                        + " placed, the off hand " + owner.getOffhandItem()
                        + ", cooldown " + cooldownTicks(owner, Items.TRIDENT)
                        + " (1.16: one placed, the trident taken, 5 ticks)");
            }
            tridents.forEach(Entity::discard);
            owner.getCooldowns().removeCooldown(Items.TRIDENT);

            // 1.16's third refusal (:350): a trident pressed in the first 10
            // ticks of the user's use of another item went nowhere; the
            // coordinator's call, by the owner's reasoning: it stays too
            power.resetAbilityCooldowns();
            owner.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
            owner.setItemSlot(EquipmentSlot.OFFHAND,
                    new ItemStack(Items.TRIDENT));
            owner.startUsingItem(InteractionHand.MAIN_HAND);
            helper.assertTrue(owner.isUsingItem()
                            && owner.getUseItemRemainingTicks()
                                    == Items.TRIDENT.getDefaultInstance()
                                            .getUseDuration(owner),
                    "Fixture: the user is not at the start of a use "
                            + "of the bow.");
            stamina = power.getStamina();
            pressResolved(helper, power, owner,
                    KraftWorkAddon.PLACE_PROJECTILE, InputMethod.CLICK,
                    PLACE_KEY);
            AbilityInput.keyRelease(PLACE_KEY, owner);
            userActions.tick();
            tridents = level.getEntitiesOfClass(
                    ThrownTrident.class, owner.getBoundingBox().inflate(4.0D),
                    trident -> trident.getOwner() == owner);
            kept = owner.getOffhandItem();
            if (!tridents.isEmpty() || !kept.is(Items.TRIDENT)
                    || kept.getCount() != 1 || kept.getDamageValue() != 0
                    || owner.getCooldowns().isOnCooldown(Items.TRIDENT)
                    || stamina - power.getStamina()
                            != KraftWorkSpec.PLACE_STAMINA
                    || !power.isAbilityOnCooldown(
                            KraftWorkAddon.PLACE_PROJECTILE)) {
                wrong.add("a trident pressed in the first ticks of the use of "
                        + "a bow: " + tridents.size() + " placed, the off "
                        + "hand " + kept + ", cooldown "
                        + owner.getCooldowns().isOnCooldown(Items.TRIDENT)
                        + ", stamina paid " + (stamina - power.getStamina())
                        + " (1.16 placed none; the coordinator's call, by "
                        + "the owner's: the trident kept, no item cooldown; "
                        + "1.16's press paid " + KraftWorkSpec.PLACE_STAMINA
                        + " and cooled down)");
            }
            tridents.forEach(Entity::discard);
            owner.stopUsingItem();
            owner.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            power.resetAbilityCooldowns();

            level.setBlockAndUpdate(feet, Blocks.WATER.defaultBlockState());
            level.setBlockAndUpdate(feet.above(),
                    Blocks.WATER.defaultBlockState());
            owner.baseTick();
            helper.assertTrue(owner.isInWaterOrRain(),
                    "Fixture: the user is not in water.");
            boolean wet = KraftWorkProjectileFactory.place(level, owner, null,
                    riptide.copy());
            tridents = level.getEntitiesOfClass(ThrownTrident.class,
                    owner.getBoundingBox().inflate(4.0D),
                    trident -> trident.getOwner() == owner);
            if (!wet || tridents.size() != 1) {
                wrong.add("a Riptide trident placed in water: placed " + wet
                        + ", " + tridents.size() + " (1.16: one)");
            }
            tridents.forEach(Entity::discard);
            level.setBlockAndUpdate(feet, Blocks.AIR.defaultBlockState());
            level.setBlockAndUpdate(feet.above(),
                    Blocks.AIR.defaultBlockState());
            owner.baseTick();

            KraftWorkProjectileFactory.place(level, owner, null,
                    new ItemStack(Items.TRIDENT));
            tridents = level.getEntitiesOfClass(ThrownTrident.class,
                    owner.getBoundingBox().inflate(4.0D),
                    trident -> trident.getOwner() == owner);
            if (tridents.size() != 1 || tridents.getFirst().getWeaponItem()
                    .getDamageValue() != 1) {
                wrong.add("a trident placed: " + tridents.stream()
                        .map(trident -> "damage " + trident.getWeaponItem()
                                .getDamageValue())
                        .toList() + " (1.16: one, damage 1)");
            }
            tridents.forEach(Entity::discard);

            owner.setItemSlot(EquipmentSlot.MAINHAND,
                    new ItemStack(Items.FLINT_AND_STEEL));
            sounds.clear();
            KraftWorkProjectileFactory.place(level, owner, null,
                    new ItemStack(ModItems.MOLOTOV.get()));
            if (owner.getMainHandItem().getDamageValue() != 1
                    || sounds.size() != 1
                    || sounds.getFirst().getOriginalVolume() != 0.5F
                    || sounds.getFirst().getSource() != SoundSource.PLAYERS
                    || sounds.getFirst().getOriginalPitch() < 0.4F / 1.2F
                    || sounds.getFirst().getOriginalPitch() > 0.4F / 0.8F) {
                wrong.add("a molotov placed: the flint and steel's damage "
                        + owner.getMainHandItem().getDamageValue()
                        + ", sounds " + sounds.stream().map(sound ->
                                sound.getSource() + " volume "
                                        + sound.getOriginalVolume()
                                        + " pitch "
                                        + sound.getOriginalPitch())
                                .toList() + " (1.16: 1, one molotov_throw, "
                        + "PLAYERS, volume 0.5, pitch 1/3 to 1/2)");
            }
        }
        finally {
            NeoForge.EVENT_BUS.unregister(listener);
            level.setBlockAndUpdate(feet, Blocks.AIR.defaultBlockState());
            level.setBlockAndUpdate(feet.above(),
                    Blocks.AIR.defaultBlockState());
            level.getEntitiesOfClass(Projectile.class,
                            owner.getBoundingBox().inflate(4.0D),
                            projectile -> projectile.getOwner() == owner)
                    .forEach(Entity::discard);
            owner.connection.disconnect(Component.literal(
                    "Kraft Work trident and molotov GameTest complete"));
        }
        helper.assertTrue(wrong.isEmpty(),
                "1.16's trident and molotov took what they took: "
                        + String.join("; ", wrong));
        helper.succeed();
    }

    // 1.16 KraftWorkPlaceProjectile shot each kind before it held it: a
    // molotov, an egg and an experience bottle at 0.75 (:322, :461, :476), a
    // trident at 2.5 + Riptide x 0.5 (:354), the rest at 1.5; the blade hat
    // with inaccuracy 0.5 (:339), the nugget's bullet with 0 (:373), the
    // others 1. Release sends the held shot on along its direction.
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void placedProjectilesAreShotAtTheirOwnSpeedAndSpreadAsIn116(
            GameTestHelper helper) {
        TestContext context =
                prepareUnsummonedKraftWork(helper, "kw-place-speed");
        ServerPlayer owner = context.owner();
        ServerLevel level = helper.getLevel();
        context.power().setResolveLevel(2);
        turnUser(owner, 30.0F, 30.0F);
        owner.setXRot(-20.0F);
        owner.xRotO = -20.0F;
        owner.setDeltaMovement(Vec3.ZERO);
        owner.setKnownMovement(Vec3.ZERO);
        owner.setItemSlot(EquipmentSlot.MAINHAND,
                new ItemStack(Items.FLINT_AND_STEEL));
        float pitch = owner.getXRot() * (float) (Math.PI / 180.0D);
        float yaw = owner.getYRot() * (float) (Math.PI / 180.0D);
        Vec3 aim = new Vec3(-Mth.sin(yaw) * Mth.cos(pitch), -Mth.sin(pitch),
                Mth.cos(yaw) * Mth.cos(pitch)).normalize();
        ItemStack riptide = new ItemStack(Items.TRIDENT);
        riptide.enchant(level.registryAccess()
                .lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(Enchantments.RIPTIDE), 3);
        Object[][] rows = {
                {new ItemStack(Items.ARROW), 1.5F, 1.0F},
                {new ItemStack(Items.SPLASH_POTION), 1.5F, 1.0F},
                {new ItemStack(ModItems.KNIFE.get()), 1.5F, 1.0F},
                {new ItemStack(ModItems.MOLOTOV.get()), 0.75F, 1.0F},
                {new ItemStack(ModItems.BLADE_HAT.get()), 1.5F, 0.5F},
                {new ItemStack(Items.TRIDENT), 2.5F, 1.0F},
                {new ItemStack(Items.IRON_NUGGET), 1.5F, 0.0F},
                {new ItemStack(Items.FIRE_CHARGE), 1.5F, 1.0F},
                {new ItemStack(ModItems.CLACKERS.get()), 1.5F, 1.0F},
                {new ItemStack(Items.FIREWORK_ROCKET), 1.5F, 1.0F},
                {new ItemStack(Items.ENDER_PEARL), 1.5F, 1.0F},
                {new ItemStack(Items.SNOWBALL), 1.5F, 1.0F},
                {new ItemStack(Items.EGG), 0.75F, 1.0F},
                {new ItemStack(Items.EXPERIENCE_BOTTLE), 0.75F, 1.0F},
                {new ItemStack(Items.COBBLESTONE), 1.5F, 1.0F},
                {riptide, 4.0F, 1.0F}};
        List<String> wrong = new ArrayList<>();
        BlockPos feet = owner.blockPosition();
        try {
            for (Object[] row : rows) {
                ItemStack stack = (ItemStack) row[0];
                float speed = (float) row[1];
                float inaccuracy = (float) row[2];
                if (stack == riptide) {
                    level.setBlockAndUpdate(feet,
                            Blocks.WATER.defaultBlockState());
                    level.setBlockAndUpdate(feet.above(),
                            Blocks.WATER.defaultBlockState());
                    owner.baseTick();
                    owner.setDeltaMovement(Vec3.ZERO);
                    helper.assertTrue(owner.isInWaterOrRain(),
                            "Fixture: the user is not in water.");
                }
                // a single shot of inaccuracy 1 stays within 0.5's bound
                // more often than not, so the blade hat goes 20 times
                int shots = inaccuracy == 0.5F ? 20 : 1;
                double bound = 0.0172275D * inaccuracy + 1.0E-4D;
                double widest = 0.0D;
                List<String> lengths = new ArrayList<>();
                for (int shot = 0; shot < shots; shot++) {
                    String name = BuiltInRegistries.ITEM.getKey(
                            stack.getItem()).getPath()
                            + (stack == riptide ? " (Riptide III, in water)"
                                    : "");
                    if (!KraftWorkProjectileFactory.place(level, owner, null,
                            stack.copy())) {
                        wrong.add(name + " was not placed");
                        break;
                    }
                    List<Projectile> placed = level.getEntitiesOfClass(
                            Projectile.class,
                            owner.getBoundingBox().inflate(4.0D),
                            projectile -> projectile.getOwner() == owner);
                    if (placed.size() != 1
                            || !isLockedEntity(placed.getFirst())) {
                        wrong.add(name + " left " + placed.size()
                                + " projectiles, held "
                                + placed.stream().map(
                                        KraftWorkGameTests::isLockedEntity)
                                        .toList());
                        placed.forEach(Entity::discard);
                        break;
                    }
                    Vec3 shotMotion = KraftWorkLockService.state(
                            placed.getFirst()).storedMotion();
                    Vec3 spread = shotMotion.scale(1.0D / speed)
                            .subtract(aim);
                    widest = Math.max(widest, Math.max(Math.abs(spread.x),
                            Math.max(Math.abs(spread.y),
                                    Math.abs(spread.z))));
                    lengths.add(String.format(Locale.ROOT, "%.3f",
                            shotMotion.length()));
                    placed.forEach(Entity::discard);
                    if (shot == shots - 1 && widest > bound) {
                        wrong.add(name + " shot at " + lengths
                                + ", spread up to "
                                + String.format(Locale.ROOT, "%.5f", widest)
                                + " (1.16: speed " + speed + ", inaccuracy "
                                + inaccuracy + ", a spread within "
                                + String.format(Locale.ROOT, "%.5f", bound)
                                + ")");
                    }
                }
            }
        }
        finally {
            level.setBlockAndUpdate(feet, Blocks.AIR.defaultBlockState());
            level.setBlockAndUpdate(feet.above(),
                    Blocks.AIR.defaultBlockState());
            level.getEntitiesOfClass(Projectile.class,
                            owner.getBoundingBox().inflate(4.0D),
                            projectile -> projectile.getOwner() == owner)
                    .forEach(Entity::discard);
            owner.connection.disconnect(Component.literal(
                    "Kraft Work placed speed GameTest complete"));
        }
        helper.assertTrue(wrong.isEmpty(),
                "1.16 shot each placed projectile at its own speed and "
                        + "spread: " + String.join("; ", wrong));
        helper.succeed();
    }

    // 1.16 KraftWorkPlaceProjectile :413 built a placed firework shot at an
    // angle (new FireworkRocketEntity(world, item, itemOwner, x, eyeY - 0.15,
    // z, true)), attached to no one: released, it flew on straight along its
    // shot at the release speed, as a rocket from a crossbow does.
    @GameTest(template = "empty", timeoutTicks = 40)
    public static void placedFireworkFliesOnAlongItsShotWhenReleasedAsIn116(
            GameTestHelper helper) {
        TestContext context =
                prepareUnsummonedKraftWork(helper, "kw-place-firework");
        ServerPlayer owner = context.owner();
        // steeply up and one tick of flight: the test cell's barrier walls
        // and ceiling are a few blocks away
        turnUser(owner, 30.0F, 30.0F);
        owner.setXRot(-80.0F);
        owner.xRotO = -80.0F;
        owner.setDeltaMovement(Vec3.ZERO);
        helper.assertTrue(KraftWorkProjectileFactory.place(helper.getLevel(),
                        owner, null, new ItemStack(Items.FIREWORK_ROCKET)),
                "Fixture: the firework was not placed.");
        List<FireworkRocketEntity> placed = helper.getLevel()
                .getEntitiesOfClass(FireworkRocketEntity.class,
                        owner.getBoundingBox().inflate(4.0D),
                        rocket -> rocket.getOwner() == owner);
        helper.assertTrue(placed.size() == 1
                        && isLockedEntity(placed.getFirst()),
                "Fixture: the place left " + placed.size()
                        + " fireworks, held " + placed.stream()
                                .map(KraftWorkGameTests::isLockedEntity)
                                .toList());
        FireworkRocketEntity rocket = placed.getFirst();
        List<String> wrong = new ArrayList<>();
        if (rocket.attachedToEntity != null || !rocket.isShotAtAngle()) {
            wrong.add("placed attached to " + rocket.attachedToEntity
                    + ", shot at an angle " + rocket.isShotAtAngle()
                    + " (1.16: attached to no one, shot at an angle)");
        }
        Vec3 direction = KraftWorkLockService.state(rocket).storedMotion()
                .normalize();
        Vec3 release = direction.scale(KraftWorkSpec.MAX_RELEASE_SPEED);
        Vec3[] heldAt = new Vec3[1];
        helper.startSequence().thenExecuteAfter(1, () -> {
            heldAt[0] = rocket.position();
            helper.assertTrue(KraftWorkLockService.releaseTarget(owner, rocket),
                    "Fixture: the held firework was not released.");
        }).thenExecuteAfter(1, () -> {
            Vec3 flown = rocket.position().subtract(heldAt[0]);
            double along = flown.dot(direction);
            double aside = flown.subtract(direction.scale(along)).length();
            double ticks = along / KraftWorkSpec.MAX_RELEASE_SPEED;
            if (rocket.isRemoved() || isLockedEntity(rocket)
                    || ticks < 1.0D - 1.0E-6D
                    || Math.abs(ticks - Math.rint(ticks)) > 1.0E-4D
                    || aside > 1.0E-4D
                    || rocket.getDeltaMovement().distanceTo(release)
                            > 1.0E-6D) {
                wrong.add("released, it is removed " + rocket.isRemoved()
                        + ", held " + isLockedEntity(rocket) + ", moved "
                        + String.format(Locale.ROOT, "%.4f", along)
                        + " along its shot and "
                        + String.format(Locale.ROOT, "%.4f", aside)
                        + " aside from " + heldAt[0] + " to "
                        + rocket.position() + " (its user at "
                        + owner.position() + "), moving "
                        + rocket.getDeltaMovement() + " (1.16: whole ticks of "
                        + release + " along its shot, straight on)");
            }
            rocket.discard();
            owner.connection.disconnect(Component.literal(
                    "Kraft Work firework GameTest complete"));
            helper.assertTrue(wrong.isEmpty(),
                    "1.16 released a placed firework along its shot: "
                            + String.join("; ", wrong));
        }).thenSucceed();
    }

    // 1.16 KraftWorkPlaceProjectile :370-382 placed an iron nugget as a
    // TommyGunBulletEntity shot straight (inaccuracy 0), which, frozen
    // (canUpdate false), did not age; its release (KraftWorkStandType
    // .ReleaseProjectile :217-220, replaceProjectile :267-277) removed it
    // and shot a fresh bullet from its place and facing at min(0.143 x
    // energy, 3.15), its base damage 0.05 an energy.
    @GameTest(template = "empty", timeoutTicks = 80)
    public static void ironNuggetIsPlacedAsATommyGunBulletAsIn116(
            GameTestHelper helper) {
        TestContext context =
                prepareUnsummonedKraftWork(helper, "kw-place-nugget");
        ServerPlayer owner = context.owner();
        owner.setYRot(30.0F);
        owner.setYHeadRot(30.0F);
        owner.setXRot(-20.0F);
        Vec3 view = owner.getViewVector(1.0F);
        List<String> wrong = new ArrayList<>();
        helper.assertTrue(KraftWorkProjectileFactory.place(helper.getLevel(),
                        owner, null, new ItemStack(Items.IRON_NUGGET)),
                "Fixture: the iron nugget was not placed.");
        List<Projectile> placed = helper.getLevel().getEntitiesOfClass(
                Projectile.class, owner.getBoundingBox().inflate(4.0D),
                projectile -> projectile.getOwner() == owner);
        helper.assertTrue(placed.size() == 1
                        && placed.getFirst() instanceof TommyGunBulletEntity
                        && isLockedEntity(placed.getFirst()),
                "1.16 placed an iron nugget as a held Tommy gun bullet; the "
                        + "port placed " + placed.stream().map(entity ->
                                entity.getClass().getSimpleName()).toList());
        TommyGunBulletEntity held = (TommyGunBulletEntity) placed.getFirst();
        Vec3 heldAt = held.position();
        Vec3 aim = KraftWorkLockService.state(held).storedMotion().normalize();
        // the view's float math against the shot's double math
        if (aim.distanceTo(view) > 1.0E-3D) {
            wrong.add("the bullet was shot along " + aim + ", the view "
                    + view + " (1.16: inaccuracy 0)");
        }
        List<TommyGunBulletEntity> fresh = new ArrayList<>();
        Mob[] target = new Mob[1];
        List<Float> hits = new ArrayList<>();
        Consumer<LivingIncomingDamageEvent> damage = event -> {
            if (event.getEntity() == target[0]
                    && event.getSource().getDirectEntity()
                            instanceof TommyGunBulletEntity) {
                hits.add(event.getAmount());
            }
        };
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true,
                LivingIncomingDamageEvent.class, damage);
        helper.startSequence().thenExecuteAfter(45, () -> {
            if (held.isRemoved() || !isLockedEntity(held)
                    || !held.position().equals(heldAt)) {
                wrong.add("45 ticks held, the bullet removed "
                        + held.isRemoved() + ", held " + isLockedEntity(held)
                        + " at its place " + held.position().equals(heldAt)
                        + " (1.16: still held, never ticked to its 40-tick "
                        + "end)");
            }
            KraftWorkLockService.addEnergy(held);
            KraftWorkLockService.releaseTarget(owner, held);
            fresh.addAll(helper.getLevel().getEntitiesOfClass(
                    TommyGunBulletEntity.class,
                    owner.getBoundingBox().inflate(4.0D),
                    bullet -> bullet != held && bullet.getOwner() == owner));
            Vec3 speed = aim.scale(KraftWorkSpec.ENERGY_SPEED_PER_CHARGE
                    * 10.0D);
            if (!held.isRemoved() || fresh.size() != 1
                    || fresh.getFirst().position().distanceTo(heldAt)
                            > 1.0E-6D
                    || fresh.getFirst().getDeltaMovement().distanceTo(speed)
                            > 1.0E-3D) {
                wrong.add("released with 10 energy: the held bullet removed "
                        + held.isRemoved() + ", " + fresh.size()
                        + " fresh bullet(s)" + (fresh.isEmpty() ? "" : " at "
                                + fresh.getFirst().position() + " moving "
                                + fresh.getFirst().getDeltaMovement())
                        + " (1.16: removed, one at " + heldAt + " moving "
                        + speed + ")");
            }
            target[0] = helper.spawn(EntityType.COW, new BlockPos(1, 2, 1));
            target[0].setNoAi(true);
            target[0].setNoGravity(true);
            Vec3 at = heldAt.add(aim.scale(2.5D));
            target[0].setPos(at.x, at.y - target[0].getBbHeight() / 2.0D,
                    at.z);
        }).thenExecuteAfter(5, () -> {
            NeoForge.EVENT_BUS.unregister(damage);
            if (hits.isEmpty() || hits.stream()
                    .anyMatch(amount -> Math.abs(amount - 0.5F) > 1.0E-4F)) {
                wrong.add("the fresh bullet hit a cow on its path for "
                        + hits + " (1.16: its base damage, 0.05 x 10 energy "
                        + "= 0.5 a hit)");
            }
            helper.assertTrue(KraftWorkProjectileFactory.place(
                            helper.getLevel(), owner, null,
                            new ItemStack(Items.IRON_NUGGET)),
                    "Fixture: the second iron nugget was not placed.");
            checkFreshBulletHeldAgain(helper, owner, heldOne(helper, owner,
                    TommyGunBulletEntity.class), wrong);
            fresh.forEach(Entity::discard);
            held.discard();
            target[0].discard();
            owner.connection.disconnect(Component.literal(
                    "Kraft Work nugget GameTest complete"));
            helper.assertTrue(wrong.isEmpty(),
                    "1.16 placed an iron nugget as a Tommy gun bullet: "
                            + String.join("; ", wrong));
        }).thenSucceed();
    }

    // 1.16 held a Crazy Diamond block bullet without ticking it (canUpdate
    // false), so it did not expire with its dying shooter, and its release
    // (KraftWorkStandType.ReleaseProjectile :217-220, replaceProjectile
    // :256-266) removed it and shot a fresh block bullet, with no block and
    // its releasing user's, from its place and facing at min(0.143 x energy,
    // 3.15), its base damage 0.05 an energy.
    @GameTest(template = "empty", timeoutTicks = 40)
    public static void crazyDiamondBlockBulletIsHeldAndReplacedAsIn116(
            GameTestHelper helper) {
        TestContext context =
                prepareUnsummonedKraftWork(helper, "kw-cd-bullet");
        ServerPlayer owner = context.owner();
        ServerLevel level = helper.getLevel();
        owner.setYRot(30.0F);
        owner.setYHeadRot(30.0F);
        owner.setXRot(-20.0F);
        Vec3 view = owner.getViewVector(1.0F);
        List<String> wrong = new ArrayList<>();
        Mob shooter = helper.spawn(EntityType.COW, new BlockPos(1, 2, 4));
        shooter.setNoAi(true);
        shooter.setNoGravity(true);
        CrazyDBlockBulletEntity held =
                new CrazyDBlockBulletEntity(shooter, level);
        held.setBlock(Blocks.STONE);
        held.shootFromRotation(owner, 1.5F, 0.0F);
        Vec3 heldAt = owner.getEyePosition().add(view.scale(0.5D));
        held.setPos(heldAt);
        level.addFreshEntity(held);
        helper.assertTrue(KraftWorkLockService.lockWhole(owner, held),
                "Fixture: the user did not hold the block bullet.");
        // a shooter dying, not yet removed, expires a ticking bullet
        shooter.setHealth(0.0F);
        List<CrazyDBlockBulletEntity> fresh = new ArrayList<>();
        Mob[] target = new Mob[1];
        List<Float> hits = new ArrayList<>();
        Consumer<LivingIncomingDamageEvent> damage = event -> {
            if (event.getEntity() == target[0]
                    && event.getSource().getDirectEntity()
                            instanceof CrazyDBlockBulletEntity) {
                hits.add(event.getAmount());
            }
        };
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true,
                LivingIncomingDamageEvent.class, damage);
        helper.startSequence().thenExecuteAfter(3, () -> {
            if (held.isRemoved() || !isLockedEntity(held)
                    || !held.position().equals(heldAt)) {
                wrong.add("3 ticks held with its shooter dying, the block "
                        + "bullet removed " + held.isRemoved() + ", held "
                        + isLockedEntity(held) + " at its place "
                        + held.position().equals(heldAt) + " (1.16: still "
                        + "held, never ticked)");
            }
            KraftWorkLockService.addEnergy(held);
            KraftWorkLockService.releaseTarget(owner, held);
            fresh.addAll(level.getEntitiesOfClass(
                    CrazyDBlockBulletEntity.class,
                    owner.getBoundingBox().inflate(4.0D),
                    bullet -> bullet != held && bullet.getOwner() == owner));
            Vec3 speed = view.scale(KraftWorkSpec.ENERGY_SPEED_PER_CHARGE
                    * 10.0D);
            if (!held.isRemoved() || fresh.size() != 1
                    || fresh.getFirst().position().distanceTo(heldAt)
                            > 1.0E-6D
                    || fresh.getFirst().getDeltaMovement().distanceTo(speed)
                            > 1.0E-3D
                    || fresh.getFirst().getBlock() != null) {
                wrong.add("released with 10 energy: the held block bullet "
                        + "removed " + held.isRemoved() + ", "
                        + fresh.size() + " fresh one(s) of the user"
                        + (fresh.isEmpty() ? "" : " at "
                                + fresh.getFirst().position() + " moving "
                                + fresh.getFirst().getDeltaMovement()
                                + " of block " + fresh.getFirst().getBlock())
                        + " (1.16: removed, one of no block at " + heldAt
                        + " moving " + speed + ")");
            }
            target[0] = helper.spawn(EntityType.COW, new BlockPos(1, 2, 1));
            target[0].setNoAi(true);
            target[0].setNoGravity(true);
            Vec3 at = heldAt.add(view.scale(2.5D));
            target[0].setPos(at.x, at.y - target[0].getBbHeight() / 2.0D,
                    at.z);
        }).thenExecuteAfter(5, () -> {
            NeoForge.EVENT_BUS.unregister(damage);
            if (hits.isEmpty() || hits.stream()
                    .anyMatch(amount -> Math.abs(amount - 0.5F) > 1.0E-4F)) {
                wrong.add("the fresh block bullet hit a cow on its path for "
                        + hits + " (1.16: its base damage, 0.05 x 10 "
                        + "energy = 0.5 a hit)");
            }
            CrazyDBlockBulletEntity second =
                    new CrazyDBlockBulletEntity(owner, level);
            second.shootFromRotation(owner, 1.5F, 0.0F);
            second.setPos(heldAt);
            level.addFreshEntity(second);
            helper.assertTrue(KraftWorkLockService.lockWhole(owner, second),
                    "Fixture: the second block bullet was not held.");
            checkFreshBulletHeldAgain(helper, owner, second, wrong);
            checkAutomaticReleaseShooter(helper, owner, heldAt, wrong);
            fresh.forEach(Entity::discard);
            held.discard();
            shooter.discard();
            target[0].discard();
            owner.connection.disconnect(Component.literal(
                    "Kraft Work block bullet GameTest complete"));
            helper.assertTrue(wrong.isEmpty(),
                    "1.16 held a Crazy Diamond block bullet and replaced it "
                            + "on release: " + String.join("; ", wrong));
        }).thenSucceed();
    }

    // 1.16 handed the automatic releases the Kraft Work user as well (tickUser
    // :149 past 15 blocks, GameplayEventHandler :269 on logout or death, a
    // dying user included), so replaceProjectile (:256-277) made the fresh
    // bullet that user's, whoever had shot the held one
    private static void checkAutomaticReleaseShooter(GameTestHelper helper,
            ServerPlayer owner, Vec3 heldAt, List<String> wrong) {
        ServerLevel level = helper.getLevel();
        Mob other = helper.spawn(EntityType.COW, new BlockPos(1, 2, 6));
        other.setNoAi(true);
        Mob dying = helper.spawn(EntityType.COW, new BlockPos(3, 2, 6));
        dying.setNoAi(true);
        List<Entity> made = new ArrayList<>(List.of(other, dying));
        try {
            CrazyDBlockBulletEntity far =
                    new CrazyDBlockBulletEntity(other, level);
            far.shootFromRotation(owner, 1.5F, 0.0F);
            far.setPos(heldAt);
            level.addFreshEntity(far);
            helper.assertTrue(KraftWorkLockService.lockWhole(owner, far),
                    "Fixture: the cow's block bullet was not held.");
            KraftWorkLockService.addEnergy(far);
            double dx = far.getX() - owner.getX();
            double dy = far.getY() - owner.getY();
            far.setPos(far.getX(), far.getY(), owner.getZ()
                    + Math.sqrt(15.5D * 15.5D - dx * dx - dy * dy));
            KraftWorkLockService.postTick(far);
            checkReleasedShooter(level, far, owner, made, wrong,
                    "a cow's block bullet held by the user and carried 15.5 "
                            + "blocks out (postTick's release)");

            TommyGunBulletEntity nugget =
                    new TommyGunBulletEntity(other, level);
            nugget.shootFromRotation(owner, 1.5F, 0.0F);
            nugget.setPos(heldAt);
            level.addFreshEntity(nugget);
            helper.assertTrue(KraftWorkLockService.lockWhole(owner, nugget),
                    "Fixture: the cow's nugget bullet was not held.");
            KraftWorkLockService.addEnergy(nugget);
            KraftWorkLockService.releaseOwned(level, owner.getUUID());
            checkReleasedShooter(level, nugget, owner, made, wrong,
                    "a cow's tommy gun bullet held by the user, freed at the "
                            + "user's logout (releaseOwned)");

            CrazyDBlockBulletEntity orphan =
                    new CrazyDBlockBulletEntity(other, level);
            orphan.shootFromRotation(owner, 1.5F, 0.0F);
            orphan.setPos(dying.getEyePosition().add(0.0D, 1.0D, 0.0D));
            level.addFreshEntity(orphan);
            helper.assertTrue(KraftWorkLockService.lockWhole(dying, orphan),
                    "Fixture: the dying holder did not hold the block bullet.");
            KraftWorkLockService.addEnergy(orphan);
            dying.setHealth(0.0F);
            KraftWorkLockService.releaseOwned(level, dying.getUUID());
            checkReleasedShooter(level, orphan, dying, made, wrong,
                    "a cow's block bullet freed at its holder's death "
                            + "(releaseOwned)");
        }
        finally {
            made.forEach(Entity::discard);
        }
    }

    private static void checkReleasedShooter(ServerLevel level,
            Projectile held, LivingEntity holder, List<Entity> made,
            List<String> wrong, String row) {
        Vec3 at = held.position();
        List<Projectile> fresh = level.getEntitiesOfClass(Projectile.class,
                new AABB(at, at).inflate(0.5D),
                bullet -> bullet != held && bullet.getType() == held.getType()
                        && bullet.position().distanceTo(at) < 1.0E-6D);
        made.add(held);
        made.addAll(fresh);
        if (!held.isRemoved() || fresh.size() != 1
                || fresh.getFirst().getOwner() != holder) {
            wrong.add(row + ": the held one removed " + held.isRemoved()
                    + ", " + fresh.size() + " fresh one(s)"
                    + (fresh.isEmpty() ? "" : " of "
                            + fresh.getFirst().getOwner())
                    + " (1.16: one fresh bullet of the Kraft Work user "
                    + holder + ")");
        }
    }

    // 1.16 gave the fresh bullet the held one's kinetic energy
    // (KraftWorkStandType.replaceProjectile :263, :274), but its speed and
    // damage came from the release's own energy, no charge or release took a
    // bullet that was not held, and the one new hold a bullet could get, the
    // guard's, set the energy to 0 (KraftWorkBlock :167): the fresh bullet,
    // held again, starts with none
    private static void checkFreshBulletHeldAgain(GameTestHelper helper,
            ServerPlayer owner, Projectile held, List<String> wrong) {
        Vec3 at = held.position();
        KraftWorkLockService.addEnergy(held);
        KraftWorkLockService.releaseTarget(owner, held);
        List<Projectile> fresh = helper.getLevel().getEntitiesOfClass(
                Projectile.class, new AABB(at, at).inflate(0.5D),
                bullet -> bullet != held && bullet.getOwner() == owner
                        && bullet.position().distanceTo(at) < 1.0E-6D);
        boolean heldAgain = fresh.size() == 1
                && KraftWorkLockService.lockWhole(owner, fresh.getFirst());
        int energy = heldAgain
                ? KraftWorkLockService.state(fresh.getFirst()).energy()
                : -1;
        boolean ready = heldAgain
                && KraftWorkLockService.state(fresh.getFirst()).ready();
        if (!held.isRemoved() || !heldAgain || energy != 0 || ready) {
            wrong.add("a held " + held.getType().toShortString()
                    + " released with 10 energy: removed " + held.isRemoved()
                    + ", " + fresh.size() + " fresh one(s), held again "
                    + heldAgain + " with energy " + energy + ", ready "
                    + ready + " (1.16: the fresh one's energy went unread "
                    + "until a new hold set it to 0)");
        }
        fresh.forEach(Entity::discard);
    }

    // A user's death, logout, clone or change of dimension frees every lock
    // it held (KraftWorkGameEvents.clearOwner -> releaseOwned); a held
    // nugget bullet goes as a fresh one (1.16 ReleaseProjectile :217-220),
    // which adds an entity and removes one, so the walk over the level's
    // live entity map must not release in place
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void releaseOwnedFreesEveryLockOfItsUserAndItsHeldBullets(
            GameTestHelper helper) {
        TestContext context =
                prepareUnsummonedKraftWork(helper, "kw-release-owned");
        ServerPlayer owner = context.owner();
        ServerLevel level = helper.getLevel();
        List<String> wrong = new ArrayList<>();
        Mob zombie = armedZombie(helper);
        List<Entity> made = new ArrayList<>();
        try {
            for (ItemStack item : new ItemStack[] {
                    new ItemStack(Items.IRON_NUGGET),
                    new ItemStack(Items.IRON_NUGGET),
                    new ItemStack(Items.IRON_NUGGET),
                    new ItemStack(Items.ARROW)}) {
                helper.assertTrue(KraftWorkProjectileFactory.place(level,
                                owner, null, item),
                        "Fixture: " + item + " was not placed.");
            }
            helper.assertTrue(KraftWorkLockService.lockEquipment(
                            owner, zombie) > 0,
                    "Fixture: the armed mob was not locked.");
            List<Projectile> held = level.getEntitiesOfClass(Projectile.class,
                    owner.getBoundingBox().inflate(4.0D),
                    projectile -> projectile.getOwner() == owner);
            made.addAll(held);
            List<Projectile> bullets = held.stream()
                    .filter(TommyGunBulletEntity.class::isInstance).toList();
            helper.assertTrue(held.size() == 4 && bullets.size() == 3
                            && held.stream().allMatch(
                                    KraftWorkGameTests::isLockedEntity),
                    "Fixture: the user does not hold three nugget bullets "
                            + "and an arrow: " + held);
            Projectile arrow = held.stream()
                    .filter(Arrow.class::isInstance).findFirst().orElseThrow();
            try {
                KraftWorkLockService.releaseOwned(level, owner.getUUID());
            }
            catch (RuntimeException exception) {
                wrong.add("releaseOwned threw " + exception);
            }
            List<TommyGunBulletEntity> fresh = level.getEntitiesOfClass(
                    TommyGunBulletEntity.class,
                    owner.getBoundingBox().inflate(4.0D),
                    bullet -> !bullets.contains(bullet)
                            && bullet.getOwner() == owner);
            made.addAll(fresh);
            long kept = bullets.stream().filter(bullet -> !bullet.isRemoved())
                    .count();
            if (kept != 0 || fresh.size() != 3
                    || fresh.stream().anyMatch(
                            KraftWorkGameTests::isLockedEntity)
                    || isLockedEntity(arrow)
                    || isLockedEntity(zombie)
                    || KraftWorkLockService.hasAnyLockEffect(zombie)) {
                wrong.add("releaseOwned left " + kept + " held bullet(s), "
                        + fresh.size() + " fresh one(s), the arrow held "
                        + isLockedEntity(arrow) + ", the mob locked "
                        + isLockedEntity(zombie) + " with lock effects "
                        + KraftWorkLockService.hasAnyLockEffect(zombie)
                        + " (every lock freed, each held bullet replaced by "
                        + "a fresh one)");
            }
        }
        finally {
            KraftWorkLockService.releaseOwned(level, owner.getUUID());
            made.forEach(Entity::discard);
            level.getEntitiesOfClass(Projectile.class,
                            owner.getBoundingBox().inflate(4.0D),
                            projectile -> projectile.getOwner() == owner)
                    .forEach(Entity::discard);
            zombie.discard();
            owner.connection.disconnect(Component.literal(
                    "Kraft Work releaseOwned GameTest complete"));
        }
        helper.assertTrue(wrong.isEmpty(),
                "A user's locks are freed whole on its death or logout: "
                        + String.join("; ", wrong));
        helper.succeed();
    }

    // 1.16 KraftWorkStandType.ReleaseProjectile (:230-245) ran on every
    // release (the release actions, Release Target, the out-of-range release
    // in tickUser :149, death and logout in GameplayEventHandler :269): a
    // sound by kind at the projectile for every player, its user too, and
    // none for one charged too little. The release actions played none.
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void releasedProjectilesSoundByKindAtThemselvesAsIn116(
            GameTestHelper helper) {
        TestContext context =
                prepareUnsummonedKraftWork(helper, "kw-release-sound");
        ServerPlayer owner = context.owner();
        StandPower power = context.power();
        ServerLevel level = helper.getLevel();
        power.setResolveLevel(2);
        LivingComponentAction userActions =
                LivingComponentAction.getComponent(owner, PowerClass.STAND);
        List<String> wrong = new ArrayList<>();
        List<PlayLevelSoundEvent.AtPosition> atUser = new ArrayList<>();
        Consumer<PlayLevelSoundEvent.AtPosition> listener = event -> {
            if (event.getSound() != null && event.getSound().value()
                            == KraftWorkAddon.KRAFT_WORK_RELEASED_PROJECTILE.get()
                    && event.getPosition().distanceToSqr(
                            owner.position()) < 1.0E-6D) {
                atUser.add(event);
            }
        };
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true,
                PlayLevelSoundEvent.AtPosition.class, listener);
        try {
            for (int charges : new int[] {1, 0}) {
                Projectile arrow = placeCharged(helper, owner,
                        new ItemStack(Items.ARROW), charges);
                entitySoundsSentTo(owner);
                power.resetAbilityCooldowns();
                pressResolved(helper, power, owner, KraftWorkAddon.RELEASE_ALL,
                        InputMethod.CLICK, RELEASE_KEY);
                for (int tick = 0; tick < 3; tick++) {
                    userActions.tick();
                }
                AbilityInput.keyRelease(RELEASE_KEY, owner);
                checkReleaseSound(wrong, "Release All pressed at an arrow "
                                + "with " + charges * 10 + " energy",
                        owner, arrow, isLockedEntity(arrow),
                        charges > 0 ? SoundEvents.ARROW_SHOOT : null,
                        1.0F, 0.625F, 0.8334F);
                arrow.discard();
            }

            Projectile knife = placeCharged(helper, owner,
                    new ItemStack(ModItems.KNIFE.get()), 1);
            entitySoundsSentTo(owner);
            power.resetAbilityCooldowns();
            clickReleaseTarget(helper, power, owner, userActions);
            checkReleaseSound(wrong, "Release Target clicked at a knife with "
                            + "10 energy",
                    owner, knife, isLockedEntity(knife),
                    ModSoundEvents.KNIFE_THROW.get(), 0.5F, 0.3333F, 0.5F);
            knife.discard();

            // the pitch factors are 1.16's integer divisions: an arrow
            // takes its higher pitch only past 20 energy
            Object[][] rows = {
                    {new ItemStack(Items.ARROW), 3, SoundEvents.ARROW_SHOOT,
                            1.125F, 1.3334F},
                    {new ItemStack(ModItems.BLADE_HAT.get()), 1,
                            ModSoundEvents.BLADE_HAT_THROW.get(), 0.75F, 1.25F},
                    {new ItemStack(Items.TRIDENT), 1,
                            SoundEvents.TRIDENT_THROW.value(), 1.0F, 1.0F},
                    {new ItemStack(Items.SNOWBALL), 2, null, 0.0F, 0.0F},
                    {new ItemStack(Items.SNOWBALL), 3,
                            KraftWorkAddon.KRAFT_WORK_RELEASED_PROJECTILE.get(),
                            0.625F, 0.8334F},
                    {new ItemStack(Items.IRON_NUGGET), 3,
                            KraftWorkAddon.KRAFT_WORK_RELEASED_PROJECTILE.get(),
                            0.625F, 0.8334F},
                    {new ItemStack(Items.COBBLESTONE), 1, null, 0.0F, 0.0F}};
            for (Object[] row : rows) {
                ItemStack item = (ItemStack) row[0];
                int charges = (Integer) row[1];
                Projectile held = placeCharged(helper, owner, item, charges);
                entitySoundsSentTo(owner);
                KraftWorkLockService.releaseTarget(owner, held);
                checkReleaseSound(wrong, item.getItem() + " released with "
                                + charges * 10 + " energy",
                        owner, held, isLockedEntity(held),
                        (SoundEvent) row[2], 1.0F, (Float) row[3],
                        (Float) row[4]);
                discardOwnedProjectiles(helper, owner);
            }

            // the out-of-range release (1.16 tickUser :142-149, past 15
            // blocks) and the release at the user's death or logout
            Projectile far = placeCharged(helper, owner,
                    new ItemStack(Items.ARROW), 1);
            // 15.5 blocks out: past the lock, within the sound's 16
            double dx = far.getX() - owner.getX();
            double dy = far.getY() - owner.getY();
            far.setPos(far.getX(), far.getY(), owner.getZ()
                    + Math.sqrt(15.5D * 15.5D - dx * dx - dy * dy));
            entitySoundsSentTo(owner);
            KraftWorkLockService.postTick(far);
            checkReleaseSound(wrong, "an arrow with 10 energy carried past "
                            + "the lock's 15 blocks",
                    owner, far, isLockedEntity(far), SoundEvents.ARROW_SHOOT,
                    1.0F, 0.625F, 0.8334F);
            far.discard();
            Projectile dying = placeCharged(helper, owner,
                    new ItemStack(Items.ARROW), 1);
            entitySoundsSentTo(owner);
            KraftWorkLockService.releaseOwned(level, owner.getUUID());
            checkReleaseSound(wrong, "an arrow with 10 energy freed at its "
                            + "user's death or logout",
                    owner, dying, isLockedEntity(dying),
                    SoundEvents.ARROW_SHOOT, 1.0F, 0.625F, 0.8334F);
            dying.discard();

            // an eye of ender, no projectile in 1.16, made no sound
            BlockPos stronghold = owner.blockPosition().offset(40, 0, 0);
            KraftWorkProjectileFactory.placeEyeTowards(level, owner,
                    new ItemStack(Items.ENDER_EYE), stronghold);
            List<EyeOfEnder> eyes = level.getEntitiesOfClass(EyeOfEnder.class,
                    owner.getBoundingBox().inflate(4.0D),
                    KraftWorkGameTests::isLockedEntity);
            helper.assertTrue(eyes.size() == 1,
                    "Fixture: " + eyes.size() + " held eye(s) of ender.");
            entitySoundsSentTo(owner);
            KraftWorkLockService.releaseTarget(owner, eyes.getFirst());
            checkReleaseSound(wrong, "a held eye of ender released", owner,
                    eyes.getFirst(), isLockedEntity(eyes.getFirst()), null,
                    1.0F, 0.0F, 0.0F);
            eyes.forEach(Entity::discard);
            if (!atUser.isEmpty()) {
                wrong.add("the user's own release sound played at the user "
                        + atUser.size() + " time(s) (1.16's release actions "
                        + "played none)");
            }
        }
        finally {
            NeoForge.EVENT_BUS.unregister(listener);
            AbilityInput.keyRelease(RELEASE_KEY, owner);
            userActions.setAction(null, SyncType.NO_SYNC);
            KraftWorkLockService.releaseOwned(level, owner.getUUID());
            discardOwnedProjectiles(helper, owner);
            level.getEntitiesOfClass(EyeOfEnder.class,
                            owner.getBoundingBox().inflate(4.0D))
                    .forEach(Entity::discard);
            owner.connection.disconnect(Component.literal(
                    "Kraft Work release sound GameTest complete"));
        }
        helper.assertTrue(wrong.isEmpty(),
                "1.16 played a sound by kind at each released projectile, "
                        + "for every player: " + String.join("; ", wrong));
        helper.succeed();
    }

    // 1.16 KraftWorkStandType.setCanUpdateServerSide (:190-195) sent canUpdate
    // false for a held entity to the clients tracking it as well
    // (TrSetEntityCanUpdatePacket): there a held firework sprayed no sparks
    // and a held eye of ender no portal particles. The whole lock sets a
    // synced flag on the kinds the server holds without ticking, which a
    // client starting to track one receives with it, and the release removes
    // it; a client skips their tick while it reads the flag.
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void heldEyesFireworksAndBulletsStillOnClientsAsIn116(
            GameTestHelper helper) {
        TestContext context =
                prepareUnsummonedKraftWork(helper, "kw-held-client");
        ServerPlayer owner = context.owner();
        ServerLevel level = helper.getLevel();
        context.power().setResolveLevel(2);
        List<String> wrong = new ArrayList<>();
        try {
            for (ItemStack item : new ItemStack[] {
                    new ItemStack(Items.FIREWORK_ROCKET),
                    new ItemStack(Items.IRON_NUGGET),
                    new ItemStack(Items.ARROW)}) {
                helper.assertTrue(KraftWorkProjectileFactory.place(level,
                                owner, null, item),
                        "Fixture: " + item + " was not placed.");
            }
            KraftWorkProjectileFactory.placeEyeTowards(level, owner,
                    new ItemStack(Items.ENDER_EYE),
                    owner.blockPosition().offset(40, 0, 0));
            CrazyDBlockBulletEntity block =
                    new CrazyDBlockBulletEntity(owner, level);
            block.shootFromRotation(owner, 1.5F, 0.0F);
            level.addFreshEntity(block);
            helper.assertTrue(KraftWorkLockService.lockWhole(owner, block),
                    "Fixture: the block bullet was not held.");
            List<Entity> still = List.of(
                    heldOne(helper, owner, FireworkRocketEntity.class),
                    heldOne(helper, owner, EyeOfEnder.class),
                    heldOne(helper, owner, TommyGunBulletEntity.class),
                    block);
            Entity arrow = heldOne(helper, owner, Arrow.class);
            for (Entity held : still) {
                if (!KraftWorkLockService.heldStillOnClients(held)
                        || !clientCopyHeldStill(level, owner, held)) {
                    wrong.add("a held " + held.getType().toShortString()
                            + ": flagged " + KraftWorkLockService
                                    .heldStillOnClients(held)
                            + ", a client starting to track it reads it "
                            + "still " + clientCopyHeldStill(level, owner,
                                    held)
                            + " (1.16: canUpdate false on its clients)");
                }
            }
            // 1.16 stopped every held projectile on its clients (tickUser
            // :143-144), an arrow too
            if (!KraftWorkLockService.heldStillOnClients(arrow)
                    || !clientCopyHeldStill(level, owner, arrow)) {
                wrong.add("a held arrow: flagged " + KraftWorkLockService
                                .heldStillOnClients(arrow)
                        + ", a client starting to track it reads it still "
                        + clientCopyHeldStill(level, owner, arrow)
                        + " (1.16: canUpdate false on its clients)");
            }
            KraftWorkLockService.releaseProjectiles(owner, ReleaseFilter.ALL);
            for (Entity released : still.subList(0, 2)) {
                if (KraftWorkLockService.heldStillOnClients(released)
                        || clientCopyHeldStill(level, owner, released)) {
                    wrong.add("a released "
                            + released.getType().toShortString()
                            + ": flagged " + KraftWorkLockService
                                    .heldStillOnClients(released)
                            + ", a client starting to track it reads it "
                            + "still " + clientCopyHeldStill(level, owner,
                                    released)
                            + " (1.16: canUpdate true again)");
                }
            }
        }
        finally {
            KraftWorkLockService.releaseOwned(level, owner.getUUID());
            level.getEntitiesOfClass(Entity.class,
                            owner.getBoundingBox().inflate(4.0D),
                            entity -> entity instanceof Projectile
                                    || entity instanceof EyeOfEnder)
                    .forEach(Entity::discard);
            owner.connection.disconnect(Component.literal(
                    "Kraft Work held client tick GameTest complete"));
        }
        helper.assertTrue(wrong.isEmpty(),
                "1.16 stopped a held eye, firework or bullet on its clients: "
                        + String.join("; ", wrong));
        helper.succeed();
    }

    // 1.16 stopped the update of whatever Kraft Work held whole (canUpdate
    // false: KraftWorkStandType.tickUser :143-144, :157-160, KraftWorkLockTarget
    // :247, KraftWorkBlock :199) on the server and on its clients, and the
    // release let it go on from there: a held primed TNT kept its fuse and
    // blew up that many ticks after its release, a falling block kept its
    // time, an arrow its facing, a boat its damage, an armor stand its fire.
    // A mob held piece by piece went on updating, and so did a road roller
    // while it had a rider (1.16 RoadRollerEntity.canUpdate :80-82), an
    // owner-bound projectile with its owner and an HG barrier.
    @GameTest(template = "empty", timeoutTicks = 60)
    public static void wholeLocksHoldWithoutTickingAsIn116(
            GameTestHelper helper) {
        TestContext context =
                prepareUnsummonedKraftWork(helper, "kw-whole-still");
        ServerPlayer owner = context.owner();
        ServerLevel level = helper.getLevel();
        context.power().setResolveLevel(2);
        turnUser(owner, 30.0F, 30.0F);
        owner.setXRot(-20.0F);
        owner.xRotO = -20.0F;
        List<String> wrong = new ArrayList<>();

        helper.assertTrue(KraftWorkProjectileFactory.place(level, owner, null,
                        new ItemStack(Items.ARROW)),
                "Fixture: the arrow was not placed.");
        Arrow arrow = heldOne(helper, owner, Arrow.class);
        float arrowYaw = arrow.getYRot();
        float arrowPitch = arrow.getXRot();
        Vec3 tntAt = helper.absoluteVec(new Vec3(1.5D, 5.0D, 6.5D));
        PrimedTnt tnt = new PrimedTnt(level, tntAt.x, tntAt.y, tntAt.z, owner);
        tnt.setFuse(10);
        tnt.setDeltaMovement(Vec3.ZERO);
        tnt.setNoGravity(true);
        level.addFreshEntity(tnt);
        FallingBlockEntity sand = EntityType.FALLING_BLOCK.create(level);
        Boat boat = EntityType.BOAT.create(level);
        RoadRollerEntity roller = new RoadRollerEntity(level);
        helper.assertTrue(sand != null && boat != null,
                "Fixture: no falling block or boat entity.");
        sand.setPos(helper.absoluteVec(new Vec3(6.5D, 6.0D, 1.5D)));
        sand.setNoGravity(true);
        sand.dropItem = false;
        sand.time = 598;
        level.addFreshEntity(sand);
        boat.setPos(helper.absoluteVec(new Vec3(4.5D, 2.0D, 1.5D)));
        boat.setNoGravity(true);
        boat.setDamage(10.0F);
        level.addFreshEntity(boat);
        ArmorStand armorStand = helper.spawn(EntityType.ARMOR_STAND,
                new BlockPos(1, 2, 4));
        armorStand.setRemainingFireTicks(30);
        Vec3 fireballAt = helper.absoluteVec(new Vec3(3.5D, 6.0D, 2.5D));
        SmallFireball fireball = new SmallFireball(level, fireballAt.x,
                fireballAt.y, fireballAt.z, new Vec3(0.0D, 0.0D, 0.5D));
        level.addFreshEntity(fireball);
        roller.setPos(helper.absoluteVec(new Vec3(5.5D, 3.0D, 5.5D)));
        roller.setNoGravity(true);
        level.addFreshEntity(roller);
        Mob rider = helper.spawn(EntityType.COW, new BlockPos(5, 5, 5));
        rider.setNoAi(true);
        helper.assertTrue(rider.startRiding(roller, true),
                "Fixture: the cow did not ride the road roller.");
        roller.setRemainingFireTicks(30);
        Mob husk = helper.spawn(EntityType.HUSK, new BlockPos(6, 2, 1));
        husk.setNoAi(true);
        husk.setNoGravity(true);
        husk.setItemSlot(EquipmentSlot.MAINHAND,
                new ItemStack(Items.IRON_SWORD));
        husk.hurtTime = 10;
        List<Entity> whole = List.of(tnt, sand, boat, armorStand, fireball,
                roller);
        for (Entity entity : whole) {
            helper.assertTrue(KraftWorkLockService.lockWhole(owner, entity),
                    "Fixture: the " + entity.getType().toShortString()
                            + " was not held whole.");
        }
        helper.assertTrue(KraftWorkLockService.lockEquipment(owner, husk) > 0,
                "Fixture: the armed husk was not held piece by piece.");
        // 1.16 Lock Armor's heavy punch held an armor stand by its armor
        // (KraftWorkLockArmor :111, :166-167), and tickUser, whose living
        // branch left armor stands out (:114), stopped it whole (:157-160)
        ArmorStand armored = helper.spawn(EntityType.ARMOR_STAND,
                new BlockPos(3, 2, 6));
        armored.setItemSlot(EquipmentSlot.HEAD,
                new ItemStack(Items.IRON_HELMET));
        armored.setRemainingFireTicks(30);
        helper.assertTrue(KraftWorkLockService.lockArmorAfterHeavy(owner,
                        armored, 1.0F) > 0,
                "Fixture: Lock Armor did not hold the armored armor stand.");

        // 1.16 OwnerBoundProjectileEntity.canUpdate (:424-427) followed its
        // owner, and HGBarrierEntity.canUpdate (:236-239) was always true
        Mob fingerUser = helper.spawn(EntityType.COW, new BlockPos(2, 5, 6));
        fingerUser.setNoAi(true);
        fingerUser.setNoGravity(true);
        SPStarFingerEntity finger = new SPStarFingerEntity(fingerUser, level);
        finger.setLifeSpan(10);
        finger.setPos(fingerUser.getEyePosition());
        level.addFreshEntity(finger);
        SPStarFingerEntity ownerless = new SPStarFingerEntity(
                ModEntityTypes.SP_STAR_FINGER.get(), level);
        ownerless.setLifeSpan(1);
        ownerless.setPos(helper.absoluteVec(new Vec3(1.5D, 6.0D, 6.5D)));
        level.addFreshEntity(ownerless);
        HGBarrierEntity barrier = new HGBarrierEntity(
                ModEntityTypes.HG_BARRIER.get(), level);
        barrier.setPos(helper.absoluteVec(new Vec3(3.5D, 6.0D, 6.5D)));
        level.addFreshEntity(barrier);
        for (Entity bound : List.of(finger, ownerless, barrier)) {
            helper.assertTrue(KraftWorkLockService.lockWhole(owner, bound),
                    "Fixture: the " + bound.getType().toShortString()
                            + " was not held whole.");
        }
        for (Entity bound : List.of(finger, barrier)) {
            if (KraftWorkLockService.heldStillOnClients(bound)
                    || clientCopyHeldStill(level, owner, bound)) {
                wrong.add("a held " + bound.getType().toShortString()
                        + (bound == finger ? " with its owner" : "")
                        + ": still on clients " + KraftWorkLockService
                                .heldStillOnClients(bound)
                        + ", a client starting to track it reads it still "
                        + clientCopyHeldStill(level, owner, bound)
                        + " (1.16: its canUpdate kept it updating)");
            }
        }
        if (!KraftWorkLockService.heldStillOnClients(ownerless)
                || !clientCopyHeldStill(level, owner, ownerless)) {
            wrong.add("a held sp_star_finger with no owner: flagged "
                    + KraftWorkLockService.heldStillOnClients(ownerless)
                    + ", a client starting to track it reads it still "
                    + clientCopyHeldStill(level, owner, ownerless)
                    + " (1.16: no owner to follow, canUpdate false)");
        }
        // 1.16's Stingy Eyes beam was an OwnerBoundProjectileEntity
        // (SpaceRipperStingyEyesEntity :21): bound, it followed its owner's
        // update and detached past tick 20 (:44, :51); only then did the
        // hold stop it
        Mob beamUser = helper.spawn(EntityType.COW, new BlockPos(6, 2, 2));
        beamUser.setNoAi(true);
        beamUser.setYRot(90.0F);
        beamUser.setYHeadRot(90.0F);
        beamUser.setXRot(0.0F);
        SpaceRipperStingyEyesEntity beam =
                new SpaceRipperStingyEyesEntity(level, beamUser, true);
        beam.tickCount = 20;
        level.addFreshEntity(beam);
        helper.assertTrue(KraftWorkLockService.lockWhole(owner, beam),
                "Fixture: the Stingy Eyes beam was not held whole.");
        if (KraftWorkLockService.suspendsTick(beam)
                || KraftWorkLockService.heldStillOnClients(beam)
                || clientBeamHeldStill(level, owner, beam)
                || !beam.getExistingData(KraftWorkAddon.HELD_STILL)
                        .orElse(false)) {
            wrong.add("a held Stingy Eyes beam bound to its owner: its "
                    + "update skipped " + KraftWorkLockService.suspendsTick(
                            beam)
                    + ", still on clients "
                    + KraftWorkLockService.heldStillOnClients(beam)
                    + ", a client starting to track it reads it still "
                    + clientBeamHeldStill(level, owner, beam) + ", flagged "
                    + beam.getExistingData(KraftWorkAddon.HELD_STILL)
                            .orElse(false)
                    + " (1.16: bound, it followed its owner's update; "
                    + "flagged for when it detaches)");
        }

        for (Entity held : List.of(arrow, fireball, tnt, sand, boat,
                armorStand, armored)) {
            if (!KraftWorkLockService.heldStillOnClients(held)
                    || !clientCopyHeldStill(level, owner, held)) {
                wrong.add("a held " + held.getType().toShortString()
                        + ": flagged " + KraftWorkLockService
                                .heldStillOnClients(held)
                        + ", a client starting to track it reads it still "
                        + clientCopyHeldStill(level, owner, held)
                        + " (1.16: canUpdate false on its clients too)");
            }
        }
        if (KraftWorkLockService.heldStillOnClients(husk)) {
            wrong.add("a husk held piece by piece is still on clients "
                    + "(1.16: it went on updating)");
        }
        boolean[] exploded = new boolean[1];
        Consumer<ExplosionEvent.Start> blast = event -> {
            if (event.getExplosion().getDirectSourceEntity() == tnt) {
                exploded[0] = true;
                event.setCanceled(true);
            }
        };
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false,
                ExplosionEvent.Start.class, blast);
        int[] rollerFire = new int[1];
        helper.startSequence().thenExecuteAfter(15, () -> {
            if (!isLockedEntity(arrow)
                    || Math.abs(arrow.getYRot() - arrowYaw) > 1.0E-4F
                    || Math.abs(arrow.getXRot() - arrowPitch) > 1.0E-4F) {
                wrong.add("a held arrow facing yaw " + arrowYaw + " pitch "
                        + arrowPitch + ": 15 ticks later held "
                        + isLockedEntity(arrow) + ", facing yaw "
                        + arrow.getYRot() + " pitch " + arrow.getXRot()
                        + " (1.16: not updated, its facing kept)");
            }
            if (tnt.isRemoved() || exploded[0] || tnt.getFuse() != 10) {
                wrong.add("a held primed TNT with a fuse of 10: 15 ticks "
                        + "later removed " + tnt.isRemoved() + ", exploded "
                        + exploded[0] + ", fuse " + tnt.getFuse()
                        + " (1.16: not updated, its fuse kept)");
            }
            if (sand.isRemoved() || sand.time != 598) {
                wrong.add("a held falling block at time 598: 15 ticks later "
                        + "removed " + sand.isRemoved() + ", time "
                        + sand.time + " (1.16: not updated, it never "
                        + "timed out past 600)");
            }
            if (boat.getDamage() != 10.0F) {
                wrong.add("a held boat with damage 10: 15 ticks later "
                        + "damage " + boat.getDamage()
                        + " (1.16: not updated, its damage kept)");
            }
            if (armorStand.getRemainingFireTicks() != 30) {
                wrong.add("a held armor stand with 30 fire ticks: 15 ticks "
                        + "later " + armorStand.getRemainingFireTicks()
                        + " (1.16: not updated, its fire kept)");
            }
            if (armored.getRemainingFireTicks() != 30
                    || !isLockedEntity(armored)) {
                wrong.add("an armor stand held by Lock Armor by its helmet "
                        + "with 30 fire ticks: 15 ticks later "
                        + armored.getRemainingFireTicks() + ", held "
                        + isLockedEntity(armored)
                        + " (1.16: not updated, its fire kept)");
            }
            if (husk.hurtTime != 0 || !isLockedEntity(husk)) {
                wrong.add("a husk held piece by piece with a hurt time of "
                        + "10: 15 ticks later " + husk.hurtTime + ", held "
                        + isLockedEntity(husk) + " (1.16: it went on "
                        + "updating)");
            }
            if (!finger.isRemoved() || !barrier.isRemoved()) {
                wrong.add("a held sp_star_finger with its owner and a "
                        + "lifespan of 10: 15 ticks later removed "
                        + finger.isRemoved() + "; a held hg_barrier with no "
                        + "user: removed " + barrier.isRemoved()
                        + " (1.16: their own update went on and ended them)");
            }
            if (beam.isRemoved() || beam.isBoundToOwner()
                    || !isLockedEntity(beam)
                    || !KraftWorkLockService.suspendsTick(beam)
                    || !KraftWorkLockService.heldStillOnClients(beam)
                    || !clientBeamHeldStill(level, owner, beam)) {
                wrong.add("a held Stingy Eyes beam bound to its owner at "
                        + "tick 20: 15 ticks later removed "
                        + beam.isRemoved() + ", bound "
                        + beam.isBoundToOwner() + ", held "
                        + isLockedEntity(beam) + ", its update skipped "
                        + KraftWorkLockService.suspendsTick(beam)
                        + ", still on clients " + KraftWorkLockService
                                .heldStillOnClients(beam)
                        + ", a client copy reads it still "
                        + clientBeamHeldStill(level, owner, beam)
                        + " (1.16: it went on updating while bound, "
                        + "detached in tick 21 and was then held still)");
            }
            if (ownerless.isRemoved() || !isLockedEntity(ownerless)) {
                wrong.add("a held sp_star_finger with no owner and a "
                        + "lifespan of 1: 15 ticks later removed "
                        + ownerless.isRemoved() + ", held "
                        + isLockedEntity(ownerless)
                        + " (1.16: canUpdate false, not updated)");
            }
            if (roller.getRemainingFireTicks() >= 30
                    || KraftWorkLockService.heldStillOnClients(roller)) {
                wrong.add("a held road roller with a rider and 30 fire "
                        + "ticks: 15 ticks later "
                        + roller.getRemainingFireTicks()
                        + ", still on clients " + KraftWorkLockService
                                .heldStillOnClients(roller)
                        + " (1.16: it went on updating while its rider "
                        + "could)");
            }
            KraftWorkLockService.releaseTarget(owner, tnt);
            KraftWorkLockService.releaseTarget(owner, sand);
            rider.stopRiding();
            rollerFire[0] = roller.getRemainingFireTicks();
        }).thenExecuteAfter(5, () -> {
            if (tnt.isRemoved() || Math.abs(tnt.getFuse() - 5) > 1) {
                wrong.add("a primed TNT released with a fuse of 10: 5 ticks "
                        + "later removed " + tnt.isRemoved() + ", fuse "
                        + tnt.getFuse() + " (1.16: its fuse went on from 10)");
            }
            if (!sand.isRemoved()) {
                wrong.add("a falling block released at time 598: 5 ticks "
                        + "later still there at time " + sand.time
                        + " (1.16: it went on from its time and timed out "
                        + "past 600)");
            }
            if (roller.getRemainingFireTicks() != rollerFire[0]
                    || !KraftWorkLockService.heldStillOnClients(roller)
                    || !clientCopyHeldStill(level, owner, roller)) {
                wrong.add("a held road roller whose rider got off with "
                        + rollerFire[0] + " fire ticks: 5 ticks later "
                        + roller.getRemainingFireTicks()
                        + ", still on clients " + KraftWorkLockService
                                .heldStillOnClients(roller)
                        + " (1.16: not updated without a rider)");
            }
            KraftWorkLockService.releaseOwned(level, owner.getUUID());
            for (Entity released : List.of(arrow, fireball, boat,
                    armorStand, roller, armored)) {
                if (isLockedEntity(released)
                        || KraftWorkLockService.heldStillOnClients(released)
                        || clientCopyHeldStill(level, owner, released)) {
                    wrong.add("a released "
                            + released.getType().toShortString() + ": held "
                            + isLockedEntity(released) + ", still on clients "
                            + KraftWorkLockService.heldStillOnClients(released)
                            + " (1.16: canUpdate true again)");
                }
            }
        }).thenExecuteAfter(8, () -> {
            NeoForge.EVENT_BUS.unregister(blast);
            if (!tnt.isRemoved() || !exploded[0]) {
                wrong.add("a primed TNT released with a fuse of 10: 13 ticks "
                        + "later removed " + tnt.isRemoved() + ", exploded "
                        + exploded[0] + " (1.16: it blew up 10 ticks after "
                        + "its release)");
            }
            for (Entity made : List.of(arrow, tnt, sand, boat, armorStand,
                    fireball, roller, rider, husk, finger, ownerless, barrier,
                    fingerUser, armored, beam, beamUser)) {
                made.discard();
            }
            owner.connection.disconnect(Component.literal(
                    "Kraft Work whole lock tick GameTest complete"));
            helper.assertTrue(wrong.isEmpty(),
                    "1.16 held what Kraft Work held whole without updating "
                            + "it: " + String.join("; ", wrong));
        }).thenSucceed();
    }

    private static <T extends Entity> T heldOne(GameTestHelper helper,
            ServerPlayer owner, Class<T> kind) {
        List<T> held = helper.getLevel().getEntitiesOfClass(kind,
                owner.getBoundingBox().inflate(4.0D),
                KraftWorkGameTests::isLockedEntity);
        helper.assertTrue(held.size() == 1,
                "Fixture: " + held.size() + " held " + kind.getSimpleName());
        return held.getFirst();
    }

    // what a client starting to track the entity reads: a fresh copy given
    // the attachments NeoForge sends with the entity's first data
    private static boolean clientCopyHeldStill(ServerLevel level,
            ServerPlayer tracker, Entity entity) {
        Entity copy = entity.getType().create(level);
        if (copy == null) {
            return false;
        }
        List<Packet<? super ClientGamePacketListener>> sent =
                new ArrayList<>();
        AttachmentSync.syncInitialEntityAttachments(entity, tracker,
                sent::add);
        for (Packet<? super ClientGamePacketListener> packet : sent) {
            if (packet instanceof ClientboundCustomPayloadPacket custom
                    && custom.payload()
                            instanceof SyncAttachmentsPayload sync) {
                AttachmentSync.receiveSyncedDataAttachments(copy,
                        level.registryAccess(), sync.types(),
                        sync.syncPayload());
            }
        }
        return KraftWorkLockService.heldStillOnClients(copy);
    }

    // the same for a Stingy Eyes beam, whose copy also takes the synced data
    // and the owner its spawn packet carries
    private static boolean clientBeamHeldStill(ServerLevel level,
            ServerPlayer tracker, SpaceRipperStingyEyesEntity beam) {
        SpaceRipperStingyEyesEntity copy =
                ModEntityTypes.SPACE_RIPPER_STINGY_EYES.get().create(level);
        if (copy == null) {
            return false;
        }
        var data = beam.getEntityData().getNonDefaultValues();
        if (data != null) {
            copy.getEntityData().assignValues(data);
        }
        copy.setOwner(beam.getOwner());
        List<Packet<? super ClientGamePacketListener>> sent =
                new ArrayList<>();
        AttachmentSync.syncInitialEntityAttachments(beam, tracker,
                sent::add);
        for (Packet<? super ClientGamePacketListener> packet : sent) {
            if (packet instanceof ClientboundCustomPayloadPacket custom
                    && custom.payload()
                            instanceof SyncAttachmentsPayload sync) {
                AttachmentSync.receiveSyncedDataAttachments(copy,
                        level.registryAccess(), sync.types(),
                        sync.syncPayload());
            }
        }
        return KraftWorkLockService.heldStillOnClients(copy);
    }

    // the production factory's one held projectile of that item, charged
    private static Projectile placeCharged(GameTestHelper helper,
            ServerPlayer owner, ItemStack item, int charges) {
        helper.assertTrue(KraftWorkProjectileFactory.place(helper.getLevel(),
                        owner, null, item),
                "Fixture: " + item + " was not placed.");
        List<Projectile> held = helper.getLevel().getEntitiesOfClass(
                Projectile.class, owner.getBoundingBox().inflate(4.0D),
                projectile -> projectile.getOwner() == owner
                        && isLockedEntity(projectile));
        helper.assertTrue(held.size() == 1,
                "Fixture: " + item + " left " + held.size()
                        + " held projectile(s).");
        for (int charge = 0; charge < charges; charge++) {
            KraftWorkLockService.addEnergy(held.getFirst());
        }
        return held.getFirst();
    }

    private static void discardOwnedProjectiles(GameTestHelper helper,
            ServerPlayer owner) {
        helper.getLevel().getEntitiesOfClass(Projectile.class,
                        owner.getBoundingBox().inflate(4.0D),
                        projectile -> projectile.getOwner() == owner)
                .forEach(Entity::discard);
    }

    // the entity sound packets the mock player's connection was sent since
    // the last call: what its client plays, and at which entity
    private static List<ClientboundSoundEntityPacket> entitySoundsSentTo(
            ServerPlayer player) {
        EmbeddedChannel channel = (EmbeddedChannel) player.connection
                .getConnection().channel();
        channel.flushOutbound();
        List<ClientboundSoundEntityPacket> sounds = new ArrayList<>();
        for (Object message = channel.readOutbound(); message != null;
                message = channel.readOutbound()) {
            if (message instanceof ClientboundSoundEntityPacket sound) {
                sounds.add(sound);
            }
        }
        return sounds;
    }

    private static void checkReleaseSound(List<String> wrong, String row,
            ServerPlayer owner, Entity released, boolean stillHeld,
            SoundEvent sound, float volume, float minPitch, float maxPitch) {
        List<ClientboundSoundEntityPacket> sent = entitySoundsSentTo(owner)
                .stream().filter(packet -> packet.getId() == released.getId())
                .toList();
        boolean right = sound == null
                ? sent.isEmpty()
                : sent.size() == 1
                        && sent.getFirst().getSound().value() == sound
                        && sent.getFirst().getSource() == SoundSource.PLAYERS
                        && sent.getFirst().getVolume() == volume
                        && sent.getFirst().getPitch() >= minPitch - 1.0E-4F
                        && sent.getFirst().getPitch() <= maxPitch + 1.0E-4F;
        if (stillHeld || !right) {
            wrong.add(row + ": still held " + stillHeld + ", its user was "
                    + "sent " + sent.stream().map(packet -> packet.getSound()
                                    .value().getLocation().getPath()
                            + " " + packet.getSource() + " volume "
                            + packet.getVolume() + " pitch "
                            + packet.getPitch()).toList()
                    + " at it (1.16: " + (sound == null ? "none"
                            : sound.getLocation().getPath() + " PLAYERS "
                                    + "volume " + volume + " pitch "
                                    + minPitch + "-" + maxPitch) + ")");
        }
    }

    // 1.16 KraftWorkPlaceProjectile :486, :539-559 placed an eye of ender as
    // an EyeOfEnderEntity from the user's middle (x, getY(0.5), z) towards
    // the nearest stronghold within 100, held there, with the use counted
    // and 3 ticks of cooldown; released, it flew on. With no stronghold
    // nothing was placed and the off hand still lost the eye; the owner
    // changed that (2026-10-24): the eye stays in the off hand.
    @GameTest(template = "empty", timeoutTicks = 40)
    public static void eyeOfEnderIsPlacedTowardsAStrongholdAsIn116(
            GameTestHelper helper) {
        TestContext context =
                prepareUnsummonedKraftWork(helper, "kw-place-eye");
        ServerPlayer owner = context.owner();
        StandPower power = context.power();
        owner.setGameMode(GameType.SURVIVAL);
        ServerLevel level = helper.getLevel();
        LivingComponentAction userActions =
                LivingComponentAction.getComponent(owner, PowerClass.STAND);
        helper.assertTrue(level.findNearestMapStructure(
                        StructureTags.EYE_OF_ENDER_LOCATED,
                        owner.blockPosition(), 100, false) == null,
                "Fixture: the GameTest overworld has a stronghold; the "
                        + "press row expects none.");
        List<String> wrong = new ArrayList<>();
        Vec3 spot = new Vec3(owner.getX(), owner.getY(0.5D), owner.getZ());
        int used = owner.getStats().getValue(
                Stats.ITEM_USED.get(Items.ENDER_EYE));
        owner.setItemSlot(EquipmentSlot.OFFHAND,
                new ItemStack(Items.ENDER_EYE, 2));
        pressResolved(helper, power, owner, KraftWorkAddon.PLACE_PROJECTILE,
                InputMethod.CLICK, PLACE_KEY);
        AbilityInput.keyRelease(PLACE_KEY, owner);
        userActions.tick();
        List<Entity> anything = level.getEntitiesOfClass(Entity.class,
                owner.getBoundingBox().inflate(4.0D),
                entity -> entity instanceof EyeOfEnder
                        || entity instanceof Projectile);
        int counted = owner.getStats().getValue(
                Stats.ITEM_USED.get(Items.ENDER_EYE)) - used;
        if (!anything.isEmpty() || owner.getOffhandItem().getCount() != 2
                || owner.getCooldowns().isOnCooldown(Items.ENDER_EYE)
                || counted != 0) {
            wrong.add("an eye of ender pressed with no stronghold: placed "
                    + anything.stream().map(entity -> entity.getClass()
                            .getSimpleName()).toList()
                    + ", " + owner.getOffhandItem().getCount() + " left, "
                    + "cooldown " + owner.getCooldowns().isOnCooldown(
                            Items.ENDER_EYE)
                    + ", uses counted " + counted + " (the owner's call: "
                    + "nothing placed, the eye kept, 2 left, no cooldown or "
                    + "use)");
        }
        anything.forEach(Entity::discard);

        // the placement once a stronghold is found, towards one 40 east
        BlockPos stronghold = owner.blockPosition().offset(40, 0, 0);
        boolean placed = KraftWorkProjectileFactory.placeEyeTowards(level,
                owner, new ItemStack(Items.ENDER_EYE), stronghold);
        List<EyeOfEnder> eyes = level.getEntitiesOfClass(EyeOfEnder.class,
                owner.getBoundingBox().inflate(4.0D));
        counted = owner.getStats().getValue(
                Stats.ITEM_USED.get(Items.ENDER_EYE)) - used;
        if (!placed || eyes.size() != 1 || !isLockedEntity(eyes.getFirst())
                || eyes.getFirst().position().distanceTo(spot) > 1.0E-6D
                || cooldownTicks(owner, Items.ENDER_EYE) != 3
                || counted != 1) {
            wrong.add("an eye of ender placed towards a stronghold: "
                    + eyes.size() + " eye(s)" + (eyes.isEmpty() ? "" : " at "
                            + eyes.getFirst().position() + " held "
                            + isLockedEntity(eyes.getFirst()))
                    + ", cooldown " + cooldownTicks(owner, Items.ENDER_EYE)
                    + ", uses counted " + counted + " (1.16: one held eye at "
                    + spot + ", cooldown 3, 1 use)");
        }
        Vec3[] at = new Vec3[1];
        helper.startSequence().thenExecuteAfter(2, () -> {
            if (!eyes.isEmpty()) {
                at[0] = eyes.getFirst().position();
                KraftWorkLockService.releaseProjectiles(owner,
                        ReleaseFilter.ALL);
            }
        }).thenExecuteAfter(3, () -> {
            if (!eyes.isEmpty() && (isLockedEntity(eyes.getFirst())
                    || eyes.getFirst().getX() - at[0].x < 1.0E-3D)) {
                wrong.add("the released eye held " + isLockedEntity(
                        eyes.getFirst()) + ", moved east " + (eyes.getFirst()
                        .getX() - at[0].x) + " (1.16: it flew on towards "
                        + "its stronghold)");
            }
            eyes.forEach(Entity::discard);
            owner.connection.disconnect(Component.literal(
                    "Kraft Work eye of ender GameTest complete"));
            helper.assertTrue(wrong.isEmpty(),
                    "1.16 placed an eye of ender towards a stronghold: "
                            + String.join("; ", wrong));
        }).thenSucceed();
    }

    private static void checkPlacedDamage(GameTestHelper helper,
            ServerPlayer owner, ItemStack stack, double expected,
            List<String> wrong) {
        owner.getCooldowns().removeCooldown(stack.getItem());
        if (!KraftWorkProjectileFactory.place(helper.getLevel(), owner, null,
                stack.copy())) {
            wrong.add(stack + " was not placed");
            return;
        }
        List<AbstractArrow> placed = helper.getLevel().getEntitiesOfClass(
                AbstractArrow.class, owner.getBoundingBox().inflate(4.0D),
                arrow -> arrow.getOwner() == owner);
        if (placed.size() != 1
                || Math.abs(placed.getFirst().getBaseDamage() - expected)
                        > 1.0E-9D) {
            wrong.add(stack + " placed with base damage " + placed.stream()
                    .map(arrow -> String.valueOf(arrow.getBaseDamage()))
                    .toList() + " (1.16: " + expected + ")");
        }
        placed.forEach(Entity::discard);
    }

    // 1.16 StandEntityTask paid the tick cost of the held energy action and
    // ignored the result: the hold kept charging on an empty bar.
    @GameTest(template = "empty", timeoutTicks = 30)
    public static void energyHoldKeepsChargingOnAnEmptyBar(
            GameTestHelper helper) {
        TestContext context =
                prepareKraftWork(helper, "kw-energy-empty");
        KraftWorkItemProjectile projectile =
                placeCobblestone(helper, context);
        context.owner().setYRot(context.owner().getYRot() + 180.0F);
        context.owner().setYHeadRot(context.owner().getYRot());
        context.stand().setYRot(context.owner().getYRot());
        context.stand().setYHeadRot(context.owner().getYRot());
        emptyBar(helper, context);

        Ability ability = context.power().getAbility(
                KraftWorkAddon.ENERGY_ACCUMULATION);
        helper.assertTrue(
                ability instanceof KraftWorkActionAbility
                        && AbilityInput.withConditionCheck(
                                ability, context.owner()),
                "1.16 had no stamina check: Energy Accumulation must be "
                        + "admitted on an empty bar.");
        AbilityInput.keyPress(
                ENERGY_KEY,
                ability,
                context.owner(),
                new FriendlyByteBuf(Unpooled.buffer()),
                InputMethod.HOLD,
                0.0F,
                BufferingState.clickCanBuffer(),
                ability.abilityId);
        EntityActionInstance action =
                LivingComponentAction.getCurEntityAction(context.stand());
        helper.assertTrue(
                action instanceof KraftWorkActionAbility.KraftWorkAction
                        && action.getPhase() == ActionPhase.WINDUP,
                "The held energy action did not start on an empty bar.");

        // two windups and performs at least (1.16 charged once a perform)
        helper.runAfterDelay(20, () -> {
            try {
                helper.assertTrue(
                        LivingComponentAction.getCurEntityAction(
                                        context.stand())
                                        == action
                                && !action.isOver(),
                        "The energy hold stopped on an empty bar: phase="
                                + action.getPhase());
                helper.assertTrue(
                        KraftWorkLockService.state(projectile).energy()
                                >= 2 * KraftWorkSpec.ENERGY_PER_TICK,
                        "The energy hold stopped charging on an empty "
                                + "bar: energy="
                                + KraftWorkLockService.state(projectile)
                                        .energy());
                helper.assertTrue(
                        context.power().getStamina() == 0.0F,
                        "The paid tick cost must keep the bar at 0: "
                                + context.power().getStamina());
            }
            finally {
                AbilityInput.keyRelease(ENERGY_KEY, context.owner());
                cleanup(
                        context,
                        projectile,
                        "Kraft Work empty-bar energy GameTest complete");
            }
            helper.succeed();
        });
    }

    // 1.16 KraftWorkEnergyAccumulation (InitStands :146, class :62-108) was a
    // click StandEntityAction: a windup of a light attack at a quarter of the
    // speed (halved for the first press), one perform tick that charged the
    // locked projectiles once, then a recovery of the light attack's x4 (x2
    // arms only) that any press, its own too, cancelled. A held use key
    // clicked again every 4 ticks; the queued click replaced it as its
    // recovery began, so a held key charged once a windup, not every tick.
    @GameTest(template = "empty", timeoutTicks = 90)
    public static void energyChargesOncePerWindupAndGivesWayInItsRecoveryAsIn116(
            GameTestHelper helper) {
        TestContext context =
                prepareKraftWork(helper, "kw-energy-cycle");
        KraftWorkItemProjectile projectile =
                placeCobblestone(helper, context);
        context.owner().setYRot(context.owner().getYRot() + 180.0F);
        context.owner().setYHeadRot(context.owner().getYRot());
        context.stand().setYRot(context.owner().getYRot());
        context.stand().setYHeadRot(context.owner().getYRot());
        StandEntity stand = context.stand();
        Ability ability = context.power().getAbility(
                KraftWorkAddon.ENERGY_ACCUMULATION);
        AbilityInput.keyPress(
                ENERGY_KEY,
                ability,
                context.owner(),
                new FriendlyByteBuf(Unpooled.buffer()),
                InputMethod.HOLD,
                0.0F,
                BufferingState.clickCanBuffer(),
                ability.abilityId);
        EntityActionInstance action =
                LivingComponentAction.getCurEntityAction(stand);
        List<String> wrong = new ArrayList<>();
        double quarterSpeed = stand.getAttackSpeed() / 4.0D;
        double windup = Math.max((24.0D - quarterSpeed) / 4.0D, 2.0D) / 2.0D;
        float firstWindup = action != null
                ? action.phasesLength.getFloat(ActionPhase.WINDUP) : -1.0F;
        if (!(action instanceof KraftWorkActionAbility.KraftWorkAction)
                || action.getPhase() != ActionPhase.WINDUP
                || firstWindup < Math.floor(windup)
                || firstWindup > Math.floor(windup) + 1.0D) {
            wrong.add("the press did not start the first-press windup ("
                    + (action != null ? action.getPhase() : null) + ", "
                    + firstWindup + " ticks, 1.16 " + windup + ")");
        }
        List<Integer> charges = new ArrayList<>();
        int[] tick = {0};
        int[] lastEnergy = {0};
        helper.onEachTick(() -> {
            int energy = KraftWorkLockService.state(projectile).energy();
            if (energy != lastEnergy[0]) {
                charges.add(tick[0]);
                lastEnergy[0] = energy;
            }
            tick[0]++;
        });
        helper.runAfterDelay(30, () -> {
            int energy = KraftWorkLockService.state(projectile).energy();
            if (charges.size() < 3 || charges.size() > 6
                    || energy != charges.size()
                            * KraftWorkSpec.ENERGY_PER_TICK) {
                wrong.add("a held key charged " + charges.size()
                        + " times in 30 ticks (energy " + energy
                        + ", at ticks " + charges + "), 1.16 once a "
                        + "windup and perform, 4 or 5 times");
            }
            for (int i = 1; i < charges.size(); i++) {
                if (charges.get(i) - charges.get(i - 1) < 5) {
                    wrong.add("two charges " + (charges.get(i)
                            - charges.get(i - 1)) + " ticks apart "
                            + charges + ", 1.16 a full windup between");
                    break;
                }
            }
            AbilityInput.keyRelease(ENERGY_KEY, context.owner());
        });
        helper.runAfterDelay(45, () -> {
            try {
                int recovery = StandStatFormulas.getLightAttackRecovery(
                        stand.getAttackSpeed(), stand.getFinisherMeter()) * 4;
                if (stand.getCurStandAction() != action
                        || action.getPhase() != ActionPhase.RECOVERY
                        || action.phasesLength.getFloat(ActionPhase.RECOVERY)
                                != recovery) {
                    wrong.add("after the key was let go the action did not "
                            + "finish its cycle into a recovery of " + recovery
                            + " ticks: " + action.getPhase() + ", "
                            + action.phasesLength.getFloat(ActionPhase.RECOVERY));
                }
                else {
                    Ability punch = context.power().getAbility(
                            KraftWorkAddon.PUNCH);
                    AbilityInput.keyPress(
                            PLACE_KEY,
                            punch,
                            context.owner(),
                            new FriendlyByteBuf(Unpooled.buffer()),
                            InputMethod.CLICK,
                            0.0F,
                            BufferingState.clickCanBuffer(),
                            punch.abilityId);
                    AbilityInput.keyRelease(PLACE_KEY, context.owner());
                    EntityActionInstance next = stand.getCurStandAction();
                    if (next == null || next == action
                            || next.ability != punch) {
                        wrong.add("a punch pressed in the recovery did not "
                                + "replace it at once: " + next);
                    }
                }
                helper.assertTrue(wrong.isEmpty(),
                        "1.16 energy accumulation was a click action "
                                + "chained by the held key: " + wrong);
            }
            finally {
                cleanup(
                        context,
                        projectile,
                        "Kraft Work energy cycle GameTest complete");
            }
            helper.succeed();
        });
    }

    // 1.16 KraftWorkEnergyAccumulation.standPerform (:80-93) charged every
    // projectile in reach whose PositionLocking was set, whoever had locked
    // it, and played the giving-energy sound at each one for every player
    // (PLAYERS, volume 1, pitch 1 / (rand * 0.4 + 1.2) + 0.5).
    @GameTest(template = "empty", timeoutTicks = 40)
    public static void energyChargesEveryLockedProjectileAtItsPlaceAsIn116(
            GameTestHelper helper) {
        TestContext context =
                prepareKraftWork(helper, "kw-energy-any-lock");
        TestContext other =
                prepareKraftWork(helper, "kw-energy-other-lock");
        StandEntity stand = context.stand();
        Snowball snowball = lockedSnowball(helper, other.owner(),
                stand.position().add(0.0D, 1.0D, 0.0D), false);
        List<PlayLevelSoundEvent> sounds = new ArrayList<>();
        // other tests charge their own items at the same time elsewhere
        Consumer<PlayLevelSoundEvent.AtEntity> atEntity = event -> {
            if (event.getSound() != null && event.getSound().value()
                            == KraftWorkAddon.KRAFT_WORK_GIVING_KINETIC_ENERGY.get()
                    && (event.getEntity() == snowball
                            || event.getEntity().distanceToSqr(stand) < 2.25D)) {
                sounds.add(event);
            }
        };
        Consumer<PlayLevelSoundEvent.AtPosition> atPosition = event -> {
            if (event.getSound() != null && event.getSound().value()
                            == KraftWorkAddon.KRAFT_WORK_GIVING_KINETIC_ENERGY.get()
                    && (event.getPosition().distanceToSqr(stand.position()) < 2.25D
                            || event.getPosition().distanceToSqr(
                                    context.owner().position()) < 2.25D)) {
                sounds.add(event);
            }
        };
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true,
                PlayLevelSoundEvent.AtEntity.class, atEntity);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true,
                PlayLevelSoundEvent.AtPosition.class, atPosition);
        Ability ability = context.power().getAbility(
                KraftWorkAddon.ENERGY_ACCUMULATION);
        AbilityInput.keyPress(
                ENERGY_KEY,
                ability,
                context.owner(),
                new FriendlyByteBuf(Unpooled.buffer()),
                InputMethod.HOLD,
                0.0F,
                BufferingState.clickCanBuffer(),
                ability.abilityId);
        // the first windup is two or three ticks, the next at least four
        helper.runAfterDelay(6, () -> {
            try {
                var lock = KraftWorkLockService.state(snowball);
                helper.assertTrue(
                        lock.locked() && other.owner().getUUID().equals(
                                lock.owner()),
                        "Fixture: the snowball is not locked by the other "
                                + "Kraft Work user.");
                helper.assertTrue(
                        lock.energy() == KraftWorkSpec.ENERGY_PER_TICK
                                && lock.ready(),
                        "1.16 charged every locked projectile in reach, "
                                + "whoever had locked it: the other user's "
                                + "snowball has energy " + lock.energy()
                                + ", ready " + lock.ready());
                float lowest = 1.0F / 1.6F + 0.5F - 1.0E-4F;
                float highest = 1.0F / 1.2F + 0.5F + 1.0E-4F;
                boolean atTheSnowball = sounds.size() == 1
                        && sounds.getFirst()
                                instanceof PlayLevelSoundEvent.AtEntity heard
                        && heard.getEntity() == snowball
                        && heard.getSource() == SoundSource.PLAYERS
                        && heard.getOriginalVolume() == 1.0F
                        && heard.getOriginalPitch() >= lowest
                        && heard.getOriginalPitch() <= highest;
                List<String> heardSounds = new ArrayList<>();
                for (PlayLevelSoundEvent event : sounds) {
                    heardSounds.add((event
                                    instanceof PlayLevelSoundEvent.AtEntity entity
                                    ? "at " + entity.getEntity().getType()
                                            .toShortString()
                                    : "at a position, " + ((PlayLevelSoundEvent
                                            .AtPosition) event).getPosition())
                            + " " + event.getSource() + " volume "
                            + event.getOriginalVolume() + " pitch "
                            + event.getOriginalPitch());
                }
                helper.assertTrue(atTheSnowball,
                        "1.16 played the giving-energy sound at the charged "
                                + "projectile for every player (PLAYERS, "
                                + "volume 1, pitch " + lowest + " to "
                                + highest + "); the port played "
                                + heardSounds);
            }
            finally {
                NeoForge.EVENT_BUS.unregister(atEntity);
                NeoForge.EVENT_BUS.unregister(atPosition);
                AbilityInput.keyRelease(ENERGY_KEY, context.owner());
                snowball.discard();
                other.stand().discard();
                other.owner().connection.disconnect(Component.literal(
                        "Kraft Work energy lock owner GameTest complete"));
                context.stand().discard();
                context.owner().connection.disconnect(Component.literal(
                        "Kraft Work energy any-lock GameTest complete"));
            }
            helper.succeed();
        });
    }

    // 1.16: the held use key clicked again 4 ticks after the press and every
    // 4 ticks after that. The first action's windup is halved, so its
    // recovery could begin before that first repeat click, which then
    // replaced it (isCancelable): the second action began 4 ticks after the
    // press. A repeat click during an action's windup or perform was queued
    // (canBeQueued) and replaced it as its recovery began, even with the key
    // let go: one more charge, its recovery read as it began. The clicks
    // count from the press, a task held back by the summon lock or queued
    // behind another task included.
    @GameTest(template = "empty", timeoutTicks = 80)
    public static void energyChainsOnTheHeldKeysRepeatClicksAsIn116(
            GameTestHelper helper) {
        TestContext context =
                prepareKraftWork(helper, "kw-energy-clicks");
        KraftWorkItemProjectile projectile =
                placeCobblestone(helper, context);
        context.owner().setYRot(context.owner().getYRot() + 180.0F);
        context.owner().setYHeadRot(context.owner().getYRot());
        context.stand().setYRot(context.owner().getYRot());
        context.stand().setYHeadRot(context.owner().getYRot());
        StandEntity stand = context.stand();
        Ability ability = context.power().getAbility(
                KraftWorkAddon.ENERGY_ACCUMULATION);
        AbilityInput.keyPress(
                ENERGY_KEY,
                ability,
                context.owner(),
                new FriendlyByteBuf(Unpooled.buffer()),
                InputMethod.HOLD,
                0.0F,
                BufferingState.clickCanBuffer(),
                ability.abilityId);
        EntityActionInstance action =
                LivingComponentAction.getCurEntityAction(stand);
        helper.assertTrue(
                action instanceof KraftWorkActionAbility.KraftWorkAction
                        && action.getPhase() == ActionPhase.WINDUP
                        && action.getPhaseTick() == 0.0F,
                "Fixture: the held key did not start energy accumulation "
                        + "in its windup.");
        // the first windup rolled its shorter length (2.5 at speed 16)
        action.phasesLength.put(ActionPhase.WINDUP, 2.0F);
        action.setPhaseStart(ActionPhase.WINDUP);
        List<Integer> charges = new ArrayList<>();
        List<Float> windups = new ArrayList<>();
        List<String> wrong = new ArrayList<>();
        int[] tick = {0};
        int[] lastEnergy = {0};

        // A press Kraft Work queued behind its heavy punch: 1.16's held key
        // still clicked every 4 ticks from the press, so the queued action,
        // started later, went on at those clicks, not 4 ticks after its start.
        TestContext queuedContext =
                prepareKraftWork(helper, "kw-energy-queued");
        StandEntity queuedStand = queuedContext.stand();
        // out of reach of the first user's held cobblestone
        Vec3 queuedAt = Vec3.atBottomCenterOf(
                helper.absolutePos(new BlockPos(6, 2, 6)));
        queuedContext.owner().setPos(queuedAt.x, queuedAt.y, queuedAt.z);
        queuedStand.setPos(queuedAt.x, queuedAt.y, queuedAt.z);
        pressResolved(helper, queuedContext.power(), queuedContext.owner(),
                KraftWorkAddon.HEAVY_PUNCH, InputMethod.CLICK, PUNCH_KEY);
        AbilityInput.keyRelease(PUNCH_KEY, queuedContext.owner());
        EntityActionInstance heavy = queuedStand.getCurStandAction();
        Ability queuedEnergy = queuedContext.power().getAbility(
                KraftWorkAddon.ENERGY_ACCUMULATION);
        long pressTime = helper.getLevel().getGameTime();
        BufferingState queuedPress = BufferingState.clickCanBuffer();
        AbilityInput.keyPress(
                ENERGY_KEY,
                queuedEnergy,
                queuedContext.owner(),
                new FriendlyByteBuf(Unpooled.buffer()),
                InputMethod.HOLD,
                0.0F,
                queuedPress,
                queuedEnergy.abilityId);
        helper.assertTrue(
                heavy != null
                        && heavy.ability == queuedContext.power().getAbility(
                                KraftWorkAddon.HEAVY_PUNCH)
                        && queuedPress.shouldBuffer
                        && queuedStand.getCurStandAction() == heavy,
                "Fixture: the energy press was not queued behind Kraft "
                        + "Work's heavy punch: " + heavy + ", buffered "
                        + queuedPress.shouldBuffer);
        // A press made as Kraft Work is summoned: the new Stand's summon lock
        // held its task back (StandEntity.tick :1270-1281), the held key went
        // on clicking every 4 ticks from the press all the same.
        TestContext lockedContext =
                prepareUnsummonedKraftWork(helper, "kw-energy-locked");
        helper.assertTrue(
                KraftWorkAddon.KRAFT_WORK.get().summon(
                        lockedContext.owner(), lockedContext.power()),
                "Kraft Work could not be summoned.");
        StandEntity lockedStand = lockedContext.power().getSummonedStandEntity();
        helper.assertTrue(lockedStand != null && lockedStand.isAddedToLevel()
                        && lockedStand.summonLockTicks
                                == StandStatFormulas.getSummonLockTicks(
                                        KraftWorkSpec.SPEED)
                        && lockedStand.summonLockTicks > 1,
                "Fixture: Kraft Work was not just summoned: " + lockedStand);
        Ability lockedEnergy = lockedContext.power().getAbility(
                KraftWorkAddon.ENERGY_ACCUMULATION);
        long lockedPressTime = helper.getLevel().getGameTime();
        pressWithKraftWorkOut(helper, lockedContext.owner(), ENERGY_KEY,
                lockedEnergy, InputMethod.HOLD);
        EntityActionInstance lockedAction = lockedStand.getCurStandAction();
        helper.assertTrue(lockedAction != null
                        && lockedAction.ability == lockedEnergy,
                "Kraft Work did not take the energy accumulation press: "
                        + lockedAction);
        int[] lockLeftAfterTheFirstTick = {-1};
        long[] lockedNextWindupAt = {-1L};
        List<String> lockedSeen = new ArrayList<>();
        Runnable watchLocked = () -> {
            long sincePress =
                    helper.getLevel().getGameTime() - lockedPressTime;
            // held back by the lock, the action is put in its recovery, where
            // the next click is taken at once
            if (sincePress == 1
                    && lockedStand.getCurStandAction() == lockedAction) {
                lockLeftAfterTheFirstTick[0] = lockedStand.summonLockTicks;
                lockedAction.setPhaseStart(ActionPhase.RECOVERY);
            }
            if (sincePress <= 9) {
                lockedSeen.add(sincePress + ":"
                        + (lockedStand.getCurStandAction() == lockedAction
                                ? lockedAction.getPhase().toString()
                                : "gone"));
            }
            if (sincePress > 1 && lockedNextWindupAt[0] < 0
                    && lockedStand.getCurStandAction() == lockedAction
                    && lockedAction.getPhase() == ActionPhase.WINDUP) {
                lockedNextWindupAt[0] = sincePress;
                lockedAction.forceStop();
                lockedAction.syncPhaseChanges();
            }
        };
        EntityActionInstance[] replayed = new EntityActionInstance[1];
        long[] replayedAt = {-1L};
        long[] nextWindupAt = {-1L};
        ActionPhase[] lastPhase = new ActionPhase[1];
        List<String> queuedSeen = new ArrayList<>();
        // GameTestHelper.onEachTick keeps one task per tick
        Runnable watchQueued = () -> {
            long sincePress = helper.getLevel().getGameTime() - pressTime;
            if (sincePress >= 1 && !heavy.isOver()) {
                heavy.forceStop();
                heavy.syncPhaseChanges();
            }
            EntityActionInstance current = queuedStand.getCurStandAction();
            if (sincePress <= 8) {
                queuedSeen.add(sincePress + ":" + (current == null ? "none"
                        : current.ability.getAbilityId().nameInMoveset() + "/"
                                + current.getPhase()));
            }
            if (replayed[0] == null && current != null
                    && current.ability.getAbilityId().equals(
                            queuedEnergy.abilityId)) {
                // its windup does not matter here: the next click is
                // taken in the recovery
                replayed[0] = current;
                replayedAt[0] = sincePress;
                current.setPhaseStart(ActionPhase.RECOVERY);
                lastPhase[0] = ActionPhase.RECOVERY;
            }
            else if (replayed[0] != null && nextWindupAt[0] < 0
                    && current == replayed[0]) {
                if (current.getPhase() == ActionPhase.WINDUP
                        && lastPhase[0] == ActionPhase.RECOVERY) {
                    nextWindupAt[0] = sincePress;
                }
                lastPhase[0] = current.getPhase();
            }
        };
        helper.onEachTick(() -> {
            watchQueued.run();
            watchLocked.run();
            int energy = KraftWorkLockService.state(projectile).energy();
            if (energy != lastEnergy[0]) {
                lastEnergy[0] = energy;
                charges.add(tick[0]);
                windups.add(action.phasesLength.getFloat(ActionPhase.WINDUP));
                if (charges.size() == 2) {
                    AbilityInput.keyRelease(ENERGY_KEY, context.owner());
                    // a slower Stand: the last recovery must be read as it begins
                    context.owner().getAttribute(ModEntityAttributes.STAND_SPEED)
                            .setBaseValue(10.0D);
                    StandEntity.updateStandStatAttributes(stand, context.owner());
                }
                else if (charges.size() == 3) {
                    float recovery = KraftWorkActionAbility.energyRecovery(stand);
                    if (stand.getCurStandAction() != action
                            || action.getPhase() != ActionPhase.RECOVERY
                            || action.getCurPhaseLength() != recovery) {
                        wrong.add("the last recovery is not the one read as it "
                                + "began (" + recovery + " ticks at speed "
                                + stand.getAttackSpeed() + "): "
                                + action.getPhase() + ", "
                                + action.getCurPhaseLength());
                    }
                }
            }
            tick[0]++;
        });
        helper.runAfterDelay(45, () -> {
            try {
                if (charges.size() < 2) {
                    wrong.add("fixture: only " + charges.size()
                            + " charges at ticks " + charges);
                }
                else {
                    // the first charge came 2 ticks after the press
                    int afterPress = charges.get(1) - charges.get(0) + 2
                            - Math.round(windups.get(1));
                    if (afterPress != 4) {
                        wrong.add("the second action began " + afterPress
                                + " ticks after the press (charges at "
                                + charges + ", windups " + windups
                                + "), 1.16 at the first repeat click, 4");
                    }
                    if (charges.size() != 3) {
                        wrong.add("after the key was let go in the second "
                                + "action, " + (charges.size() - 2)
                                + " more charges (at " + charges + "), 1.16 "
                                + "one: the click queued in that windup");
                    }
                }
                // the press's first tick counts 0 (the game tick after it),
                // so its first repeat click is the 5th game tick after it
                if (replayedAt[0] < 1 || replayedAt[0] > 3
                        || nextWindupAt[0] != 5) {
                    wrong.add("an energy press queued behind the heavy punch "
                            + "and replayed " + replayedAt[0] + " game "
                            + "ticks after the press went on to its next "
                            + "windup " + nextWindupAt[0] + " game ticks "
                            + "after the press, 1.16 at the held key's first "
                            + "repeat click, counted from the press: 5 "
                            + "(Kraft Work's task by game tick "
                            + queuedSeen + ")");
                }
                if (lockLeftAfterTheFirstTick[0] <= 0) {
                    wrong.add("fixture: the summon lock was over after "
                            + "the first game tick ("
                            + lockLeftAfterTheFirstTick[0] + " ticks left): "
                            + lockedSeen);
                }
                else if (lockedNextWindupAt[0] != 5) {
                    wrong.add("an energy press made as Kraft Work was "
                            + "summoned (its summon lock had "
                            + lockLeftAfterTheFirstTick[0] + " ticks left "
                            + "after the first game tick) went on to its "
                            + "next windup "
                            + lockedNextWindupAt[0] + " game ticks after the "
                            + "press, 1.16 at the held key's first repeat "
                            + "click, counted from the press: 5 (by game "
                            + "tick " + lockedSeen + ")");
                }
                helper.assertTrue(wrong.isEmpty(),
                        "1.16 energy accumulation was chained by the held "
                                + "key's repeat clicks: " + wrong);
            }
            finally {
                AbilityInput.keyRelease(ENERGY_KEY, lockedContext.owner());
                lockedStand.discard();
                lockedContext.owner().connection.disconnect(
                        Component.literal("Kraft Work locked energy "
                                + "GameTest complete"));
                AbilityInput.keyRelease(ENERGY_KEY, queuedContext.owner());
                queuedStand.discard();
                queuedContext.owner().connection.disconnect(
                        Component.literal("Kraft Work queued energy "
                                + "GameTest complete"));
                cleanup(
                        context,
                        projectile,
                        "Kraft Work energy clicks GameTest complete");
            }
            helper.succeed();
        });
    }

    // 1.16 Lock Target and Release Target had no stamina condition: a block
    // under the crosshair is locked and freed on an empty bar.
    @GameTest(template = "empty", timeoutTicks = 40)
    public static void lockAndReleaseBlockOnAnEmptyBar(
            GameTestHelper helper) {
        TestContext context = prepareEmptyBar(helper, "kw-block-empty");
        ServerPlayer owner = context.owner();
        // The Stand spawns on its user until its first tick; keep it
        // out of the aim ray.
        context.stand().setPos(
                owner.getX(), owner.getY(), owner.getZ() - 3.0D);
        BlockPos block = new BlockPos(1, 3, 3);
        helper.setBlock(block, Blocks.STONE);
        BlockPos absolute = helper.absolutePos(block);
        Ability lockTarget = context.power().getAbility(
                KraftWorkAddon.LOCK_TARGET);
        Ability releaseTarget = context.power().getAbility(
                KraftWorkAddon.RELEASE_TARGET);
        helper.assertTrue(
                lockTarget instanceof KraftWorkActionAbility
                        && releaseTarget instanceof KraftWorkActionAbility,
                "Lock or Release Target is not a Kraft Work action.");
        helper.assertTrue(
                lockTarget.checkSpecificConditions(context.power())
                        .isPositive(),
                "1.16 had no stamina check: Lock Block must be admitted "
                        + "on an empty bar.");
        AbilityInput.keyPress(
                LOCK_KEY,
                lockTarget,
                owner,
                new FriendlyByteBuf(Unpooled.buffer()),
                InputMethod.HOLD,
                0.0F,
                BufferingState.clickCanBuffer(),
                lockTarget.abilityId);

        helper.runAfterDelay(8, () -> {
            AbilityInput.keyRelease(LOCK_KEY, owner);
            helper.assertTrue(
                    markersOn(helper, absolute) == 1,
                    "Lock Block did not lock the block on an empty bar.");
            context.power().setStamina(0.0F);
            helper.assertTrue(
                    releaseTarget.checkSpecificConditions(context.power())
                            .isPositive(),
                    "1.16 had no stamina check: Release Block must be "
                            + "admitted on an empty bar.");
            AbilityInput.keyPress(
                    RELEASE_KEY,
                    releaseTarget,
                    owner,
                    new FriendlyByteBuf(Unpooled.buffer()),
                    InputMethod.CLICK,
                    0.0F,
                    BufferingState.clickCanBuffer(),
                    releaseTarget.abilityId);
            helper.runAfterDelay(8, () -> {
                try {
                    helper.assertTrue(
                            markersOn(helper, absolute) == 0,
                            "Release Block did not free the block on an "
                                    + "empty bar.");
                }
                finally {
                    context.stand().discard();
                    owner.connection.disconnect(Component.literal(
                            "Kraft Work empty-bar block GameTest complete"));
                }
                helper.succeed();
            });
        });
    }

    // 1.16 kraft_work_energy_accumulation standOffsetFront() and
    // KraftWorkComboPunch.getOffsetFromUser noYOffset(0, 0.5) in either mode,
    // (left, up, forward) from the user's feet at the mode's default y,
    // turned by the view yaw; the combo punch's Stand moved by itself from
    // the end of its windup.
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void energyAndComboPunchPutTheStandWhereTheDonorDid(
            GameTestHelper helper) {
        TestContext context =
                prepareKraftWork(helper, "kw-action-offsets");
        ServerPlayer owner = context.owner();
        StandEntity stand = context.stand();
        try {
            owner.getData(ModDataAttachmentTypes.PLAYER_BROADCASTED_SETTINGS
                            .get())
                    .standSide = HumanoidArm.RIGHT;
            stand.updatePosition(owner);
            helper.assertTrue(stand.isFollowingUser()
                            && !stand.isArmsOnlyMode(),
                    "Fixture: Kraft Work is not a full Stand following "
                            + "its user.");

            startOffsetAction(helper, context,
                    KraftWorkAddon.ENERGY_ACCUMULATION);
            assertFrontOffset(helper, owner, stand, 0.2D,
                    "energy accumulation");
            stopOffsetAction(context);

            EntityActionInstance combo = startOffsetAction(helper, context,
                    KraftWorkAddon.COMBO_PUNCH);
            assertFrontOffset(helper, owner, stand, 0.2D,
                    "the combo punch windup");
            combo.setPhase(ActionPhase.WINDUP, 8);
            helper.assertTrue(!stand.isFollowingUser(),
                    "1.16 let the combo punch's Stand move by itself in "
                            + "the last two windup ticks.");
            combo.setPhase(ActionPhase.RECOVERY, 0);
            helper.assertTrue(!stand.isFollowingUser(),
                    "1.16 let the combo punch's Stand stay where it "
                            + "punched through the recovery.");
            stopOffsetAction(context);

            // each action replaces the one before: clearing it would
            // unsummon the arms
            stand.setArmsOnlyMode();
            startOffsetAction(helper, context,
                    KraftWorkAddon.ENERGY_ACCUMULATION);
            turnUser(owner, 0.0F, 50.0F);
            assertStandAt(helper, owner, stand, 0.0D, 0.0D, 0.15D,
                    "arms-only energy accumulation (the arms-only "
                            + "default), view yaw 0");
            startOffsetAction(helper, context,
                    KraftWorkAddon.COMBO_PUNCH);
            assertFrontOffset(helper, owner, stand, 0.0D,
                    "the arms-only combo punch windup");
        }
        finally {
            stand.discard();
            owner.connection.disconnect(Component.literal(
                    "Kraft Work action offset GameTest complete"));
        }
        helper.succeed();
    }

    private static EntityActionInstance startOffsetAction(
            GameTestHelper helper, TestContext context, String name) {
        helper.assertTrue(
                context.power().getAbility(name) instanceof EntityActionType,
                "Kraft Work has no action " + name);
        EntityActionType type =
                (EntityActionType) context.power().getAbility(name);
        EntityActionInstance action = type.createActionObj();
        type.initActionFromConfig(
                action, helper.getLevel(), context.owner(), context.stand());
        action.phasesLength.put(ActionPhase.BUTTON_CHARGE, 0.0F);
        action.phasesLength.put(ActionPhase.WINDUP, 10.0F);
        action.phasesLength.put(ActionPhase.PERFORM, 5.0F);
        action.phasesLength.put(ActionPhase.RECOVERY, 10.0F);
        action.setStartingPhase();
        LivingComponentAction.getComponent(context.stand())
                .setAction(action, context.owner(), SyncType.NO_SYNC);
        helper.assertTrue(context.stand().getCurStandAction() == action,
                "Fixture: " + name + " did not become the Stand's action.");
        return action;
    }

    // a Stand left far from its user is retracted when its action ends
    private static void stopOffsetAction(TestContext context) {
        context.stand().setPos(context.owner().position());
        LivingComponentAction.getComponent(context.stand())
                .setAction(null, SyncType.NO_SYNC);
    }

    private static void assertFrontOffset(GameTestHelper helper,
            ServerPlayer owner, StandEntity stand, double y, String what) {
        // the body is turned away from the view: a body-yaw offset misses
        turnUser(owner, 0.0F, 50.0F);
        assertStandAt(helper, owner, stand, 0.0D, y, 0.5D,
                what + ", view yaw 0, body yaw 50");
        // facing west the forward is -x
        turnUser(owner, 90.0F, 0.0F);
        assertStandAt(helper, owner, stand, -0.5D, y, 0.0D,
                what + ", view yaw 90, body yaw 0");
    }

    private static void turnUser(
            ServerPlayer user, float viewYaw, float bodyYaw) {
        user.setYRot(viewYaw);
        user.yRotO = viewYaw;
        user.setYHeadRot(viewYaw);
        user.yHeadRotO = viewYaw;
        user.yBodyRot = bodyYaw;
        user.yBodyRotO = bodyYaw;
    }

    private static void assertStandAt(GameTestHelper helper,
            ServerPlayer user, StandEntity stand,
            double dx, double dy, double dz, String when) {
        stand.updatePosition(user);
        Vec3 actual = stand.position().subtract(user.position());
        helper.assertTrue(Math.abs(actual.x - dx) < 1.0E-4D
                        && Math.abs(actual.y - dy) < 1.0E-4D
                        && Math.abs(actual.z - dz) < 1.0E-4D,
                "1.16 put Kraft Work at its user's feet + (" + dx + ", "
                        + dy + ", " + dz + ") during " + when + ", got "
                        + actual + " (offset "
                        + stand.offsetFromUser.getRelativeOffset() + " "
                        + stand.offsetFromUser.getRotations() + ")");
    }

    private static int markersOn(GameTestHelper helper, BlockPos block) {
        return helper.getLevel().getEntitiesOfClass(
                        KraftWorkBlockMarkerEntity.class,
                        new AABB(block))
                .size();
    }

    private static TestContext prepareEmptyBar(
            GameTestHelper helper, String playerName) {
        TestContext context = prepareKraftWork(helper, playerName);
        emptyBar(helper, context);
        return context;
    }

    private static void emptyBar(
            GameTestHelper helper, TestContext context) {
        context.owner().setGameMode(GameType.SURVIVAL);
        context.power().setStamina(0.0F);
        helper.assertTrue(
                !context.power().isStaminaInfinite()
                        && context.power().getStamina() == 0.0F,
                "Fixture: the Kraft Work user's stamina is not finite "
                        + "and empty.");
    }

    private static TestContext prepareKraftWork(
            GameTestHelper helper, String playerName) {
        TestContext unsummoned =
                prepareUnsummonedKraftWork(helper, playerName);
        ServerPlayer owner = unsummoned.owner();
        StandPower power = unsummoned.power();
        helper.assertTrue(
                KraftWorkAddon.KRAFT_WORK.get().summon(owner, power),
                "Kraft Work could not be summoned.");
        StandEntity stand = power.getSummonedStandEntity();
        helper.assertTrue(
                stand != null && stand.isAddedToLevel(),
                "Kraft Work was not added to the server level.");
        if (stand == null) {
            throw new IllegalStateException(
                    "Missing summoned Kraft Work entity");
        }
        stand.summonLockTicks = 0;
        return new TestContext(owner, power, stand);
    }

    private static TestContext prepareUnsummonedKraftWork(
            GameTestHelper helper, String playerName) {
        ServerPlayer owner =
                makeCompatibleMockPlayer(helper, playerName);
        Vec3 position = Vec3.atBottomCenterOf(
                helper.absolutePos(new BlockPos(1, 2, 1)));
        owner.setPos(position.x, position.y, position.z);
        owner.setXRot(0.0F);
        owner.setYRot(0.0F);
        owner.setYHeadRot(0.0F);

        StandPower power = StandPower.get(owner);
        helper.assertTrue(
                power != null,
                "The mock player has no Stand power attachment.");
        if (power == null) {
            throw new IllegalStateException(
                    "Missing Stand power attachment");
        }
        power.setStand(KraftWorkAddon.KRAFT_WORK.get());
        power.skipProgression();
        power.setStamina(power.getMaxStamina());
        helper.assertTrue(
                power.getSummonedStandEntity() == null,
                "Fixture: Kraft Work is out before its summon.");
        return new TestContext(owner, power, null);
    }

    private static KraftWorkItemProjectile placeCobblestone(
            GameTestHelper helper, TestContext context) {
        return placeItem(helper, context, new ItemStack(Items.COBBLESTONE));
    }

    private static KraftWorkItemProjectile placeItem(
            GameTestHelper helper, TestContext context, ItemStack item) {
        helper.assertTrue(
                KraftWorkProjectileFactory.place(
                        helper.getLevel(),
                        context.owner(),
                        context.stand(),
                        item),
                "The production fixed-item factory rejected " + item.getItem());
        List<KraftWorkItemProjectile> projectiles =
                helper.getLevel().getEntitiesOfClass(
                        KraftWorkItemProjectile.class,
                        new AABB(context.owner().blockPosition())
                                .inflate(4.0D));
        helper.assertTrue(
                projectiles.size() == 1,
                "Expected one fixed-item projectile, found "
                        + projectiles.size() + ".");
        if (projectiles.isEmpty()) {
            throw new IllegalStateException(
                    "Missing fixed-item projectile");
        }
        KraftWorkItemProjectile projectile = projectiles.getFirst();
        helper.assertTrue(
                KraftWorkLockService.state(projectile).locked()
                        && context.owner().getUUID().equals(
                                KraftWorkLockService.state(projectile)
                                        .owner()),
                "The production factory did not lock the fixed item "
                        + "to its owner.");
        return projectile;
    }

    private static ServerPlayer makeCompatibleMockPlayer(
            GameTestHelper helper, String playerName) {
        GameProfile profile = new GameProfile(
                UUID.randomUUID(), playerName);
        CommonListenerCookie cookie =
                CommonListenerCookie.createInitial(profile, false);
        ServerPlayer player = new RecordingPlayer(
                helper.getLevel().getServer(),
                helper.getLevel(),
                profile,
                cookie.clientInformation());
        Connection connection =
                new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        NetworkRegistry.configureMockConnection(connection);
        helper.getLevel()
                .getServer()
                .getPlayerList()
                .placeNewPlayer(connection, player, cookie);
        return player;
    }

    private static void cleanup(
            TestContext context,
            KraftWorkItemProjectile projectile,
            String message) {
        projectile.discard();
        context.stand().discard();
        context.owner().connection.disconnect(
                Component.literal(message));
    }

    private record TestContext(
            ServerPlayer owner,
            StandPower power,
            StandEntity stand) {}

    // a mock player that also keeps the action-bar messages it is sent
    private static final class RecordingPlayer extends ServerPlayer {
        private final List<Component> actionBar = new ArrayList<>();

        private RecordingPlayer(MinecraftServer server, ServerLevel level,
                GameProfile profile, ClientInformation information) {
            super(server, level, profile, information);
        }

        @Override
        public void displayClientMessage(Component message,
                boolean actionBar) {
            if (actionBar) {
                this.actionBar.add(message);
            }
            super.displayClientMessage(message, actionBar);
        }

        private List<String> conditionMessages() {
            return actionBar.stream()
                    .map(message -> message.getContents()
                                    instanceof TranslatableContents contents
                            ? contents.getKey()
                            : message.getString())
                    .filter(key -> key.startsWith(CONDITION_MESSAGE))
                    .toList();
        }
    }
}
