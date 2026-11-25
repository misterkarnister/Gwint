package gwint.engine;

/** Result of executing one resolution step. */
final class StepResult {

    static final StepResult CONTINUE = new StepResult(null);

    private final ChoiceRequest request;

    private StepResult(ChoiceRequest request) {
        this.request = request;
    }

    static StepResult awaitChoice(ChoiceRequest request) {
        return new StepResult(request);
    }

    boolean awaitsChoice() {
        return request != null;
    }

    ChoiceRequest request() {
        return request;
    }
}
