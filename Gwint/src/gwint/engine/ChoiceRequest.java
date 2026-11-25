package gwint.engine;

/** A decision the rules engine needs before it can continue resolving a play. */
public sealed interface ChoiceRequest permits RowChoice, CardChoice {

    SideState actor();

    String prompt();
}
