package gwint;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;

/**
 * Console entry point. Run with {@code --seed N} for reproducible AI choices
 * and {@code --preview} to open the Swing card preview after the match.
 */
public final class Gwint {

    private Gwint() {
    }

    public static void main(String[] args) {
        // jestem tu i dzialam
        long seed = System.currentTimeMillis();
        boolean preview = false;
        boolean gui = false;
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
            } else if ("--gui".equals(args[i])) {
                gui = true;
            }
        }

        CardCatalog catalog = CardCatalog.load();
        LeaderCatalog leaders = LeaderCatalog.load();
        Random random = new Random(seed);

        ConsoleHumanController consoleHuman = new ConsoleHumanController(new Scanner(System.in), System.out);
        RandomAiController randomAi = new RandomAiController(random);

        if (gui) {
            runGui(catalog, leaders, consoleHuman, randomAi, random);
            return;
        }

        PlayerController human = consoleHuman;
        PlayerController ai = randomAi;
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

    private static void runGui(CardCatalog catalog, LeaderCatalog leaders,
                               ConsoleHumanController consoleHuman, RandomAiController randomAi,
                               Random random) {
        BoardView view = createBoardView();
        PlayerController human = new SwingPlayerController(consoleHuman, view);
        PlayerController unitAi = new UnitFirstAiController(randomAi, random);
        PlayerController ai = new DelayedAiController(unitAi, view, 600);
        Match match = new Match(catalog, leaders, human, ai, random, System.out,
                (current, event) -> view.refresh(current));
        Thread game = new Thread(() -> {
            match.play();
            view.showResult(match.result());
        }, "gwint-game");
        game.start();
    }

    private static BoardView createBoardView() {
        AtomicReference<BoardView> reference = new AtomicReference<>();
        try {
            SwingUtilities.invokeAndWait(() -> reference.set(new BoardView()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while creating the board", e);
        } catch (InvocationTargetException e) {
            throw new IllegalStateException("Failed to create the board", e);
        }
        return reference.get();
    }
}
