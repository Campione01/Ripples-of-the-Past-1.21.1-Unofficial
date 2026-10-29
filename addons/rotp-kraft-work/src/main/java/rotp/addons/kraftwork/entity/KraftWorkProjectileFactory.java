package rotp.addons.kraftwork.entity;

import rotp.core.customobjects.entity_projectile.BladeHatEntity;
import rotp.core.customobjects.entity_projectile.ClackersEntity;
import rotp.core.customobjects.entity_projectile.KnifeEntity;
import rotp.core.customobjects.entity_projectile.MolotovEntity;
import rotp.core.customobjects.entity_projectile.TommyGunBulletEntity;
import rotp.core.init.ModItems;
import rotp.core.init.ModSoundEvents;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;

import rotp.addons.kraftwork.KraftWorkAddon;
import rotp.addons.kraftwork.state.KraftWorkLockService;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.tags.StructureTags;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.EyeOfEnder;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.SmallFireball;
import net.minecraft.world.entity.projectile.Snowball;
import net.minecraft.world.entity.projectile.ThrownEgg;
import net.minecraft.world.entity.projectile.ThrownEnderpearl;
import net.minecraft.world.entity.projectile.ThrownExperienceBottle;
import net.minecraft.world.entity.projectile.ThrownPotion;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ThrowablePotionItem;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.Vec3;

public final class KraftWorkProjectileFactory {
    private KraftWorkProjectileFactory() {}

    public static boolean isUseless(ItemStack source) {
        return source.getItem() instanceof DyeItem
                || source.is(Items.BONE_MEAL)
                || source.is(Items.GUNPOWDER)
                || source.is(Items.SUGAR)
                || source.is(Items.REDSTONE)
                || source.is(Items.GLOWSTONE_DUST)
                || source.is(Items.BLAZE_POWDER);
    }

    // 1.16 KraftWorkPlaceProjectile.projectileList; any other off-hand
    // stack, an empty one too, reads as Place Item.
    public static boolean isProjectileItem(
            ItemStack item, LivingEntity user) {
        return item.getItem() instanceof ArrowItem
                || item.getItem() instanceof ThrowablePotionItem
                || item.is(ModItems.KNIFE)
                || item.is(ModItems.CLACKERS) && !item.isEnchanted()
                || item.is(ModItems.MOLOTOV)
                        && user.getMainHandItem().is(Items.FLINT_AND_STEEL)
                || item.is(ModItems.BLADE_HAT)
                || item.is(Items.TRIDENT)
                || item.is(Items.IRON_NUGGET)
                || item.is(Items.FIRE_CHARGE)
                || item.is(Items.FIREWORK_ROCKET)
                || item.is(Items.ENDER_PEARL)
                || item.is(Items.SNOWBALL)
                || item.is(Items.EGG)
                || item.is(Items.EXPERIENCE_BOTTLE);
    }

    // 1.16 KraftWorkPlaceProjectile.placeProjectile took what it threw from
    // the off hand, outside creative (:557-559)
    public static int placeHeld(
            ServerLevel level,
            LivingEntity user,
            StandEntity controller,
            ItemStack held) {
        if (held.isEmpty()) {
            return 0;
        }
        int taken = placeFrom(level, user, controller, held).taken();
        if (!(user instanceof Player player)
                || !player.getAbilities().instabuild) {
            held.shrink(taken);
        }
        return taken;
    }

    public static boolean place(
            ServerLevel level,
            LivingEntity user,
            StandEntity controller,
            ItemStack source) {
        return !source.isEmpty()
                && placeFrom(level, user, controller, source).placed() > 0;
    }

    // 1.16 :289-291: from Resolve 3 an unsneaking user threw up to 8 knives
    // of the stack at once (getStaminaCost :566-574 asked 10 for each)
    public static int itemsToPlace(LivingEntity user, ItemStack item) {
        return item.is(ModItems.KNIFE) && throwsKnivesInFlight(user)
                ? Math.min(item.getCount(), 8)
                : 1;
    }

    private record Placement(int taken, int placed) {}

    private static Placement placeFrom(
            ServerLevel level,
            LivingEntity user,
            StandEntity controller,
            ItemStack source) {
        // the owner's call (2026-10-24, and the coordinator's for the third
        // refusal): a trident that does not go (Riptide out of water and
        // rain, or pressed in the first 10 ticks of another item's use) and
        // an eye of ender with no stronghold stay with their user, where 1.16
        // placed nothing and still took them (:557-559)
        if (!tridentGoes(user, source)) {
            return new Placement(0, 0);
        }
        if (source.is(Items.ENDER_EYE)) {
            int placed =
                    placeEye(level, user, source.copyWithCount(1)) ? 1 : 0;
            return new Placement(placed, placed);
        }
        int count = itemsToPlace(user, source);
        int placed = 0;
        for (int index = 0; index < count; index++) {
            if (placeOne(level, user, controller,
                    source.copyWithCount(1), index)) {
                placed++;
            }
        }
        if (placed == 0) {
            return new Placement(0, 0);
        }
        // 1.16 :311-313, at volume 0 as 1.16 played it
        if (source.is(ModItems.KNIFE) && throwsKnivesInFlight(user)) {
            level.playSound(null, user.getX(), user.getY(), user.getZ(),
                    count == 1
                            ? ModSoundEvents.KNIFE_THROW.get()
                            : ModSoundEvents.KNIVES_THROW.get(),
                    SoundSource.PLAYERS, 0.0F,
                    0.4F / (level.random.nextFloat() * 0.4F + 0.8F));
        }
        int cooldown = cooldown(source, count);
        if (user instanceof Player player && cooldown > 0) {
            player.getCooldowns().addCooldown(source.getItem(), cooldown);
        }
        return new Placement(count, placed);
    }

    private static boolean placeOne(
            ServerLevel level,
            LivingEntity user,
            StandEntity controller,
            ItemStack one,
            int index) {
        Projectile projectile = create(
                level, user, controller, one);
        if (projectile == null) {
            return false;
        }
        projectile.setOwner(user);
        projectile.setPos(placedPosition(user, projectile));
        projectile.shootFromRotation(
                user,
                user.getXRot(),
                user.getYRot(),
                0.0F,
                initialSpeed(level, projectile, one),
                inaccuracy(projectile, index));
        // 1.16 :260, :304, :340, :355: a sixth of its base damage; a placed
        // item takes its own (KraftWorkItemProjectile, :530)
        if (projectile instanceof AbstractArrow arrow
                && !(projectile instanceof KraftWorkItemProjectile)) {
            arrow.setBaseDamage(arrow.getBaseDamage() / 6.0D);
        }
        boolean inFlight = projectile instanceof KnifeEntity
                && throwsKnivesInFlight(user);
        level.addFreshEntity(projectile);
        if (!(inFlight
                ? KraftWorkLockService.lockWholeAfterFlight(
                        user, projectile, 1)
                : KraftWorkLockService.lockWhole(user, projectile))) {
            projectile.discard();
            return false;
        }
        if (user instanceof Player player && awardsUseStat(projectile)) {
            player.awardStat(Stats.ITEM_USED.get(one.getItem()));
        }
        // 1.16 :331-334: the flint and steel took a use and the bottle
        // whooshed
        if (projectile instanceof MolotovEntity) {
            user.getMainHandItem().hurtAndBreak(1, user,
                    LivingEntity.getSlotForHand(user.getUsedItemHand()));
            if (!user.isSilent()) {
                user.playSound(ModSoundEvents.MOLOTOV_THROW.get(), 0.5F,
                        0.4F / (level.random.nextFloat() * 0.4F + 0.8F));
            }
        }
        return true;
    }

    // 1.16 :486, :539-559: an eye of ender went as an EyeOfEnderEntity from
    // the user's middle to the nearest stronghold within 100, as the
    // vanilla eye, with its trigger, use, 3 ticks and level event 1003;
    // with none nothing went
    private static boolean placeEye(
            ServerLevel level, LivingEntity user, ItemStack one) {
        BlockPos stronghold = level.findNearestMapStructure(
                StructureTags.EYE_OF_ENDER_LOCATED, user.blockPosition(),
                100, false);
        return stronghold != null
                && placeEyeTowards(level, user, one, stronghold);
    }

    public static boolean placeEyeTowards(ServerLevel level,
            LivingEntity user, ItemStack one, BlockPos stronghold) {
        EyeOfEnder eye = new EyeOfEnder(
                level, user.getX(), user.getY(0.5D), user.getZ());
        eye.setItem(one);
        eye.signalTo(stronghold);
        level.addFreshEntity(eye);
        if (!KraftWorkLockService.lockWhole(user, eye)) {
            eye.discard();
            return false;
        }
        if (user instanceof ServerPlayer player) {
            CriteriaTriggers.USED_ENDER_EYE.trigger(player, stronghold);
        }
        if (user instanceof Player player) {
            player.awardStat(Stats.ITEM_USED.get(one.getItem()));
            player.getCooldowns().addCooldown(one.getItem(), 3);
        }
        level.levelEvent(null, 1003, user.blockPosition(), 0);
        return true;
    }

    // 1.16 :350: a trident went with 10 ticks of its use, and with Riptide
    // only in water or rain; else nothing went
    private static boolean tridentGoes(LivingEntity user, ItemStack item) {
        return !item.is(Items.TRIDENT)
                || item.getUseDuration(user)
                                - user.getUseItemRemainingTicks() >= 10
                        && !riptideOutOfWater(user, item);
    }

    private static boolean riptideOutOfWater(
            LivingEntity user, ItemStack item) {
        return item.is(Items.TRIDENT)
                && EnchantmentHelper.getTridentSpinAttackStrength(
                        item, user) > 0.0F
                && !user.isInWaterOrRain();
    }

    // 1.16 :267-553: 3 ticks, 3 a knife (:314-317), 5 for a trident (:363),
    // 10 for a fire charge or a firework (:399, :422), 20 for an ender pearl
    // (:438); clackers set none (:402-411)
    private static int cooldown(ItemStack item, int count) {
        if (item.is(ModItems.KNIFE)) {
            return 3 * count;
        }
        if (item.is(ModItems.CLACKERS) && !item.isEnchanted()) {
            return 0;
        }
        if (item.is(Items.TRIDENT)) {
            return 5;
        }
        if (item.is(Items.FIRE_CHARGE) || item.is(Items.FIREWORK_ROCKET)) {
            return 10;
        }
        return item.is(Items.ENDER_PEARL) ? 20 : 3;
    }

    // 1.16 :328, :364, :439, :454, :469 counted a use of the item
    private static boolean awardsUseStat(Projectile projectile) {
        return projectile instanceof ThrownEnderpearl
                || projectile instanceof Snowball
                || projectile instanceof ThrownEgg
                || projectile instanceof ThrownTrident
                || projectile instanceof MolotovEntity;
    }

    // 1.16 :303: the knives after the first spread with inaccuracy 16; the
    // nugget's bullet went straight (:373), the blade hat with 0.5 (:339)
    private static float inaccuracy(Projectile projectile, int index) {
        if (projectile instanceof TommyGunBulletEntity) {
            return 0.0F;
        }
        if (projectile instanceof BladeHatEntity) {
            return 0.5F;
        }
        return projectile instanceof KnifeEntity && index > 0
                ? 16.0F
                : 1.0F;
    }

    // 1.16 KraftWorkPlaceProjectile.placeProjectile set each kind at
    // getY(0.5) + 0.5, moved along the view's x and z only: an arrow 0.75
    // (:258), a fire charge 0.8 (:384, :391), the blade hat and the trident
    // 0.75 along z alone (:338, :353), the rest and a placed item 0.5 (:276,
    // :528). Molotovs, clackers and the knives thrown unsneaking from
    // Resolve 3 kept their constructor's spot (:294-300, :320, :403).
    private static Vec3 placedPosition(
            LivingEntity user, Projectile projectile) {
        if (projectile instanceof MolotovEntity
                || projectile instanceof ClackersEntity
                || projectile instanceof KnifeEntity
                        && throwsKnivesInFlight(user)) {
            return new Vec3(user.getX(), user.getEyeY() - 0.1F,
                    user.getZ());
        }
        Vec3 view = user.getViewVector(1.0F);
        double y = user.getY(0.5D) + 0.5D;
        if (projectile instanceof BladeHatEntity
                || projectile instanceof ThrownTrident) {
            return new Vec3(user.getX(), y,
                    user.getZ() + view.z * 0.75D);
        }
        double forward = projectile instanceof SmallFireball
                ? 0.3F + 0.5D
                : projectile instanceof AbstractArrow
                                && !(projectile instanceof KnifeEntity)
                                && !(projectile
                                        instanceof KraftWorkItemProjectile)
                        ? 0.75D
                        : 0.5D;
        return new Vec3(user.getX() + view.x * forward, y,
                user.getZ() + view.z * forward);
    }

    private static boolean throwsKnivesInFlight(LivingEntity user) {
        StandPower power = StandPower.get(user);
        return !user.isShiftKeyDown()
                && power != null
                && power.getResolveLevel() >= 3;
    }

    private static Projectile create(
            ServerLevel level,
            LivingEntity user,
            StandEntity controller,
            ItemStack item) {
        // 1.16 placed whatever its projectileList left out, a drinkable
        // potion, enchanted clackers or a molotov with no flint and steel
        // in the main hand, as a plain item (:250, :486-538)
        if (!isProjectileItem(item, user)) {
            return placedItem(level, user, controller, item);
        }
        if (item.getItem() instanceof ArrowItem arrowItem) {
            AbstractArrow arrow = arrowItem.createArrow(
                    level, item, user, null);
            arrow.pickup = user instanceof Player player
                    && player.getAbilities().instabuild
                            ? AbstractArrow.Pickup.CREATIVE_ONLY
                            : AbstractArrow.Pickup.ALLOWED;
            return arrow;
        }
        if (item.getItem() instanceof ThrowablePotionItem) {
            ThrownPotion potion = new ThrownPotion(level, user);
            potion.setItem(item);
            return potion;
        }
        if (item.is(Items.TRIDENT)) {
            // 1.16 :351: the trident placed took a use
            item.hurtAndBreak(1, level,
                    user instanceof ServerPlayer player ? player : null,
                    broken -> user.onEquippedItemBroken(broken,
                            LivingEntity.getSlotForHand(
                                    user.getUsedItemHand())));
            return new ThrownTrident(level, user, item);
        }
        if (item.is(Items.FIRE_CHARGE)) {
            return new SmallFireball(
                    level, user, user.getLookAngle());
        }
        // 1.16 :413: shot at an angle, so released it flies on along its
        // shot; the user-attached rocket would be pulled back to the user
        if (item.is(Items.FIREWORK_ROCKET)) {
            return new FireworkRocketEntity(level, item, user, user.getX(),
                    user.getEyeY() - 0.15F, user.getZ(), true);
        }
        if (item.is(Items.ENDER_PEARL)) {
            return new ThrownEnderpearl(level, user);
        }
        if (item.is(Items.SNOWBALL)) {
            Snowball snowball = new Snowball(level, user);
            snowball.setItem(item);
            return snowball;
        }
        if (item.is(Items.EGG)) {
            ThrownEgg egg = new ThrownEgg(level, user);
            egg.setItem(item);
            return egg;
        }
        if (item.is(Items.EXPERIENCE_BOTTLE)) {
            ThrownExperienceBottle bottle =
                    new ThrownExperienceBottle(level, user);
            bottle.setItem(item);
            return bottle;
        }
        if (item.is(ModItems.KNIFE)) {
            return new KnifeEntity(level, user, item);
        }
        if (item.is(ModItems.MOLOTOV)) {
            return new MolotovEntity(level, user, item);
        }
        if (item.is(ModItems.BLADE_HAT)) {
            return new BladeHatEntity(level, user, item);
        }
        if (item.is(ModItems.CLACKERS)) {
            return new ClackersEntity(level, user, item);
        }
        // 1.16 :370-382: the nugget went as a Tommy gun bullet
        if (item.is(Items.IRON_NUGGET)) {
            return new TommyGunBulletEntity(user, level);
        }
        return placedItem(level, user, controller, item);
    }

    private static KraftWorkItemProjectile placedItem(
            ServerLevel level,
            LivingEntity user,
            StandEntity controller,
            ItemStack item) {
        KraftWorkItemProjectile generic =
                new KraftWorkItemProjectile(
                        KraftWorkAddon.KW_ITEM.get(), level);
        generic.initialize(user, controller, item);
        return generic;
    }

    // 1.16 shot a molotov, an egg and an experience bottle at 0.75 (:322,
    // :461, :476) and a trident at 2.5 + Riptide x 0.5 (:354), the rest at
    // 1.5; the held shot is what Release turns into its direction
    private static float initialSpeed(
            ServerLevel level, Projectile projectile, ItemStack one) {
        if (projectile instanceof MolotovEntity
                || projectile instanceof ThrownEgg
                || projectile instanceof ThrownExperienceBottle) {
            return 0.75F;
        }
        if (projectile instanceof ThrownTrident) {
            return 2.5F + one.getEnchantmentLevel(level.registryAccess()
                    .lookupOrThrow(Registries.ENCHANTMENT)
                    .getOrThrow(Enchantments.RIPTIDE)) * 0.5F;
        }
        return 1.5F;
    }
}
