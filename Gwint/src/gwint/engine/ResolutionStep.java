package gwint.engine;

/** One deterministic unit of play resolution. */
interface ResolutionStep {

    StepResult execute(GameState state, PlayContext context);

    /** Records a player decision. Only choice steps override this. */
    default void answer(Object choice) {
        throw new IllegalStateException("This step does not accept choices");
    }
}
