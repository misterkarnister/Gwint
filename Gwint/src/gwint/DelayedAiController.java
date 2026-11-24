package gwint;

import java.util.List;
import java.util.Objects;

final class DelayedAiController implements PlayerController {

    private final PlayerController delegate;
    private final BoardView view;
    private final long delayMillis;

    DelayedAiController(PlayerController delegate, BoardView view, long delayMillis) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.view = Objects.requireNonNull(view, "view");
        this.delayMillis = Math.max(0, delayMillis);
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
        view.setStatus("Opponent is thinking...");
        pause(delayMillis);
        Actions.Action action = delegate.chooseAction(match, self);
        view.setStatus("Opponent: " + describe(action, self));
        pause(Math.max(150, delayMillis / 3));
        return action;
    }

    private String describe(Actions.Action action, Player self) {
        if (action instanceof Actions.Play play) {
            List<CardInstance> hand = self.getHand();
            if (play.handIndex >= 0 && play.handIndex < hand.size()) {
                return "plays " + hand.get(play.handIndex).card().getName();
            }
            return "plays a card";
        }
        if (action instanceof Actions.Pass) {
            return "passes";
        }
        if (action instanceof Actions.Leader) {
            return self.getLeader() == null ? "uses leader" : "uses leader " + self.getLeader().getName();
        }
        return "has no move";
    }

    private void pause(long millis) {
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
