package gwint;

import java.util.List;

/**
 * Strategy interface for controlling a player. The match engine only calls
 * these methods; it never reads from the console or touches Swing itself.
 */
public interface PlayerController {

    String name();

    Karta.Faction chooseFaction(CardCatalog catalog);

    DeckList buildDeck(CardCatalog catalog, Karta.Faction faction);

    Leader chooseLeader(LeaderCatalog leaders, Karta.Faction faction);

    /** Returns the cards from the dealt hand that should be exchanged (at most two). */
    List<CardInstance> chooseMulligan(List<CardInstance> hand);

    Actions.Action chooseAction(Match match, Player self);
}
