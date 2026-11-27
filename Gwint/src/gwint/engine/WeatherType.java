package gwint.engine;

import java.util.EnumSet;
import java.util.Set;

/** Individual weather flags active in the shared weather zone. */
public enum WeatherType {
    BITING_FROST,
    IMPENETRABLE_FOG,
    TORRENTIAL_RAIN;

    /** Weather flags contributed by a weather card ability. */
    public static Set<WeatherType> forAbility(Ability ability) {
        if (ability == null) {
            return Set.of();
        }
        return switch (ability) {
            case BITING_FROST -> EnumSet.of(BITING_FROST);
            case IMPENETRABLE_FOG -> EnumSet.of(IMPENETRABLE_FOG);
            case TORRENTIAL_RAIN -> EnumSet.of(TORRENTIAL_RAIN);
            case SKELLIGE_STORM -> EnumSet.of(IMPENETRABLE_FOG, TORRENTIAL_RAIN);
            default -> Set.of();
        };
    }

    public BattleRow affectedRow() {
        return switch (this) {
            case BITING_FROST -> BattleRow.MELEE;
            case IMPENETRABLE_FOG -> BattleRow.RANGED;
            case TORRENTIAL_RAIN -> BattleRow.SIEGE;
        };
    }

    /** User-facing name used in score and board displays. */
    public String displayName() {
        return switch (this) {
            case BITING_FROST -> "Biting Frost (Melee -> 1)";
            case IMPENETRABLE_FOG -> "Impenetrable Fog (Ranged -> 1)";
            case TORRENTIAL_RAIN -> "Torrential Rain (Siege -> 1)";
        };
    }
}
