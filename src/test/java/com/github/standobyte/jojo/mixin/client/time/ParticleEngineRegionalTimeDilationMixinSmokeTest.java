package com.github.standobyte.jojo.mixin.client.time;

public final class ParticleEngineRegionalTimeDilationMixinSmokeTest {
	private ParticleEngineRegionalTimeDilationMixinSmokeTest() {}

	public static void run() {
		verifyMovingAndRotatingParticleHoldsAcrossSkippedFrames();
	}

	private static void verifyMovingAndRotatingParticleHoldsAcrossSkippedFrames() {
		MovingParticleState particle = new MovingParticleState(
				0.0D, 0.0D, 0.0D,
				2.0D, -1.0D, 0.5D,
				2.0D, -1.0D, 0.5D,
				0.0F, 0.5F, 0.5F,
				1, 20);
		double currentX = particle.x;
		double currentY = particle.y;
		double currentZ = particle.z;
		float currentRoll = particle.roll;

		for (double partialTick : new double[] {
				0.05D, 0.75D, 0.2D, 0.9D
		}) {
			ParticleEngineRegionalTimeDilationMixin
					.jojo_ripples$stabilizeSkippedTick(particle);
			check(lerp(partialTick, particle.xo, particle.x) == currentX
							&& lerp(partialTick, particle.yo, particle.y)
									== currentY
							&& lerp(partialTick, particle.zo, particle.z)
									== currentZ
							&& lerp(
									partialTick,
									particle.oRoll,
									particle.roll) == currentRoll,
					"skipped frame replayed prior movement or rotation");
			check(particle.x == currentX
							&& particle.y == currentY
							&& particle.z == currentZ
							&& particle.xd == 2.0D
							&& particle.yd == -1.0D
							&& particle.zd == 0.5D
							&& particle.roll == currentRoll
							&& particle.rollDelta == 0.5F
							&& particle.age == 1
							&& particle.lifetime == 20,
					"interpolation stabilization changed logical state");
		}

		particle.logicalTick();
		check(particle.xo == currentX
						&& particle.yo == currentY
						&& particle.zo == currentZ
						&& particle.x == 4.0D
						&& particle.y == -2.0D
						&& particle.z == 1.0D
						&& particle.oRoll == currentRoll
						&& particle.roll == 1.0F
						&& lerp(0.5D, particle.xo, particle.x) == 3.0D
						&& lerp(0.5D, particle.yo, particle.y) == -1.5D
						&& lerp(0.5D, particle.zo, particle.z) == 0.75D
						&& lerp(
								0.5D,
								particle.oRoll,
								particle.roll) == 0.75D,
				"scheduled logical tick did not resume normal interpolation");
	}

	private static double lerp(
			double partialTick,
			double previous,
			double current) {
		return previous + partialTick * (current - previous);
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}

	private static final class MovingParticleState
			implements ParticleRegionalTimeDilationInterpolationAccessor {
		private double xo;
		private double yo;
		private double zo;
		private double x;
		private double y;
		private double z;
		private final double xd;
		private final double yd;
		private final double zd;
		private float oRoll;
		private float roll;
		private final float rollDelta;
		private int age;
		private final int lifetime;

		private MovingParticleState(
				double xo,
				double yo,
				double zo,
				double x,
				double y,
				double z,
				double xd,
				double yd,
				double zd,
				float oRoll,
				float roll,
				float rollDelta,
				int age,
				int lifetime) {
			this.xo = xo;
			this.yo = yo;
			this.zo = zo;
			this.x = x;
			this.y = y;
			this.z = z;
			this.xd = xd;
			this.yd = yd;
			this.zd = zd;
			this.oRoll = oRoll;
			this.roll = roll;
			this.rollDelta = rollDelta;
			this.age = age;
			this.lifetime = lifetime;
		}

		private void logicalTick() {
			xo = x;
			yo = y;
			zo = z;
			x += xd;
			y += yd;
			z += zd;
			oRoll = roll;
			roll += rollDelta;
			age++;
		}

		@Override
		public double jojo_ripples$getCurrentX() {
			return x;
		}

		@Override
		public double jojo_ripples$getCurrentY() {
			return y;
		}

		@Override
		public double jojo_ripples$getCurrentZ() {
			return z;
		}

		@Override
		public float jojo_ripples$getCurrentRoll() {
			return roll;
		}

		@Override
		public void jojo_ripples$setPreviousX(double x) {
			xo = x;
		}

		@Override
		public void jojo_ripples$setPreviousY(double y) {
			yo = y;
		}

		@Override
		public void jojo_ripples$setPreviousZ(double z) {
			zo = z;
		}

		@Override
		public void jojo_ripples$setPreviousRoll(float roll) {
			oRoll = roll;
		}
	}
}
