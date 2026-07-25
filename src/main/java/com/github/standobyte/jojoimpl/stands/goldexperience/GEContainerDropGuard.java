package com.github.standobyte.jojoimpl.stands.goldexperience;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

public final class GEContainerDropGuard {
    private static final Set<BlockEntity> KEEP_ITEMS = Collections.newSetFromMap(new IdentityHashMap<>());

    private GEContainerDropGuard() {}

    public static boolean shouldKeepItems(Object container) {
        return container instanceof BlockEntity blockEntity && KEEP_ITEMS.contains(blockEntity);
    }

    public static boolean removeBlockKeepingContainerItems(Level level, BlockPos blockPos, @Nullable BlockEntity blockEntity) {
        if (!(blockEntity instanceof Container)) {
            return level.removeBlock(blockPos, false);
        }
        KEEP_ITEMS.add(blockEntity);
        try {
            return level.removeBlock(blockPos, false);
        }
        finally {
            KEEP_ITEMS.remove(blockEntity);
        }
    }
}
