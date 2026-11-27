package gwint.engine;

import gwint.CardInstance;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Human-readable explanation of every effect active on one player row. */
public record RowModifiers(
        SideState side,
        BattleRow row,
        Set<WeatherType> weather,
        Map<String, List<String>> bondGroups,
        List<String> moraleSources,
        List<String> hornSources,
        int rawStrength,
        int effectiveStrength) {

    public RowModifiers {
        weather = Collections.unmodifiableSet(EnumSet.copyOf(weather));
        Map<String, List<String>> groups = new LinkedHashMap<>();
        bondGroups.forEach((key, names) -> groups.put(key, List.copyOf(names)));
        bondGroups = Collections.unmodifiableMap(groups);
        moraleSources = List.copyOf(moraleSources);
        hornSources = List.copyOf(hornSources);
    }

    public boolean hasEffects() {
        return !weather.isEmpty() || !bondGroups.isEmpty()
                || !moraleSources.isEmpty() || !hornSources.isEmpty();
    }

    /** Short tags suitable for appending to a row score, e.g. {@code [Horn x2, Frost]}. */
    public List<String> tags() {
        List<String> tags = new ArrayList<>();
        for (WeatherType type : WeatherType.values()) {
            if (weather.contains(type)) {
                tags.add(type.displayName());
            }
        }
        for (Map.Entry<String, List<String>> entry : bondGroups.entrySet()) {
            int count = entry.getValue().size();
            if (count > 1) {
                tags.add("Tight Bond x" + count + " (" + String.join(", ", entry.getValue()) + ")");
            }
        }
        if (!moraleSources.isEmpty()) {
            tags.add("Morale Boost +" + moraleSources.size()
                    + " (" + String.join(", ", moraleSources) + ")");
        }
        if (!hornSources.isEmpty()) {
            tags.add((hornSources.size() > 1 ? "Horn/Dandelion" : "Commander's Horn")
                    + " x2 (" + String.join(", ", hornSources) + ")");
        }
        return tags;
    }

    /** Single-line description of all effects on this row. */
    public String describe() {
        return String.join("; ", tags());
    }

    /** Summary including raw and effective strength, for score displays. */
    public String describeWithStrength() {
        StringBuilder sb = new StringBuilder();
        sb.append(rawStrength);
        if (effectiveStrength != rawStrength) {
            sb.append(" -> ").append(effectiveStrength);
        }
        if (hasEffects()) {
            sb.append(" [").append(describe()).append(']');
        }
        return sb.toString();
    }

    static RowModifiers of(GameState state, SideState side, BattleRow row) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(side, "side");
        Objects.requireNonNull(row, "row");

        Set<WeatherType> weather = EnumSet.noneOf(WeatherType.class);
        for (WeatherType type : WeatherType.values()) {
            if (state.isWeatherActive(type) && type.affectedRow() == row) {
                weather.add(type);
            }
        }

        Map<String, List<String>> bonds = new LinkedHashMap<>();
        List<String> morale = new ArrayList<>();
        List<String> horns = new ArrayList<>();
        int raw = 0;

        CardInstance hornSpecial = state.activeSpecial(side, row);
        if (hornSpecial != null && hornSpecial.definition().has(Ability.COMMANDERS_HORN)) {
            horns.add(hornSpecial.definition().name());
        }

        for (CardInstance unit : side.units(row)) {
            if (!StrengthCalculator.isUnitLike(unit)) {
                continue;
            }
            CardView def = unit.definition();
            raw += def.baseStrength();

            if (!def.isHero() && def.has(Ability.TIGHT_BOND) && def.bondGroupId() != null) {
                bonds.computeIfAbsent(def.bondGroupId(), key -> new ArrayList<>())
                        .add(def.name());
            }
            if (!def.isHero() && def.has(Ability.MORALE_BOOST)) {
                morale.add(def.name());
            }
            if (!def.isHero() && def.has(Ability.COMMANDERS_HORN)) {
                horns.add(def.name());
            }
        }

        // A group with only one member has no visible effect.
        bonds.values().removeIf(names -> names.size() < 2);
        // If a special Horn and Dandelion are both present the bonus still applies once.
        int effective = StrengthCalculator.rowStrength(state, side, row);
        return new RowModifiers(side, row, weather, bonds, morale, horns, raw, effective);
    }
}
