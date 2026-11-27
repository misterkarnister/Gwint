package gwint;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.Test;

public class MatchTest {

    private enum Mode { PASS, PLAY }

    private static final class ScriptedController implements PlayerController {
        private final String name;
        private final Karta.Faction faction;
        private final Mode mode;
        private final int seed;
        private final boolean exchangeAll;

        ScriptedController(String name, Karta.Faction faction, Mode mode, int seed) {
            this(name, faction, mode, seed, false);
        }

        ScriptedController(String name, Karta.Faction faction, Mode mode, int seed, boolean exchangeAll) {
            this.name = name;
            this.faction = faction;
            this.mode = mode;
            this.seed = seed;
            this.exchangeAll = exchangeAll;
        }

        @Override public String name() { return name; }

        @Override public Karta.Faction chooseFaction(CardCatalog catalog) { return faction; }

        @Override public DeckList buildDeck(CardCatalog catalog, Karta.Faction chosen) {
            return DeckList.randomDeck(catalog, chosen, new Random(seed));
        }

        @Override public Leader chooseLeader(LeaderCatalog leaders, Karta.Faction chosen) {
            return leaders.forFaction(chosen).get(0);
        }

        @Override public List<CardInstance> chooseMulligan(List<CardInstance> hand) {
            return exchangeAll ? new ArrayList<>(hand) : List.of();
        }

        @Override public Actions.Action chooseAction(Match match, Player self) {
            if (mode == Mode.PASS || self.getHand().isEmpty()) {
                return new Actions.Pass();
            }
            int best = 0;
            for (int i = 1; i < self.getHand().size(); i++) {
                if (self.getHand().get(i).card().getPower()
                        > self.getHand().get(best).card().getPower()) {
                    best = i;
                }
            }
            return new Actions.Play(best);
        }
    }

    private static Match match(PlayerController one, PlayerController two, PrintStream out) {
        CardCatalog catalog = CardCatalog.load();
        LeaderCatalog leaders = LeaderCatalog.load();
        return new Match(catalog, leaders, one, two, new Random(7), out);
    }

    @Test
    public void twoPassingPlayersDrawAfterThreeRounds() {
        ByteArrayOutputStream log = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(log, true, StandardCharsets.UTF_8);
        Match match = match(
                new ScriptedController("Anna", Karta.Faction.POTWORY, Mode.PASS, 1),
                new ScriptedController("Boris", Karta.Faction.SCOIATEL, Mode.PASS, 2), out);

        match.play();

        assertTrue(match.isFinished());
        assertEquals(3, match.round());
        assertTrue(match.result(), match.result().contains("draw"));
        assertEquals(0, match.players()[0].getRoundWins());
        assertEquals(0, match.players()[1].getRoundWins());
    }

    @Test
    public void playingCardsWinsRoundsByPower() {
        ByteArrayOutputStream log = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(log, true, StandardCharsets.UTF_8);
        Match match = match(
                new ScriptedController("Anna", Karta.Faction.POTWORY, Mode.PLAY, 3),
                new ScriptedController("Boris", Karta.Faction.SCOIATEL, Mode.PASS, 4), out);

        match.play();

        assertTrue(match.isFinished());
        assertTrue(match.result(), match.result().startsWith("Anna"));
        assertFalse(match.result().contains("draw"));
        assertEquals(1, match.players()[0].getRoundWins());
        assertEquals(0, match.players()[1].getRoundWins());
    }

    @Test
    public void roundWinDoesNotAffectOtherPlayer() {
        Player winner = new Player("Winner", new ScriptedController("Winner",
                Karta.Faction.POTWORY, Mode.PASS, 9));
        Player loser = new Player("Loser", new ScriptedController("Loser",
                Karta.Faction.SCOIATEL, Mode.PASS, 10));

        winner.addRoundWin();

        assertEquals(1, winner.getRoundWins());
        assertEquals(0, loser.getRoundWins());
    }

    @Test
    public void listenerReceivesLifecycleEvents() {
        ByteArrayOutputStream log = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(log, true, StandardCharsets.UTF_8);
        List<MatchListener.Event> events = new ArrayList<>();
        Match match = new Match(CardCatalog.load(), LeaderCatalog.load(),
                new ScriptedController("Anna", Karta.Faction.POTWORY, Mode.PASS, 11),
                new ScriptedController("Boris", Karta.Faction.SCOIATEL, Mode.PASS, 12),
                new Random(7), out, (current, event) -> events.add(event));

        match.play();

        assertTrue(events.contains(MatchListener.Event.ACTION));
        assertTrue(events.contains(MatchListener.Event.ROUND_END));
        assertTrue(events.contains(MatchListener.Event.MATCH_END));
    }

    @Test
    public void mulliganNeverExceedsTwoCards() {
        ByteArrayOutputStream log = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(log, true, StandardCharsets.UTF_8);
        Match match = match(
                new ScriptedController("Anna", Karta.Faction.POTWORY, Mode.PASS, 5, true),
                new ScriptedController("Boris", Karta.Faction.SCOIATEL, Mode.PASS, 6), out);

        match.play();

        String output = log.toString(StandardCharsets.UTF_8);
        assertTrue(output, output.contains("Anna exchanged 2 card(s)"));
        assertEquals(GameRules.HAND_SIZE, match.players()[0].getHand().size());
    }
}
