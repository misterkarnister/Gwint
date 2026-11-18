package gwint;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Random;
import org.junit.Test;

public class DeckListTest {

    private final CardCatalog catalog = CardCatalog.load();

    @Test
    public void enforcesFactionAndCopyLimits() {
        DeckList deck = new DeckList(catalog, Karta.Faction.POTWORY);
        Karta monster = catalog.byId(102); // Draug, POTWORY, count 1
        assertTrue(deck.add(monster));
        assertFalse(deck.add(monster));

        Karta northern = catalog.byId(25); // Vernon Roche, other faction
        assertFalse(deck.add(northern));
    }

    @Test
    public void unitRequirementIsChecked() {
        DeckList deck = new DeckList(catalog, Karta.Faction.POTWORY);
        assertFalse(deck.isValid());
        assertTrue(deck.validationErrors().stream()
                .anyMatch(e -> e.contains("at least 22")));

        deck.add(catalog.byId(17), 3);
        deck.add(catalog.byId(18), 3);
        deck.add(catalog.byId(21), 3);
        deck.add(catalog.byId(19), 1);
        assertEquals(10, deck.specialCount());
        assertFalse(deck.isValid());
    }

    @Test
    public void specialCardLimitIsEnforced() {
        DeckList deck = new DeckList(catalog, Karta.Faction.SCOIATEL);
        assertEquals(3, deck.add(catalog.byId(17), 3)); // Biting Frost, count 3
        assertEquals(3, deck.add(catalog.byId(18), 3)); // Impenetrable Fog, count 3
        assertEquals(3, deck.add(catalog.byId(21), 3)); // Skellige Storm, count 3
        assertEquals(1, deck.add(catalog.byId(19), 2)); // only one slot left before the limit
        assertEquals(10, deck.specialCount());
        assertFalse(deck.add(catalog.byId(23)));  // Commander's Horn
        assertEquals(10, deck.specialCount());
    }

    @Test
    public void randomDecksAreAlwaysLegal() {
        for (Karta.Faction faction : catalog.playableFactions()) {
            for (int seed = 0; seed < 20; seed++) {
                DeckList deck = DeckList.randomDeck(catalog, faction, new Random(seed));
                assertTrue("Faction " + faction + " seed " + seed, deck.isValid());
                assertTrue(deck.nonSpecialCount() >= GameRules.MIN_NON_SPECIAL_CARDS);
                assertTrue(deck.specialCount() <= GameRules.MAX_SPECIAL_CARDS);
                assertEquals(deck.size(), deck.toInstances(new Random(seed)).size());
            }
        }
    }
}
