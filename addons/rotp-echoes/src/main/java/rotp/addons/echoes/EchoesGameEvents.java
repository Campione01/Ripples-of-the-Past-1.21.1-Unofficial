package rotp.addons.echoes;

import rotp.core.powersystem.standpower.StandPower;

import rotp.addons.echoes.init.EchoesAttachments;
import rotp.addons.echoes.state.EchoesChatService;
import rotp.addons.echoes.state.EchoesGravityService;
import rotp.addons.echoes.state.EchoesUserState;
import rotp.addons.echoes.state.GravityMarkSavedData;
import rotp.addons.echoes.state.GravityMarkSavedData.Mark;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = RotpEchoes.MOD_ID)
public final class EchoesGameEvents {
    private static final double AUTO_RANGE = 4.0D;

    private EchoesGameEvents() {}

    @SubscribeEvent
    public static void registerCommands(RegisterCommandsEvent event) {
        EchoesCommands.register(event);
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        ServerLevel overworld = event.getServer().overworld();
        if (overworld.getGameTime() % 20L != 0L) {
            return;
        }
        for (ServerLevel level : event.getServer().getAllLevels()) {
            GravityMarkSavedData.get(level).prune(level.getGameTime());
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            // 092 carrier drag also ran client-side, where motion is decided
            if (event.getEntity().isLocalPlayer()) {
                EchoesGravityService.tickLocalCarrier(event.getEntity());
            }
            return;
        }
        EchoesGravityService.tickInventoryGravity(player);
        if (player.hasData(EchoesAttachments.USER_STATE)) {
            EchoesGravityService.tickUser(player);
        }
        if (!RotpEchoes.isEchoesUser(player)
                || player.getAbilities().instabuild) {
            return;
        }
        EchoesChatService.tickPassive(player);
        if (player.tickCount % 10 == 0) {
            tryAutoAttack(player);
        }
    }

    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Post event) {
        Entity entity = event.getEntity();
        if (entity.level().isClientSide()) {
            return;
        }
        if (entity instanceof ItemEntity item) {
            EchoesGravityService.tickInfusedItem(item);
        }
        if (entity instanceof LivingEntity living
                && living.hasData(EchoesAttachments.FREEZE_STATE)) {
            EchoesGravityService.tickFrozenTarget(living);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (EchoesGravityService.isHandFrozen(event.getPlayer())) {
            event.setCanceled(true);
            return;
        }
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        BlockPos position = event.getPos();
        GravityMarkSavedData marks = GravityMarkSavedData.get(level);
        Mark mark = marks.mark(position, level.getGameTime());
        if (mark == null) {
            return;
        }
        ServerPlayer player = (ServerPlayer) event.getPlayer();
        if (!mark.owner().equals(player.getUUID())
                && !player.hasPermissions(2)) {
            event.setCanceled(true);
            player.displayClientMessage(
                    Component.translatable(
                            "rotp_echoes.message.marked_by_other",
                            marks.remainingSeconds(
                                    mark, level.getGameTime())),
                    true);
            return;
        }

        event.setCanceled(true);
        marks.remove(position);
        ItemStack drop =
                new ItemStack(event.getState().getBlock().asItem());
        level.setBlock(
                position, Blocks.AIR.defaultBlockState(), 3);
        if (drop.isEmpty()) {
            return;
        }
        EchoesGravityService.infuse(drop, level.getGameTime());
        level.addFreshEntity(new ItemEntity(
                level,
                position.getX() + 0.5D,
                position.getY() + 0.5D,
                position.getZ() + 0.5D,
                drop));
        player.displayClientMessage(
                Component.translatable(
                        "rotp_echoes.message.gravity_item",
                        drop.getHoverName()),
                true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getEntity() instanceof LivingEntity living
                && EchoesGravityService.isHandFrozen(living)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onAttackEntity(AttackEntityEvent event) {
        if (EchoesGravityService.isHandFrozen(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickItem(
            PlayerInteractEvent.RightClickItem event) {
        if (EchoesGravityService.isHandFrozen(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickBlock(
            PlayerInteractEvent.RightClickBlock event) {
        if (EchoesGravityService.isHandFrozen(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLeftClickBlock(
            PlayerInteractEvent.LeftClickBlock event) {
        if (EchoesGravityService.isHandFrozen(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onEntityInteract(
            PlayerInteractEvent.EntityInteract event) {
        if (EchoesGravityService.isHandFrozen(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onContainerOpen(PlayerContainerEvent.Open event) {
        if (EchoesGravityService.isHandFrozen(event.getEntity())) {
            event.getEntity().closeContainer();
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onIncomingDamage(
            LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || player.getAbilities().instabuild
                || !RotpEchoes.isEchoesUser(player)) {
            return;
        }
        EchoesUserState state = RotpEchoes.userState(player);
        long gameTime = player.level().getGameTime();
        if (!state.autoActionReady(gameTime)
                || player.getRandom().nextFloat() >= 0.4F
                || !(event.getSource().getEntity()
                        instanceof LivingEntity attacker)
                || attacker == player
                || !attacker.isAlive()) {
            return;
        }
        state.resetAutoActionCooldown(gameTime);
        ensureSummoned(player);
        attacker.hurt(
                player.damageSources().playerAttack(player), 4.0F);
        attacker.knockback(
                1.5D,
                player.getX() - attacker.getX(),
                player.getZ() - attacker.getZ());
        if (player.getRandom().nextFloat() < 0.1F) {
            EchoesChatService.showCounterLine(player);
        }
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.hasData(EchoesAttachments.FREEZE_STATE)) {
            EchoesGravityService.releaseFrozenTarget(entity);
        }
        if (entity instanceof ServerPlayer player
                && player.hasData(EchoesAttachments.USER_STATE)) {
            EchoesGravityService.cleanupUser(player, true);
        }
    }

    @SubscribeEvent
    public static void onEntityLeave(EntityLeaveLevelEvent event) {
        if (event.getEntity() instanceof LivingEntity living
                && living.hasData(EchoesAttachments.FREEZE_STATE)) {
            EchoesGravityService.releaseFrozenTarget(living);
        }
    }

    @SubscribeEvent
    public static void onPlayerLogout(
            PlayerEvent.PlayerLoggedOutEvent event) {
        cleanupPlayer(event.getEntity());
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(
            PlayerEvent.PlayerChangedDimensionEvent event) {
        cleanupPlayer(event.getEntity());
    }

    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        cleanupPlayer(event.getOriginal());
        cleanupPlayer(event.getEntity());
    }

    private static void cleanupPlayer(
            net.minecraft.world.entity.player.Player player) {
        if (player instanceof ServerPlayer serverPlayer
                && player.hasData(EchoesAttachments.USER_STATE)) {
            EchoesGravityService.cleanupUser(serverPlayer, true);
        }
    }

    private static void tryAutoAttack(ServerPlayer player) {
        EchoesUserState state = RotpEchoes.userState(player);
        long gameTime = player.level().getGameTime();
        if (!state.autoActionReady(gameTime)
                || player.getRandom().nextFloat() >= 0.35F) {
            return;
        }
        Monster target = player.serverLevel()
                .getEntitiesOfClass(
                        Monster.class,
                        player.getBoundingBox().inflate(AUTO_RANGE),
                        monster -> monster.isAlive()
                                && monster.distanceTo(player) <= AUTO_RANGE)
                .stream()
                .findFirst()
                .orElse(null);
        if (target == null) {
            return;
        }
        state.resetAutoActionCooldown(gameTime);
        ensureSummoned(player);
        target.hurt(
                player.damageSources().playerAttack(player), 6.0F);
        target.knockback(
                2.0D,
                player.getX() - target.getX(),
                player.getZ() - target.getZ());
        if (player.getRandom().nextFloat() < 0.1F) {
            EchoesChatService.showCounterLine(player);
        }
    }

    private static void ensureSummoned(ServerPlayer player) {
        StandPower power = StandPower.get(player);
        if (power != null && !power.isSummoned()) {
            power.getPowerType().summon(player, power);
        }
    }
}
