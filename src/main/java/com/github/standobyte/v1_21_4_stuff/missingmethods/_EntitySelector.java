package com.github.standobyte.v1_21_4_stuff.missingmethods;

import java.util.function.Predicate;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;

public class _EntitySelector {
    public static final Predicate<Entity> CAN_BE_PICKED = EntitySelector.NO_SPECTATORS.and(Entity::isPickable);
}
