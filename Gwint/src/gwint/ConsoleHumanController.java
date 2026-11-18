package gwint;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Scanner;

/** Console front-end for the human player. */
public final class ConsoleHumanController implements PlayerController {

    private final Scanner scanner;
    private final PrintStream out;
    private Karta.Faction faction;

    public ConsoleHumanController(Scanner scanner, PrintStream out) {
        this.scanner = Objects.requireNonNull(scanner, "scanner");
        this.out = Objects.requireNonNull(out, "out");
    }

    @Override
    public String name() {
        return "You";
    }

    @Override
    public Karta.Faction chooseFaction(CardCatalog catalog) {
        List<Karta.Faction> factions = catalog.playableFactions();
        out.println("\nChoose a faction:");
        for (int i = 0; i < factions.size(); i++) {
            out.println("  " + (i + 1) + ". " + factions.get(i));
        }
        int choice = readNumber("Faction number: ", 1, factions.size());
        faction = factions.get(choice - 1);
        return faction;
    }

    @Override
    public DeckList buildDeck(CardCatalog catalog, Karta.Faction chosenFaction) {
        faction = chosenFaction;
        DeckList deck = new DeckList(catalog, faction);
        out.println("\n--- Deck building ---");
        out.println("Need at least " + GameRules.MIN_NON_SPECIAL_CARDS
                + " non-special cards and at most " + GameRules.MAX_SPECIAL_CARDS + " special cards.");
        printHelp();
        while (true) {
            String line = prompt("deck> ");
            if (line == null) {
                return deck.isValid() ? deck : DeckList.randomDeck(catalog, faction, new java.util.Random());
            }
            if (line.isEmpty()) {
                continue;
            }
            String[] parts = line.split("\\s+");
            switch (parts[0].toLowerCase()) {
                case "help" -> printHelp();
                case "list" -> listCards(catalog, parts.length > 1 ? parts[1] : "all");
                case "show" -> printDeck(deck);
                case "add" -> addCard(catalog, deck, parts);
                case "remove" -> removeCard(catalog, deck, parts);
                case "auto" -> {
                    DeckList generated = DeckList.randomDeck(catalog, faction, new java.util.Random());
                    out.println("Generated deck: " + generated.size() + " cards ("
                            + generated.nonSpecialCount() + " units, "
                            + generated.specialCount() + " specials).");
                    return generated;
                }
                case "done" -> {
                    if (deck.isValid()) {
                        return deck;
                    }
                    out.println("Deck is not legal yet:");
                    for (String error : deck.validationErrors()) {
                        out.println("  - " + error);
                    }
                }
                default -> out.println("Unknown command: " + parts[0]);
            }
        }
    }

    @Override
    public Leader chooseLeader(LeaderCatalog leaders, Karta.Faction chosenFaction) {
        List<Leader> options = leaders.forFaction(chosenFaction);
        if (options.isEmpty()) {
            throw new IllegalStateException("No leaders for faction " + chosenFaction);
        }
        out.println("\nChoose a leader:");
        for (int i = 0; i < options.size(); i++) {
            out.println("  " + (i + 1) + ". " + options.get(i));
        }
        int choice = readNumber("Leader number: ", 1, options.size());
        return options.get(choice - 1);
    }

    @Override
    public List<CardInstance> chooseMulligan(List<CardInstance> hand) {
        List<CardInstance> exchange = new ArrayList<>();
        if (hand.isEmpty()) {
            return exchange;
        }
        out.println("\nYour starting hand:");
        printNumbered(hand);
        out.println("Exchange up to " + GameRules.MAX_MULLIGAN
                + " cards (numbers separated by spaces), or type 'none':");
        String line = prompt("Exchange: ");
        if (line == null) {
            return exchange;
        }
        line = line.trim();
        if (line.isEmpty() || line.equalsIgnoreCase("none")) {
            return exchange;
        }
        for (String token : line.split("\\s+")) {
            try {
                int index = Integer.parseInt(token) - 1;
                if (index >= 0 && index < hand.size() && !exchange.contains(hand.get(index))) {
                    exchange.add(hand.get(index));
                    if (exchange.size() == GameRules.MAX_MULLIGAN) {
                        break;
                    }
                }
            } catch (NumberFormatException ignored) {
                // Ignore non-numeric tokens.
            }
        }
        return exchange;
    }

    @Override
    public Actions.Action chooseAction(Match match, Player self) {
        List<CardInstance> hand = self.getHand();
        out.println("\nYour hand:");
        if (hand.isEmpty()) {
            out.println("  (empty)");
        } else {
            printNumbered(hand);
        }
        out.println("Commands: play <number>, pass, leader, help");
        while (true) {
            String line = prompt("Your move: ");
            if (line == null) {
                return new Actions.Pass();
            }
            line = line.trim();
            if (line.isEmpty()) {
                continue;
            }
            String[] parts = line.split("\\s+");
            switch (parts[0].toLowerCase()) {
                case "play" -> {
                    if (parts.length < 2) {
                        out.println("Usage: play <number>");
                        continue;
                    }
                    try {
                        int index = Integer.parseInt(parts[1]) - 1;
                        if (index >= 0 && index < hand.size()) {
                            return new Actions.Play(index);
                        }
                        out.println("No card with number " + parts[1]);
                    } catch (NumberFormatException e) {
                        out.println("No card with number " + parts[1]);
                    }
                }
                case "pass" -> {
                    return new Actions.Pass();
                }
                case "leader" -> {
                    if (match.currentPlayer().isLeaderUsed()) {
                        out.println("Leader ability already used.");
                    } else {
                        return new Actions.Leader();
                    }
                }
                case "help" -> out.println("Commands: play <number>, pass, leader, help");
                default -> out.println("Unknown command: " + parts[0]);
            }
        }
    }

    private void addCard(CardCatalog catalog, DeckList deck, String[] parts) {
        if (parts.length < 2) {
            out.println("Usage: add <id> [amount]");
            return;
        }
        Karta card = parseCard(catalog, parts[1]);
        if (card == null) {
            return;
        }
        if (!deck.allowed(card)) {
            out.println(card.getName() + " belongs to another faction.");
            return;
        }
        int amount = 1;
        if (parts.length > 2) {
            try {
                amount = Integer.parseInt(parts[2]);
            } catch (NumberFormatException e) {
                out.println("Invalid amount: " + parts[2]);
                return;
            }
        }
        int added = deck.add(card, amount);
        out.println("Added " + added + " copy/copies of " + card.getName()
                + " (max " + card.getCount() + ").");
    }

    private void removeCard(CardCatalog catalog, DeckList deck, String[] parts) {
        if (parts.length < 2) {
            out.println("Usage: remove <id> [amount]");
            return;
        }
        Karta card = parseCard(catalog, parts[1]);
        if (card == null) {
            return;
        }
        int amount = 1;
        if (parts.length > 2) {
            try {
                amount = Integer.parseInt(parts[2]);
            } catch (NumberFormatException e) {
                out.println("Invalid amount: " + parts[2]);
                return;
            }
        }
        int removed = 0;
        for (int i = 0; i < amount && deck.remove(card); i++) {
            removed++;
        }
        out.println("Removed " + removed + " copy/copies of " + card.getName() + ".");
    }

    private Karta parseCard(CardCatalog catalog, String token) {
        try {
            Karta card = catalog.byId(Integer.parseInt(token));
            if (card == null) {
                out.println("No card with id " + token + " (use 'list' to see ids).");
            }
            return card;
        } catch (NumberFormatException e) {
            out.println("Invalid card id: " + token);
            return null;
        }
    }

    private void listCards(CardCatalog catalog, String filter) {
        out.println("Cards available to " + faction + " (filter: " + filter + "):");
        for (Karta k : catalog.allowedFor(faction)) {
            boolean skip = switch (filter.toLowerCase()) {
                case "units", "unit" -> k.isSpecial();
                case "specials", "special" -> !k.isSpecial();
                default -> false;
            };
            if (skip) {
                continue;
            }
            out.println("  " + k.getId() + ". " + k.getName()
                    + " | power " + k.getPower() + " | " + k.getType()
                    + " | " + k.getRow() + " | copies " + k.getCount());
        }
    }

    private void printDeck(DeckList deck) {
        out.println("Your deck: " + deck.size() + " cards ("
                + deck.nonSpecialCount() + " non-special, "
                + deck.specialCount() + " special, total power " + deck.totalPower() + ")");
        for (Karta k : deck.cards()) {
            out.println("  " + k.getName());
        }
    }

    private void printNumbered(List<CardInstance> cards) {
        for (int i = 0; i < cards.size(); i++) {
            CardInstance ci = cards.get(i);
            out.println("  " + (i + 1) + ". " + ci.card().getName()
                    + " - power " + ci.card().getPower() + ", " + ci.card().getRow());
        }
    }

    private void printHelp() {
        out.println("Commands: add <id> [amount], remove <id> [amount], list [units|specials],"
                + " show, auto, done");
    }

    private String prompt(String message) {
        out.print(message);
        out.flush();
        if (!scanner.hasNextLine()) {
            return null;
        }
        return scanner.nextLine();
    }

    private int readNumber(String message, int min, int max) {
        while (true) {
            String line = prompt(message);
            if (line == null) {
                return min;
            }
            try {
                int value = Integer.parseInt(line.trim());
                if (value >= min && value <= max) {
                    return value;
                }
            } catch (NumberFormatException ignored) {
                // Re-prompt.
            }
            out.println("Enter a number between " + min + " and " + max + ".");
        }
    }
}
