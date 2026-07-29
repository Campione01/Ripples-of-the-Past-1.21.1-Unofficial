package com.github.standobyte.jojo.subsystems.rollback;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import com.github.standobyte.jojo.api.rollback.AtomicRollbackJournal;
import com.github.standobyte.jojo.api.rollback.AtomicRollbackJournal.Entry;
import com.github.standobyte.jojo.api.rollback.AtomicRollbackJournal.InverseFailure;
import com.github.standobyte.jojo.api.rollback.AtomicRollbackJournal.Outcome;
import com.github.standobyte.jojo.api.rollback.AtomicRollbackJournal.Status;

/**
 * Internal executor for the public bounded rollback-journal facade.
 */
public final class AtomicRollbackJournalExecutor {
	private AtomicRollbackJournalExecutor() {}

	public static Outcome apply(List<Entry> entries) {
		Objects.requireNonNull(entries, "entries");
		if (entries.size() > AtomicRollbackJournal.MAX_ENTRIES) {
			throw new IllegalArgumentException(
					"rollback journal entry limit exceeded");
		}

		List<Entry> prepared = List.copyOf(entries);
		Set<String> entryIds = new HashSet<>();
		for (Entry entry : prepared) {
			if (!entryIds.add(entry.id())) {
				throw new IllegalArgumentException(
						"duplicate rollback journal entry id: "
								+ entry.id());
			}
		}

		List<Entry> attempted = new ArrayList<>(prepared.size());
		try {
			for (Entry entry : prepared) {
				attempted.add(entry);
				entry.apply().run();
			}
			return new Outcome(
					Status.APPLIED,
					attempted.size(),
					null,
					List.of());
		}
		catch (RuntimeException failure) {
			List<InverseFailure> inverseFailures = new ArrayList<>();
			for (int index = attempted.size() - 1;
					index >= 0;
					index--) {
				Entry entry = attempted.get(index);
				try {
					entry.inverse().run();
				}
				catch (RuntimeException inverseFailure) {
					if (inverseFailure != failure) {
						failure.addSuppressed(inverseFailure);
					}
					inverseFailures.add(new InverseFailure(
							entry.id(), inverseFailure));
				}
			}
			return new Outcome(
					inverseFailures.isEmpty()
							? Status.ROLLED_BACK
							: Status.INVERSE_FAILED,
					attempted.size(),
					failure,
					inverseFailures);
		}
	}
}
