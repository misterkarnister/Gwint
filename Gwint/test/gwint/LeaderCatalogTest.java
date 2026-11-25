package gwint;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.Test;

public class LeaderCatalogTest {

    private final LeaderCatalog catalog = LeaderCatalog.load();

    @Test
    public void loadsLeadersFromJsonOnly() {
        assertEquals(12, catalog.all().size());
        assertEquals("Careful tactician", leader("John Natalis").getDescription());
        assertEquals(Karta.Faction.KROLESTWO_POLNOCY, leader("John Natalis").getFaction());
        assertEquals("NONE", leader("John Natalis").getAbilityId());
        assertTrue(catalog.all().stream().noneMatch(l -> l.getName().equals("Geralt of Rivia")));
        assertTrue(catalog.all().stream().noneMatch(l -> l.getName().equals("Philippa Eilhart")));
        assertTrue(catalog.all().stream().noneMatch(l -> l.getName().equals("Letho of Gulet")));
    }

    @Test
    public void usesExactJsonDescriptions() {
        assertEquals("Seasoned commander", leader("Vernon Roche").getDescription());
        assertEquals("Wealthy patron of the arts", leader("Esterad Thyssen").getDescription());
        assertEquals("Ambitious general", leader("Morvran Voorhis").getDescription());
        assertEquals("Ancient druid sage", leader("Eithne").getDescription());
        assertEquals("Primitive druid of the swamps", leader("Dagon").getDescription());
        assertEquals("Ancient beast of the mountains", leader("Kayran").getDescription());
    }

    @Test
    public void filtersByPlayableFaction() {
        CardCatalog cards = CardCatalog.load();
        for (Karta.Faction faction : cards.playableFactions()) {
            List<Leader> options = catalog.forFaction(faction);
            assertEquals("Faction " + faction, 3, options.size());
            for (Leader option : options) {
                assertEquals(faction, option.getFaction());
            }
        }
        assertTrue(catalog.forFaction(Karta.Faction.NEUTRAL).isEmpty());
    }

    @Test
    public void hasExactlyExpectedLeaderPoolPerFaction() {
        assertEquals(Set.of("Vernon Roche", "John Natalis", "Esterad Thyssen"),
                names(catalog.forFaction(Karta.Faction.KROLESTWO_POLNOCY)));
        assertEquals(Set.of("Emhyr var Emreis", "Morvran Voorhis", "Tibor Eggebracht"),
                names(catalog.forFaction(Karta.Faction.KROLENSTWO_NILFGARDU)));
        assertEquals(Set.of("Eithne", "Isengrim Faoiltiarna", "Iorveth"),
                names(catalog.forFaction(Karta.Faction.SCOIATEL)));
        assertEquals(Set.of("Dagon", "The Unseen Elder", "Kayran"),
                names(catalog.forFaction(Karta.Faction.POTWORY)));
    }

    @Test
    public void aiChoosesOnlyCatalogLeaders() {
        RandomAiController ai = new RandomAiController(new Random(17));
        for (Karta.Faction faction : CardCatalog.load().playableFactions()) {
            List<Leader> options = catalog.forFaction(faction);
            for (int i = 0; i < 20; i++) {
                Leader chosen = ai.chooseLeader(catalog, faction);
                assertNotNull(chosen);
                assertTrue(chosen.getName(), options.contains(chosen));
                assertEquals(faction, chosen.getFaction());
            }
        }
    }

    private Set<String> names(List<Leader> leaders) {
        return leaders.stream().map(Leader::getName).collect(Collectors.toSet());
    }

    private Leader leader(String name) {
        return catalog.all().stream()
                .filter(l -> l.getName().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No leader named " + name));
    }
}
