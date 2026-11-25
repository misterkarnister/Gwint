package gwint.engine;

/** Every non-leader ability supported by the engine. */
public enum Ability {
    AGILE,
    HERO,
    SPY,
    MEDIC,
    TIGHT_BOND,
    MORALE_BOOST,
    COMMANDERS_HORN,
    MUSTER,
    SCORCH_GLOBAL,
    SCORCH_MELEE,
    SCORCH_RANGED,
    SCORCH_SIEGE,
    DECOY,
    BITING_FROST,
    IMPENETRABLE_FOG,
    TORRENTIAL_RAIN,
    SKELLIGE_STORM,
    CLEAR_WEATHER,
    MARDROEME,
    BERSERKER,
    SUMMON_AVENGER;

    /** User-facing label used in card lists and previews. */
    public String displayName() {
        StringBuilder sb = new StringBuilder();
        for (String part : name().split("_")) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(part.charAt(0))
                    .append(part.substring(1).toLowerCase(java.util.Locale.ROOT));
        }
        return sb.toString();
    }
}
