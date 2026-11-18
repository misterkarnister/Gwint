package gwint;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;

/**
 * Drives one complete match: pregame setup, mulligan, and up to three rounds.
 * Players are controlled through {@link PlayerController}, so the engine has
 * no dependency on the console or on Swing.
 */
public final class Match {

    private final CardCatalog catalog;
    private final LeaderCatalog leaders;
    private final Random random;
    private final PrintStream out;
    private final Player[] players;

    private int round = 1;
    private Player turn;
    private boolean finished;
    private String result = "";

    public Match(CardCatalog catalog, LeaderCatalog leaders,
                 PlayerController playerOne, PlayerController playerTwo,
                 Random random, PrintStream out) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.leaders = Objects.requireNonNull(leaders, "leaders");
        this.random = Objects.requireNonNull(random, "random");
        this.out = Objects.requireNonNull(out, "out");
        this.players = new Player[]{
            new Player(playerOne.name(), playerOne),
            new Player(playerTwo.name(), playerTwo)
        };
    }

    public void play() {
        out.println("=== Gwent ===");
        for (Player p : players) {
            setup(p);
        }
        for (Player p : players) {
            dealAndMulligan(p);
        }
        out.println("\n=== Match begins ===");
        turn = players[0];
        while (!finished) {
            playRound();
        }
        out.println("\n" + result);
    }

    private void setup(Player player) {
        PlayerController controller = player.getController();
        Karta.Faction faction = controller.chooseFaction(catalog);
        DeckList deck = controller.buildDeck(catalog, faction);
        int attempts = 0;
        while (!deck.isValid() && attempts++ < 10) {
            out.println(player.getName() + " submitted an invalid deck: " + deck.validationErrors());
            deck = controller.buildDeck(catalog, faction);
        }
        if (!deck.isValid()) {
            throw new IllegalStateException("Cannot build a legal deck for " + player.getName());
        }
        player.setLeader(controller.chooseLeader(leaders, faction));
        player.setDeck(deck.toInstances(random));
        out.println(player.getName() + ": " + faction + ", leader "
                + player.getLeader().getName() + ", " + deck.size() + " cards");
    }

    private void dealAndMulligan(Player player) {
        player.draw(GameRules.HAND_SIZE);
        List<CardInstance> requested = player.getController()
                .chooseMulligan(new ArrayList<>(player.getHand()));
        List<CardInstance> exchange = new ArrayList<>();
        if (requested != null) {
            for (CardInstance card : requested) {
                if (card != null && player.getHand().contains(card) && !exchange.contains(card)) {
                    exchange.add(card);
                    if (exchange.size() == GameRules.MAX_MULLIGAN) {
                        break;
                    }
                }
            }
        }
        player.removeFromHand(exchange);
        player.returnToDeck(exchange, random);
        player.draw(exchange.size());
        out.println(player.getName() + " exchanged " + exchange.size() + " card(s) at the start");
    }

    private void playRound() {
        out.println("\n--- Round " + round + " of " + GameRules.MAX_ROUNDS + " ---");
        while (!finished) {
            render();
            if (turn.isPassed()) {
                out.println(turn.getName() + " has already passed, skipping turn.");
                turn = other(turn);
                continue;
            }
            Actions.Action action = turn.getController().chooseAction(this, turn);
            boolean consumesTurn = apply(action, turn);
            if (finished) {
                return;
            }
            if (bothPassed()) {
                endRound();
                return;
            }
            if (consumesTurn) {
                turn = other(turn);
            }
        }
    }

    private boolean apply(Actions.Action action, Player player) {
        if (action instanceof Actions.Play play) {
            List<CardInstance> hand = player.getHand();
            if (play.handIndex < 0 || play.handIndex >= hand.size()) {
                out.println("Invalid card index - " + player.getName() + " passes instead.");
                player.setPassed(true);
                return true;
            }
            CardInstance card = hand.get(play.handIndex);
            player.play(card);
            out.println(player.getName() + " plays " + card.card().getName()
                    + " (" + card.card().getPower() + ") to " + card.card().getRow());
            return true;
        }
        if (action instanceof Actions.Pass) {
            player.setPassed(true);
            out.println(player.getName() + " passes.");
            return true;
        }
        if (action instanceof Actions.Leader) {
            if (player.useLeader()) {
                out.println(player.getName() + " uses leader " + player.getLeader().getName()
                        + " (no effect in power-only rules).");
            } else {
                out.println("Leader ability already used.");
            }
            return false;
        }
        out.println("Unknown action from " + player.getName() + "; passing.");
        player.setPassed(true);
        return true;
    }

    private boolean bothPassed() {
        return players[0].isPassed() && players[1].isPassed();
    }

    private void endRound() {
        int scoreOne = players[0].score();
        int scoreTwo = players[1].score();
        out.println("\nRound " + round + " scores: " + players[0].getName() + " " + scoreOne
                + " - " + scoreTwo + " " + players[1].getName());

        Player winner = null;
        if (scoreOne > scoreTwo) {
            winner = players[0];
        } else if (scoreTwo > scoreOne) {
            winner = players[1];
        }
        if (winner != null) {
            winner.addRoundWin();
            out.println(winner.getName() + " wins the round.");
        } else {
            out.println("The round is a draw.");
        }

        for (Player p : players) {
            p.clearBoard();
            p.setPassed(false);
        }

        if (players[0].getRoundWins() >= GameRules.ROUNDS_TO_WIN
                || players[1].getRoundWins() >= GameRules.ROUNDS_TO_WIN) {
            finish(winnerOfMatch().getName() + " wins the match "
                    + players[0].getRoundWins() + "-" + players[1].getRoundWins() + ".");
            return;
        }
        if (round >= GameRules.MAX_ROUNDS) {
            if (players[0].getRoundWins() == players[1].getRoundWins()) {
                finish("The match ends in a draw " + players[0].getRoundWins()
                        + "-" + players[1].getRoundWins() + ".");
            } else {
                finish(winnerOfMatch().getName() + " wins the match "
                        + players[0].getRoundWins() + "-" + players[1].getRoundWins() + ".");
            }
            return;
        }

        round++;
        turn = winner != null ? winner : other(turn);
    }

    private Player winnerOfMatch() {
        return players[0].getRoundWins() >= players[1].getRoundWins() ? players[0] : players[1];
    }

    private void finish(String message) {
        finished = true;
        result = message;
        out.println("=== " + message + " ===");
    }

    private void render() {
        out.println("Scores: " + players[0].getName() + " " + players[0].score()
                + " | " + players[1].getName() + " " + players[1].score()
                + "   (round wins " + players[0].getRoundWins() + "-"
                + players[1].getRoundWins() + ")");
        for (Player p : players) {
            out.println(p.getName() + " board:");
            for (Karta.Row row : Karta.Row.values()) {
                List<CardInstance> cards = p.getBoard(row);
                if (cards.isEmpty()) {
                    continue;
                }
                StringBuilder sb = new StringBuilder("  " + row + ": ");
                for (CardInstance ci : cards) {
                    sb.append(ci.card().getName()).append("(")
                            .append(ci.card().getPower()).append(") ");
                }
                out.println(sb.toString().stripTrailing());
            }
        }
        out.println("Turn: " + turn.getName()
                + (turn.isLeaderUsed() ? " (leader used)" : " (leader available)"));
    }

    private Player other(Player player) {
        return player == players[0] ? players[1] : players[0];
    }

    public List<CardInstance> handOf(Player player) {
        return player.getHand();
    }

    public List<CardInstance> boardOf(Player player, Karta.Row row) {
        return player.getBoard(row);
    }

    public int scoreOf(Player player) {
        return player.score();
    }

    public int round() {
        return round;
    }

    public Player currentPlayer() {
        return turn;
    }

    public Player opponentOf(Player player) {
        return other(player);
    }

    public boolean isFinished() {
        return finished;
    }

    public String result() {
        return result;
    }

    public Player[] players() {
        return players.clone();
    }

    public CardCatalog catalog() {
        return catalog;
    }

    public LeaderCatalog leaders() {
        return leaders;
    }

    public Random random() {
        return random;
    }
}
