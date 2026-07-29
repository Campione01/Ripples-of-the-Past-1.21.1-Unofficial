package com.github.standobyte.jojo.api.stand;

/**
 * Vetoes one source-owned automated Stand grant without affecting manual or
 * unrelated acquisition paths. Implementations must be side-effect free.
 */
@FunctionalInterface
public interface AutomatedStandGrantVeto {
	boolean vetoes(AutomatedStandGrantQuery query);
}
