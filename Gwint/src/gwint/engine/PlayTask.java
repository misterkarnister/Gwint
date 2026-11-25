package gwint.engine;

import gwint.CardInstance;
import java.util.Set;

/** State for one card being resolved inside a play resolution. */
final class PlayTask {

    final SideState actor;
    final CardInstance card;
    final PlayOrigin origin;
    final boolean suppressMuster;
    final Set<Ability> abilities;

    BattleRow rowChoice;
    BattleRow placedRow;
    CardInstance cardChoice;

    PlayTask(SideState actor, CardInstance card, PlayOrigin origin,
             boolean suppressMuster, Set<Ability> abilities) {
        this.actor = actor;
        this.card = card;
        this.origin = origin;
        this.suppressMuster = suppressMuster;
        this.abilities = abilities;
    }

    boolean has(Ability ability) {
        return abilities.contains(ability);
    }
}
