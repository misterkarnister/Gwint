package gwint;

/**
 * A faction leader. In this version the leader is selected before the match
 * and recorded on the player, but its ability has no gameplay effect
 * (the rules use power sums only). The ability id is kept so an effect can be
 * attached later without changing the pregame flow.
 */
public class Leader {

    private int id;
    private String name;
    private Karta.Faction faction;
    private String description;
    private String abilityId;

    /** Required by Gson. */
    public Leader() {
    }

    public Leader(int id, String name, Karta.Faction faction, String description, String abilityId) {
        this.id = id;
        this.name = name;
        this.faction = faction;
        this.description = description;
        this.abilityId = abilityId;
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Karta.Faction getFaction() {
        return faction;
    }

    public String getDescription() {
        return description;
    }

    public String getAbilityId() {
        return abilityId;
    }

    @Override
    public String toString() {
        return name + " (" + faction + ") - " + description;
    }
}
