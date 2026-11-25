package gwint;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.KeyboardFocusManager;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.LayoutManager;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.InputMap;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.KeyStroke;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import javax.swing.border.Border;
import javax.swing.event.ListSelectionListener;
import javax.swing.text.JTextComponent;

final class SetupWizard {

    private final CardCatalog cards;
    private final LeaderCatalog leaders;
    private final Random random;
    private final Runnable onQuit;

    private Karta.Faction faction;
    private DeckList deck;
    private Leader leader;

    private enum DeckFocus {
        AVAILABLE,
        CURRENT_DECK
    }

    SetupWizard(CardCatalog cards, LeaderCatalog leaders, Random random, Runnable onQuit) {
        this.cards = cards;
        this.leaders = leaders;
        this.random = random;
        this.onQuit = onQuit;
    }

    Karta.Faction chooseFaction() {
        faction = onEdt(this::showFactionDialog);
        return faction;
    }

    DeckList buildDeck(Karta.Faction chosenFaction) {
        faction = chosenFaction;
        deck = onEdt(() -> showDeckDialog(chosenFaction));
        return deck;
    }

    Leader chooseLeader(Karta.Faction chosenFaction) {
        faction = chosenFaction;
        leader = onEdt(() -> showLeaderDialog(chosenFaction));
        return leader;
    }

    List<CardInstance> chooseMulligan(List<CardInstance> hand) {
        return onEdt(() -> showMulliganDialog(hand));
    }

    private <T> T onEdt(Supplier<T> task) {
        if (SwingUtilities.isEventDispatchThread()) {
            return task.get();
        }
        AtomicReference<T> result = new AtomicReference<>();
        try {
            SwingUtilities.invokeAndWait(() -> result.set(task.get()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted during game setup", e);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException("Failed during game setup", cause);
        }
        return result.get();
    }

    private Karta.Faction showFactionDialog() {
        List<Karta.Faction> factions = cards.playableFactions();
        AtomicInteger selected = new AtomicInteger();
        AtomicReference<Karta.Faction> result = new AtomicReference<>();
        AtomicReference<JDialog> dialogRef = new AtomicReference<>();
        JButton confirm = new JButton("NEXT");
        List<FactionCard> factionCards = new ArrayList<>();

        Runnable confirmAction = () -> {
            result.set(factions.get(selected.get()));
            dialogRef.get().dispose();
        };
        Consumer<Integer> select = index -> {
            selected.set(Math.floorMod(index, factions.size()));
            for (FactionCard card : factionCards) {
                card.setSelected(factionCards.indexOf(card) == selected.get());
            }
        };


        JPanel row = new JPanel(new GridLayout(1, factions.size(), 16, 16));
        row.setOpaque(false);
        for (Karta.Faction option : factions) {
            int index = factionCards.size();
            FactionCard card = new FactionCard(option, () -> select.accept(index), confirmAction);
            factionCards.add(card);
            row.add(card);
        }

        JPanel body = new JPanel(new BorderLayout(12, 12));
        body.setOpaque(false);
        body.add(header("Choose your faction", "Click a card or use Left/Right arrows, then press Enter."),
                BorderLayout.NORTH);
        body.add(row, BorderLayout.CENTER);
        body.add(footer(confirm, quitButton()), BorderLayout.SOUTH);

        JDialog dialog = dialog("Setup 1/4 - Faction", body, new Dimension(1180, 440));
        dialogRef.set(dialog);
        confirm.addActionListener(event -> confirmAction.run());
        installKeys(dialog, row, confirm,
                () -> select.accept(selected.get() - 1),
                () -> select.accept(selected.get() + 1),
                null);
        select.accept(0);
        dialog.setVisible(true);
        return result.get();
    }

    private DeckList showDeckDialog(Karta.Faction chosenFaction) {
        AtomicReference<DeckList> working = new AtomicReference<>(new DeckList(cards, chosenFaction));
        List<Karta> available = cards.allowedFor(chosenFaction);
        AtomicReference<DeckList> result = new AtomicReference<>();
        AtomicReference<JDialog> dialogRef = new AtomicReference<>();
        AtomicReference<DeckFocus> focus = new AtomicReference<>(DeckFocus.AVAILABLE);

        DefaultListModel<Karta> availableModel = new DefaultListModel<>();
        for (Karta card : available) {
            availableModel.addElement(card);
        }
        JList<Karta> cardList = new JList<>(availableModel);
        cardList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        cardList.setFixedCellHeight(56);
        cardList.setBackground(UiTheme.PANEL);
        cardList.setForeground(UiTheme.TEXT);
        cardList.setCellRenderer(new CardRowRenderer(() -> working.get()));
        cardList.setBorder(BorderFactory.createLineBorder(UiTheme.GOLD, 1));

        JProgressBar units = new JProgressBar(0, GameRules.MIN_NON_SPECIAL_CARDS);
        JProgressBar specials = new JProgressBar(0, GameRules.MAX_SPECIAL_CARDS);
        UiTheme.styleProgress(units);
        UiTheme.styleProgress(specials);
        JPanel bars = new JPanel(new GridLayout(2, 1, 8, 8));
        bars.setOpaque(false);
        bars.add(units);
        bars.add(specials);

        DefaultListModel<Karta> deckModel = new DefaultListModel<>();
        JList<Karta> deckList = new JList<>(deckModel);
        deckList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        deckList.setBackground(UiTheme.PANEL);
        deckList.setForeground(UiTheme.TEXT);
        deckList.setFixedCellHeight(30);
        deckList.setBorder(BorderFactory.createLineBorder(UiTheme.GOLD, 1));
        deckList.setCellRenderer(new DeckEntryRenderer(() -> working.get()));

        JLabel summary = new JLabel();
        summary.setForeground(UiTheme.TEXT);
        summary.setFont(new Font("SansSerif", Font.BOLD, 13));
        JLabel errors = new JLabel();
        errors.setFont(new Font("SansSerif", Font.PLAIN, 12));

        JPanel deckSide = new JPanel(new BorderLayout(10, 10));
        deckSide.setOpaque(false);
        deckSide.add(bars, BorderLayout.NORTH);
        deckSide.add(new JScrollPane(deckList), BorderLayout.CENTER);
        JPanel status = new JPanel();
        status.setLayout(new BoxLayout(status, BoxLayout.Y_AXIS));
        status.setOpaque(false);
        status.add(summary);
        status.add(errors);
        deckSide.add(status, BorderLayout.SOUTH);

        JButton addOne = new JButton("+1");
        JButton addFive = new JButton("+5");
        JButton removeOne = new JButton("-1");
        JButton removeFive = new JButton("-5");
        JButton auto = new JButton("Auto-Deck [A]");
        JButton confirm = new JButton("CONFIRM DECK");
        JLabel focusLabel = new JLabel("Focus: Available cards");
        focusLabel.setForeground(UiTheme.GOLD_LIGHT);
        focusLabel.setFont(new Font("SansSerif", Font.BOLD, 12));

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        controls.setOpaque(false);
        controls.add(focusLabel);
        controls.add(addOne);
        controls.add(addFive);
        controls.add(removeOne);
        controls.add(removeFive);
        controls.add(auto);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                new JScrollPane(cardList), deckSide);
        split.setResizeWeight(0.62);
        split.setBorder(null);

        JPanel body = new JPanel(new BorderLayout(10, 10));
        body.setOpaque(false);
        body.add(header("Build your deck - " + UiTheme.faction(chosenFaction),
                "Up/Down select, Left/Right or Tab switch lists, Space/+ add, -/Backspace remove, A auto-deck, Enter confirm."),
                BorderLayout.NORTH);
        body.add(split, BorderLayout.CENTER);
        JPanel south = new JPanel(new BorderLayout(8, 6));
        south.setOpaque(false);
        south.add(controls, BorderLayout.NORTH);
        south.add(footer(confirm, quitButton()), BorderLayout.SOUTH);
        body.add(south, BorderLayout.SOUTH);

        JDialog dialog = dialog("Setup 2/4 - Deck", body, new Dimension(1240, 760));
        dialogRef.set(dialog);

        Runnable refresh = () -> {
            DeckList current = working.get();
            cardList.repaint();
            deckModel.clear();
            List<Karta> unique = new ArrayList<>();
            for (Karta card : current.cards()) {
                boolean known = false;
                for (Karta existing : unique) {
                    if (existing.getId() == card.getId()) {
                        known = true;
                        break;
                    }
                }
                if (!known) {
                    unique.add(card);
                }
            }
            for (Karta card : unique) {
                deckModel.addElement(card);
            }
            int unitCount = current.nonSpecialCount();
            int specialCount = current.specialCount();
            units.setValue(Math.min(unitCount, GameRules.MIN_NON_SPECIAL_CARDS));
            units.setString("Units  " + unitCount + " / " + GameRules.MIN_NON_SPECIAL_CARDS);
            specials.setValue(Math.min(specialCount, GameRules.MAX_SPECIAL_CARDS));
            specials.setString("Specials  " + specialCount + " / " + GameRules.MAX_SPECIAL_CARDS);
            specials.setForeground(specialCount >= GameRules.MAX_SPECIAL_CARDS ? UiTheme.CRIMSON : UiTheme.SPECIAL);
            summary.setText("Total cards: " + current.size() + "   Total power: " + current.totalPower());
            List<String> issues = current.validationErrors();
            if (issues.isEmpty()) {
                errors.setText("Deck is legal.");
                errors.setForeground(UiTheme.GOLD_LIGHT);
            } else {
                errors.setText("<html>" + String.join("<br>", issues) + "</html>");
                errors.setForeground(new Color(232, 126, 126));
            }
            Karta selected = cardList.getSelectedValue();
            Karta removable = focus.get() == DeckFocus.CURRENT_DECK
                    ? deckList.getSelectedValue() : cardList.getSelectedValue();
            boolean canAdd = selected != null && current.canAdd(selected);
            boolean canRemove = removable != null && current.countOf(removable) > 0;
            addOne.setEnabled(canAdd);
            addFive.setEnabled(canAdd);
            removeOne.setEnabled(canRemove);
            removeFive.setEnabled(canRemove);
            confirm.setEnabled(current.isValid());
        };

        Consumer<Integer> add = amount -> {
            Karta selected = cardList.getSelectedValue();
            if (selected == null) {
                return;
            }
            if (working.get().add(selected, amount) == 0) {
                JOptionPane.showMessageDialog(dialog,
                        "Cannot add more copies of " + selected.getName()
                                + " (copy limit or special-card limit reached).",
                        "Deck builder", JOptionPane.INFORMATION_MESSAGE);
            }
            refresh.run();
        };
        Consumer<Karta> removeCard = selected -> {
            if (selected == null) {
                return;
            }
            working.get().remove(selected);
            refresh.run();
        };
        Runnable addOneAction = () -> add.accept(1);
        Runnable removeOneAction = () -> removeCard.accept(focus.get() == DeckFocus.CURRENT_DECK
                ? deckList.getSelectedValue() : cardList.getSelectedValue());
        Runnable autoDeckAction = () -> {
            Component focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
            if (focusOwner instanceof JTextComponent) {
                return;
            }
            working.set(DeckList.randomDeck(cards, chosenFaction, random));
            refresh.run();
        };
        Runnable confirmAction = () -> {
            if (confirm.isEnabled()) {
                confirm.doClick();
            }
        };
        Runnable updateFocus = () -> {
            boolean availableFocus = focus.get() == DeckFocus.AVAILABLE;
            cardList.setBorder(BorderFactory.createLineBorder(
                    availableFocus ? UiTheme.GOLD_LIGHT : UiTheme.GOLD, availableFocus ? 3 : 1));
            deckList.setBorder(BorderFactory.createLineBorder(
                    availableFocus ? UiTheme.GOLD : UiTheme.GOLD_LIGHT, availableFocus ? 1 : 3));
            focusLabel.setText("Focus: " + (availableFocus ? "Available cards" : "Current deck"));
            refresh.run();
        };
        Runnable switchFocus = () -> {
            focus.set(focus.get() == DeckFocus.AVAILABLE ? DeckFocus.CURRENT_DECK : DeckFocus.AVAILABLE);
            updateFocus.run();
        };
        Runnable moveUp = () -> {
            if (focus.get() == DeckFocus.AVAILABLE) {
                int index = cardList.getSelectedIndex();
                if (index > 0) {
                    cardList.setSelectedIndex(index - 1);
                    cardList.ensureIndexIsVisible(index - 1);
                }
            } else {
                int index = deckList.getSelectedIndex();
                if (index > 0) {
                    deckList.setSelectedIndex(index - 1);
                    deckList.ensureIndexIsVisible(index - 1);
                }
            }
        };
        Runnable moveDown = () -> {
            if (focus.get() == DeckFocus.AVAILABLE) {
                int index = cardList.getSelectedIndex();
                if (index >= 0 && index < availableModel.size() - 1) {
                    cardList.setSelectedIndex(index + 1);
                    cardList.ensureIndexIsVisible(index + 1);
                }
            } else {
                int index = deckList.getSelectedIndex();
                if (index >= 0 && index < deckModel.size() - 1) {
                    deckList.setSelectedIndex(index + 1);
                    deckList.ensureIndexIsVisible(index + 1);
                }
            }
        };

        addOne.addActionListener(event -> addOneAction.run());
        addFive.addActionListener(event -> add.accept(5));
        removeOne.addActionListener(event -> removeOneAction.run());
        removeFive.addActionListener(event -> removeCard.accept(focus.get() == DeckFocus.CURRENT_DECK
                ? deckList.getSelectedValue() : cardList.getSelectedValue()));
        auto.addActionListener(event -> autoDeckAction.run());
        ListSelectionListener refreshSelection = event -> {
            if (!event.getValueIsAdjusting()) {
                refresh.run();
            }
        };
        cardList.addListSelectionListener(refreshSelection);
        deckList.addListSelectionListener(refreshSelection);
        cardList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                if (event.getClickCount() == 2) {
                    addOneAction.run();
                }
            }
        });
        deckList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                if (event.getClickCount() == 2) {
                    removeOneAction.run();
                }
            }
        });
        confirm.addActionListener(event -> {
            result.set(working.get());
            dialogRef.get().dispose();
        });

        cardList.setSelectedIndex(0);
        updateFocus.run();
        dialog.getRootPane().setDefaultButton(confirm);
        bindDeckRootKeys(dialog.getRootPane(), confirmAction, switchFocus, () -> auto.doClick());
        bindDeckListKeys(cardList, confirmAction, moveUp, moveDown, switchFocus,
                addOneAction, removeOneAction, () -> auto.doClick());
        bindDeckListKeys(deckList, confirmAction, moveUp, moveDown, switchFocus,
                addOneAction, removeOneAction, () -> auto.doClick());
        SwingUtilities.invokeLater(cardList::requestFocusInWindow);
        dialog.setVisible(true);
        return result.get();
    }

    private Leader showLeaderDialog(Karta.Faction chosenFaction) {
        List<Leader> options = leaders.forFaction(chosenFaction);
        if (options.size() != 3) {
            throw new IllegalStateException("Expected exactly 3 leaders for faction " + chosenFaction
                    + " in leaders.json, found " + options.size());
        }
        AtomicInteger selected = new AtomicInteger();
        AtomicReference<Leader> result = new AtomicReference<>();
        AtomicReference<JDialog> dialogRef = new AtomicReference<>();
        JButton confirm = new JButton("CONFIRM LEADER");
        List<LeaderCard> leaderCards = new ArrayList<>();

        Runnable confirmAction = () -> {
            result.set(options.get(selected.get()));
            dialogRef.get().dispose();
        };
        Consumer<Integer> select = index -> {
            selected.set(Math.floorMod(index, options.size()));
            for (LeaderCard card : leaderCards) {
                card.setSelected(leaderCards.indexOf(card) == selected.get());
            }
        };


        JPanel row = new JPanel(new GridLayout(1, options.size(), 18, 18));
        row.setOpaque(false);
        for (Leader option : options) {
            int index = leaderCards.size();
            LeaderCard card = new LeaderCard(option, () -> select.accept(index), confirmAction);
            leaderCards.add(card);
            row.add(card);
        }

        JPanel body = new JPanel(new BorderLayout(12, 12));
        body.setOpaque(false);
        body.add(header("Choose your leader",
                "Only leaders from leaders.json are shown. Abilities are display-only in this version."),
                BorderLayout.NORTH);
        body.add(row, BorderLayout.CENTER);
        body.add(footer(confirm, quitButton()), BorderLayout.SOUTH);

        JDialog dialog = dialog("Setup 3/4 - Leader", body, new Dimension(1080, 600));
        dialogRef.set(dialog);
        confirm.addActionListener(event -> confirmAction.run());
        installKeys(dialog, row, confirm,
                () -> select.accept(selected.get() - 1),
                () -> select.accept(selected.get() + 1),
                null);
        select.accept(0);
        dialog.setVisible(true);
        return result.get();
    }

    private List<CardInstance> showMulliganDialog(List<CardInstance> hand) {
        boolean[] marked = new boolean[hand.size()];
        AtomicInteger swapCount = new AtomicInteger();
        AtomicInteger zone = new AtomicInteger(0);
        AtomicInteger column = new AtomicInteger(0);
        AtomicReference<List<CardInstance>> result = new AtomicReference<>();
        JLabel status = new JLabel();
        status.setForeground(UiTheme.GOLD_LIGHT);
        status.setFont(new Font("SansSerif", Font.BOLD, 15));

        JPanel grid = new JPanel(new GridLayout(2, 5, 16, 16));
        grid.setOpaque(false);
        List<MulliganCard> cardsUi = new ArrayList<>();

        Runnable refresh = () -> {
            for (int i = 0; i < cardsUi.size(); i++) {
                cardsUi.get(i).setMarked(marked[i]);
                cardsUi.get(i).setFocused(zone.get() < 2
                        && zone.get() == i / 5 && column.get() == i % 5);
            }
            status.setText("Selected to swap: " + swapCount.get() + " / " + GameRules.MAX_MULLIGAN
                    + "     Enter toggles a card     Enter on Continue starts the match");
        };
        Consumer<Integer> toggle = index -> {
            int i = Math.floorMod(index, hand.size());
            if (marked[i]) {
                marked[i] = false;
                swapCount.decrementAndGet();
            } else if (swapCount.get() < GameRules.MAX_MULLIGAN) {
                marked[i] = true;
                swapCount.incrementAndGet();
            }
            refresh.run();
        };
        Consumer<Integer> focusCard = index -> {
            zone.set(index / 5);
            column.set(index % 5);
        };

        for (CardInstance instance : hand) {
            int index = cardsUi.size();
            MulliganCard card = new MulliganCard(instance, () -> {
                focusCard.accept(index);
                toggle.accept(index);
            });
            cardsUi.add(card);
            grid.add(card);
        }

        JButton confirm = new JButton("CONTINUE");
        UiTheme.styleButton(confirm);
        confirm.setFont(new Font("SansSerif", Font.BOLD, 15));
        Border normalConfirmBorder = confirm.getBorder();

        JPanel top = new JPanel(new BorderLayout(4, 4));
        top.setOpaque(false);
        top.add(header("Starting hand - mulligan",
                "Arrows choose a card or Continue. Enter toggles SWAP / WYMIANA or confirms."), BorderLayout.NORTH);
        top.add(status, BorderLayout.SOUTH);

        JPanel body = new JPanel(new BorderLayout(12, 12));
        body.setOpaque(false);
        body.add(top, BorderLayout.NORTH);
        body.add(grid, BorderLayout.CENTER);
        JPanel south = new JPanel(new BorderLayout(8, 6));
        south.setOpaque(false);
        south.add(footer(confirm, quitButton()), BorderLayout.SOUTH);
        body.add(south, BorderLayout.SOUTH);

        JDialog dialog = dialog("Setup 4/4 - Mulligan", body, new Dimension(1240, 780));
        dialog.getRootPane().setDefaultButton(null);
        confirm.addActionListener(event -> {
            List<CardInstance> exchange = new ArrayList<>();
            for (int i = 0; i < hand.size(); i++) {
                if (marked[i]) {
                    exchange.add(hand.get(i));
                }
            }
            result.set(exchange);
            dialog.dispose();
        });

        Runnable activate = () -> {
            if (zone.get() == 2) {
                confirm.doClick();
            } else {
                toggle.accept(zone.get() * 5 + column.get());
            }
        };
        Runnable moveLeft = () -> {
            if (zone.get() < 2) {
                column.set(Math.floorMod(column.get() - 1, 5));
                refresh.run();
            }
        };
        Runnable moveRight = () -> {
            if (zone.get() < 2) {
                column.set(Math.floorMod(column.get() + 1, 5));
                refresh.run();
            }
        };
        Runnable moveDown = () -> {
            if (zone.get() == 0) {
                zone.set(1);
            } else if (zone.get() == 1) {
                zone.set(2);
            }
            refresh.run();
        };
        Runnable moveUp = () -> {
            if (zone.get() == 2) {
                zone.set(1);
            } else if (zone.get() == 1) {
                zone.set(0);
            }
            refresh.run();
        };
        Runnable updateConfirmFocus = () -> confirm.setBorder(zone.get() == 2
                ? BorderFactory.createLineBorder(UiTheme.GOLD_LIGHT, 3) : normalConfirmBorder);
        Runnable refreshWithConfirm = () -> {
            refresh.run();
            updateConfirmFocus.run();
        };

        Runnable moveLeftTracked = () -> {
            moveLeft.run();
            updateConfirmFocus.run();
        };
        Runnable moveRightTracked = () -> {
            moveRight.run();
            updateConfirmFocus.run();
        };
        Runnable moveDownTracked = () -> {
            moveDown.run();
            updateConfirmFocus.run();
        };
        Runnable moveUpTracked = () -> {
            moveUp.run();
            updateConfirmFocus.run();
        };

        bindMulliganKeys(grid, activate, moveLeftTracked, moveRightTracked, moveUpTracked, moveDownTracked);
        refreshWithConfirm.run();
        SwingUtilities.invokeLater(grid::requestFocusInWindow);
        dialog.setVisible(true);
        return result.get();
    }

    private void installKeys(JDialog dialog, JComponent focusOwner, JButton confirm,
                             Runnable left, Runnable right, Runnable toggle) {
        dialog.getRootPane().setDefaultButton(confirm);
        bindKeys(dialog.getRootPane(), confirm, left, right, toggle);
        if (focusOwner != null) {
            bindKeys(focusOwner, confirm, left, right, toggle);
            focusOwner.setFocusable(true);
            SwingUtilities.invokeLater(focusOwner::requestFocusInWindow);
        }
    }

    private void bindKeys(JComponent component, JButton confirm,
                          Runnable left, Runnable right, Runnable toggle) {
        InputMap input = component.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        ActionMap actions = component.getActionMap();
        addBinding(input, actions, KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0),
                "gwint.setup.confirm", () -> {
                    if (confirm.isEnabled()) {
                        confirm.doClick();
                    }
                });
        addBinding(input, actions, KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0), "gwint.setup.left", left);
        addBinding(input, actions, KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0), "gwint.setup.up", left);
        addBinding(input, actions, KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0), "gwint.setup.right", right);
        addBinding(input, actions, KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0), "gwint.setup.down", right);
        addBinding(input, actions, KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0), "gwint.setup.toggle", toggle);

        InputMap focused = component.getInputMap(JComponent.WHEN_FOCUSED);
        ActionMap focusedActions = component.getActionMap();
        addBinding(focused, focusedActions, KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0),
                "gwint.setup.confirm", () -> {
                    if (confirm.isEnabled()) {
                        confirm.doClick();
                    }
                });
        addBinding(focused, focusedActions, KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0), "gwint.setup.left", left);
        addBinding(focused, focusedActions, KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0), "gwint.setup.up", left);
        addBinding(focused, focusedActions, KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0), "gwint.setup.right", right);
        addBinding(focused, focusedActions, KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0), "gwint.setup.down", right);
        addBinding(focused, focusedActions, KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0), "gwint.setup.toggle", toggle);
    }

    private void addBinding(InputMap input, ActionMap actions, KeyStroke key, String name, Runnable action) {
        if (action == null) {
            return;
        }
        input.put(key, name);
        actions.put(name, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent event) {
                action.run();
            }
        });
    }

    private void bindDeckRootKeys(JComponent component, Runnable confirm,
                                  Runnable switchFocus, Runnable autoDeck) {
        InputMap input = component.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        ActionMap actions = component.getActionMap();
        addBinding(input, actions, KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "gwint.deck.confirm", confirm);
        addBinding(input, actions, KeyStroke.getKeyStroke(KeyEvent.VK_TAB, 0), "gwint.deck.switch", switchFocus);
        addBinding(input, actions, KeyStroke.getKeyStroke(KeyEvent.VK_A, 0), "gwint.deck.auto", autoDeck);
    }

    private void bindDeckListKeys(JList<?> list, Runnable confirm, Runnable moveUp, Runnable moveDown,
                                  Runnable switchFocus, Runnable add, Runnable remove, Runnable autoDeck) {
        InputMap input = list.getInputMap(JComponent.WHEN_FOCUSED);
        ActionMap actions = list.getActionMap();
        addBinding(input, actions, KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "gwint.deck.confirm", confirm);
        addBinding(input, actions, KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0), "gwint.deck.up", moveUp);
        addBinding(input, actions, KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0), "gwint.deck.down", moveDown);
        addBinding(input, actions, KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0), "gwint.deck.switch", switchFocus);
        addBinding(input, actions, KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0), "gwint.deck.switch", switchFocus);
        addBinding(input, actions, KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0), "gwint.deck.add", add);
        addBinding(input, actions, KeyStroke.getKeyStroke('+'), "gwint.deck.add.plus", add);
        addBinding(input, actions, KeyStroke.getKeyStroke('='), "gwint.deck.add.equals", add);
        addBinding(input, actions, KeyStroke.getKeyStroke(KeyEvent.VK_ADD, 0), "gwint.deck.add.numpad", add);
        addBinding(input, actions, KeyStroke.getKeyStroke('-'), "gwint.deck.remove", remove);
        addBinding(input, actions, KeyStroke.getKeyStroke(KeyEvent.VK_SUBTRACT, 0), "gwint.deck.remove.numpad", remove);
        addBinding(input, actions, KeyStroke.getKeyStroke(KeyEvent.VK_BACK_SPACE, 0), "gwint.deck.remove.back", remove);
        addBinding(input, actions, KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "gwint.deck.remove.delete", remove);
        addBinding(input, actions, KeyStroke.getKeyStroke(KeyEvent.VK_A, 0), "gwint.deck.auto", autoDeck);
    }

    private void bindMulliganKeys(JComponent component, Runnable activate,
                                  Runnable left, Runnable right, Runnable up, Runnable down) {
        InputMap input = component.getInputMap(JComponent.WHEN_FOCUSED);
        ActionMap actions = component.getActionMap();
        addBinding(input, actions, KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "gwint.mulligan.activate", activate);
        addBinding(input, actions, KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0), "gwint.mulligan.space", activate);
        addBinding(input, actions, KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0), "gwint.mulligan.left", left);
        addBinding(input, actions, KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0), "gwint.mulligan.right", right);
        addBinding(input, actions, KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0), "gwint.mulligan.up", up);
        addBinding(input, actions, KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0), "gwint.mulligan.down", down);
    }

    private JPanel header(String text, String subtitle) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setOpaque(false);
        JLabel title = new JLabel(text, SwingConstants.CENTER);
        title.setForeground(UiTheme.GOLD_LIGHT);
        title.setFont(new Font("SansSerif", Font.BOLD, 22));
        title.setAlignmentX(Component.CENTER_ALIGNMENT);
        JLabel hint = new JLabel(subtitle, SwingConstants.CENTER);
        hint.setForeground(UiTheme.MUTED);
        hint.setFont(new Font("SansSerif", Font.PLAIN, 13));
        hint.setAlignmentX(Component.CENTER_ALIGNMENT);
        panel.add(title);
        panel.add(Box.createVerticalStrut(4));
        panel.add(hint);
        return panel;
    }

    private JButton quitButton() {
        JButton quit = new JButton("QUIT");
        quit.addActionListener(event -> onQuit.run());
        return quit;
    }

    private JPanel footer(JButton... buttons) {
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        footer.setOpaque(false);
        for (JButton button : buttons) {
            UiTheme.styleButton(button);
            footer.add(button);
        }
        return footer;
    }

    private JDialog dialog(String title, JComponent body, Dimension size) {
        JDialog dialog = new JDialog((Frame) null, title, true);
        dialog.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        dialog.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                onQuit.run();
            }
        });
        JPanel root = new WoodPanel(new BorderLayout(12, 12));
        root.setBorder(BorderFactory.createEmptyBorder(18, 18, 18, 18));
        root.add(body, BorderLayout.CENTER);
        dialog.setContentPane(root);
        dialog.setSize(size);
        dialog.setLocationRelativeTo(null);
        return dialog;
    }

    private static String rowIcon(Karta.Row row) {
        if (row == null) {
            return null;
        }
        return switch (row) {
            case PRZOD -> "icon_melee.png";
            case SRODEK -> "icon_range.png";
            case TYL -> "icon_siege.png";
            case OTHER -> "icon_special.png";
        };
    }

    private static Font fittedFont(Graphics2D g, String text, int maxWidth, int size) {
        Font font = new Font("SansSerif", Font.BOLD, size);
        while (font.getSize() > 10 && g.getFontMetrics(font).stringWidth(text) > maxWidth) {
            font = font.deriveFont((float) font.getSize() - 1);
        }
        return font;
    }

    private static void drawWrapped(Graphics2D g, String text, int x, int y, int width,
                                    int lineHeight, int maxLines) {
        String[] words = text.split("\\s+");
        StringBuilder line = new StringBuilder();
        int lines = 0;
        for (String word : words) {
            String candidate = line.length() == 0 ? word : line + " " + word;
            if (g.getFontMetrics().stringWidth(candidate) > width && line.length() > 0) {
                g.drawString(line.toString(), x, y + lines * lineHeight);
                lines++;
                if (lines >= maxLines) {
                    return;
                }
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(candidate);
            }
        }
        if (line.length() > 0 && lines < maxLines) {
            g.drawString(line.toString(), x, y + lines * lineHeight);
        }
    }

    private static void paintCardFrame(Graphics2D g, int width, int height, boolean selected, boolean hover) {
        BufferedImage wood = TextureLoader.get("board_background.png");
        if (wood != null) {
            g.drawImage(wood, 0, 0, width, height, null);
        } else {
            g.setColor(UiTheme.WOOD);
            g.fillRect(0, 0, width, height);
        }
        g.setColor(new Color(0, 0, 0, 115));
        g.fillRect(0, 0, width, height);
        if (selected || hover) {
            g.setColor(new Color(UiTheme.GOLD_LIGHT.getRed(), UiTheme.GOLD_LIGHT.getGreen(),
                    UiTheme.GOLD_LIGHT.getBlue(), selected ? 210 : 120));
            g.setStroke(new BasicStroke(selected ? 5f : 3f));
        } else {
            g.setColor(UiTheme.GOLD);
            g.setStroke(new BasicStroke(2f));
        }
        g.drawRoundRect(2, 2, width - 5, height - 5, 16, 16);
        g.setStroke(new BasicStroke(1f));
    }

    private static final class WoodPanel extends JPanel {
        WoodPanel(LayoutManager layout) {
            super(layout);
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            BufferedImage wood = TextureLoader.get("board_background.png");
            if (wood != null) {
                g.drawImage(wood, 0, 0, getWidth(), getHeight(), null);
            } else {
                g.setColor(UiTheme.WOOD_DARK);
                g.fillRect(0, 0, getWidth(), getHeight());
            }
            g.setColor(new Color(0, 0, 0, 145));
            g.fillRect(0, 0, getWidth(), getHeight());
            g.dispose();
            super.paintComponent(g0);
        }
    }

    private static final class DeckEntryRenderer extends JPanel implements ListCellRenderer<Karta> {
        private final Supplier<DeckList> deck;
        private Karta card;
        private boolean selected;

        DeckEntryRenderer(Supplier<DeckList> deck) {
            this.deck = deck;
            setOpaque(true);
            setBorder(BorderFactory.createEmptyBorder(3, 10, 3, 10));
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends Karta> list, Karta value, int index,
                                                      boolean isSelected, boolean cellHasFocus) {
            card = value;
            selected = isSelected;
            return this;
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setColor(selected ? UiTheme.BLUE : UiTheme.PANEL);
            g.fillRect(0, 0, getWidth(), getHeight());
            if (card != null) {
                g.setFont(new Font("SansSerif", Font.BOLD, 14));
                g.setColor(UiTheme.TEXT);
                g.drawString(card.getName(), 10, 20);
                DeckList current = deck.get();
                String count = "x" + (current == null ? 0 : current.countOf(card));
                g.setFont(new Font("SansSerif", Font.BOLD, 13));
                g.setColor(UiTheme.GOLD_LIGHT);
                FontMetrics metrics = g.getFontMetrics();
                g.drawString(count, getWidth() - metrics.stringWidth(count) - 10, 20);
            }
            g.dispose();
        }
    }

    private static final class FactionCard extends JPanel {
        private final Karta.Faction faction;
        private final Runnable onClick;
        private final Runnable onConfirm;
        private boolean selected;
        private boolean hover;

        FactionCard(Karta.Faction faction, Runnable onClick, Runnable onConfirm) {
            this.faction = faction;
            this.onClick = onClick;
            this.onConfirm = onConfirm;
            setPreferredSize(new Dimension(240, 290));
            setOpaque(false);
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent event) {
                    hover = true;
                    repaint();
                }

                @Override
                public void mouseExited(MouseEvent event) {
                    hover = false;
                    repaint();
                }

                @Override
                public void mouseClicked(MouseEvent event) {
                    onClick.run();
                    if (event.getClickCount() == 2) {
                        onConfirm.run();
                    }
                }
            });
        }

        void setSelected(boolean selected) {
            if (this.selected != selected) {
                this.selected = selected;
                repaint();
            }
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            paintCardFrame(g, getWidth(), getHeight(), selected, hover);
            g.setFont(new Font("SansSerif", Font.BOLD, 21));
            g.setColor(UiTheme.GOLD_LIGHT);
            String name = UiTheme.faction(faction);
            FontMetrics metrics = g.getFontMetrics();
            g.drawString(name, (getWidth() - metrics.stringWidth(name)) / 2, 46);
            g.setColor(UiTheme.GOLD);
            g.setStroke(new BasicStroke(2f));
            g.drawLine(28, 62, getWidth() - 28, 62);
            g.setStroke(new BasicStroke(1f));
            g.setFont(new Font("SansSerif", Font.PLAIN, 14));
            g.setColor(UiTheme.TEXT);
            drawWrapped(g, UiTheme.passive(faction), 24, 96, getWidth() - 48, 22, 6);
            g.setFont(new Font("SansSerif", Font.BOLD, 12));
            g.setColor(selected ? UiTheme.GOLD_LIGHT : UiTheme.MUTED);
            String hint = selected ? "SELECTED - PRESS ENTER" : "CLICK TO SELECT";
            metrics = g.getFontMetrics();
            g.drawString(hint, (getWidth() - metrics.stringWidth(hint)) / 2, getHeight() - 24);
            g.dispose();
        }
    }

    private static final class LeaderCard extends JPanel {
        private final Leader leader;
        private final Runnable onClick;
        private final Runnable onConfirm;
        private boolean selected;
        private boolean hover;

        LeaderCard(Leader leader, Runnable onClick, Runnable onConfirm) {
            this.leader = leader;
            this.onClick = onClick;
            this.onConfirm = onConfirm;
            setPreferredSize(new Dimension(260, 440));
            setOpaque(false);
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent event) {
                    hover = true;
                    repaint();
                }

                @Override
                public void mouseExited(MouseEvent event) {
                    hover = false;
                    repaint();
                }

                @Override
                public void mouseClicked(MouseEvent event) {
                    onClick.run();
                    if (event.getClickCount() == 2) {
                        onConfirm.run();
                    }
                }
            });
        }

        void setSelected(boolean selected) {
            if (this.selected != selected) {
                this.selected = selected;
                repaint();
            }
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            paintCardFrame(g, getWidth(), getHeight(), selected, hover);
            BufferedImage portrait = TextureLoader.get("card_back.png");
            int portraitWidth = 160;
            int portraitHeight = 230;
            int px = (getWidth() - portraitWidth) / 2;
            if (portrait != null) {
                g.drawImage(portrait, px, 18, portraitWidth, portraitHeight, null);
            }
            g.setColor(UiTheme.GOLD);
            g.setStroke(new BasicStroke(3f));
            g.drawRoundRect(px - 3, 15, portraitWidth + 6, portraitHeight + 6, 12, 12);
            g.setStroke(new BasicStroke(1f));

            g.setFont(new Font("SansSerif", Font.BOLD, 19));
            g.setColor(UiTheme.GOLD_LIGHT);
            String name = leader.getName();
            FontMetrics metrics = g.getFontMetrics();
            g.drawString(name, (getWidth() - metrics.stringWidth(name)) / 2, 276);
            g.setFont(new Font("SansSerif", Font.PLAIN, 13));
            g.setColor(UiTheme.MUTED);
            String factionName = UiTheme.faction(leader.getFaction());
            metrics = g.getFontMetrics();
            g.drawString(factionName, (getWidth() - metrics.stringWidth(factionName)) / 2, 298);
            g.setColor(UiTheme.TEXT);
            g.setFont(new Font("SansSerif", Font.PLAIN, 13));
            drawWrapped(g, leader.getDescription(), 22, 330, getWidth() - 44, 18, 3);
            g.setFont(new Font("SansSerif", Font.BOLD, 11));
            g.setColor(selected ? UiTheme.GOLD_LIGHT : UiTheme.MUTED);
            String hint = selected ? "SELECTED - PRESS ENTER" : "NO ACTIVE ABILITIES";
            metrics = g.getFontMetrics();
            g.drawString(hint, (getWidth() - metrics.stringWidth(hint)) / 2, getHeight() - 22);
            g.dispose();
        }
    }

    private static final class CardRowRenderer extends JPanel implements ListCellRenderer<Karta> {
        private final Supplier<DeckList> deck;
        private Karta card;
        private boolean selected;

        CardRowRenderer(Supplier<DeckList> deck) {
            this.deck = deck;
            setOpaque(true);
            setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends Karta> list, Karta value, int index,
                                                      boolean isSelected, boolean cellHasFocus) {
            card = value;
            selected = isSelected;
            return this;
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(selected ? UiTheme.BLUE : UiTheme.PANEL);
            g.fillRect(0, 0, getWidth(), getHeight());
            if (card == null) {
                g.dispose();
                return;
            }
            if (!card.isSpecial()) {
                g.setColor(UiTheme.GOLD);
                g.fillOval(6, 8, 38, 38);
                g.setColor(UiTheme.WOOD_DARK);
                g.setFont(new Font("SansSerif", Font.BOLD, 17));
                String power = String.valueOf(card.getPower());
                FontMetrics metrics = g.getFontMetrics();
                g.drawString(power, 25 - metrics.stringWidth(power) / 2, 33);
            }
            String icon = card.getRow() == null ? null : switch (card.getRow()) {
                case PRZOD -> "icon_melee.png";
                case SRODEK -> "icon_range.png";
                case TYL -> "icon_siege.png";
                case OTHER -> "icon_special.png";
            };
            if (icon != null) {
                BufferedImage image = TextureLoader.get(icon);
                if (image != null) {
                    g.drawImage(image, 52, 10, 34, 34, null);
                }
            }
            g.setFont(new Font("SansSerif", Font.BOLD, 15));
            g.setColor(UiTheme.typeColor(card.getType()));
            g.drawString(card.getName(), 98, 24);
            g.setFont(new Font("SansSerif", Font.PLAIN, 12));
            g.setColor(UiTheme.MUTED);
            DeckList current = deck.get();
            int count = current == null ? 0 : current.countOf(card);
            g.drawString(UiTheme.faction(card.getFaction()) + "  |  " + UiTheme.typeLabel(card.getType())
                    + "  |  in deck " + count + "/" + card.getCount(), 98, 43);
            g.dispose();
        }
    }

    private static final class MulliganCard extends JComponent {
        private final Karta karta;
        private final CardPanel card;
        private boolean marked;
        private boolean focused;

        MulliganCard(CardInstance instance, Runnable onClick) {
            karta = instance.card();
            card = new CardPanel(karta, false, 170, 255);
            setLayout(new BorderLayout());
            add(card, BorderLayout.CENTER);
            setPreferredSize(new Dimension(170, 255));
            setOpaque(false);
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent event) {
                    onClick.run();
                }
            });
        }

        void setMarked(boolean marked) {
            if (this.marked != marked) {
                this.marked = marked;
            }
            repaint();
        }

        void setFocused(boolean focused) {
            if (this.focused != focused) {
                this.focused = focused;
                repaint();
            }
        }

        @Override
        public void paint(Graphics g0) {
            super.paint(g0);
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            if (marked) {
                g.setColor(new Color(UiTheme.CRIMSON.getRed(), UiTheme.CRIMSON.getGreen(),
                        UiTheme.CRIMSON.getBlue(), 155));
                g.fillRoundRect(0, 0, getWidth(), getHeight(), 14, 14);
            }

            g.setColor(UiTheme.GOLD_LIGHT);
            g.fillOval(8, 8, 46, 46);
            g.setColor(UiTheme.WOOD_DARK);
            g.setFont(new Font("SansSerif", Font.BOLD, 21));
            String power = karta.isSpecial() ? "S" : String.valueOf(karta.getPower());
            FontMetrics metrics = g.getFontMetrics();
            g.drawString(power, 31 - metrics.stringWidth(power) / 2, 39);

            String icon = rowIcon(karta.getRow());
            if (icon != null) {
                BufferedImage image = TextureLoader.get(icon);
                if (image != null) {
                    g.drawImage(image, getWidth() - 50, 10, 40, 40, null);
                }
            }

            int stripHeight = 40;
            int stripY = getHeight() - stripHeight;
            g.setColor(new Color(18, 14, 12, 225));
            g.fillRect(0, stripY, getWidth(), stripHeight);
            g.setFont(fittedFont(g, karta.getName(), getWidth() - 16, 16));
            g.setColor(Color.WHITE);
            metrics = g.getFontMetrics();
            g.drawString(karta.getName(), (getWidth() - metrics.stringWidth(karta.getName())) / 2,
                    stripY + 27);

            if (marked) {
                int bannerY = 68;
                int bannerHeight = 56;
                g.setColor(new Color(UiTheme.CRIMSON.getRed(), UiTheme.CRIMSON.getGreen(),
                        UiTheme.CRIMSON.getBlue(), 225));
                g.fillRoundRect(14, bannerY, getWidth() - 28, bannerHeight, 10, 10);
                g.setColor(UiTheme.GOLD_LIGHT);
                g.setStroke(new BasicStroke(2f));
                g.drawRoundRect(14, bannerY, getWidth() - 28, bannerHeight, 10, 10);
                g.setStroke(new BasicStroke(1f));
                g.setFont(new Font("SansSerif", Font.BOLD, 21));
                metrics = g.getFontMetrics();
                g.drawString("SWAP", (getWidth() - metrics.stringWidth("SWAP")) / 2, bannerY + 24);
                g.setColor(Color.WHITE);
                g.setFont(new Font("SansSerif", Font.BOLD, 16));
                metrics = g.getFontMetrics();
                g.drawString("WYMIANA", (getWidth() - metrics.stringWidth("WYMIANA")) / 2, bannerY + 45);
            }

            if (focused) {
                g.setColor(UiTheme.GOLD_LIGHT);
                g.setStroke(new BasicStroke(4f));
                g.drawRoundRect(2, 2, getWidth() - 5, getHeight() - 5, 14, 14);
                g.setStroke(new BasicStroke(1f));
            }
            g.dispose();
        }
    }
}
