package rotp.addons.ultimate_lifeform;

import java.util.List;
import java.util.Locale;

import rotp.core.api.timestop.TimeStopLifecycleEvent;
import rotp.core.customobjects.entity_projectile.ModdedProjectileEntity;
import rotp.core.init.ModDamageTypes;
import rotp.core.init.ModSoundEvents;
import rotp.core.init.ModStatusEffects;
import rotp.core.init.power.ModPlayerPowers;
import rotp.core.mechanics.JojoDefinitions;
import rotp.core.network.s2c.TrPillarmanParticlesPacket;
import rotp.core.powersystem.playerpower.PlayerPower;
import rotp.core.powersystem.standpower.StandUtil;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.util.functions.DamageUtil;
import rotp.core.impl.powers.pillarman.PillarmanData;
import rotp.core.impl.powers.pillarman.PillarmanMode;
import rotp.addons.ultimate_lifeform.block.UltimateStoneMaskBlockEntity;
import rotp.addons.ultimate_lifeform.config.UltimateLifeformConfig;
import rotp.addons.ultimate_lifeform.entity.UltimatePhantomEntity;
import rotp.addons.ultimate_lifeform.entity.UltimateRabbitEntity;
import rotp.addons.ultimate_lifeform.item.UltimateStoneMaskItem;
import rotp.addons.ultimate_lifeform.power.UltimateLifeformData;
import rotp.addons.ultimate_lifeform.power.UltimateLifeformPowerType;
import rotp.addons.ultimate_lifeform.power.UltimateLifeformSpec;
import com.mojang.brigadier.arguments.StringArgumentType;

import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.ChorusFlowerBlock;
import net.minecraft.world.level.block.ChorusPlantBlock;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.CoralFanBlock;
import net.minecraft.world.level.block.CoralPlantBlock;
import net.minecraft.world.level.block.CoralWallFanBlock;
import net.minecraft.world.level.block.GrowingPlantBlock;
import net.minecraft.world.level.block.NetherSproutsBlock;
import net.minecraft.world.level.block.RootsBlock;
import net.minecraft.world.level.block.SugarCaneBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

@EventBusSubscriber(
        modid = UltimateLifeformAddon.MOD_ID,
        bus = EventBusSubscriber.Bus.GAME)
public final class UltimateLifeformGameEvents {
    private static final List<
            net.neoforged.neoforge.registries.DeferredHolder<
                    Item, ? extends Item>>
            ESSENCES = List.of(
                    UltimateLifeformAddon.SOUL_ESSENCE,
                    UltimateLifeformAddon.FIRE_ESSENCE,
                    UltimateLifeformAddon.EARTH_ESSENCE,
                    UltimateLifeformAddon.WATER_ESSENCE,
                    UltimateLifeformAddon.LIFE_ESSENCE,
                    UltimateLifeformAddon.DEATH_ESSENCE,
                    UltimateLifeformAddon.SUN_ESSENCE);
    // data/rotp_ul/damage_type/ulf_fly_too_high.json and its damage type tags
    public static final ResourceKey<DamageType> ULF_FLY_TOO_HIGH =
            ResourceKey.create(Registries.DAMAGE_TYPE,
                    UltimateLifeformAddon.id("ulf_fly_too_high"));

    private UltimateLifeformGameEvents() {}

    @SubscribeEvent
    public static void preserveLevitationWhileStunned(
            EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof LivingEntity entity)
                || !ModStatusEffects.isStunned(entity)) {
            return;
        }
        MobEffectInstance levitation =
                entity.getEffect(MobEffects.LEVITATION);
        if (levitation == null) {
            return;
        }

        double clampedVerticalSpeed =
                Math.min(entity.getDeltaMovement().y, 0.0D);
        double levitationSpeed =
                (0.05D * (levitation.getAmplifier() + 1)
                                - clampedVerticalSpeed)
                        * 0.2D;
        entity.setDeltaMovement(0.0D, levitationSpeed, 0.0D);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onIncomingDamage(
            LivingIncomingDamageEvent event) {
        LivingEntity target = event.getEntity();
        UltimateLifeformData data = data(target);
        if (data == null || target.level().isClientSide()) {
            applySunEssenceMultiplier(event);
            return;
        }

        if (event.getSource().is(DamageTypes.FELL_OUT_OF_WORLD)) {
            event.setAmount(event.getAmount() * 3.0F);
        }
        applySunEssenceMultiplier(event);
    }

    /*
     * 1.16 GameplayEventHandler.cancelDamage on LivingAttackEvent, posted at
     * the head of PlayerEntity.hurt (before the creative and Fire Resistance
     * checks), ran UltimateLifeformUltimateAgility.onUserAttacked (Evasion and
     * Agility) and UltimateLifeformBladeBarrage.onUserAttacked; the core's
     * cancelLivingAttack there asked the running ContinuousActionInstance:
     * the Blade Dash cancelled every hit, the kick every melee hit (its
     * living attacker the direct entity, DamageUtil.isMeleeAttack). Called
     * from PlayerUltimateFireArmorMixin after the fire armor.
     */
    public static boolean cancelHeldMoveHit(
            Player player, DamageSource source) {
        if (player.level().isClientSide()) {
            return false;
        }
        UltimateLifeformData data = data(player);
        if (data == null) {
            return false;
        }
        UltimateLifeformSpec held = data.getHeldAbility();
        if (held == UltimateLifeformSpec.BLADE_DASH_ATTACK) {
            return true;
        }
        if (held == UltimateLifeformSpec.ULTIMATE_HAMON_KICK) {
            return DamageUtil.getMeleeAttacker(source) != null;
        }
        Entity direct = source.getDirectEntity();
        if (held == UltimateLifeformSpec.EVASION
                || held == UltimateLifeformSpec.ULTIMATE_AGILITY) {
            return evades(player, held, source, direct);
        }
        if (held == UltimateLifeformSpec.BLADE_BARRAGE) {
            return deflects(player, direct);
        }
        return false;
    }

    // 1.16 UltimateLifeformUltimateAgility.onUserAttacked (:58-82): only
    // explosions got through, also a hit that bypasses invulnerability
    private static boolean evades(Player target, UltimateLifeformSpec held,
            DamageSource source, Entity direct) {
        if (source.is(DamageTypeTags.IS_EXPLOSION)
                || !(direct instanceof LivingEntity
                        || direct instanceof Projectile)) {
            return false;
        }
        if (direct instanceof StandEntity
                && !StandUtil.entityCanSeeStands(target)) {
            return false;
        }
        if (direct instanceof ModdedProjectileEntity projectile) {
            return projectile.canBeEvaded(target)
                    && (!projectile.standDamage()
                            || StandUtil.entityCanSeeStands(target));
        }
        // the counter hit the direct attacker, never an arrow's shooter
        if (held == UltimateLifeformSpec.ULTIMATE_AGILITY
                && direct instanceof LivingEntity livingAttacker
                && !(livingAttacker instanceof StandEntity)
                && target.getRandom().nextFloat() < 0.3F) {
            livingAttacker.hurt(
                    target.damageSources().playerAttack(target),
                    DamageUtil.getDamageWithoutHeldItem(target) * 0.75F);
        }
        target.level().playSound(null, direct,
                ModSoundEvents.PILLAR_MAN_EVASION.get(),
                direct.getSoundSource(), 1.0F, 1.0F);
        return true;
    }

    /*
     * 1.16 UltimateLifeformBladeBarrage.onUserAttacked (:126-150): a modded
     * projectile by its own rule; a vanilla projectile hit only while a
     * projectile within reach came from the front (48.75 degrees around the
     * look), which sparked and rang unless it lay on the ground.
     */
    private static boolean deflects(Player target, Entity direct) {
        if (direct instanceof ModdedProjectileEntity projectile) {
            return projectile.canBeEvaded(target) && !projectile.standDamage();
        }
        if (!(direct instanceof Projectile)) {
            return false;
        }
        // 1.16 REACH_DISTANCE was 5 for every game mode
        double reach = target.blockInteractionRange()
                + (target.getAbilities().instabuild ? 0.0D : 0.5D);
        double minCos = Math.cos(Math.toRadians(48.75D));
        boolean deflected = false;
        for (Projectile projectile : target.level().getEntitiesOfClass(
                Projectile.class,
                target.getBoundingBox().inflate(reach),
                entity -> entity.isAlive() && !entity.isPickable())) {
            if (target.getLookAngle().dot(projectile.getDeltaMovement()
                            .reverse().normalize()) < minCos) {
                continue;
            }
            deflected = true;
            if (!projectile.onGround()) {
                TrPillarmanParticlesPacket.send(projectile,
                        new TrPillarmanParticlesPacket(projectile.getId(), 12));
                target.level().playSound(null, projectile.getX(),
                        projectile.getY(), projectile.getZ(),
                        SoundEvents.ANVIL_LAND, projectile.getSoundSource(),
                        0.4F, 1.35F);
            }
        }
        return deflected;
    }

    /*
     * 1.16 GameplayEventHandler.cancelDamage on LivingAttackEvent, which Forge
     * posted at the head of PlayerEntity.hurt, before the creative, dead and
     * Fire Resistance checks: while the fire armor effect was on, lava, hot
     * floor, in fire (campfires too) and on fire hits were counted and
     * cancelled; fireballs got through. Called from PlayerUltimateFireArmorMixin.
     */
    public static boolean cancelFireArmorHit(
            Player player, DamageSource source) {
        if (player.level().isClientSide()
                || !player.hasEffect(UltimateLifeformAddon.ULTIMATE_FIRE_ARMOR)
                || !(source.is(DamageTypes.LAVA)
                        || source.is(DamageTypes.HOT_FLOOR)
                        || source.is(DamageTypes.IN_FIRE)
                        || source.is(DamageTypes.CAMPFIRE)
                        || source.is(DamageTypes.ON_FIRE))) {
            return false;
        }
        UltimateLifeformData data = data(player);
        if (data == null) {
            return false;
        }
        data.addFireDamageCount();
        data.syncOnUpdate(player);
        return true;
    }

    private static void applySunEssenceMultiplier(
            LivingIncomingDamageEvent event) {
        if (!event.getSource().is(ModDamageTypes.HAMON)
                || !(event.getSource().getEntity()
                        instanceof Player attacker)
                || !hasItem(
                        attacker,
                        UltimateLifeformAddon.SUN_ESSENCE.get())) {
            return;
        }
        event.setAmount(event.getAmount() * 1.5F);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDamage(
            LivingDamageEvent.Pre event) {
        if (event.getNewDamage() <= 0.0F
                || event.getEntity().level().isClientSide()) {
            return;
        }
        LivingEntity target = event.getEntity();
        if (isStoneFormForPickaxe(target)
                && event.getSource().getDirectEntity()
                        instanceof LivingEntity attacker
                && attacker.getMainHandItem().getItem()
                        instanceof PickaxeItem) {
            // 1.16 removed Resistance on every matching hit, even a fully absorbed one.
            target.removeEffect(MobEffects.DAMAGE_RESISTANCE);
            // Legacy LivingDamage ran after absorption; NeoForge Pre runs before it.
            float absorbed = Math.min(
                    target.getAbsorptionAmount(), event.getNewDamage());
            float remaining = event.getNewDamage() - absorbed;
            if (remaining > 0.0F) {
                event.setNewDamage(absorbed + remaining * 5.0F);
            }
        }
        activateMasks(
                event.getEntity().level(),
                event.getEntity().blockPosition());
    }

    // 1.16 GameplayEventHandler.onLivingDamage gave the pickaxe weakness to
    // the Ultimate Lifeform stone form and to a core Pillar Man in stone form.
    private static boolean isStoneFormForPickaxe(LivingEntity target) {
        UltimateLifeformData data = data(target);
        if (data != null) {
            return data.isStoneForm();
        }
        return PlayerPower.getPowerData(
                target, ModPlayerPowers.PILLAR_MAN)
                .map(PillarmanData::isStoneFormEnabled)
                .orElse(false);
    }

    @SubscribeEvent
    public static void onPlayerTick(
            PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide()
                || !player.isAlive()) {
            return;
        }
        applyMaskConversion(player);
        applyEssencePassives(player);
        applyHeightLimit(player);
    }

    /*
     * 1.16 GameplayEventHandler.onPlayerTick: above Y 256 Freeze IV and 25
     * damage of CustomDamageSources.ULF_FLY_TOO_HIGH (bypassArmor and
     * bypassMagic, no source entity), with its own death message.
     */
    public static void applyHeightLimit(Player player) {
        if (data(player) != null && player.getY() > 256.0D) {
            player.addEffect(new MobEffectInstance(
                    ModStatusEffects.FREEZE,
                    100,
                    3));
            player.hurt(
                    DamageUtil.make(player.level(), ULF_FLY_TOO_HIGH),
                    25.0F);
        }
    }

    private static void applyMaskConversion(Player player) {
        ItemStack head = player.getInventory().armor.get(3);
        if (!head.is(
                        UltimateLifeformAddon
                                .STONE_MASK_UL_WITH_STONE.get())
                || UltimateStoneMaskItem.isConverting(head)) {
            return;
        }
        Level level = player.level();
        BlockPos pos = player.blockPosition();
        boolean fullDaylight =
                level.isDay()
                        && level.canSeeSky(pos)
                        && level.getMaxLocalRawBrightness(pos)
                                > 7
                        && !level.isRaining();
        if (!fullDaylight) {
            return;
        }
        PillarmanData pillarman = PlayerPower.getPowerData(
                player, ModPlayerPowers.PILLAR_MAN)
                .orElse(null);
        PlayerPower power = PlayerPower.get(player);
        if (pillarman == null
                || power == null
                || power.getPowerType()
                        != ModPlayerPowers.PILLAR_MAN.get()) {
            return;
        }
        PillarmanMode oldMode = pillarman.getMode();
        power.setPowerType(
                UltimateLifeformAddon.ULTIMATE_LIFEFORM.get());
        UltimateLifeformData ultimateData = data(player);
        if (ultimateData == null) {
            return;
        }
        ultimateData.setPreviousMode(player, oldMode);
        UltimateStoneMaskItem.beginAjaConversion(head);
        player.addEffect(new MobEffectInstance(
                ModStatusEffects.STUN,
                120,
                0));
        level.playSound(
                null,
                player,
                ModSoundEvents
                        .STONE_MASK_ACTIVATION_ENTITY.get(),
                player.getSoundSource(),
                1.0F,
                1.0F);
        level.playSound(
                null,
                player,
                ModSoundEvents.AJA_STONE_BEAM.get(),
                player.getSoundSource(),
                1.0F,
                1.0F);
    }

    private static void applyEssencePassives(Player player) {
        if (hasItem(
                        player,
                        UltimateLifeformAddon
                                .FIRE_ESSENCE.get())
                && !player.isEyeInFluid(FluidTags.LAVA)
                && !player.isOnFire()) {
            player.addEffect(hidden(
                    MobEffects.FIRE_RESISTANCE, 400, 0));
        }
        if (hasItem(
                        player,
                        UltimateLifeformAddon
                                .WATER_ESSENCE.get())
                && player.isEyeInFluid(FluidTags.WATER)) {
            player.addEffect(hidden(
                    MobEffects.WATER_BREATHING, 15, 0));
            player.addEffect(hidden(
                    MobEffects.DOLPHINS_GRACE, 15, 1));
        }
        if (hasItem(
                        player,
                        UltimateLifeformAddon.SUN_ESSENCE.get())
                && JojoDefinitions.isUndeadOrVampiric(player)) {
            player.addEffect(hidden(
                    ModStatusEffects.VAMPIRE_SUN_BURN,
                    40,
                    2));
            player.addEffect(hidden(
                    MobEffects.WITHER, 40, 0));
        }
        boolean holdsDeath =
                player.getMainHandItem().is(
                        UltimateLifeformAddon
                                .DEATH_ESSENCE.get())
                        || player.getOffhandItem().is(
                                UltimateLifeformAddon
                                        .DEATH_ESSENCE.get());
        if (!holdsDeath) {
            return;
        }
        List<LivingEntity> nearby =
                player.level().getEntitiesOfClass(
                        LivingEntity.class,
                        player.getBoundingBox().inflate(8.0D));
        for (LivingEntity entity : nearby) {
            player.addEffect(hidden(
                    JojoDefinitions.isUndeadOrVampiric(entity)
                            ? MobEffects.POISON : MobEffects.WITHER,
                    40,
                    0));
        }
        // 1.16 ran doFractionTimes(4) from an unfiltered PlayerTickEvent,
        // which fires at START and END: 8 samples per server tick.
        for (int index = 0; index < 8; index++) {
            deathEssenceDecay(player);
        }
    }

    private static MobEffectInstance hidden(
            Holder<net.minecraft.world.effect.MobEffect> effect,
            int duration,
            int amplifier) {
        return new MobEffectInstance(
                effect,
                duration,
                amplifier,
                false,
                false,
                true);
    }

    private static void deathEssenceDecay(Player player) {
        BlockPos randomPos = BlockPos.containing(
                player.getRandomX(8.0D),
                player.getY((player.getRandom().nextDouble() * 2.0D - 1.0D) * 8.0D),
                player.getRandomZ(8.0D));
        BlockState state = player.level()
                .getBlockState(randomPos);
        if (state.is(Blocks.GRASS_BLOCK)) {
            player.level().setBlockAndUpdate(
                    randomPos, Blocks.DIRT.defaultBlockState());
        }
        else if (isDeathEssenceDecayPlant(state)) {
            player.level().setBlockAndUpdate(
                    randomPos, Blocks.AIR.defaultBlockState());
        }
    }

    // 1.16 decayed Material LEAVES, PLANT, REPLACEABLE_PLANT, WATER_PLANT
    // and REPLACEABLE_WATER_PLANT; its BlockTags comparison never matched.
    // LEAVES: the leaves tag. PLANT: saplings, flowers, mushrooms, crops,
    // stems, lily pad, nether wart, berry bush, fungi (BushBlock), weeping
    // and twisting vines (GrowingPlantBlock), sugar cane, cocoa, chorus.
    // REPLACEABLE_PLANT: grass, ferns, dead bush, tall plants, vine.
    // REPLACEABLE_WATER_PLANT: seagrass. WATER_PLANT: kelp, sea pickle and
    // live coral (dead coral was STONE). Roots and nether sprouts are
    // BushBlocks but were REPLACEABLE_FIREPROOF_PLANT, which 1.16 kept.
    private static boolean isDeathEssenceDecayPlant(BlockState state) {
        Block block = state.getBlock();
        if (block instanceof RootsBlock
                || block instanceof NetherSproutsBlock) {
            return false;
        }
        return state.is(BlockTags.LEAVES)
                || block instanceof BushBlock
                || block instanceof VineBlock
                || block instanceof GrowingPlantBlock
                || block instanceof SugarCaneBlock
                || block instanceof CocoaBlock
                || block instanceof ChorusPlantBlock
                || block instanceof ChorusFlowerBlock
                || block instanceof CoralPlantBlock
                || block instanceof CoralFanBlock
                || block instanceof CoralWallFanBlock;
    }

    // Projectiles are charged by UltimateHamonProjectileCharge.
    @SubscribeEvent
    public static void onPlayerJoin(
            EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()
                || !(event.getEntity()
                        instanceof ServerPlayer player)) {
            return;
        }
        PlayerPower power = PlayerPower.get(player);
        UltimateLifeformData flightData = data(player);
        if (power != null
                && power.getPowerType()
                        == UltimateLifeformAddon.ULTIMATE_LIFEFORM.get()
                && flightData != null
                && flightData.isFlying()
                && !player.hasEffect(UltimateLifeformAddon.ULTIMATE_FIRE_ARMOR)
                && !player.hasEffect(UltimateLifeformAddon.ULTIMATE_ARMOR)
                && player.isAlive()
                && !player.onGround()
                && player.isFallFlying()) {
            // Restore the transient lease before the first flight tick.
            UltimateLifeformPowerType.enableFlight(player);
        }
        restoreSummonsLater(player);
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(
            PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            UltimateLifeformData data = data(player);
            if (data != null) {
                data.clearNightVisionOnLogin(player);
                data.clearHealthDebuffOnLogin(player);
            }
        }
    }

    private static void restoreSummonsLater(
            ServerPlayer player) {
        player.getServer().execute(() -> {
            if (!player.isAlive()) {
                return;
            }
            UltimateLifeformData data = data(player);
            if (data == null
                    || !(player.level()
                            instanceof ServerLevel level)) {
                return;
            }
            int rabbits = data.getSummonedRabbitsCount();
            removeSummons(player);
            int rabbitLife =
                    data.getSummonedRabbitsLifetime();
            // 1.16 GameplayEventHandler.onSpawn: every counted summon came
            // back at a random spot of the user's box with its age, also one
            // that was already returning
            for (int index = 0; index < rabbits; index++) {
                UltimateRabbitEntity rabbit =
                        UltimateLifeformAddon
                                .ULTIMATE_RABBIT.get()
                                .create(level);
                if (rabbit != null) {
                    rabbit.moveTo(
                            player.getRandomX(1.0D),
                            player.getRandomY(),
                            player.getRandomZ(1.0D),
                            player.getYRot(),
                            0.0F);
                    rabbit.setSummonedBy(
                            player, rabbitLife);
                    level.addFreshEntity(rabbit);
                }
            }
            int phantoms = data.getSummonedPhantomsCount();
            int phantomLife =
                    data.getSummonedPhantomsLifetime();
            for (int index = 0; index < phantoms; index++) {
                UltimatePhantomEntity phantom =
                        UltimateLifeformAddon
                                .ULTIMATE_PHANTOM.get()
                                .create(level);
                if (phantom != null) {
                    phantom.moveTo(
                            player.getRandomX(1.0D),
                            player.getRandomY() + 1.0D,
                            player.getRandomZ(1.0D),
                            player.getYRot(),
                            0.0F);
                    phantom.setSummonedBy(
                            player, phantomLife);
                    level.addFreshEntity(phantom);
                }
            }
        });
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onDeath(LivingDeathEvent event) {
        LivingEntity user = event.getEntity();
        UltimateLifeformData data = data(user);
        if (data == null || user.level().isClientSide()) {
            return;
        }
        PlayerPower power = PlayerPower.get(user);
        boolean kept = power != null
                && UltimateLifeformAddon
                        .ULTIMATE_LIFEFORM.get()
                        .keepOnDeath(power);
        if (!kept) {
            for (var essence : ESSENCES) {
                if (user.getRandom().nextDouble()
                        <= UltimateLifeformConfig
                                .ESSENCE_DROP_CHANCE.get()) {
                    user.spawnAtLocation(
                            new ItemStack(essence.get()));
                }
            }
        }
        removeSummons(user);
        data.clearSummons();
        data.syncOnUpdate(user);
    }

    private static void removeSummons(LivingEntity owner) {
        if (owner.getServer() == null) {
            return;
        }
        for (ServerLevel level :
                owner.getServer().getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (entity instanceof UltimateRabbitEntity rabbit
                        && rabbit.isOwnedBy(owner.getUUID())) {
                    rabbit.discard();
                }
                else if (entity
                                instanceof UltimatePhantomEntity
                                        phantom
                        && phantom.isOwnedBy(owner.getUUID())) {
                    phantom.discard();
                }
            }
        }
    }

    @SubscribeEvent
    public static void onExplosion(
            ExplosionEvent.Detonate event) {
        for (Entity entity : event.getAffectedEntities()) {
            if (!(entity instanceof LivingEntity living)) {
                continue;
            }
            UltimateLifeformData data = data(living);
            if (data != null
                    && data.getEnergyRatio()
                            <= UltimateLifeformConfig
                                    .SKY_LAUNCH_ENERGY_PERCENT
                                    .get()
                                    / 100.0F) {
                living.addEffect(new MobEffectInstance(
                        MobEffects.LEVITATION,
                        200,
                        100,
                        false,
                        false,
                        false));
            }
            else if (living instanceof IronGolem
                    && living.getHealth()
                                    / living.getMaxHealth()
                            <= UltimateLifeformConfig
                                    .SKY_LAUNCH_ENERGY_PERCENT
                                    .get()) {
                // Preserve the legacy percent-vs-ratio bug.
                living.addEffect(new MobEffectInstance(
                        MobEffects.LEVITATION,
                        200,
                        100,
                        false,
                        false,
                        false));
            }
        }
    }

    @SubscribeEvent
    public static void onEarthEssenceDrops(
            BlockDropsEvent event) {
        if (!(event.getBreaker() instanceof Player player)
                || !(event.getLevel()
                        instanceof ServerLevel level)
                || !hasItem(
                        player,
                        UltimateLifeformAddon
                                .EARTH_ESSENCE.get())) {
            return;
        }
        ItemStack tool = event.getTool().copy();
        Holder<Enchantment> fortune = level.registryAccess()
                .lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(Enchantments.FORTUNE);
        // 1.16 appended Fortune max(2, level + 2) to the loot tool, but its
        // getItemEnchantmentLevel read the first Fortune entry, and 0 for an
        // empty hand: only a tool without Fortune mined as Fortune II. The
        // empty hand is skipped before writing; its copy() is shared EMPTY.
        if (tool.isEmpty()
                || EnchantmentHelper.getItemEnchantmentLevel(
                                fortune, tool) > 0) {
            return;
        }
        ItemEnchantments.Mutable enchantments =
                new ItemEnchantments.Mutable(tool.getOrDefault(
                        DataComponents.ENCHANTMENTS,
                        ItemEnchantments.EMPTY));
        enchantments.set(fortune, 2);
        tool.set(
                DataComponents.ENCHANTMENTS,
                enchantments.toImmutable());
        List<ItemStack> boosted = Block.getDrops(
                event.getState(),
                level,
                event.getPos(),
                event.getBlockEntity(),
                player,
                tool);
        event.getDrops().clear();
        for (ItemStack stack : boosted) {
            event.getDrops().add(new ItemEntity(
                    level,
                    event.getPos().getX() + 0.5D,
                    event.getPos().getY() + 0.5D,
                    event.getPos().getZ() + 0.5D,
                    stack));
        }
    }

    @SubscribeEvent
    public static void onTimeStopPreStart(
            TimeStopLifecycleEvent.PreStart event) {
        Entity user = event.getLevel().getEntity(
                event.getInstance().userId());
        if (!(user instanceof LivingEntity living)
                || data(living) == null) {
            return;
        }
        int ticksLeft = Math.min(
                TimeStopLifecycleEvent.MAX_TOTAL_TICKS,
                Math.max(
                        1,
                        Math.round(
                                event.getInstance().ticksLeft()
                                        * 1.25F)));
        int totalTicks = Math.min(
                TimeStopLifecycleEvent.MAX_TOTAL_TICKS,
                Math.max(
                        ticksLeft,
                        Math.round(
                                event.getInstance().totalTicks()
                                        * 1.25F)));
        event.setInstance(
                event.getInstance().withTiming(
                        ticksLeft, totalTicks));
    }

    @SubscribeEvent
    public static void registerCommands(
            RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("rotp_ul")
                        .requires(source ->
                                source.hasPermission(2))
                        .then(Commands.literal("mode")
                                .then(Commands.argument(
                                                "mode",
                                                StringArgumentType
                                                        .word())
                                        .suggests((context, builder) ->
                                                net.minecraft.commands
                                                        .SharedSuggestionProvider
                                                        .suggest(
                                                                List.of(
                                                                        "none",
                                                                        "light",
                                                                        "wind",
                                                                        "heat"),
                                                                builder))
                                        .executes(context -> {
                                            ServerPlayer player =
                                                    context
                                                            .getSource()
                                                            .getPlayerOrException();
                                            UltimateLifeformData data =
                                                    data(player);
                                            if (data == null) {
                                                context.getSource()
                                                        .sendFailure(
                                                                Component
                                                                        .literal(
                                                                                "The player is not an Ultimate Lifeform."));
                                                return 0;
                                            }
                                            String name =
                                                    StringArgumentType
                                                            .getString(
                                                                    context,
                                                                    "mode")
                                                            .toUpperCase(
                                                                    Locale.ROOT);
                                            PillarmanMode mode;
                                            try {
                                                mode = PillarmanMode
                                                        .valueOf(name);
                                            }
                                            catch (IllegalArgumentException
                                                    ignored) {
                                                context.getSource()
                                                        .sendFailure(
                                                                Component
                                                                        .literal(
                                                                                "Mode must be none, light, wind, or heat."));
                                                return 0;
                                            }
                                            data.setPreviousMode(
                                                    player, mode);
                                            context.getSource()
                                                    .sendSuccess(
                                                            () -> Component
                                                                    .literal(
                                                                            "Ultimate Lifeform mode: "
                                                                                    + mode.name()
                                                                                            .toLowerCase(
                                                                                                    Locale.ROOT)),
                                                            false);
                                            return 1;
                                        }))));
    }

    private static void activateMasks(
            Level level, BlockPos center) {
        for (BlockPos pos : BlockPos.betweenClosed(
                center.offset(-2, -2, -2),
                center.offset(2, 2, 2))) {
            BlockState state = level.getBlockState(pos);
            if (!state.is(
                    UltimateLifeformAddon
                            .UL_STONE_MASK_NOSTONE.get())) {
                continue;
            }
            if (state.getValue(
                            BlockStateProperties.ATTACH_FACE)
                    == AttachFace.FLOOR
                    && level.getBlockEntity(pos)
                            instanceof UltimateStoneMaskBlockEntity
                                    mask) {
                mask.activate();
            }
            else {
                if (level.getBlockEntity(pos)
                        instanceof UltimateStoneMaskBlockEntity
                                mask) {
                    Block.popResource(
                            level, pos, mask.getStack());
                }
                level.removeBlock(pos, false);
            }
        }
    }

    // 1.16 PlayerInventory.contains matched the item alone (sameItem), so a
    // renamed essence still counted; 1.21 contains(ItemStack) also compares
    // data components.
    private static boolean hasItem(
            Player player, Item item) {
        return player.getInventory().contains(
                stack -> stack.is(item));
    }

    private static UltimateLifeformData data(
            LivingEntity entity) {
        return PlayerPower.getPowerData(
                entity,
                UltimateLifeformAddon.ULTIMATE_LIFEFORM)
                .orElse(null);
    }
}
