package gwint;

import gwint.engine.BattleRow;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Random;

/**
 * Opponent that picks randomly at every decision: faction, deck, leader,
 * mulligan and each turn's action.
 */
public final class RandomAiController implements PlayerController {

    private final Random random;
    private Karta.Faction faction;

    public RandomAiController(Random random) {
        this.random = Objects.requireNonNull(random, "random");
    }

    @Override
    public String name() {
        return "Random AI";
    }

    @Override
    public Karta.Faction chooseFaction(CardCatalog catalog) {
        List<Karta.Faction> factions = catalog.playableFactions();
        faction = factions.get(random.nextInt(factions.size()));
        return faction;
    }

    @Override
    public DeckList buildDeck(CardCatalog catalog, Karta.Faction chosenFaction) {
        faction = chosenFaction;
        return DeckList.randomDeck(catalog, chosenFaction, random);
    }

    @Override
    public Leader chooseLeader(LeaderCatalog leaders, Karta.Faction chosenFaction) {
        List<Leader> options = leaders.forFaction(chosenFaction);
        if (options.isEmpty()) {
            throw new IllegalStateException("No leaders for faction " + chosenFaction);
        }
        return options.get(random.nextInt(options.size()));
    }

    @Override
    public List<CardInstance> chooseMulligan(List<CardInstance> hand) {
        List<CardInstance> exchange = new ArrayList<>();
        if (hand.isEmpty()) {
            return exchange;
        }
        int count = random.nextInt(GameRules.MAX_MULLIGAN + 1);
        List<CardInstance> shuffled = new ArrayList<>(hand);
        Collections.shuffle(shuffled, random);
        for (int i = 0; i < count && i < shuffled.size(); i++) {
            exchange.add(shuffled.get(i));
        }
        return exchange;
    }

    @Override
    public Actions.Action chooseAction(Match match, Player self) {
        if (self.getHand().isEmpty()) {
            return new Actions.Pass();
        }
        if (!match.currentPlayer().isLeaderUsed() && random.nextDouble() < 0.08) {
            return new Actions.Leader();
        }
        if (random.nextDouble() < 0.25) {
            return new Actions.Pass();
        }
        return new Actions.Play(random.nextInt(self.getHand().size()));
    }

    @Override
    public BattleRow chooseRow(Match match, Player self, CardInstance card,
                               List<BattleRow> options) {
        return options.get(random.nextInt(options.size()));
    }

    @Override
    public BattleRow chooseSpecialRow(Match match, Player self, CardInstance card,
                                      List<BattleRow> options) {
        return options.get(random.nextInt(options.size()));
    }

    @Override
    public CardInstance chooseRevival(Match match, Player self, List<CardInstance> options) {
        return options.get(random.nextInt(options.size()));
    }

    @Override
    public CardInstance chooseDecoyTarget(Match match, Player self, List<CardInstance> options) {
        return options.get(random.nextInt(options.size()));
    }
}
