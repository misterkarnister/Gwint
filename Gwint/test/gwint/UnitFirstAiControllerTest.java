package gwint;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Random;
import org.junit.Test;

public class UnitFirstAiControllerTest {

    @Test
    public void playsUnitEvenWhenSpecialIsPresent() {
        CardCatalog catalog = CardCatalog.load();
        Karta unit = firstCard(catalog, false);
        Karta special = firstCard(catalog, true);
        Player player = playerWith(new RandomAiController(new Random(1)), unit, special);

        Actions.Action action = new UnitFirstAiController(
                new RandomAiController(new Random(2)), new Random(3)).chooseAction(null, player);

        assertTrue(action instanceof Actions.Play);
        int index = ((Actions.Play) action).handIndex;
        assertTrue(player.getHand().get(index).card().getType() != Karta.Type.SPECIAL);
    }

    @Test
    public void passesWhenOnlySpecialCardsRemain() {
        CardCatalog catalog = CardCatalog.load();
        Player player = playerWith(new RandomAiController(new Random(1)),
                firstCard(catalog, true), firstCard(catalog, true));

        Actions.Action action = new UnitFirstAiController(
                new RandomAiController(new Random(2)), new Random(3)).chooseAction(null, player);

        assertTrue(action instanceof Actions.Pass);
        assertEquals(2, player.getHand().size());
    }

    private static Karta firstCard(CardCatalog catalog, boolean special) {
        for (Karta card : catalog.all()) {
            if ((card.getType() == Karta.Type.SPECIAL) == special) {
                return card;
            }
        }
        throw new IllegalStateException("No card for special=" + special);
    }

    private static Player playerWith(PlayerController controller, Karta... cards) {
        Player player = new Player("AI", controller);
        List<CardInstance> deck = new java.util.ArrayList<>();
        for (Karta card : cards) {
            deck.add(new CardInstance(card));
        }
        player.setDeck(deck);
        player.draw(deck.size());
        return player;
    }
}
