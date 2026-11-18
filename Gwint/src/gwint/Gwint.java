package gwint;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Scanner;

/**
 * Console entry point. Run with {@code --seed N} for reproducible AI choices
 * and {@code --preview} to open the Swing card preview after the match.
 */
public final class Gwint {

    private Gwint() {
    }

    public static void main(String[] args) {
        long seed = System.currentTimeMillis();
        boolean preview = false;
        for (int i = 0; i < args.length; i++) {
            if ("--seed".equals(args[i]) && i + 1 < args.length) {
                try {
                    seed = Long.parseLong(args[i + 1]);
                } catch (NumberFormatException e) {
                    System.out.println("Ignoring invalid seed: " + args[i + 1]);
                }
                i++;
            } else if ("--preview".equals(args[i])) {
                preview = true;
            }
        }

        CardCatalog catalog = CardCatalog.load();
        LeaderCatalog leaders = LeaderCatalog.load();
        Random random = new Random(seed);

        PlayerController human = new ConsoleHumanController(new Scanner(System.in), System.out);
        PlayerController ai = new RandomAiController(random);
        Match match = new Match(catalog, leaders, human, ai, random, System.out);
        match.play();

        if (preview) {
            List<Karta> cards = new ArrayList<>();
            Player player = match.players()[0];
            for (CardInstance ci : player.getDeck()) {
                cards.add(ci.card());
            }
            for (CardInstance ci : player.getHand()) {
                cards.add(ci.card());
            }
            for (CardInstance ci : player.boardCards()) {
                cards.add(ci.card());
            }
            GraphicView.pokaz(cards);
        }
    }
}
