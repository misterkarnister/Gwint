package gwint;

import gwint.engine.Ability;
import gwint.engine.BattleRow;
import gwint.engine.CardChoice;
import gwint.engine.ChoiceRequest;
import gwint.engine.GameEngine;
import gwint.engine.GameState;
import gwint.engine.IllegalPlayException;
import gwint.engine.InstanceIdSource;
import gwint.engine.PlayComplete;
import gwint.engine.PlayOutcome;
import gwint.engine.PlayPending;
import gwint.engine.RowChoice;
import gwint.engine.RowModifiers;
import gwint.engine.RowPurpose;
import gwint.engine.SequentialIds;
import gwint.engine.SideState;
import gwint.engine.StrengthBreakdown;
import gwint.engine.WeatherType;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.Set;

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
    private final MatchListener listener;
    private final InstanceIdSource ids;
    private final GameState state;
    private final GameEngine engine;

    private int round = 1;
    private Player turn;
    private boolean finished;
    private String result = "";

    public Match(CardCatalog catalog, LeaderCatalog leaders,
                 PlayerController playerOne, PlayerController playerTwo,
                 Random random, PrintStream out) {
        this(catalog, leaders, playerOne, playerTwo, random, out, null);
    }

    public Match(CardCatalog catalog, LeaderCatalog leaders,
                 PlayerController playerOne, PlayerController playerTwo,
                 Random random, PrintStream out, MatchListener listener) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.leaders = Objects.requireNonNull(leaders, "leaders");
        this.random = Objects.requireNonNull(random, "random");
        this.out = Objects.requireNonNull(out, "out");
        this.listener = listener;
        this.ids = new SequentialIds();
        this.players = new Player[]{
            new Player(playerOne.name(), playerOne, ids),
            new Player(playerTwo.name(), playerTwo, ids)
        };
        this.state = new GameState(players[0].side(), players[1].side(), catalog, ids);
        this.engine = new GameEngine(state);
        players[0].attach(state);
        players[1].attach(state);
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
        player.setDeck(deck.toInstances(random, ids));
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
            notifyEvent(MatchListener.Event.ACTION);
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
            try {
                resolve(engine.playFromHand(player.side(), card), player);
            } catch (IllegalPlayException | IllegalArgumentException e) {
                out.println("Cannot play " + card.card().getName() + ": " + e.getMessage()
                        + " - " + player.getName() + " passes instead.");
                player.setPassed(true);
                return true;
            }
            out.println(player.getName() + " plays " + card.card().getName()
                    + " (" + engine.effectiveStrength(card) + ")" + describePlayEffect(card));
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

    /** Adds immediate feedback for effects that changed the board on this play. */
    private String describePlayEffect(CardInstance card) {
        StringBuilder sb = new StringBuilder();
        Set<Ability> abilities = card.definition().abilities();

        if (abilities.contains(Ability.SPY)) {
            sb.append(" [Spy: +2 cards, placed on opponent board]");
        }
        if (abilities.contains(Ability.CLEAR_WEATHER)) {
            sb.append(" [weather cleared]");
        }
        for (Ability ability : abilities) {
            for (WeatherType type : WeatherType.forAbility(ability)) {
                sb.append(" [").append(type.displayName()).append(']');
            }
        }
        if (abilities.contains(Ability.SCORCH_GLOBAL)) {
            sb.append(" [Scorch: strongest non-hero units destroyed]");
        }
        if (abilities.contains(Ability.SCORCH_MELEE)) {
            sb.append(" [Scorch: strongest enemy Melee unit destroyed]");
        }
        if (abilities.contains(Ability.SCORCH_RANGED)) {
            sb.append(" [Scorch: strongest enemy Ranged unit destroyed]");
        }
        if (abilities.contains(Ability.SCORCH_SIEGE)) {
            sb.append(" [Scorch: strongest enemy Siege unit destroyed]");
        }
        if (abilities.contains(Ability.MARDROEME)) {
            sb.append(" [Berserkers transformed]");
        }
        if (abilities.contains(Ability.MUSTER)) {
            sb.append(" [Muster: matching cards summoned]");
        }
        if (abilities.contains(Ability.MEDIC)) {
            sb.append(" [Medic: unit revived]");
        }
        if (abilities.contains(Ability.DECOY)) {
            sb.append(" [Decoy: unit returned to hand]");
        }

        BattleRow row = card.row();
        SideState owner = row == null ? null : engine.state().ownerOf(card);
        if (owner != null) {
            RowModifiers modifiers = engine.rowModifiers(owner, row);
            if (modifiers.hasEffects()) {
                sb.append(" | ").append(row).append(": ").append(modifiers.describe());
            }
        }
        return sb.toString();
    }

    /** Drives every outstanding target prompt to completion. */
    private void resolve(PlayOutcome outcome, Player player) {
        while (outcome instanceof PlayPending pending) {
            Object answer = askChoice(pending.request(), player);
            outcome = pending.session().answer(answer);
        }
        if (!(outcome instanceof PlayComplete)) {
            throw new IllegalStateException("Play resolution did not complete");
        }
    }

    private Object askChoice(ChoiceRequest request, Player player) {
        PlayerController controller = player.getController();
        if (request instanceof RowChoice row) {
            return row.purpose() == RowPurpose.SPECIAL
                    ? controller.chooseSpecialRow(this, player, row.card(), row.options())
                    : controller.chooseRow(this, player, row.card(), row.options());
        }
        if (request instanceof CardChoice card) {
            return card.purpose() == gwint.engine.CardPurpose.REVIVE
                    ? controller.chooseRevival(this, player, card.options())
                    : controller.chooseDecoyTarget(this, player, card.options());
        }
        throw new IllegalStateException("Unknown choice request: " + request);
    }

    private boolean bothPassed() {
        return players[0].isPassed() && players[1].isPassed();
    }

    private void endRound() {
        out.println("\nRound " + round + " final board (before scoring):");
        renderRoundBreakdown();
        GameEngine.RoundResult scores = engine.finishRound();
        out.println("\nRound " + round + " scores: " + players[0].getName() + " "
                + scores.firstScore() + " - " + scores.secondScore() + " " + players[1].getName());

        Player winner = scores.winnerIndex() == null ? null : players[scores.winnerIndex()];
        if (winner != null) {
            winner.addRoundWin();
            out.println(winner.getName() + " wins the round.");
        } else {
            out.println("The round is a draw.");
        }

        for (Player p : players) {
            p.setPassed(false);
        }
        notifyEvent(MatchListener.Event.ROUND_END);

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
        engine.beginNextRound();
        turn = winner != null ? winner : other(turn);
    }

    /** Prints the exact per-row contribution to a round's final score. */
    private void renderRoundBreakdown() {
        for (Player p : players) {
            List<String> rows = new ArrayList<>();
            for (BattleRow row : BattleRow.values()) {
                RowModifiers modifiers = engine.rowModifiers(p.side(), row);
                if (modifiers.effectiveStrength() == 0 && !modifiers.hasEffects()) {
                    continue;
                }
                rows.add(row + " " + modifiers.describeWithStrength());
            }
            out.println("  " + p.getName() + ": "
                    + (rows.isEmpty() ? "0" : String.join(" | ", rows)));
        }
    }

    private Player winnerOfMatch() {
        return players[0].getRoundWins() >= players[1].getRoundWins() ? players[0] : players[1];
    }

    private void finish(String message) {
        finished = true;
        result = message;
        out.println("=== " + message + " ===");
        notifyEvent(MatchListener.Event.MATCH_END);
    }

    private void notifyEvent(MatchListener.Event event) {
        if (listener != null) {
            listener.onStateChanged(this, event);
        }
    }

    private void render() {
        out.println("Scores: " + players[0].getName() + " " + engine.totalStrength(players[0].side())
                + " | " + players[1].getName() + " " + engine.totalStrength(players[1].side())
                + "   (round wins " + players[0].getRoundWins() + "-"
                + players[1].getRoundWins() + ")");
        renderWeather();
        for (Player p : players) {
            out.println(p.getName() + " board (" + engine.totalStrength(p.side()) + "):");
            for (BattleRow row : BattleRow.values()) {
                renderRow(p, row);
            }
        }
        out.println("Turn: " + turn.getName()
                + (turn.isLeaderUsed() ? " (leader used)" : " (leader available)"));
    }

    private void renderWeather() {
        Set<WeatherType> active = engine.state().weather();
        if (active.isEmpty()) {
            out.println("Weather: clear");
            return;
        }
        List<String> names = new ArrayList<>();
        for (WeatherType type : WeatherType.values()) {
            if (active.contains(type)) {
                names.add(type.displayName());
            }
        }
        out.println("Weather: " + String.join(", ", names));
    }

    private void renderRow(Player player, BattleRow row) {
        SideState side = player.side();
        RowModifiers modifiers = engine.rowModifiers(side, row);
        List<CardInstance> cards = side.units(row);
        CardInstance special = side.special(row);
        if (cards.isEmpty() && special == null && !modifiers.hasEffects()) {
            return;
        }

        StringBuilder sb = new StringBuilder("  ").append(row).append(": ")
                .append(modifiers.describeWithStrength());
        if (special != null) {
            sb.append(" | row card: ").append(special.definition().name());
        }
        if (!cards.isEmpty()) {
            sb.append(" | ");
            for (CardInstance ci : cards) {
                StrengthBreakdown breakdown = engine.breakdown(ci);
                sb.append(ci.card().getName()).append('(').append(breakdown.finalStrength());
                if (breakdown.isModified()) {
                    sb.append('[').append(String.join("+", breakdown.tags())).append(']');
                }
                sb.append(") ");
            }
        }
        out.println(sb.toString().stripTrailing());
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
        return engine.totalStrength(player.side());
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

    /** Exposed for engine-level integration tests. */
    public GameState gameState() {
        return state;
    }

    public GameEngine engine() {
        return engine;
    }

    public SideState sideOf(Player player) {
        return player.side();
    }
}
