package gwint;

import gwint.engine.Ability;
import gwint.engine.BattleRow;
import gwint.engine.CardView;
import gwint.engine.RowModifiers;
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
            printNumbered(hand, match, self);
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

    @Override
    public BattleRow chooseRow(Match match, Player self, CardInstance card,
                               List<BattleRow> options) {
        out.println("\n" + card.card().getName() + " is Agile. Choose a row:");
        return askRow(options, match, self);
    }

    @Override
    public BattleRow chooseSpecialRow(Match match, Player self, CardInstance card,
                                      List<BattleRow> options) {
        out.println("\nChoose a row for " + card.card().getName() + ":");
        return askRow(options, match, self);
    }

    @Override
    public CardInstance chooseRevival(Match match, Player self, List<CardInstance> options) {
        out.println("\nChoose a card to revive from your discard pile:");
        printNumbered(options, match, self);
        return options.get(readNumber("Card number: ", 1, options.size()) - 1);
    }

    @Override
    public CardInstance chooseDecoyTarget(Match match, Player self, List<CardInstance> options) {
        out.println("\nChoose one of your units to return to hand:");
        printNumbered(options, match, self);
        return options.get(readNumber("Card number: ", 1, options.size()) - 1);
    }

    private BattleRow askRow(List<BattleRow> options, Match match, Player self) {
        for (int i = 0; i < options.size(); i++) {
            BattleRow option = options.get(i);
            String detail = match == null ? "" : activeEffects(match, self, option);
            out.println("  " + (i + 1) + ". " + option
                    + (detail.isEmpty() ? "" : "  [" + detail + "]"));
        }
        return options.get(readNumber("Row number: ", 1, options.size()) - 1);
    }

    private String activeEffects(Match match, Player self, BattleRow row) {
        RowModifiers modifiers = match.engine().rowModifiers(self.side(), row);
        return modifiers.hasEffects() ? modifiers.describe() : "";
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
                    + (k.isHero() ? " [HERO]" : "")
                    + " | power " + k.getPower() + " | " + k.getType()
                    + " | " + k.getRow() + " | copies " + k.getCount()
                    + " | Abilities: " + abilityList(k));
        }
    }

    private void printDeck(DeckList deck) {
        out.println("Your deck: " + deck.size() + " cards ("
                + deck.nonSpecialCount() + " non-special, "
                + deck.specialCount() + " special, total power " + deck.totalPower() + ")");
        for (Karta k : deck.cards()) {
            out.println("  " + k.getName() + (k.isHero() ? " [HERO]" : "")
                    + " | " + k.getRow() + " | Abilities: " + abilityList(k));
        }
    }

    private void printNumbered(List<CardInstance> cards) {
        printNumbered(cards, null, null);
    }

    private void printNumbered(List<CardInstance> cards, Match match, Player self) {
        for (int i = 0; i < cards.size(); i++) {
            CardInstance ci = cards.get(i);
            CardView def = ci.definition();
            int strength = match == null
                    ? ci.card().getPower() : match.engine().breakdown(ci).finalStrength();
            StringBuilder sb = new StringBuilder("  " + (i + 1) + ". " + def.name());
            if (def.isHero()) {
                sb.append(" [HERO]");
            }
            sb.append(" - strength ").append(strength).append(", ").append(ci.card().getRow());
            if (match != null && ci.row() != null && self != null
                    && self.side().findUnit(ci.uid()) != null) {
                String effects = activeEffects(match, self, ci.row());
                if (!effects.isEmpty()) {
                    sb.append("  [").append(effects).append(']');
                }
            }
            sb.append(" | Abilities: ").append(abilityList(def));
            out.println(sb);
        }
    }

    private String abilityList(CardView card) {
        if (card.abilities().isEmpty()) {
            return "none";
        }
        StringBuilder sb = new StringBuilder();
        for (Ability ability : card.abilities()) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(ability.displayName());
        }
        return sb.toString();
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
