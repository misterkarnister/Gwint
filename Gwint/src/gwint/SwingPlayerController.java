package gwint;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

final class SwingPlayerController implements PlayerController {

    private final PlayerController delegate;
    private final BoardView view;
    private final BlockingQueue<Actions.Action> actions = new LinkedBlockingQueue<>(1);

    SwingPlayerController(PlayerController delegate, BoardView view) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.view = Objects.requireNonNull(view, "view");
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
        actions.clear();
        view.open();
        view.beginTurn(GameSnapshot.of(match, "Your turn - click a card or PASS"), this::submit);
        try {
            return actions.take();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Actions.Pass();
        }
    }

    private void submit(Actions.Action action) {
        actions.offer(Objects.requireNonNull(action, "action"));
    }
}
