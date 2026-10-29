package rotp.addons.heavensdoor.state;

import java.util.UUID;

import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.init.ModStatusEffects;

import rotp.addons.heavensdoor.HeavensDoorAddon;
import rotp.addons.heavensdoor.entity.HeavensDoorStandEntity;
import rotp.addons.heavensdoor.item.PageData;
import rotp.addons.heavensdoor.item.PageItem;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public final class BookStateService {
    private static final ResourceLocation PAGE_MAX_HEALTH =
            HeavensDoorAddon.id("page_max_health");
    private static final ResourceLocation PAGE_GRAVITY =
            HeavensDoorAddon.id("page_gravity");

    private BookStateService() {}

    public static LivingEntity stateTarget(
            LivingEntity target) {
        if (target instanceof StandEntity stand
                && stand.getUser() != null) {
            return stand.getUser();
        }
        return target;
    }

    public static boolean markFromHit(
            HeavensDoorStandEntity stand,
            LivingEntity rawTarget) {
        LivingEntity owner =
                stand != null ? stand.getUser() : null;
        LivingEntity target = stateTarget(rawTarget);
        if (owner == null
                || target == null
                || target == owner
                || target.level().isClientSide()
                || !target.isAlive()
                || owner.level() != target.level()
                || HeavensDoorAddon.controller(owner) != stand) {
            return false;
        }

        BookState state = HeavensDoorAddon.bookState(target);
        validatePages(target);
        if (state.active()) {
            BookCommandSessionService.revokeMatchingBook(
                    target.getServer(),
                    state.ownerUuid(),
                    target.getUUID(),
                    state.generation());
        }
        if (owner instanceof ServerPlayer player) {
            BookCommandLeaseService.onBookRecast(
                    player, target);
            BookCommandSessionService.revoke(player);
        }
        BookStateRules.BookStrength strength = BookStateRules.strengthFor(owner, target);
        int duration = strength.durationTicks();
        int amplifier = strength.amplifier();
        long expiresAt = target.level().getGameTime() + duration;
        ResourceLocation dimension =
                target.level().dimension().location();
        boolean changed = state.mark(
                owner.getUUID(),
                target.getUUID(),
                dimension,
                expiresAt,
                amplifier,
                PageOwnerSavedData.get(owner.getServer()).begin(owner.getUUID()));
        if (target.hasEffect(ModStatusEffects.RESOLVE)) {
            state.markResolveCountered();
        }
        applyBookEffect(target, owner, duration, amplifier);
        updatePageEffects(target, state);
        if (changed) {
            HeavensDoorAddon.syncBookState(target);
        }
        return changed;
    }

    public static LivingEntity authorizedTarget(
            LivingEntity owner, LivingEntity rawTarget) {
        return owner != null && HeavensDoorAddon.controller(owner) != null
                ? ownedBookTarget(owner, rawTarget)
                : null;
    }

    /**
     * authorizedTarget without the summoned Stand: what a press asks
     * before the Stand is summoned for it (1.16 summoned it first).
     */
    public static LivingEntity ownedBookTarget(
            LivingEntity owner, LivingEntity rawTarget) {
        LivingEntity target = stateTarget(rawTarget);
        if (owner == null
                || target == null
                || target == owner
                || owner.level() != target.level()
                || owner.level().isClientSide()
                || !owner.isAlive()
                || !target.isAlive()
                || owner.distanceToSqr(target)
                        > BookStateRules.ACTION_RANGE
                                * BookStateRules.ACTION_RANGE
                || !target.hasData(HeavensDoorAddon.BOOK_STATE)) {
            return null;
        }
        BookState state = HeavensDoorAddon.bookState(target);
        return state.isOwnedBy(owner.getUUID())
                && ownerDeathEpochMatches(target, state)
                && state.matches(
                        target.getUUID(),
                        target.level().dimension().location())
                && target.level().getGameTime()
                        <= state.expiresAt()
                ? target
                : null;
    }

    /**
     * 1.16 TearOutAPage.checkTarget reason for this aim (TearPageRules
     * key), or null. Both sides; the client reads the synced book.
     */
    public static String tearRefusal(
            LivingEntity owner, LivingEntity rawTarget) {
        LivingEntity target = stateTarget(rawTarget);
        if (owner == null || target == null) {
            return TearPageRules.NO_BOOK;
        }
        boolean book = ownsLiveBook(owner, target);
        if (book && HeavensDoorAddon.bookState(target).pageCount() > 0
                && !HeavensDoorAddon.bookState(target).pagesOwnedBy(owner.getUUID())) {
            book = false;
        }
        return TearPageRules.refusal(
                book,
                book ? HeavensDoorAddon.bookState(target).pageCount() : 0,
                BookStateRules.MAX_PAGES,
                owner.distanceToSqr(target),
                BookStateRules.ACTION_RANGE);
    }

    // authorizedTarget's lease checks without the range.
    private static boolean ownsLiveBook(
            LivingEntity owner, LivingEntity target) {
        if (target == owner
                || owner.level() != target.level()
                || !target.isAlive()
                || !target.hasData(HeavensDoorAddon.BOOK_STATE)) {
            return false;
        }
        BookState state = HeavensDoorAddon.bookState(target);
        return state.isOwnedBy(owner.getUUID())
                && ownerDeathEpochMatches(target, state)
                && state.matches(
                        target.getUUID(),
                        target.level().dimension().location())
                && target.level().getGameTime()
                        <= state.expiresAt();
    }

    public static boolean tearPage(
            LivingEntity owner, LivingEntity rawTarget) {
        if (owner == null
                || !(owner.level() instanceof ServerLevel)) {
            return false;
        }
        LivingEntity target = authorizedTarget(owner, rawTarget);
        if (target == null) {
            return false;
        }
        if (target.getMaxHealth() <= 1.0F) {
            return false;
        }
        BookState state = HeavensDoorAddon.bookState(target);
        validatePages(target);
        if (state.hasPages() && !state.pagesOwnedBy(owner.getUUID())) {
            return false;
        }
        long ownerEpoch = PageOwnerSavedData.get(owner.getServer())
                .begin(owner.getUUID());
        if (!state.addPage(ownerEpoch)) {
            return false;
        }
        updatePageModifiers(target, state.pageCount());
        updateForgottenEffects(target, owner, state.pageCount());
        givePage(owner, PageItem.create(
                target, owner, state.pageGeneration()));
        HeavensDoorAddon.syncBookState(target);
        return true;
    }

    private static boolean ownerDeathEpochMatches(LivingEntity target, BookState state) {
        return target.level().isClientSide() || PageOwnerSavedData.get(target.getServer())
                .matches(state.ownerUuid(), state.bookOwnerEpoch());
    }

    public static boolean returnHeldPage(LivingEntity owner) {
        if (owner == null
                || !(owner.level() instanceof ServerLevel level)) {
            return false;
        }
        ItemStack stack = owner.getMainHandItem();
        PageData data = PageItem.data(stack);
        if (data == null
                || !owner.isAlive()
                || !data.creatorUuid().equals(owner.getUUID())) {
            return false;
        }
        Entity entity = level.getEntity(data.targetUuid());
        if (!(entity instanceof LivingEntity target)
                || owner.distanceToSqr(target)
                        > BookStateRules.RETURN_PAGE_RANGE
                                * BookStateRules.RETURN_PAGE_RANGE
                || !pageMatches(target, data)) {
            return false;
        }
        BookState state = HeavensDoorAddon.bookState(target);
        if (!state.returnPage()) {
            return false;
        }
        stack.shrink(1);
        updatePageModifiers(target, state.pageCount());
        updateForgottenEffects(target, owner, state.pageCount());
        HeavensDoorAddon.syncBookState(target);
        return true;
    }

    public static boolean pageMatches(
            LivingEntity target, PageData data) {
        if (target == null
                || data == null
                || !target.hasData(HeavensDoorAddon.BOOK_STATE)) {
            return false;
        }
        BookState state = HeavensDoorAddon.bookState(target);
        validatePages(target);
        return target.isAlive()
                && target.getUUID().equals(data.targetUuid())
                && state.pagesMatch(data.targetUuid(), data.creatorUuid(),
                        data.dimension(), data.generation());
    }

    public static int restoreBoundPages(
            LivingEntity target,
            LivingEntity creator,
            PageData data,
            int requested) {
        if (creator == null || data == null
                || requested <= 0
                || !data.creatorUuid().equals(creator.getUUID())
                || !pageMatches(target, data)) {
            return 0;
        }
        BookState state = HeavensDoorAddon.bookState(target);
        int restored = Math.min(
                requested, state.pageCount());
        for (int index = 0; index < restored; index++) {
            state.returnPage();
        }
        if (restored > 0) {
            updatePageModifiers(target, state.pageCount());
            updateForgottenEffects(
                    target, creator, state.pageCount());
            HeavensDoorAddon.syncBookState(target);
        }
        return restored;
    }

    public static boolean tick(LivingEntity target) {
        if (!(target.level() instanceof ServerLevel level) || target.isRemoved()) {
            return false;
        }
        if (!target.hasData(HeavensDoorAddon.BOOK_STATE)) {
            cleanupEffects(target);
            return false;
        }
        BookState state = HeavensDoorAddon.bookState(target);
        if (!target.isAlive()) {
            clearPages(target);
            clear(target);
            return false;
        }
        validatePages(target);
        if (!state.active()) {
            target.removeEffect(HeavensDoorAddon.BOOK_EFFECT);
            updatePageEffects(target, state);
            return false;
        }

        Entity ownerEntity = level.getEntity(state.ownerUuid());
        LivingEntity owner = ownerEntity instanceof LivingEntity living
                ? living
                : null;
        if (!target.isAlive()
                || owner == null
                || owner == target
                || !owner.isAlive()
                || owner.level() != target.level()
                || !ownerDeathEpochMatches(target, state)
                || !state.matches(
                        target.getUUID(),
                        level.dimension().location())
                || level.getGameTime() > state.expiresAt()
                || owner.distanceToSqr(target)
                        > BookStateRules.OWNER_RANGE
                                * BookStateRules.OWNER_RANGE) {
            clear(target);
            return false;
        }

        if (target.hasEffect(ModStatusEffects.RESOLVE) && state.weakenForResolve(level.getGameTime())) {
            if (state.expiresAt() <= level.getGameTime()) {
                clear(target);
                return false;
            }
            target.removeEffect(HeavensDoorAddon.BOOK_EFFECT);
            HeavensDoorAddon.syncBookState(target);
            BookCommandSessionService.refreshMatchingBook(target, state);
        }
        MobEffectInstance book =
                target.getEffect(HeavensDoorAddon.BOOK_EFFECT);
        if (book == null) {
            int remaining = (int) Math.clamp(
                    state.expiresAt() - level.getGameTime(),
                    1L,
                    Integer.MAX_VALUE);
            applyBookEffect(
                    target,
                    owner,
                    remaining,
                    state.bookAmplifier());
        }
        if (target.tickCount % 20 == 0) {
            updatePageEffects(target, state);
        }
        return true;
    }

    public static boolean clear(LivingEntity rawTarget) {
        LivingEntity target = stateTarget(rawTarget);
        if (target == null || target.level().isClientSide()) {
            return false;
        }
        BookState state = target.hasData(
                HeavensDoorAddon.BOOK_STATE)
                ? HeavensDoorAddon.bookState(target)
                : null;
        UUID ownerUuid = state != null && state.active()
                ? state.ownerUuid()
                : null;
        UUID targetUuid = state != null && state.active()
                ? state.targetUuid()
                : null;
        long generation = state != null && state.active()
                ? state.generation()
                : 0L;
        boolean changed = state != null && state.clear();
        boolean effectChanged = target.removeEffect(HeavensDoorAddon.BOOK_EFFECT);
        if (state != null) {
            updatePageEffects(target, state);
        }
        else {
            effectChanged |= cleanupEffects(target);
        }
        BookCommandSessionService.revokeMatchingBook(
                target.getServer(),
                ownerUuid,
                targetUuid,
                generation);
        if (changed || effectChanged) {
            HeavensDoorAddon.syncBookState(target);
            return true;
        }
        return false;
    }

    public static int clearOwnedBy(
            MinecraftServer server, UUID ownerUuid) {
        if (server == null || ownerUuid == null) {
            return 0;
        }
        int cleared = 0;
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (entity instanceof LivingEntity living
                        && living.hasData(
                                HeavensDoorAddon.BOOK_STATE)
                        && HeavensDoorAddon.bookState(living)
                                .isOwnedBy(ownerUuid)
                        && clear(living)) {
                    cleared++;
                }
            }
        }
        return cleared;
    }

    public static LivingEntity findLoadedEntity(MinecraftServer server, UUID uuid) {
        if (server != null && uuid != null) {
            for (ServerLevel level : server.getAllLevels()) {
                Entity entity = level.getEntity(uuid);
                if (entity instanceof LivingEntity living && !living.isRemoved()) {
                    return living;
                }
            }
        }
        return null;
    }

    public static void tickPageOwner(LivingEntity owner) {
        if (!(owner.level() instanceof ServerLevel)) {
            return;
        }
        boolean creative = owner instanceof Player player
                && player.getAbilities().instabuild;
        if (PageOwnerSavedData.shouldInvalidate(owner.isAlive(),
                owner.getHealth(), owner.getMaxHealth(), creative)) {
            invalidateOwnedPages(owner);
        }
    }

    public static void invalidateOwnedPages(LivingEntity owner) {
        if (!(owner.level() instanceof ServerLevel level)) {
            return;
        }
        PageOwnerSavedData owners = PageOwnerSavedData.get(level.getServer());
        // Players/current HD users also cover pre-v3 pages not loaded since upgrade.
        if (!owners.tracks(owner.getUUID())
                && !(owner instanceof Player)
                && !HeavensDoorAddon.isHeavensDoorUser(owner)) {
            return;
        }
        if (!owners.invalidate(owner.getUUID())) {
            return;
        }
        for (ServerLevel targetLevel : level.getServer().getAllLevels()) {
            for (Entity entity : targetLevel.getAllEntities()) {
                if (entity instanceof LivingEntity target
                        && target.hasData(HeavensDoorAddon.BOOK_STATE)
                        && HeavensDoorAddon.bookState(target).pagesOwnedBy(owner.getUUID())) {
                    clearPages(target);
                }
            }
        }
    }

    private static void validatePages(LivingEntity target) {
        if (!(target.level() instanceof ServerLevel level)) {
            return;
        }
        BookState state = HeavensDoorAddon.bookState(target);
        if (!state.hasPages()) {
            return;
        }
        PageOwnerSavedData owners = PageOwnerSavedData.get(level.getServer());
        if (!owners.tracks(state.pageOwnerUuid())
                && owners.matches(state.pageOwnerUuid(), state.pageOwnerEpoch())) {
            owners.begin(state.pageOwnerUuid());
        }
        LivingEntity creator = findLoadedEntity(level.getServer(), state.pageOwnerUuid());
        if (creator != null) {
            tickPageOwner(creator);
        }
        if (state.hasPages() && (!target.getUUID().equals(state.pageTargetUuid())
                || !PageOwnerSavedData.get(level.getServer()).matches(
                        state.pageOwnerUuid(), state.pageOwnerEpoch()))) {
            clearPages(target);
        }
    }

    public static boolean clearPages(LivingEntity target) {
        if (target == null || target.level().isClientSide()
                || !target.hasData(HeavensDoorAddon.BOOK_STATE)) {
            return false;
        }
        BookState state = HeavensDoorAddon.bookState(target);
        boolean changed = state.clearPages();
        updatePageModifiers(target, 0);
        removeForgottenEffects(target);
        if (changed) {
            HeavensDoorAddon.syncBookState(target);
        }
        return changed;
    }

    private static void updatePageEffects(LivingEntity target, BookState state) {
        updatePageModifiers(target, state.pageCount());
        updateForgottenEffects(target,
                findLoadedEntity(target.getServer(), state.pageOwnerUuid()), state.pageCount());
    }

    private static void applyBookEffect(
            LivingEntity target,
            LivingEntity owner,
            int duration,
            int amplifier) {
        MobEffectInstance current =
                target.getEffect(HeavensDoorAddon.BOOK_EFFECT);
        if (current != null
                && current.getAmplifier() != amplifier) {
            target.removeEffect(HeavensDoorAddon.BOOK_EFFECT);
        }
        target.addEffect(
                new MobEffectInstance(
                        HeavensDoorAddon.BOOK_EFFECT,
                        duration,
                        amplifier,
                        false,
                        false,
                        false),
                owner);
        target.setHealth(Math.min(
                target.getHealth(), target.getMaxHealth()));
    }

    private static void updatePageModifiers(
            LivingEntity target, int pages) {
        int count = BookStateRules.clampPages(pages);
        updateModifier(
                target.getAttribute(Attributes.MAX_HEALTH),
                PAGE_MAX_HEALTH,
                -0.5D * count);
        updateModifier(
                target.getAttribute(Attributes.GRAVITY),
                PAGE_GRAVITY,
                -0.001D * count);
        target.setHealth(Math.min(
                target.getHealth(), target.getMaxHealth()));
    }

    private static void updateModifier(
            AttributeInstance attribute,
            ResourceLocation id,
            double amount) {
        if (attribute == null) {
            return;
        }
        AttributeModifier current = attribute.getModifier(id);
        if (amount == 0.0D ? current == null
                : current != null && current.amount() == amount
                        && current.operation() == AttributeModifier.Operation.ADD_VALUE) {
            return;
        }
        attribute.removeModifier(id);
        if (amount != 0.0D) {
            attribute.addTransientModifier(
                    new AttributeModifier(
                            id,
                            amount,
                            AttributeModifier.Operation.ADD_VALUE));
        }
    }

    private static void updateForgottenEffects(
            LivingEntity target,
            LivingEntity owner,
            int pages) {
        int count = BookStateRules.clampPages(pages);
        for (int index = 0; index < 10; index++) {
            Holder<MobEffect> effect = forgottenEffect(index);
            if (index < count) {
                if (!target.hasEffect(effect)) {
                    target.addEffect(
                            new MobEffectInstance(
                                    effect,
                                    BookStateRules
                                            .FORGOTTEN_EFFECT_DURATION,
                                    0,
                                    false,
                                    false,
                                    true),
                            owner);
                }
            }
            else {
                target.removeEffect(effect);
            }
        }
    }

    private static Holder<MobEffect> forgottenEffect(int index) {
        return switch (index) {
            case 0 -> HeavensDoorAddon.FORGOT_SECOND_HAND;
            case 1 -> HeavensDoorAddon.FORGOT_SWIM;
            case 2 -> HeavensDoorAddon.FORGOT_RUN;
            case 3 -> HeavensDoorAddon.FORGOT_JUMP;
            case 4 -> HeavensDoorAddon.FORGOT_SHIFT;
            case 5 -> HeavensDoorAddon.FORGOT_ATTACK;
            case 6 -> HeavensDoorAddon.FORGOT_CRAFT;
            case 7 -> HeavensDoorAddon.FORGOT_CONTAINERS;
            case 8 -> HeavensDoorAddon.FORGOT_BREAK;
            case 9 -> HeavensDoorAddon.FORGOT_STAND;
            default -> throw new IllegalArgumentException(
                    "invalid page index " + index);
        };
    }

    private static void removeForgottenEffects(
            LivingEntity target) {
        for (int index = 0; index < 10; index++) {
            target.removeEffect(forgottenEffect(index));
        }
    }

    private static boolean cleanupEffects(
            LivingEntity target) {
        boolean changed =
                target.removeEffect(HeavensDoorAddon.BOOK_EFFECT);
        for (int index = 0; index < 10; index++) {
            changed |= target.removeEffect(
                    forgottenEffect(index));
        }
        updatePageModifiers(target, 0);
        return changed;
    }

    private static void givePage(
            LivingEntity owner, ItemStack page) {
        if (owner instanceof Player player) {
            if (!player.addItem(page)) {
                player.drop(page, false);
            }
        }
        else {
            owner.spawnAtLocation(page);
        }
    }
}
