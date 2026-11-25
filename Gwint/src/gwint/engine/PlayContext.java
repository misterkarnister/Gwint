package gwint.engine;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

/** Shared queue and chain state for one top-level play. */
final class PlayContext {

    final Deque<ResolutionStep> steps = new ArrayDeque<>();
    final Set<Integer> visited = new HashSet<>();
}
