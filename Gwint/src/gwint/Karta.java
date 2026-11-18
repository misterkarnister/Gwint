package gwint;

/**
 * Definition of a single card, as stored in {@code assets/data/karty.json}.
 * One instance of this class describes a card type; {@link CardInstance}
 * represents a concrete copy of it that is actually in a player's deck or hand.
 */
public class Karta {

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
        OTHER,
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

    public boolean isSpecial() {
        return type == Type.SPECIAL;
    }

    @Override
    public String toString() {
        return name + " [id=" + ID + ", power=" + power + ", " + faction
                + ", " + row + ", " + type + "]";
    }
}
