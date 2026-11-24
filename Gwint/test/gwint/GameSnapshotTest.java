package gwint;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.Test;

public class GameSnapshotTest {

    private static final class ScriptedController implements PlayerController {

        private final String name;
        private final Karta.Faction faction;
        private final int seed;

        private ScriptedController(String name, Karta.Faction faction, int seed) {
            this.name = name;
            this.faction = faction;
            this.seed = seed;
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
            return new ArrayList<>();
        }

        @Override public Actions.Action chooseAction(Match match, Player self) {
            return self.getHand().isEmpty() ? new Actions.Pass() : new Actions.Play(0);
        }
    }

    @Test
    public void copiesFinishedMatchIntoImmutableState() {
        PrintStream out = new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8);
        Match match = new Match(CardCatalog.load(), LeaderCatalog.load(),
                new ScriptedController("One", Karta.Faction.KROLESTWO_POLNOCY, 3),
                new ScriptedController("Two", Karta.Faction.POTWORY, 4),
                new Random(7), out);
        match.play();

        GameSnapshot snapshot = GameSnapshot.of(match, "test status");
        assertTrue(snapshot.finished());
        assertFalse(snapshot.result().isEmpty());
        assertEquals("test status", snapshot.status());
        assertTrue(snapshot.round() >= 1 && snapshot.round() <= GameRules.MAX_ROUNDS);
        assertEquals("One", snapshot.player().name());
        assertEquals("Two", snapshot.opponent().name());
        assertTrue(snapshot.player().roundWins() >= 0);
        assertTrue(snapshot.opponent().roundWins() >= 0);
        assertEquals(match.players()[0].getLives(), snapshot.player().lives());
        assertEquals(match.players()[1].getLives(), snapshot.opponent().lives());

        try {
            snapshot.player().board(Karta.Row.PRZOD).add(null);
            fail("Board rows must be immutable");
        } catch (UnsupportedOperationException expected) {
            assertTrue(true);
        }
        try {
            snapshot.player().hand().clear();
            fail("Hand must be immutable");
        } catch (UnsupportedOperationException expected) {
            assertTrue(true);
        }
    }
}
