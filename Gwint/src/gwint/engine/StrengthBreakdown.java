package gwint.engine;

import java.util.ArrayList;
import java.util.List;

/** Explanation of one unit's effective strength. */
public record StrengthBreakdown(
        int uid,
        String name,
        int baseStrength,
        boolean weatherApplied,
        int bondCount,
        int moraleCount,
        boolean hornApplied,
        boolean hero,
        int finalStrength) {

    /** Short tags describing why this unit's strength differs from its base. */
    public List<String> tags() {
        List<String> tags = new ArrayList<>();
        if (hero) {
            tags.add("Hero (no modifiers)");
            return tags;
        }
        if (weatherApplied) {
            tags.add("Weather -> 1");
        }
        if (bondCount > 1) {
            tags.add("Tight Bond x" + bondCount);
        }
        if (moraleCount > 0) {
            tags.add("Morale Boost +" + moraleCount);
        }
        if (hornApplied) {
            tags.add("Horn x2");
        }
        return tags;
    }

    /** True when at least one modifier changed the printed strength. */
    public boolean isModified() {
        return hero ? baseStrength != finalStrength : !tags().isEmpty();
    }

    public String describe() {
        if (hero) {
            return name + ": hero " + finalStrength + " (immune to all modifiers)";
        }
        StringBuilder sb = new StringBuilder(name).append(": ").append(baseStrength);
        if (weatherApplied) {
            sb.append(" -> weather 1");
        }
        if (bondCount > 1) {
            sb.append(" -> bond x").append(bondCount);
        }
        if (moraleCount > 0) {
            sb.append(" -> morale +").append(moraleCount);
        }
        if (hornApplied) {
            sb.append(" -> horn x2");
        }
        return sb.append(" = ").append(finalStrength).toString();
    }
}
