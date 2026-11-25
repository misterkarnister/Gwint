package gwint.engine;

public record PlayPending(ChoiceRequest request, ResolutionSession session) implements PlayOutcome {
}
