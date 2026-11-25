package gwint;

import com.google.gson.annotations.SerializedName;
import gwint.engine.Ability;
import gwint.engine.BattleRow;
import gwint.engine.CardKind;
import gwint.engine.CardView;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Definition of a single card, as stored in {@code assets/data/karty.json}.
 * One instance of this class describes a card type; {@link CardInstance}
 * represents a concrete copy of it that is actually in a player's deck or hand.
 */
public class Karta implements CardView {

    public enum Faction {
        KROLESTWO_POLNOCY,
        SCOIATEL,
        POTWORY,
        KROLENSTWO_NILFGARDU,
        NEUTRAL
    }

    public enum Type {
        NORMAL,
        HERO,
        SPECIAL,
    }

    public enum Row {
        TYL,
        SRODEK,
        PRZOD,
        OTHER;

        /** English name shown in menus, card lists and previews. */
        public String displayName() {
            return switch (this) {
                case PRZOD -> "Melee";
                case SRODEK -> "Ranged";
                case TYL -> "Siege";
                case OTHER -> "Special";
            };
        }

        @Override
        public String toString() {
            return displayName();
        }
    }

    public enum Abillities {
        ZWINNOSC,
        BOHATER,
        MEDYK,
        MORAL,
        GRUPA,
        SZPIEG,
        BRAT
    }

    private int ID;
    private String name;
    private int power;
    private Faction faction;
    private Row row;
    private Type type;
    private Abillities abills;
    private int count;

    @SerializedName("abilities")
    private List<String> abilityNames = new ArrayList<>();
    @SerializedName("rows")
    private List<String> rowNames = new ArrayList<>();
    private String bondGroupId;
    private String musterGroupId;
    private String transformsTo;
    private String avengerTokenId;
    private boolean token;

    private transient boolean normalized;
    private transient Set<Ability> normalizedAbilities = EnumSet.noneOf(Ability.class);
    private transient List<BattleRow> normalizedRows = List.of();
    private transient String normalizedBondGroup;
    private transient String normalizedMusterGroup;

    /** Required by Gson. */
    public Karta() {
    }

    public Karta(int ID, String name, int power, Faction faction, Row row,
                 Type type, Abillities abills, int count) {
        this.ID = ID;
        this.name = name;
        this.power = power;
        this.faction = faction;
        this.row = row;
        this.type = type;
        this.abills = abills;
        this.count = count;
    }

    /** Full constructor used by test fixtures and by data normalization. */
    public Karta(int ID, String name, int power, Faction faction, Row row, Type type,
                 List<String> abilities, List<String> rows, String bondGroupId,
                 String musterGroupId, String transformsTo, String avengerTokenId,
                 boolean token, int count) {
        this.ID = ID;
        this.name = name;
        this.power = power;
        this.faction = faction;
        this.row = row;
        this.type = type;
        this.abilityNames = abilities == null ? new ArrayList<>() : new ArrayList<>(abilities);
        this.rowNames = rows == null ? new ArrayList<>() : new ArrayList<>(rows);
        this.bondGroupId = bondGroupId;
        this.musterGroupId = musterGroupId;
        this.transformsTo = transformsTo;
        this.avengerTokenId = avengerTokenId;
        this.token = token;
        this.count = count;
        normalize();
    }

    /** Convenience factory for card definitions in tests. */
    public static Karta of(int id, String name, int power, Row row, Type type,
                           int count, String... abilities) {
        return new Karta(id, name, power, Faction.NEUTRAL, row, type,
                List.of(abilities), List.of(), null, null, null, null, false, count);
    }

    public static Karta special(int id, String name, String... abilities) {
        return of(id, name, 0, Row.OTHER, Type.SPECIAL, 1, abilities);
    }

    public Karta bondGroup(String group) {
        this.bondGroupId = group;
        this.normalizedBondGroup = group;
        return this;
    }

    public Karta musterGroup(String group) {
        this.musterGroupId = group;
        this.normalizedMusterGroup = group;
        return this;
    }

    public Karta transformsTo(String id) {
        this.transformsTo = id;
        return this;
    }

    public Karta avenger(String id) {
        this.avengerTokenId = id;
        return this;
    }

    public Karta asToken() {
        this.token = true;
        return this;
    }

    /** Fills derived fields from JSON values and the legacy single-ability field. */
    public void normalize() {
        if (normalized) {
            return;
        }
        if (name == null || name.isBlank()) {
            throw new IllegalStateException("Card at id " + ID + " has no name");
        }

        EnumSet<Ability> parsed = EnumSet.noneOf(Ability.class);
        if (abilityNames != null) {
            for (String raw : abilityNames) {
                if (raw == null || raw.isBlank()) {
                    continue;
                }
                try {
                    parsed.add(Ability.valueOf(raw.trim().toUpperCase(Locale.ROOT)));
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException(
                            "Unknown ability '" + raw + "' on card " + name, e);
                }
            }
        }
        if (parsed.isEmpty() && abills != null) {
            parsed.add(legacyAbility(abills));
        }
        if (type == Type.HERO) {
            parsed.add(Ability.HERO);
        }
        this.normalizedAbilities = Collections.unmodifiableSet(parsed);

        List<BattleRow> rows = new ArrayList<>();
        if (rowNames != null) {
            for (String raw : rowNames) {
                if (raw == null || raw.isBlank()) {
                    continue;
                }
                BattleRow parsedRow = parseBattleRow(raw);
                if (parsedRow != null && !rows.contains(parsedRow)) {
                    rows.add(parsedRow);
                }
            }
        }
        if (rows.isEmpty()) {
            BattleRow legacy = BattleRow.fromKarta(row);
            if (legacy != null) {
                rows.add(legacy);
            }
        }
        if (parsed.contains(Ability.AGILE)) {
            rows = new ArrayList<>(List.of(BattleRow.MELEE, BattleRow.RANGED));
        }
        this.normalizedRows = Collections.unmodifiableList(rows);

        String baseNameKey = normalizedName();
        this.normalizedBondGroup = parsed.contains(Ability.TIGHT_BOND)
                ? valueOr(bondGroupId, baseNameKey) : null;
        this.normalizedMusterGroup = parsed.contains(Ability.MUSTER)
                ? valueOr(musterGroupId, baseNameKey) : null;
        this.normalizedAbilities = Collections.unmodifiableSet(parsed);
        this.normalized = true;
    }

    private Ability legacyAbility(Abillities legacy) {
        return switch (legacy) {
            case ZWINNOSC -> Ability.AGILE;
            case BOHATER -> Ability.HERO;
            case MEDYK -> Ability.MEDIC;
            case MORAL -> "Dandelion".equals(name)
                    ? Ability.COMMANDERS_HORN : Ability.MORALE_BOOST;
            case GRUPA -> Ability.MUSTER;
            case SZPIEG -> Ability.SPY;
            case BRAT -> Ability.TIGHT_BOND;
        };
    }

    private static BattleRow parseBattleRow(String raw) {
        return switch (raw.trim().toUpperCase(Locale.ROOT)) {
            case "MELEE", "PRZOD" -> BattleRow.MELEE;
            case "RANGED", "SRODEK" -> BattleRow.RANGED;
            case "SIEGE", "TYL" -> BattleRow.SIEGE;
            default -> null;
        };
    }

    private static String valueOr(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String normalizedName() {
        StringBuilder sb = new StringBuilder();
        boolean dash = false;
        for (char ch : name.toLowerCase(Locale.ROOT).toCharArray()) {
            if ((ch >= 'a' && ch <= 'z') || (ch >= '0' && ch <= '9')) {
                if (dash && sb.length() > 0) {
                    sb.append('-');
                }
                sb.append(ch);
                dash = false;
            } else {
                dash = true;
            }
        }
        return sb.toString();
    }

    @Override
    public String id() {
        return String.valueOf(ID);
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public int baseStrength() {
        return power;
    }

    @Override
    public BattleRow preferredRow() {
        normalize();
        return normalizedRows.isEmpty() ? null : normalizedRows.get(0);
    }

    @Override
    public List<BattleRow> playableRows() {
        normalize();
        return normalizedRows;
    }

    @Override
    public boolean isHero() {
        normalize();
        return type == Type.HERO || normalizedAbilities.contains(Ability.HERO);
    }

    @Override
    public CardKind cardKind() {
        return type == Type.SPECIAL ? CardKind.SPECIAL : CardKind.UNIT;
    }

    @Override
    public Set<Ability> abilities() {
        normalize();
        return normalizedAbilities;
    }

    @Override
    public String bondGroupId() {
        normalize();
        return normalizedBondGroup;
    }

    @Override
    public String musterGroupId() {
        normalize();
        return normalizedMusterGroup;
    }

    @Override
    public String transformsToId() {
        return transformsTo;
    }

    @Override
    public String avengerTokenId() {
        return avengerTokenId;
    }

    @Override
    public boolean isToken() {
        return token;
    }

    public int getId() {
        return ID;
    }

    public String getName() {
        return name;
    }

    public int getPower() {
        return power;
    }

    public Faction getFaction() {
        return faction;
    }

    public Row getRow() {
        return row;
    }

    public Type getType() {
        return type;
    }

    public Abillities getAbills() {
        return abills;
    }

    /** Maximum number of copies of this card that a deck may contain. */
    public int getCount() {
        return count;
    }

    public List<String> getAbilityNames() {
        return abilityNames == null ? List.of() : Collections.unmodifiableList(abilityNames);
    }

    public String getTransformsTo() {
        return transformsTo;
    }

    public String getAvengerTokenId() {
        return avengerTokenId;
    }

    public boolean isSpecial() {
        return type == Type.SPECIAL;
    }

    @Override
    public String toString() {
        return name + " [id=" + ID + ", power=" + power + ", " + faction
                + ", " + row + ", " + type + "]";
    }
}
