package gwint;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class CardCatalogTest {

    @Test
    public void loadsAndIndexesAllCards() {
        CardCatalog catalog = CardCatalog.load();
        assertEquals(146, catalog.all().size());
        assertEquals(9, catalog.specials().size());

        Karta geralt = catalog.byId(1);
        assertNotNull(geralt);
        assertEquals("Geralt of Rivia", geralt.getName());
        assertEquals(15, geralt.getPower());
        assertTrue(geralt.getType() == Karta.Type.HERO);
    }

    @Test
    public void factionQueriesOnlyReturnAllowedCards() {
        CardCatalog catalog = CardCatalog.load();
        for (Karta.Faction faction : catalog.playableFactions()) {
            assertFalse(catalog.unitsFor(faction).isEmpty());
            for (Karta k : catalog.allowedFor(faction)) {
                assertTrue(k.getFaction() == faction || k.getFaction() == Karta.Faction.NEUTRAL);
            }
        }
    }
}
