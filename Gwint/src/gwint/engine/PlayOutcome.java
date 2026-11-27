package gwint.engine;

/** Result of advancing a play resolution. */
public sealed interface PlayOutcome permits PlayComplete, PlayPending {
}
