package gwint;

import gwint.engine.Ability;
import gwint.engine.BattleRow;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

final class SwingPlayerController implements PlayerController {

    private final SetupWizard setup;
    private final BoardView view;
    private final BlockingQueue<Actions.Action> actions = new LinkedBlockingQueue<>(1);

    SwingPlayerController(SetupWizard setup, BoardView view) {
        this.setup = Objects.requireNonNull(setup, "setup");
        this.view = Objects.requireNonNull(view, "view");
    }

    @Override
    public String name() {
        return "You";
    }

    @Override
    public Karta.Faction chooseFaction(CardCatalog catalog) {
        return setup.chooseFaction();
    }

    @Override
    public DeckList buildDeck(CardCatalog catalog, Karta.Faction faction) {
        return setup.buildDeck(faction);
    }

    @Override
    public Leader chooseLeader(LeaderCatalog leaders, Karta.Faction faction) {
        return setup.chooseLeader(faction);
    }

    @Override
    public List<CardInstance> chooseMulligan(List<CardInstance> hand) {
        return setup.chooseMulligan(hand);
    }

    @Override
    public BattleRow chooseRow(Match match, Player self, CardInstance card,
                               List<BattleRow> options) {
        if (card == null || options == null || options.isEmpty()) {
            throw new IllegalStateException("No row options for placement");
        }
        BattleRow fallback = options.get(0);
        if (!card.definition().abilities().contains(Ability.AGILE)) {
            return fallback;
        }
        Karta.Row selected = view.consumePlacement(card.uid());
        BattleRow choice = BattleRow.fromKarta(selected);
        return choice != null && options.contains(choice) ? choice : fallback;
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
