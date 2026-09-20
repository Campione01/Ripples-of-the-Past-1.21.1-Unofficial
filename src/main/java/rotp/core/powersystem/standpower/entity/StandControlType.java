package rotp.core.powersystem.standpower.entity;

public enum StandControlType {
	CLOSE_RANGE_DIRECT,
	LONG_DISTANCE_OPERATION,
	AUTOMATIC,
	COLONY,
	PHENOMENON,
	HYBRID_FORM;

	static void validate(
			StandControlType controlType,
			double effectiveRange,
			double rangeMax,
			boolean manualControl,
			boolean distanceStrengthDecay) {
		validate(controlType, effectiveRange, rangeMax,
				manualControl, distanceStrengthDecay, false);
	}

	static void validate(
			StandControlType controlType,
			double effectiveRange,
			double rangeMax,
			boolean manualControl,
			boolean distanceStrengthDecay,
			boolean colonyCoordinatorManualControl) {
		if (controlType == null) {
			throw new IllegalStateException("standControlType is required");
		}
		if (colonyCoordinatorManualControl && controlType != COLONY) {
			throw new IllegalStateException(
					"Dedicated colony coordinator control requires COLONY");
		}
		if (!Double.isFinite(effectiveRange) || effectiveRange <= 0.0D) {
			throw new IllegalStateException(
					"effectiveRange must be finite and positive");
		}
		if (!Double.isFinite(rangeMax) || rangeMax < effectiveRange) {
			throw new IllegalStateException(
					"rangeMax must be finite and at least effectiveRange");
		}
		if (!manualControl && distanceStrengthDecay) {
			throw new IllegalStateException(
					"distanceStrengthDecay requires manualControl");
		}

		switch (controlType) {
		case CLOSE_RANGE_DIRECT -> {
			if (!manualControl || !distanceStrengthDecay) {
				throw new IllegalStateException(
						"CLOSE_RANGE_DIRECT requires manualControl and distanceStrengthDecay");
			}
			if (effectiveRange >= rangeMax) {
				throw new IllegalStateException(
						"CLOSE_RANGE_DIRECT requires a non-empty decay interval");
			}
		}
		case LONG_DISTANCE_OPERATION -> {
			if (!manualControl || distanceStrengthDecay) {
				throw new IllegalStateException(
						"LONG_DISTANCE_OPERATION requires manualControl without distanceStrengthDecay");
			}
		}
		case AUTOMATIC, PHENOMENON -> {
			if (manualControl || distanceStrengthDecay) {
				throw new IllegalStateException(
						controlType + " cannot use generic manual control or distance decay");
			}
		}
		case COLONY -> {
			if (distanceStrengthDecay || manualControl && !colonyCoordinatorManualControl) {
				throw new IllegalStateException(
						"COLONY cannot use generic manual control or distance decay");
			}
		}
		case HYBRID_FORM -> {
			// Hybrid forms own their action-specific policy outside this generic entity-body gate.
		}
		}
	}
}
