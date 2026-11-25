package gwint.engine;

import gwint.CardInstance;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Mutable state belonging to one side of the battlefield. */
public final class SideState {

    private final String name;
    private final List<CardInstance> deck = new ArrayList<>();
    private final List<CardInstance> hand = new ArrayList<>();
    private final List<CardInstance> discard = new ArrayList<>();
    private final EnumMap<BattleRow, List<CardInstance>> units = new EnumMap<>(BattleRow.class);
    private final EnumMap<BattleRow, CardInstance> specials = new EnumMap<>(BattleRow.class);

    public SideState(String name) {
        this.name = Objects.requireNonNull(name, "name");
        for (BattleRow row : BattleRow.values()) {
            units.put(row, new ArrayList<>());
        }
    }

    public String name() {
        return name;
    }

    public List<CardInstance> deck() {
        return Collections.unmodifiableList(deck);
    }

    public List<CardInstance> hand() {
        return Collections.unmodifiableList(hand);
    }

    public List<CardInstance> discard() {
        return Collections.unmodifiableList(discard);
    }

    public List<CardInstance> units(BattleRow row) {
        return Collections.unmodifiableList(units.get(row));
    }

    public List<CardInstance> allUnits() {
        List<CardInstance> all = new ArrayList<>();
        for (BattleRow row : BattleRow.values()) {
            all.addAll(units.get(row));
        }
        return all;
    }

    public CardInstance special(BattleRow row) {
        return specials.get(row);
    }

    public void setDeck(List<CardInstance> cards) {
        deck.clear();
        deck.addAll(cards);
    }

    /** Draws from the top (end) of the deck into the hand. */
    public int draw(int count) {
        int drawn = 0;
        while (drawn < count && !deck.isEmpty()) {
            hand.add(deck.remove(deck.size() - 1));
            drawn++;
        }
        return drawn;
    }

    public void addToHand(CardInstance card) {
        hand.add(card);
    }

    public boolean removeFromHand(CardInstance card) {
        return removeByIdentity(hand, card);
    }

    public boolean removeFromDeck(CardInstance card) {
        return removeByIdentity(deck, card);
    }

    public void addToDiscard(CardInstance card) {
        discard.add(card);
    }

    public boolean removeFromDiscard(CardInstance card) {
        return removeByIdentity(discard, card);
    }

    public void returnToDeck(List<CardInstance> cards, java.util.Random random) {
        deck.addAll(cards);
        Collections.shuffle(deck, random);
    }

    public void removeFromHand(List<CardInstance> cards) {
        hand.removeAll(cards);
    }

    public void placeUnit(CardInstance card, BattleRow row) {
        Objects.requireNonNull(row, "row");
        card.setRow(row);
        units.get(row).add(card);
    }

    public boolean removeUnit(CardInstance card) {
        for (BattleRow row : BattleRow.values()) {
            if (removeByIdentity(units.get(row), card)) {
                return true;
            }
        }
        return false;
    }

    public CardInstance findUnit(int uid) {
        for (CardInstance card : allUnits()) {
            if (card.uid() == uid) {
                return card;
            }
        }
        return null;
    }

    /** Places a special card in a row slot. Returns false when the slot is occupied. */
    public boolean setSpecial(BattleRow row, CardInstance card) {
        if (specials.get(row) != null) {
            return false;
        }
        specials.put(row, card);
        return true;
    }

    public CardInstance removeSpecial(BattleRow row) {
        return specials.remove(row);
    }

    /** Clears board state without moving cards; used only by legacy compatibility paths. */
    public void clearBoard() {
        for (BattleRow row : BattleRow.values()) {
            units.get(row).clear();
            specials.remove(row);
        }
    }

    public List<CardInstance> takeUnitsForCleanup() {
        List<CardInstance> all = allUnits();
        for (BattleRow row : BattleRow.values()) {
            units.get(row).clear();
        }
        return all;
    }

    public List<CardInstance> takeSpecialsForCleanup() {
        List<CardInstance> all = new ArrayList<>();
        for (BattleRow row : BattleRow.values()) {
            CardInstance special = specials.remove(row);
            if (special != null) {
                all.add(special);
            }
        }
        return all;
    }

    private static boolean removeByIdentity(List<CardInstance> list, CardInstance card) {
        if (card == null) {
            return false;
        }
        for (int i = 0; i < list.size(); i++) {
            CardInstance current = list.get(i);
            if (current == card || current.uid() == card.uid()) {
                list.remove(i);
                return true;
            }
        }
        return false;
    }

    @Override
    public String toString() {
        return name;
    }
}
