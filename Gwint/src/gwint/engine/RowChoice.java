package gwint.engine;

import gwint.CardInstance;
import java.util.List;

public record RowChoice(
        SideState actor,
        CardInstance card,
        List<BattleRow> options,
        RowPurpose purpose) implements ChoiceRequest {

    public RowChoice {
        options = List.copyOf(options);
    }

    @Override
    public String prompt() {
        return purpose == RowPurpose.SPECIAL
                ? "Choose a row for " + card.definition().name()
                : "Choose a placement row for " + card.definition().name();
    }
}
