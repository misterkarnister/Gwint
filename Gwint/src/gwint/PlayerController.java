package gwint;

import gwint.engine.BattleRow;
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

    /** Chooses a row for an Agile unit. */
    default BattleRow chooseRow(Match match, Player self, CardInstance card,
                                List<BattleRow> options) {
        return options.get(0);
    }

    /** Chooses a row for Commander's Horn or Mardroeme. */
    default BattleRow chooseSpecialRow(Match match, Player self, CardInstance card,
                                       List<BattleRow> options) {
        return options.get(0);
    }

    /** Chooses a non-Hero unit card from the player's discard pile. */
    default CardInstance chooseRevival(Match match, Player self, List<CardInstance> options) {
        return options.get(0);
    }

    /** Chooses a non-Hero unit on the player's own board for Decoy. */
    default CardInstance chooseDecoyTarget(Match match, Player self, List<CardInstance> options) {
        return options.get(0);
    }
}
