package gwint;

import gwint.engine.BattleRow;
import gwint.engine.GameState;
import gwint.engine.InstanceIdSource;
import gwint.engine.SideState;
import gwint.engine.StrengthCalculator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Random;

/** Mutable state belonging to one player for the whole match. */
public final class Player {

    private final String name;
    private final PlayerController controller;
    private final SideState side;

    private GameState gameState;
    private Leader leader;
    private boolean leaderUsed;
    private boolean passed;
    private int roundWins;

    public Player(String name, PlayerController controller) {
        this(name, controller, InstanceIdSource.sequential());
    }

    public Player(String name, PlayerController controller, InstanceIdSource ids) {
        this.name = Objects.requireNonNull(name, "name");
        this.controller = Objects.requireNonNull(controller, "controller");
        Objects.requireNonNull(ids, "ids");
        this.side = new SideState(name);
    }

    public String getName() {
        return name;
    }

    public PlayerController getController() {
        return controller;
    }

    public SideState side() {
        return side;
    }

    /** Connected by the match once both sides and the shared state exist. */
    public void attach(GameState gameState) {
        this.gameState = gameState;
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
        return side.deck();
    }

    public List<CardInstance> getHand() {
        return side.hand();
    }

    public List<CardInstance> getBoard(Karta.Row row) {
        BattleRow battleRow = BattleRow.fromKarta(row);
        if (battleRow == null) {
            return List.of();
        }
        return side.units(battleRow);
    }

    public List<CardInstance> boardCards() {
        return side.allUnits();
    }

    public void setDeck(List<CardInstance> cards) {
        side.setDeck(cards);
    }

    /** Draws up to {@code count} cards from the top (end) of the deck into the hand. */
    public int draw(int count) {
        return side.draw(count);
    }

    /** Puts cards back into the deck and reshuffles it. */
    public void returnToDeck(List<CardInstance> cards, Random random) {
        side.returnToDeck(cards, random);
    }

    /** Removes cards from the hand, e.g. when they are exchanged during mulligan. */
    public void removeFromHand(List<CardInstance> cards) {
        side.removeFromHand(cards);
    }

    /**
     * Legacy placement helper kept for compatibility. Ability-aware plays go
     * through {@code GameEngine.playFromHand} instead.
     */
    public boolean play(CardInstance card) {
        if (card == null || !side.removeFromHand(card)) {
            return false;
        }
        BattleRow row = BattleRow.fromKarta(card.card().getRow());
        if (row == null) {
            row = card.definition().preferredRow();
        }
        if (row == null) {
            return false;
        }
        side.placeUnit(card, row);
        return true;
    }

    public void clearBoard() {
        side.clearBoard();
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

    /** Effective score, including weather, bonds, morale and horns. */
    public int score() {
        if (gameState != null) {
            return StrengthCalculator.totalStrength(gameState, side);
        }
        int sum = 0;
        for (CardInstance ci : side.allUnits()) {
            sum += ci.card().getPower();
        }
        return sum;
    }

    @Override
    public String toString() {
        return name;
    }
}
