package gwint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;

/** Mutable state belonging to one player for the whole match. */
public final class Player {

    private final String name;
    private final PlayerController controller;
    private final List<CardInstance> deck = new ArrayList<>();
    private final List<CardInstance> hand = new ArrayList<>();
    private final Map<Karta.Row, List<CardInstance>> board = new EnumMap<>(Karta.Row.class);

    private Leader leader;
    private boolean leaderUsed;
    private boolean passed;
    private int roundWins;
    private int lives = GameRules.START_LIVES;

    public Player(String name, PlayerController controller) {
        this.name = Objects.requireNonNull(name, "name");
        this.controller = Objects.requireNonNull(controller, "controller");
        for (Karta.Row row : Karta.Row.values()) {
            board.put(row, new ArrayList<>());
        }
    }

    public String getName() {
        return name;
    }

    public PlayerController getController() {
        return controller;
    }

    public Leader getLeader() {
        return leader;
    }

    public void setLeader(Leader leader) {
        this.leader = leader;
    }

    public boolean isLeaderUsed() {
        return leaderUsed;
    }

    /** Marks the leader ability as spent. Returns false if it was already used. */
    public boolean useLeader() {
        if (leader == null || leaderUsed) {
            return false;
        }
        leaderUsed = true;
        return true;
    }

    public List<CardInstance> getDeck() {
        return Collections.unmodifiableList(deck);
    }

    public List<CardInstance> getHand() {
        return Collections.unmodifiableList(hand);
    }

    public List<CardInstance> getBoard(Karta.Row row) {
        return Collections.unmodifiableList(board.get(row));
    }

    public List<CardInstance> boardCards() {
        List<CardInstance> all = new ArrayList<>();
        for (Karta.Row row : Karta.Row.values()) {
            all.addAll(board.get(row));
        }
        return all;
    }

    public void setDeck(List<CardInstance> cards) {
        deck.clear();
        deck.addAll(cards);
    }

    /** Draws up to {@code count} cards from the top (end) of the deck into the hand. */
    public int draw(int count) {
        int drawn = 0;
        while (drawn < count && !deck.isEmpty()) {
            hand.add(deck.remove(deck.size() - 1));
            drawn++;
        }
        return drawn;
    }

    /** Puts cards back into the deck and reshuffles it. */
    public void returnToDeck(List<CardInstance> cards, Random random) {
        deck.addAll(cards);
        Collections.shuffle(deck, random);
    }

    /** Removes cards from the hand, e.g. when they are exchanged during mulligan. */
    public void removeFromHand(List<CardInstance> cards) {
        hand.removeAll(cards);
    }

    /** Moves a card from the hand to its row on the board. */
    public boolean play(CardInstance card) {
        if (card == null || !hand.remove(card)) {
            return false;
        }
        board.get(card.card().getRow()).add(card);
        return true;
    }

    public void clearBoard() {
        for (Karta.Row row : Karta.Row.values()) {
            board.get(row).clear();
        }
    }

    public boolean isPassed() {
        return passed;
    }

    public void setPassed(boolean passed) {
        this.passed = passed;
    }

    public int getRoundWins() {
        return roundWins;
    }

    public void addRoundWin() {
        roundWins++;
    }

    public int getLives() {
        return lives;
    }

    public void loseLife() {
        if (lives > 0) {
            lives--;
        }
    }

    /** Sum of the power of all cards currently on the player's board. */
    public int score() {
        int sum = 0;
        for (CardInstance ci : boardCards()) {
            sum += ci.card().getPower();
        }
        return sum;
    }

    @Override
    public String toString() {
        return name;
    }
}
