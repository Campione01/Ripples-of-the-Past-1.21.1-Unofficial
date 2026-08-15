package com.github.standobyte.jojo.api.network;

import java.io.IOException;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;

import com.github.standobyte.jojo.api.network.AbilityNetworkDiagnostics.Stage;
import com.github.standobyte.jojo.api.network.AbilityNetworkDiagnostics.Event;
import com.github.standobyte.jojo.api.network.AbilityNetworkDiagnostics.EventWindow;

public final class AbilityNetworkDiagnosticsSmokeTest {
	private AbilityNetworkDiagnosticsSmokeTest() {}

	public static void run() {
		testBoundedEventWindow();
		testReadOnlyFacade();
		testWriterCapabilityGate();
		testMainThreadDerivationSourceContract();
		testReadContracts();
	}

	private static void testBoundedEventWindow() {
		EventWindow window = new EventWindow(256);
		for (int i = 0; i < 300; i++) {
			window.record(sequence -> event(sequence, 7L));
		}
		var snapshot = window.snapshotAfter(0L, 7L);
		check(snapshot.events().size() == 256
				&& snapshot.firstRetainedSequence() == 45L
				&& snapshot.latestSequence() == 300L
				&& snapshot.truncated(),
				"diagnostic windows must expose bounded FIFO truncation");
		check(snapshot.events().stream().allMatch(
				event -> event.epoch() == 7L),
				"retained diagnostic events must preserve their epoch");
		window.clearRetained();
		window.record(sequence -> event(sequence, 8L));
		var transitioned = window.snapshotAfter(300L, 8L);
		check(transitioned.events().size() == 1
				&& transitioned.events().getFirst().sequence() == 301L
				&& !transitioned.truncated(),
				"epoch expiry must clear receipts without rewinding sequence");
	}

	private static Event event(long sequence, long epoch) {
		return new Event(
				epoch,
				sequence,
				Stage.SERVER_INPUT_APPLIED,
				"server",
				-1,
				"none",
				"none",
				"none",
				(short) -1,
				"PRESS_CLICK",
				-1,
				-1L,
				"none",
				0,
				true,
				Thread.currentThread().getName(),
				"contract");
	}

	private static void testReadOnlyFacade() {
		for (var method : AbilityNetworkDiagnostics.class.getDeclaredMethods()) {
			check(!(Modifier.isPublic(method.getModifiers())
					&& Modifier.isStatic(method.getModifiers())
					&& method.getName().startsWith("record")),
					"the public diagnostics facade must expose reads only");
		}
		long latest = AbilityNetworkDiagnostics.latestSequence();
		var snapshot = AbilityNetworkDiagnostics.snapshotAfter(latest);
		check(snapshot.latestSequence() == latest
				&& snapshot.epoch()
						== AbilityNetworkDiagnostics.currentEpoch(),
				"diagnostic snapshots must expose current sequence and epoch");
		try {
			snapshot.events().clear();
			throw new AssertionError("diagnostic snapshots must be immutable");
		}
		catch (UnsupportedOperationException expected) {}
	}

	private static void testWriterCapabilityGate() {
		String previous = System.getProperty(
				AbilityNetworkDiagnostics.RECORDING_PROPERTY);
		System.setProperty(
				AbilityNetworkDiagnostics.RECORDING_PROPERTY, "true");
		try {
			expectSecurity(() -> AbilityNetworkDiagnostics.recordServerAbility(
				Stage.SERVER_INPUT_APPLIED,
				null,
				null,
				(short) 1,
				"PRESS_CLICK",
				0,
				true,
				new Object(),
				"unauthorized"),
				"same-package code must not bypass the server writer");
			expectSecurity(() -> ServerAbilityNetworkDiagnostics.recordAbility(
				Stage.SERVER_INPUT_APPLIED,
				null,
				null,
				(short) 1,
				"PRESS_CLICK",
				0,
				"unauthorized"),
				"addon code must not invoke the server recorder");
			expectSecurity(() -> ClientAbilityNetworkDiagnostics.recordAbility(
				Stage.CLIENT_REPLAY_APPLIED,
				null,
				null,
				(short) 1,
				"PRESS_CLICK",
				0,
				"unauthorized"),
				"addon code must not invoke the client recorder");
		}
		finally {
			if (previous != null) {
				System.setProperty(
						AbilityNetworkDiagnostics.RECORDING_PROPERTY,
						previous);
			}
			else {
				System.clearProperty(
						AbilityNetworkDiagnostics.RECORDING_PROPERTY);
			}
		}
	}

	private static void testMainThreadDerivationSourceContract() {
		String client = read(
				"src/main/java/com/github/standobyte/jojo/api/network/ClientAbilityNetworkDiagnostics.java");
		String server = read(
				"src/main/java/com/github/standobyte/jojo/api/network/ServerAbilityNetworkDiagnostics.java");
		check(client.contains("mc.isSameThread()")
				&& server.contains("server != null && server.isSameThread()"),
				"diagnostic main-thread evidence must come from the live executors");
	}

	private static void testReadContracts() {
		check(Stage.valueOf("SERVER_RELEASE_APPLIED")
				== Stage.SERVER_RELEASE_APPLIED
				&& Stage.valueOf("CLIENT_RELEASE_REJECTED")
						== Stage.CLIENT_RELEASE_REJECTED
				&& Stage.valueOf("CLIENT_ACTION_DEPENDENT_SYNC_APPLIED")
						== Stage.CLIENT_ACTION_DEPENDENT_SYNC_APPLIED,
				"release and dependent-sync receipts must remain public contracts");

		var missingAction = AbilityNetworkDiagnostics.actionState(null);
		check(!missingAction.entityPresent() && missingAction.absentOrOver(),
				"missing action state must be explicit and inert");
		var missingConnection = AbilityNetworkDiagnostics.serverConnection(
				null, null);
		check(!missingConnection.playerPresent()
				&& !missingConnection.connected()
				&& !missingConnection.payloadNegotiated(),
				"missing server connection must fail closed");

		check(!AbilityNetworkDiagnostics.isConnectionReady(
				false, true, false),
				"a missing connection must never be reported as ready");
		check(!AbilityNetworkDiagnostics.isConnectionReady(
				true, false, false),
				"a closing connection must not be reported as ready");
		check(!AbilityNetworkDiagnostics.isConnectionReady(
				true, true, true),
				"a disconnected player must not be reported as ready");
		check(AbilityNetworkDiagnostics.isConnectionReady(
				true, true, false),
				"an accepting connection for a live player must be ready");
		check(!AbilityNetworkDiagnostics.isPayloadNegotiated(false, true),
				"a channel on a closing connection must not be negotiated");
		check(AbilityNetworkDiagnostics.isPayloadNegotiated(true, true),
				"a channel on a ready connection must be negotiated");
	}

	private static String read(String path) {
		try {
			return Files.readString(Path.of(path));
		}
		catch (IOException error) {
			throw new AssertionError("failed to read " + path, error);
		}
	}

	private static void expectSecurity(Runnable action, String message) {
		try {
			action.run();
		}
		catch (SecurityException expected) {
			return;
		}
		throw new AssertionError(message);
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
