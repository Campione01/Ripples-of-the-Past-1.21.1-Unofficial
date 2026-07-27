package com.github.standobyte.jojo.powersystem.standpower;

import com.github.standobyte.jojo.powersystem.standpower.StandInstance.StandPart;
import com.github.standobyte.jojo.powersystem.standpower.type.StandType;
import com.mojang.datafixers.util.Either;

import net.minecraft.resources.ResourceLocation;

public final class TestStandInstances {
	private TestStandInstances() {}

	public static StandInstance valid(ResourceLocation standId) {
		return new TestStandInstance(standId, true);
	}

	public static StandInstance invalid(ResourceLocation standId) {
		return new TestStandInstance(standId, false);
	}

	private static final class TestStandInstance extends StandInstance {
		private final boolean exists;

		private TestStandInstance(ResourceLocation standId, boolean exists) {
			super(Either.<StandType, ResourceLocation>right(standId));
			this.exists = exists;
		}

		@Override
		public boolean standExists() {
			return exists;
		}

		@Override
		public TestStandInstance copy() {
			TestStandInstance copy = new TestStandInstance(
					getStandId(),
					exists);
			for (StandPart part : StandPart.values()) {
				if (!hasPart(part)) {
					copy.removePart(part);
				}
			}
			copy.setCustomSkin(getSelectedSkin());
			return copy;
		}
	}
}
