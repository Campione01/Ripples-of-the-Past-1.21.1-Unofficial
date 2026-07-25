package com.github.standobyte.jojo.crafting;

import java.util.ArrayDeque;
import java.util.Deque;

import javax.annotation.Nullable;

import net.minecraft.world.entity.player.Player;

public final class StandUserCraftingContext {
	private static final ThreadLocal<Deque<Player>> CURRENT_PLAYER = ThreadLocal.withInitial(ArrayDeque::new);

	private StandUserCraftingContext() {
	}

	public static void push(Player player) {
		CURRENT_PLAYER.get().addLast(player);
	}

	public static void pop() {
		Deque<Player> stack = CURRENT_PLAYER.get();
		if (!stack.isEmpty()) {
			stack.removeLast();
		}
		if (stack.isEmpty()) {
			CURRENT_PLAYER.remove();
		}
	}

	@Nullable
	public static Player getPlayer() {
		Deque<Player> stack = CURRENT_PLAYER.get();
		return stack.isEmpty() ? null : stack.peekLast();
	}
}
