package gwint.engine;

import gwint.Karta;

/** Canonical battlefield rows used by the rules engine. */
public enum BattleRow {
    MELEE,
    RANGED,
    SIEGE;

    /** Maps the legacy Polish row names stored in karty.json. */
    public static BattleRow fromKarta(Karta.Row row) {
        if (row == null) {
            return null;
        }
        return switch (row) {
            case PRZOD -> MELEE;
            case SRODEK -> RANGED;
            case TYL -> SIEGE;
            case OTHER -> null;
        };
    }

    public Karta.Row toKarta() {
        return switch (this) {
            case MELEE -> Karta.Row.PRZOD;
            case RANGED -> Karta.Row.SRODEK;
            case SIEGE -> Karta.Row.TYL;
        };
    }
}
