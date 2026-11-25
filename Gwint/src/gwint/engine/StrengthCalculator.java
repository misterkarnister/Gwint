package gwint.engine;

import gwint.CardInstance;
import java.util.List;
import java.util.Objects;

/**
 * Deterministic unit/row strength pipeline. Every score is recomputed from
 * state; no strength is ever stored on a card instance.
 */
public final class StrengthCalculator {

    private StrengthCalculator() {
    }

    public static StrengthBreakdown evaluate(GameState state, CardInstance unit) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(unit, "unit");
        CardView def = unit.definition();
        int base = def.baseStrength();
        if (def.isHero()) {
            return new StrengthBreakdown(unit.uid(), def.name(), base,
                    false, 0, 0, false, true, base);
        }

        BattleRow row = unit.row();
        SideState owner = state.ownerOf(unit);
        boolean weathered = row != null && isWeathered(state, row);
        int working = weathered ? 1 : base;

        int bondCount = 0;
        String bondKey = def.has(Ability.TIGHT_BOND) ? def.bondGroupId() : null;
        if (bondKey != null && row != null && owner != null) {
            bondCount = countBonded(state, owner, row, bondKey);
            if (bondCount > 1) {
                working *= bondCount;
            }
        }

        int moraleCount = 0;
        if (row != null && owner != null) {
            moraleCount = countMorale(state, owner, row, unit.uid());
            working += moraleCount;
        }

        boolean hornApplied = false;
        if (row != null && owner != null) {
            hornApplied = hasHornSpecial(state, owner, row)
                    || hasOtherUnitHorn(state, owner, row, unit.uid());
            if (hornApplied) {
                working *= 2;
            }
        }

        return new StrengthBreakdown(unit.uid(), def.name(), base,
                weathered, bondCount, moraleCount, hornApplied, false, working);
    }

    public static int effectiveStrength(GameState state, CardInstance unit) {
        return evaluate(state, unit).finalStrength();
    }

    public static int rowStrength(GameState state, SideState side, BattleRow row) {
        int sum = 0;
        for (CardInstance unit : side.units(row)) {
            sum += effectiveStrength(state, unit);
        }
        return sum;
    }

    public static int totalStrength(GameState state, SideState side) {
        int sum = 0;
        for (BattleRow row : BattleRow.values()) {
            sum += rowStrength(state, side, row);
        }
        return sum;
    }

    /** Units, plus the Decoy stand-in that occupies a unit row. */
    public static boolean isUnitLike(CardInstance unit) {
        CardView def = unit.definition();
        return def.cardKind() == CardKind.UNIT || def.has(Ability.DECOY);
    }

    private static boolean isWeathered(GameState state, BattleRow row) {
        for (WeatherType type : WeatherType.values()) {
            if (state.isWeatherActive(type) && type.affectedRow() == row) {
                return true;
            }
        }
        return false;
    }

    private static int countBonded(GameState state, SideState side, BattleRow row, String bondKey) {
        int count = 0;
        for (CardInstance candidate : side.units(row)) {
            CardView def = candidate.definition();
            if (def.isHero() || !isUnitLike(candidate)) {
                continue;
            }
            if (def.has(Ability.TIGHT_BOND) && bondKey.equals(def.bondGroupId())) {
                count++;
            }
        }
        return count;
    }

    private static int countMorale(GameState state, SideState side, BattleRow row, int excludedUid) {
        int count = 0;
        for (CardInstance candidate : side.units(row)) {
            if (candidate.uid() == excludedUid) {
                continue;
            }
            CardView def = candidate.definition();
            if (!def.isHero() && isUnitLike(candidate) && def.has(Ability.MORALE_BOOST)) {
                count++;
            }
        }
        return count;
    }

    private static boolean hasHornSpecial(GameState state, SideState side, BattleRow row) {
        CardInstance special = state.activeSpecial(side, row);
        return special != null
                && special.definition().cardKind() == CardKind.SPECIAL
                && special.definition().has(Ability.COMMANDERS_HORN);
    }

    private static boolean hasOtherUnitHorn(GameState state, SideState side,
                                             BattleRow row, int excludedUid) {
        for (CardInstance candidate : side.units(row)) {
            if (candidate.uid() == excludedUid) {
                continue;
            }
            CardView def = candidate.definition();
            if (!def.isHero() && isUnitLike(candidate) && def.has(Ability.COMMANDERS_HORN)) {
                return true;
            }
        }
        return false;
    }
}
