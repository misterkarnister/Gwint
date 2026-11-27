package gwint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

final class GameSnapshot {

    private final int round;
    private final String status;
    private final boolean finished;
    private final String result;
    private final Side player;
    private final Side opponent;

    private GameSnapshot(int round, String status, boolean finished, String result,
                         Side player, Side opponent) {
        this.round = round;
        this.status = status;
        this.finished = finished;
        this.result = result;
        this.player = player;
        this.opponent = opponent;
    }

    static GameSnapshot of(Match match) {
        return of(match, "");
    }

    static GameSnapshot of(Match match, String status) {
        Player[] players = match.players();
        int playerLives = Math.max(0, GameRules.ROUNDS_TO_WIN - players[1].getRoundWins());
        int opponentLives = Math.max(0, GameRules.ROUNDS_TO_WIN - players[0].getRoundWins());
        Side player = Side.of(players[0], match.currentPlayer() == players[0], playerLives);
        Side opponent = Side.of(players[1], match.currentPlayer() == players[1], opponentLives);
        return new GameSnapshot(match.round(), status == null ? "" : status,
                match.isFinished(), match.result(), player, opponent);
    }

    int round() {
        return round;
    }

    String status() {
        return status;
    }

    boolean finished() {
        return finished;
    }

    String result() {
        return result;
    }

    Side player() {
        return player;
    }

    Side opponent() {
        return opponent;
    }

    static final class Side {

        private final String name;
        private final boolean active;
        private final boolean passed;
        private final int roundWins;
        private final int lives;
        private final int score;
        private final int deckCount;
        private final String leaderName;
        private final String leaderDescription;
        private final Karta.Faction faction;
        private final List<CardRef> hand;
        private final List<CardRef> board;
        private final Map<Karta.Row, List<CardRef>> rows;

        private Side(String name, boolean active, boolean passed, int roundWins, int lives, int score,
                     int deckCount, String leaderName, String leaderDescription, Karta.Faction faction,
                     List<CardRef> hand, List<CardRef> board,
                     Map<Karta.Row, List<CardRef>> rows) {
            this.name = name;
            this.active = active;
            this.passed = passed;
            this.roundWins = roundWins;
            this.lives = lives;
            this.score = score;
            this.deckCount = deckCount;
            this.leaderName = leaderName;
            this.leaderDescription = leaderDescription;
            this.faction = faction;
            this.hand = hand;
            this.board = board;
            this.rows = rows;
        }

        static Side of(Player player, boolean active, int lives) {
            Map<Karta.Row, List<CardRef>> rows = new EnumMap<>(Karta.Row.class);
            for (Karta.Row row : Karta.Row.values()) {
                rows.put(row, new ArrayList<>());
            }
            List<CardRef> allBoard = new ArrayList<>();
            for (Karta.Row row : Karta.Row.values()) {
                Karta.Row shownRow = row == Karta.Row.OTHER ? Karta.Row.SRODEK : row;
                for (CardInstance instance : player.getBoard(row)) {
                    CardRef ref = CardRef.of(instance);
                    rows.get(shownRow).add(ref);
                    allBoard.add(ref);
                }
            }
            Map<Karta.Row, List<CardRef>> immutableRows = new EnumMap<>(Karta.Row.class);
            for (Karta.Row row : Karta.Row.values()) {
                immutableRows.put(row, Collections.unmodifiableList(new ArrayList<>(rows.get(row))));
            }
            Leader leader = player.getLeader();
            return new Side(player.getName(), active, player.isPassed(), player.getRoundWins(),
                    lives, player.score(), player.getDeck().size(),
                    leader == null ? "" : leader.getName(),
                    leader == null ? "" : leader.getDescription(),
                    leader == null ? null : leader.getFaction(),
                    immutableList(player.getHand()), Collections.unmodifiableList(allBoard),
                    Collections.unmodifiableMap(immutableRows));
        }

        String name() {
            return name;
        }

        boolean active() {
            return active;
        }

        boolean passed() {
            return passed;
        }

        int roundWins() {
            return roundWins;
        }

        int lives() {
            return lives;
        }

        int score() {
            return score;
        }

        int deckCount() {
            return deckCount;
        }

        String leaderName() {
            return leaderName;
        }

        String leaderDescription() {
            return leaderDescription;
        }

        Karta.Faction faction() {
            return faction;
        }

        List<CardRef> hand() {
            return hand;
        }

        List<CardRef> board() {
            return board;
        }

        List<CardRef> board(Karta.Row row) {
            return rows.get(row);
        }

        int rowScore(Karta.Row row) {
            int sum = 0;
            for (CardRef ref : rows.get(row)) {
                sum += ref.card().getPower();
            }
            return sum;
        }
    }

    static final class CardRef {

        private final int uid;
        private final Karta card;

        private CardRef(int uid, Karta card) {
            this.uid = uid;
            this.card = card;
        }

        static CardRef of(CardInstance instance) {
            return new CardRef(instance.uid(), instance.card());
        }

        int uid() {
            return uid;
        }

        Karta card() {
            return card;
        }
    }

    private static List<CardRef> immutableList(List<CardInstance> cards) {
        List<CardRef> result = new ArrayList<>();
        for (CardInstance instance : cards) {
            result.add(CardRef.of(instance));
        }
        return Collections.unmodifiableList(result);
    }
}
