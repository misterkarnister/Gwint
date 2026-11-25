package gwint.engine;

import gwint.CardInstance;
import java.util.List;

public record CardChoice(
        SideState actor,
        CardInstance source,
        List<CardInstance> options,
        CardPurpose purpose) implements ChoiceRequest {

    public CardChoice {
        options = List.copyOf(options);
    }

    @Override
    public String prompt() {
        return purpose == CardPurpose.REVIVE
                ? "Choose a discard card for " + source.definition().name()
                : "Choose one of your units to replace with Decoy";
    }
}
