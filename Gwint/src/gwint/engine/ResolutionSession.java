package gwint.engine;

import gwint.CardInstance;
import java.util.Objects;

/**
 * A play resolution that can pause on a player decision and be resumed later.
 * Console implementations answer immediately; asynchronous UIs can keep the
 * session and call {@link #answer(Object)} when the user responds.
 */
public final class ResolutionSession {

    private final GameState state;
    private final PlayContext context = new PlayContext();
    private ChoiceRequest pending;
    private ResolutionStep pendingStep;

    ResolutionSession(GameState state) {
        this.state = Objects.requireNonNull(state, "state");
    }

    void enqueue(ResolutionStep step) {
        context.steps.addLast(step);
    }

    public PlayOutcome advance() {
        while (!context.steps.isEmpty()) {
            ResolutionStep step = context.steps.poll();
            StepResult result = step.execute(state, context);
            if (result.awaitsChoice()) {
                pending = result.request();
                pendingStep = step;
                return new PlayPending(pending, this);
            }
        }
        return new PlayComplete();
    }

    public PlayOutcome answer(Object choice) {
        if (pending == null || pendingStep == null) {
            throw new IllegalStateException("This resolution is not waiting for a choice");
        }
        if (pending instanceof RowChoice rowChoice) {
            if (!(choice instanceof BattleRow row) || !rowChoice.options().contains(row)) {
                throw new IllegalArgumentException("Invalid row choice: " + choice);
            }
        } else if (pending instanceof CardChoice cardChoice) {
            if (!(choice instanceof CardInstance card) || !cardChoice.options().contains(card)) {
                throw new IllegalArgumentException("Invalid card choice: " + choice);
            }
        } else {
            throw new IllegalStateException("Unknown choice request: " + pending);
        }
        pendingStep.answer(choice);
        context.steps.addFirst(pendingStep);
        pending = null;
        pendingStep = null;
        return advance();
    }

    public boolean isPending() {
        return pending != null;
    }

    public ChoiceRequest pendingRequest() {
        return pending;
    }
}
