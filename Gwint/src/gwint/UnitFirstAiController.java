package gwint;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;

final class UnitFirstAiController implements PlayerController {

    private final PlayerController delegate;
    private final Random random;

    UnitFirstAiController(PlayerController delegate, Random random) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.random = Objects.requireNonNull(random, "random");
    }

    @Override
    public String name() {
        return delegate.name();
    }

    @Override
    public Karta.Faction chooseFaction(CardCatalog catalog) {
        return delegate.chooseFaction(catalog);
    }

    @Override
    public DeckList buildDeck(CardCatalog catalog, Karta.Faction faction) {
        return delegate.buildDeck(catalog, faction);
    }

    @Override
    public Leader chooseLeader(LeaderCatalog leaders, Karta.Faction faction) {
        return delegate.chooseLeader(leaders, faction);
    }

    @Override
    public List<CardInstance> chooseMulligan(List<CardInstance> hand) {
        return delegate.chooseMulligan(hand);
    }

    @Override
    public Actions.Action chooseAction(Match match, Player self) {
        List<Integer> units = new ArrayList<>();
        List<CardInstance> hand = self.getHand();
        for (int i = 0; i < hand.size(); i++) {
            if (hand.get(i).card().getType() != Karta.Type.SPECIAL) {
                units.add(i);
            }
        }
        if (units.isEmpty()) {
            return new Actions.Pass();
        }
        return new Actions.Play(units.get(random.nextInt(units.size())));
    }
}
