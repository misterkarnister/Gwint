package gwint.engine;

import java.util.List;
import java.util.Set;

/**
 * Read-only card definition seen by the rules engine. It intentionally has no
 * dependency on rendering or on the console.
 */
public interface CardView {

    String id();

    String name();

    int baseStrength();

    BattleRow preferredRow();

    List<BattleRow> playableRows();

    boolean isHero();

    CardKind cardKind();

    Set<Ability> abilities();

    default boolean has(Ability ability) {
        return ability != null && abilities().contains(ability);
    }

    /** Explicit bond group, or {@code null} when the card has no Tight Bond. */
    String bondGroupId();

    /** Explicit muster group, or {@code null} when the card has no Muster. */
    String musterGroupId();

    String transformsToId();

    String avengerTokenId();

    default boolean isToken() {
        return false;
    }
}
