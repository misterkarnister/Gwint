package gwint;

import gwint.engine.InstanceIdSource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Random;

/**
 * The deck a player assembles before the match. It contains card definitions;
 * duplicates are allowed up to {@link Karta#getCount()} and are turned into
 * distinct {@link CardInstance} objects when the match starts.
 */
public final class DeckList {

    private final CardCatalog catalog;
    private final Karta.Faction faction;
    private final List<Karta> cards = new ArrayList<>();

    public DeckList(CardCatalog catalog, Karta.Faction faction) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.faction = Objects.requireNonNull(faction, "faction");
    }

    public Karta.Faction faction() {
        return faction;
    }

    public List<Karta> cards() {
        return Collections.unmodifiableList(cards);
    }

    public int size() {
        return cards.size();
    }

    public int nonSpecialCount() {
        int n = 0;
        for (Karta k : cards) {
            if (!k.isSpecial()) {
                n++;
            }
        }
        return n;
    }

    public int specialCount() {
        return size() - nonSpecialCount();
    }

    public int totalPower() {
        int sum = 0;
        for (Karta k : cards) {
            sum += k.getPower();
        }
        return sum;
    }

    public int countOf(Karta card) {
        int n = 0;
        for (Karta k : cards) {
            if (k.getId() == card.getId()) {
                n++;
            }
        }
        return n;
    }

    public boolean allowed(Karta card) {
        return card.getFaction() == faction || card.getFaction() == Karta.Faction.NEUTRAL;
    }

    public boolean canAdd(Karta card) {
        if (card == null || !allowed(card)) {
            return false;
        }
        if (countOf(card) >= card.getCount()) {
            return false;
        }
        return !card.isSpecial() || specialCount() < GameRules.MAX_SPECIAL_CARDS;
    }

    public boolean add(Karta card) {
        if (!canAdd(card)) {
            return false;
        }
        return cards.add(card);
    }

    public int add(Karta card, int amount) {
        int added = 0;
        for (int i = 0; i < amount && add(card); i++) {
            added++;
        }
        return added;
    }

    public boolean remove(Karta card) {
        if (card == null || cards.isEmpty()) {
            return false;
        }
        for (int i = 0; i < cards.size(); i++) {
            if (cards.get(i).getId() == card.getId()) {
                cards.remove(i);
                return true;
            }
        }
        return false;
    }

    public List<String> validationErrors() {
        List<String> errors = new ArrayList<>();
        if (!catalog.playableFactions().contains(faction)) {
            errors.add("Neutral is not a playable faction on its own");
        }
        int units = nonSpecialCount();
        if (units < GameRules.MIN_NON_SPECIAL_CARDS) {
            errors.add("Need at least " + GameRules.MIN_NON_SPECIAL_CARDS
                    + " non-special cards (have " + units + ")");
        }
        if (specialCount() > GameRules.MAX_SPECIAL_CARDS) {
            errors.add("At most " + GameRules.MAX_SPECIAL_CARDS + " special cards are allowed");
        }
        for (Karta k : cards) {
            if (!allowed(k)) {
                errors.add("Card belongs to another faction: " + k.getName());
            }
            if (countOf(k) > k.getCount()) {
                errors.add("Too many copies of " + k.getName() + " (max " + k.getCount() + ")");
            }
        }
        return errors;
    }

    public boolean isValid() {
        return validationErrors().isEmpty();
    }

    /** Expands the definition list into shuffled, uniquely identified cards. */
    public List<CardInstance> toInstances(Random random) {
        return toInstances(random, InstanceIdSource.sequential());
    }

    /** Expands the definitions using a shared id source for the whole match. */
    public List<CardInstance> toInstances(Random random, InstanceIdSource ids) {
        List<CardInstance> instances = new ArrayList<>();
        for (Karta k : cards) {
            instances.add(new CardInstance(k, ids));
        }
        Collections.shuffle(instances, random);
        return instances;
    }

    /** Builds a legal deck at random. Used by the AI and by the console "auto" command. */
    public static DeckList randomDeck(CardCatalog catalog, Karta.Faction faction, Random random) {
        DeckList deck = new DeckList(catalog, faction);

        List<Karta> units = new ArrayList<>(catalog.unitsFor(faction));
        if (units.isEmpty()) {
            throw new IllegalStateException("No units available for faction " + faction);
        }
        Collections.shuffle(units, random);
        for (Karta k : units) {
            while (deck.nonSpecialCount() < GameRules.MIN_NON_SPECIAL_CARDS && deck.canAdd(k)) {
                deck.add(k);
            }
            if (deck.nonSpecialCount() >= GameRules.MIN_NON_SPECIAL_CARDS) {
                break;
            }
        }
        if (deck.nonSpecialCount() < GameRules.MIN_NON_SPECIAL_CARDS) {
            throw new IllegalStateException("Not enough cards to build a legal deck for " + faction);
        }

        int extraUnits = random.nextInt(5);
        for (int i = 0; i < extraUnits; i++) {
            deck.add(units.get(random.nextInt(units.size())));
        }

        List<Karta> specials = new ArrayList<>(catalog.specials());
        Collections.shuffle(specials, random);
        int wantedSpecials = random.nextInt(GameRules.MAX_SPECIAL_CARDS + 1);
        for (Karta s : specials) {
            while (deck.specialCount() < wantedSpecials && deck.canAdd(s)) {
                deck.add(s);
            }
        }
        return deck;
    }
}
