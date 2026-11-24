package gwint;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.InputMap;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

final class BoardView extends JFrame {

    private enum Tone { BLUE, RED }

    private static final Color GOLD = new Color(214, 174, 96);
    private static final Color BLUE = new Color(42, 88, 150);
    private static final Color RED = new Color(150, 42, 48);
    private static final Karta.Row[] PLAYER_ROWS = {
        Karta.Row.PRZOD, Karta.Row.SRODEK, Karta.Row.TYL
    };

    private final JLabel statusLabel = new JLabel("Welcome to Gwent", SwingConstants.CENTER);
    private final JLabel roundLabel = new JLabel("Round 1", SwingConstants.CENTER);
    private final JLabel opponentHandLabel = new JLabel("Opponent hand", SwingConstants.CENTER);
    private final ScoreBadgePanel opponentScore = new ScoreBadgePanel(Tone.RED, 62);
    private final ScoreBadgePanel playerScore = new ScoreBadgePanel(Tone.BLUE, 62);
    private final JPanel opponentHandPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 3, 3));
    private final JPanel playerHandPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 4, 2));
    private final LeaderPanel opponentLeader = new LeaderPanel();
    private final LeaderPanel playerLeader = new LeaderPanel();
    private final GemPanel opponentGems = new GemPanel();
    private final GemPanel playerGems = new GemPanel();
    private final JButton passButton = new JButton("PASS");
    private final LanePanel[] lanes = new LanePanel[6];
    private final Map<Integer, Karta.Row> placement = new HashMap<>();
    private final List<HandSlot> handSlots = new ArrayList<>();
    private final JPanel top;
    private final JPanel board;
    private final JPanel bottom;
    private final JPanel content;

    private volatile String pendingStatus = "Welcome to Gwent";
    private GameSnapshot current;
    private Consumer<Actions.Action> submit;
    private boolean awaitingHuman;
    private int selectedHandIndex = -1;
    private int hoveredHandIndex = -1;
    private boolean rowChoice;
    private int rowCursor;
    private int lastRound = -1;

    private int boardCardW = 62;
    private int boardCardH = 94;
    private int handCardW = 90;
    private int handCardH = 135;
    private int opponentBackW = 42;
    private int opponentBackH = 63;
    private int laneH = 90;

    BoardView() {
        super("Gwint - board");
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                submitAction(new Actions.Pass());
                dispose();
            }
        });

        statusLabel.setForeground(GOLD);
        statusLabel.setFont(new Font("SansSerif", Font.BOLD, 14));
        roundLabel.setForeground(Color.WHITE);

        top = new JPanel(new BorderLayout(10, 4));
        top.setOpaque(false);
        top.setBorder(BorderFactory.createEmptyBorder(6, 10, 2, 10));

        JPanel opponentLeft = new JPanel();
        opponentLeft.setLayout(new BoxLayout(opponentLeft, BoxLayout.Y_AXIS));
        opponentLeft.setOpaque(false);
        opponentHandLabel.setForeground(Color.WHITE);
        opponentHandPanel.setOpaque(false);
        opponentLeft.add(opponentHandLabel);
        opponentLeft.add(opponentHandPanel);
        top.add(opponentLeft, BorderLayout.WEST);

        JPanel topCenter = new JPanel(new BorderLayout(2, 2));
        topCenter.setOpaque(false);
        topCenter.add(statusLabel, BorderLayout.NORTH);
        topCenter.add(roundLabel, BorderLayout.CENTER);
        top.add(topCenter, BorderLayout.CENTER);
        top.add(sidePanel(opponentScore, opponentLeader, opponentGems, false), BorderLayout.EAST);

        board = new JPanel(new GridLayout(6, 1, 3, 3));
        board.setOpaque(false);
        board.setBorder(BorderFactory.createEmptyBorder(2, 14, 2, 14));
        Karta.Row[] order = {Karta.Row.TYL, Karta.Row.SRODEK, Karta.Row.PRZOD,
                Karta.Row.PRZOD, Karta.Row.SRODEK, Karta.Row.TYL};
        for (int i = 0; i < lanes.length; i++) {
            lanes[i] = new LanePanel(order[i], i < 3);
            board.add(lanes[i]);
        }

        bottom = new JPanel(new BorderLayout(10, 4));
        bottom.setOpaque(false);
        bottom.setBorder(BorderFactory.createEmptyBorder(2, 10, 6, 10));
        playerHandPanel.setOpaque(false);
        bottom.add(playerHandPanel, BorderLayout.CENTER);
        JPanel bottomRight = sidePanel(playerScore, playerLeader, playerGems, true);
        passButton.setFont(new Font("SansSerif", Font.BOLD, 17));
        passButton.setEnabled(false);
        passButton.addActionListener(event -> submitAction(new Actions.Pass()));
        JPanel actions = new JPanel();
        actions.setOpaque(false);
        actions.add(passButton);
        bottomRight.add(actions, BorderLayout.SOUTH);
        bottom.add(bottomRight, BorderLayout.EAST);

        content = new JPanel(new BorderLayout(0, 0)) {
            @Override
            protected void paintComponent(Graphics g) {
                BufferedImage texture = TextureLoader.get("board_background.png");
                if (texture != null) {
                    g.drawImage(texture, 0, 0, getWidth(), getHeight(), null);
                } else {
                    super.paintComponent(g);
                }
            }
        };
        content.setFocusable(true);
        content.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent event) {
                updateMetrics();
            }
        });
        bindKeys();
        content.add(top, BorderLayout.NORTH);
        content.add(board, BorderLayout.CENTER);
        content.add(bottom, BorderLayout.SOUTH);
        setContentPane(content);
        setSize(1440, 900);
        setMinimumSize(new Dimension(980, 700));
        setLocationRelativeTo(null);
    }

    private JPanel sidePanel(ScoreBadgePanel score, LeaderPanel leader, GemPanel gems,
                             boolean player) {
        JPanel panel = new JPanel(new BorderLayout(4, 2));
        panel.setOpaque(false);
        JPanel scorePanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 5, 0));
        scorePanel.setOpaque(false);
        JLabel scoreTitle = new JLabel("POWER");
        scoreTitle.setForeground(GOLD);
        scorePanel.add(scoreTitle);
        scorePanel.add(score);
        scorePanel.add(gems);
        panel.add(scorePanel, BorderLayout.NORTH);
        panel.add(leader, BorderLayout.CENTER);
        if (player) {
            scoreTitle.setHorizontalAlignment(SwingConstants.RIGHT);
        }
        return panel;
    }

    private void bindKeys() {
        InputMap input = content.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap actions = content.getActionMap();
        bind(input, actions, KeyEvent.VK_LEFT, "hand-prev", () -> moveHandSelection(-1));
        bind(input, actions, KeyEvent.VK_RIGHT, "hand-next", () -> moveHandSelection(1));
        bind(input, actions, KeyEvent.VK_UP, "row-prev", () -> moveRowSelection(-1));
        bind(input, actions, KeyEvent.VK_DOWN, "row-next", () -> moveRowSelection(1));
        bind(input, actions, KeyEvent.VK_ENTER, "confirm", this::activateSelection);
        bind(input, actions, KeyEvent.VK_SPACE, "confirm-space", this::activateSelection);
    }

    private void bind(InputMap input, ActionMap actions, int key, String name, Runnable action) {
        input.put(KeyStroke.getKeyStroke(key, 0), name);
        actions.put(name, new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent event) {
                action.run();
            }
        });
    }

    private void updateMetrics() {
        int width = content.getWidth();
        int height = content.getHeight();
        if (width <= 0 || height <= 0) {
            return;
        }
        int topHeight = clamp((int) (height * 0.15), 96, 150);
        int bottomHeight = clamp((int) (height * 0.20), 125, 190);
        int boardHeight = height - topHeight - bottomHeight;
        laneH = Math.max(46, (boardHeight - 10 - 15) / 6);
        boardCardH = clamp(laneH - 12, 36, 100);
        boardCardW = Math.max(24, (int) Math.round(boardCardH * 0.66));
        handCardH = clamp(bottomHeight - 42, 72, 145);
        int handCount = current == null ? 10 : Math.max(10, current.player().hand().size());
        int widthPerHandCard = (width - 250) / handCount;
        handCardW = clamp(Math.min((int) (handCardH * 0.66), widthPerHandCard - 4), 48, 100);
        opponentBackH = clamp(topHeight - 36, 38, 72);
        opponentBackW = Math.max(24, (int) Math.round(opponentBackH * 0.66));
        top.setPreferredSize(new Dimension(width, topHeight));
        bottom.setPreferredSize(new Dimension(width, bottomHeight));
        revalidate();
        repaint();
        if (current != null) {
            render(current);
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    void open() {
        SwingUtilities.invokeLater(() -> {
            setVisible(true);
            content.requestFocusInWindow();
        });
    }

    void showSnapshot(GameSnapshot snapshot) {
        pendingStatus = snapshot.status();
        SwingUtilities.invokeLater(() -> render(snapshot));
    }

    void refresh(Match match) {
        GameSnapshot snapshot = GameSnapshot.of(match, pendingStatus);
        SwingUtilities.invokeLater(() -> render(snapshot));
    }

    void beginTurn(GameSnapshot snapshot, Consumer<Actions.Action> consumer) {
        try {
            SwingUtilities.invokeAndWait(() -> {
                awaitingHuman = true;
                submit = consumer;
                selectedHandIndex = -1;
                hoveredHandIndex = -1;
                rowChoice = false;
                pendingStatus = snapshot.status();
                content.requestFocusInWindow();
                render(snapshot);
            });
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while starting the human turn", e);
        } catch (java.lang.reflect.InvocationTargetException e) {
            throw new IllegalStateException("Failed to start the human turn", e);
        }
    }

    void setStatus(String text) {
        pendingStatus = text == null ? "" : text;
        SwingUtilities.invokeLater(() -> statusLabel.setText(pendingStatus));
    }

    void showResult(String text) {
        pendingStatus = text;
        SwingUtilities.invokeLater(() -> {
            awaitingHuman = false;
            submit = null;
            statusLabel.setText(text);
            passButton.setEnabled(false);
        });
    }

    private void render(GameSnapshot snapshot) {
        if (snapshot == null) {
            return;
        }
        if (snapshot.round() != lastRound) {
            lastRound = snapshot.round();
            placement.clear();
            rowChoice = false;
            rowCursor = 0;
            selectedHandIndex = -1;
            hoveredHandIndex = -1;
        }
        current = snapshot;
        roundLabel.setText("Round " + snapshot.round()
                + (snapshot.player().active() ? " - your turn" : " - opponent turn"));
        opponentHandLabel.setText("Opponent hand: " + snapshot.opponent().hand().size());
        opponentScore.setScore(snapshot.opponent().score());
        playerScore.setScore(snapshot.player().score());
        opponentGems.setLives(snapshot.opponent().lives());
        playerGems.setLives(snapshot.player().lives());
        opponentLeader.setLeader(snapshot.opponent().leaderName(), snapshot.opponent().faction());
        playerLeader.setLeader(snapshot.player().leaderName(), snapshot.player().faction());
        statusLabel.setText(snapshot.status().isEmpty() ? pendingStatus : snapshot.status());

        opponentHandPanel.removeAll();
        for (GameSnapshot.CardRef ignored : snapshot.opponent().hand()) {
            opponentHandPanel.add(new CardPanel(null, true, opponentBackW, opponentBackH));
        }

        playerHandPanel.removeAll();
        handSlots.clear();
        List<GameSnapshot.CardRef> hand = snapshot.player().hand();
        for (int i = 0; i < hand.size(); i++) {
            int index = i;
            HandSlot slot = new HandSlot(hand.get(i).card(), index);
            slot.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent event) {
                    hoveredHandIndex = index;
                    slot.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                    updateHandVisuals();
                }

                @Override
                public void mouseExited(MouseEvent event) {
                    if (hoveredHandIndex == index) {
                        hoveredHandIndex = -1;
                    }
                    slot.setCursor(Cursor.getDefaultCursor());
                    updateHandVisuals();
                }

                @Override
                public void mouseClicked(MouseEvent event) {
                    content.requestFocusInWindow();
                    onHandClick(index);
                }
            });
            handSlots.add(slot);
            playerHandPanel.add(slot);
        }
        updateHandVisuals();

        Map<Karta.Row, List<GameSnapshot.CardRef>> displayed = displayRows(snapshot.player());
        for (int i = 0; i < lanes.length; i++) {
            GameSnapshot.Side side = i < 3 ? snapshot.opponent() : snapshot.player();
            List<GameSnapshot.CardRef> cards = i < 3
                    ? side.board(lanes[i].row())
                    : displayed.get(lanes[i].row());
            boolean target = targetRow(i, hand);
            lanes[i].setCards(cards, target, boardCardW, boardCardH);
        }

        passButton.setEnabled(awaitingHuman && !snapshot.finished());
        revalidate();
        repaint();
    }

    private boolean targetRow(int laneIndex, List<GameSnapshot.CardRef> hand) {
        if (!awaitingHuman || laneIndex < 3 || selectedHandIndex < 0
                || selectedHandIndex >= hand.size()) {
            return false;
        }
        if (!isAgile(hand.get(selectedHandIndex))) {
            return false;
        }
        if (!rowChoice) {
            return true;
        }
        return PLAYER_ROWS[rowCursor] == lanes[laneIndex].row();
    }

    private void updateHandVisuals() {
        for (int i = 0; i < handSlots.size(); i++) {
            boolean selected = i == selectedHandIndex;
            boolean hovered = i == hoveredHandIndex;
            handSlots.get(i).setRaised(hovered);
            handSlots.get(i).card().setSelected(selected || hovered);
        }
    }

    private Map<Karta.Row, List<GameSnapshot.CardRef>> displayRows(GameSnapshot.Side side) {
        Map<Karta.Row, List<GameSnapshot.CardRef>> rows = new EnumMap<>(Karta.Row.class);
        for (Karta.Row row : Karta.Row.values()) {
            rows.put(row, new ArrayList<>(side.board(row)));
        }
        for (Map.Entry<Integer, Karta.Row> entry : placement.entrySet()) {
            GameSnapshot.CardRef found = null;
            for (GameSnapshot.CardRef ref : side.board()) {
                if (ref.uid() == entry.getKey()) {
                    found = ref;
                    break;
                }
            }
            if (found == null) {
                continue;
            }
            final GameSnapshot.CardRef moved = found;
            for (Karta.Row row : Karta.Row.values()) {
                rows.get(row).removeIf(ref -> ref.uid() == moved.uid());
            }
            rows.get(entry.getValue()).add(moved);
        }
        return rows;
    }

    private void onHandClick(int index) {
        if (!awaitingHuman || current == null || index < 0
                || index >= current.player().hand().size()) {
            return;
        }
        GameSnapshot.CardRef ref = current.player().hand().get(index);
        if (!isAgile(ref)) {
            submitAction(new Actions.Play(index));
            return;
        }
        if (selectedHandIndex != index) {
            selectedHandIndex = index;
            rowChoice = false;
            rowCursor = baseRowIndex(ref.card().getRow());
        } else if (rowChoice) {
            rowChoice = false;
        } else {
            rowChoice = true;
        }
        render(current);
    }

    private void onLaneClick(int laneIndex) {
        if (!awaitingHuman || current == null || selectedHandIndex < 0
                || laneIndex < 3 || laneIndex >= lanes.length) {
            return;
        }
        Karta.Row row = lanes[laneIndex].row();
        if (row == Karta.Row.OTHER) {
            return;
        }
        List<GameSnapshot.CardRef> hand = current.player().hand();
        if (selectedHandIndex >= hand.size() || !isAgile(hand.get(selectedHandIndex))) {
            return;
        }
        placement.put(hand.get(selectedHandIndex).uid(), row);
        submitAction(new Actions.Play(selectedHandIndex));
    }

    private void moveHandSelection(int delta) {
        if (!awaitingHuman || current == null || current.player().hand().isEmpty()) {
            return;
        }
        int size = current.player().hand().size();
        if (selectedHandIndex < 0) {
            selectedHandIndex = delta > 0 ? 0 : size - 1;
        } else {
            selectedHandIndex = (selectedHandIndex + delta + size) % size;
        }
        hoveredHandIndex = -1;
        rowChoice = false;
        updateHandVisuals();
        render(current);
        content.requestFocusInWindow();
    }

    private void moveRowSelection(int delta) {
        if (!awaitingHuman || !rowChoice) {
            return;
        }
        rowCursor = (rowCursor + delta + PLAYER_ROWS.length) % PLAYER_ROWS.length;
        render(current);
        content.requestFocusInWindow();
    }

    private void activateSelection() {
        if (!awaitingHuman || current == null || current.player().hand().isEmpty()) {
            return;
        }
        int size = current.player().hand().size();
        if (selectedHandIndex < 0) {
            selectedHandIndex = 0;
        }
        if (selectedHandIndex >= size) {
            selectedHandIndex = size - 1;
        }
        GameSnapshot.CardRef ref = current.player().hand().get(selectedHandIndex);
        if (isAgile(ref) && !rowChoice) {
            rowChoice = true;
            rowCursor = baseRowIndex(ref.card().getRow());
            render(current);
            return;
        }
        if (rowChoice && isAgile(ref)) {
            placement.put(ref.uid(), PLAYER_ROWS[rowCursor]);
        }
        submitAction(new Actions.Play(selectedHandIndex));
    }

    private int baseRowIndex(Karta.Row row) {
        for (int i = 0; i < PLAYER_ROWS.length; i++) {
            if (PLAYER_ROWS[i] == row) {
                return i;
            }
        }
        return 0;
    }

    private boolean isAgile(GameSnapshot.CardRef ref) {
        return ref.card().getAbills() == Karta.Abillities.ZWINNOSC;
    }

    private void submitAction(Actions.Action action) {
        Consumer<Actions.Action> target = submit;
        awaitingHuman = false;
        submit = null;
        selectedHandIndex = -1;
        hoveredHandIndex = -1;
        rowChoice = false;
        passButton.setEnabled(false);
        if (target != null) {
            target.accept(action);
        }
    }

    private final class HandSlot extends JPanel {

        private final CardPanel card;

        private HandSlot(Karta karta, int index) {
            super(new BorderLayout());
            setOpaque(false);
            setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));
            card = new CardPanel(karta, false, handCardW, handCardH);
            add(card, BorderLayout.CENTER);
        }

        private CardPanel card() {
            return card;
        }

        private void setRaised(boolean raised) {
            setBorder(BorderFactory.createEmptyBorder(raised ? 0 : 10, 0, 0, 0));
        }
    }

    private final class LanePanel extends JPanel {

        private final Karta.Row row;
        private final JPanel cardsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 1));
        private final ScoreBadgePanel badge;
        private List<GameSnapshot.CardRef> cards = new ArrayList<>();
        private boolean target;

        private LanePanel(Karta.Row row, boolean opponent) {
            this.row = row;
            this.badge = new ScoreBadgePanel(opponent ? Tone.RED : Tone.BLUE, 42);
            setLayout(new BorderLayout(6, 1));
            setOpaque(false);
            setPreferredSize(new Dimension(0, laneH));
            JPanel left = new JPanel(new FlowLayout(FlowLayout.CENTER, 2, 1));
            left.setOpaque(false);
            left.add(badge);
            left.add(new IconPanel("horn_slot.png", 30, 30));
            add(left, BorderLayout.WEST);
            cardsPanel.setOpaque(false);
            add(cardsPanel, BorderLayout.CENTER);
            setBorder(BorderFactory.createLineBorder(new Color(15, 12, 10), 2));
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent event) {
                    onLaneClick(opponent ? -1 : playerLaneIndex());
                }
            });
        }

        private int playerLaneIndex() {
            for (int i = 0; i < PLAYER_ROWS.length; i++) {
                if (PLAYER_ROWS[i] == row) {
                    return i + 3;
                }
            }
            return -1;
        }

        private Karta.Row row() {
            return row;
        }

        private void setCards(List<GameSnapshot.CardRef> cards, boolean target, int cardW, int cardH) {
            this.cards = new ArrayList<>(cards);
            this.target = target;
            int score = 0;
            for (GameSnapshot.CardRef ref : this.cards) {
                score += ref.card().getPower();
            }
            badge.setScore(score);
            cardsPanel.removeAll();
            for (GameSnapshot.CardRef ref : this.cards) {
                cardsPanel.add(new CardPanel(ref.card(), false, cardW, cardH));
            }
            cardsPanel.revalidate();
            cardsPanel.repaint();
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            BufferedImage lane = TextureLoader.get("row_lane.png");
            if (lane != null) {
                g.drawImage(lane, 0, 0, getWidth(), getHeight(), null);
            }
            if (target) {
                BufferedImage highlight = TextureLoader.get("selection_highlight.png");
                if (highlight != null) {
                    g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.32f));
                    g.drawImage(highlight, 0, 0, getWidth(), getHeight(), null);
                }
            }
            g.dispose();
            super.paintComponent(g0);
        }
    }

    private static final class ScoreBadgePanel extends JPanel {

        private final Tone tone;
        private int score;

        private ScoreBadgePanel(Tone tone, int size) {
            this.tone = tone;
            setPreferredSize(new Dimension(size, size));
            setOpaque(false);
        }

        private void setScore(int score) {
            if (this.score != score) {
                this.score = score;
                repaint();
            }
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth();
            int h = getHeight();
            Color fill = tone == Tone.BLUE ? BLUE : RED;
            Color rim = tone == Tone.BLUE ? new Color(126, 176, 232) : new Color(232, 126, 126);
            g.setColor(new Color(18, 15, 14));
            g.fillOval(1, 1, w - 2, h - 2);
            g.setColor(fill);
            g.fillOval(4, 4, w - 8, h - 8);
            g.setColor(new Color(255, 255, 255, 55));
            g.fillOval(7, 7, w - 18, h / 2);
            g.setColor(rim);
            g.setStroke(new BasicStroke(2f));
            g.drawOval(3, 3, w - 6, h - 6);
            g.setColor(Color.WHITE);
            Font font = new Font("SansSerif", Font.BOLD, Math.max(12, h / 2));
            g.setFont(font);
            FontMetrics metrics = g.getFontMetrics();
            String text = String.valueOf(score);
            g.drawString(text, (w - metrics.stringWidth(text)) / 2,
                    h / 2 + metrics.getAscent() / 2 - 2);
            g.dispose();
        }
    }

    private static final class IconPanel extends JPanel {

        private final String file;

        private IconPanel(String file, int width, int height) {
            this.file = file;
            setPreferredSize(new Dimension(width, height));
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            BufferedImage image = TextureLoader.get(file);
            if (image != null) {
                g.drawImage(image, 0, 0, getWidth(), getHeight(), null);
            }
        }
    }

    private static final class GemPanel extends JPanel {

        private int lives = GameRules.START_LIVES;

        private GemPanel() {
            setPreferredSize(new Dimension(58, 28));
            setOpaque(false);
        }

        private void setLives(int lives) {
            if (this.lives != lives) {
                this.lives = lives;
                repaint();
            }
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            for (int i = 0; i < GameRules.START_LIVES; i++) {
                int x = 4 + i * 27;
                boolean active = i < lives;
                g.setColor(active ? new Color(220, 36, 44) : new Color(48, 42, 38));
                g.fillOval(x, 4, 20, 20);
                if (active) {
                    g.setColor(new Color(255, 190, 185, 160));
                    g.fillOval(x + 4, 7, 7, 6);
                }
                g.setColor(active ? new Color(255, 190, 185) : GOLD);
                g.setStroke(new BasicStroke(2f));
                g.drawOval(x, 4, 20, 20);
            }
            g.dispose();
        }
    }

    private static final class LeaderPanel extends JPanel {

        private String name = "";
        private Karta.Faction faction;

        private LeaderPanel() {
            setPreferredSize(new Dimension(70, 104));
            setOpaque(false);
        }

        private void setLeader(String name, Karta.Faction faction) {
            this.name = name == null ? "" : name;
            this.faction = faction;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            BufferedImage back = TextureLoader.get("card_back.png");
            if (back != null) {
                g.drawImage(back, 0, 0, getWidth(), getHeight(), null);
            }
            g.setColor(Color.WHITE);
            g.setFont(new Font("SansSerif", Font.BOLD, 10));
            FontMetrics metrics = g.getFontMetrics();
            String text = name.isEmpty() ? "Leader" : name;
            while (metrics.stringWidth(text) > getWidth() - 8 && text.length() > 4) {
                text = text.substring(0, text.length() - 2) + ".";
            }
            g.drawString(text, (getWidth() - metrics.stringWidth(text)) / 2, getHeight() - 8);
            if (faction != null) {
                g.drawString(faction.name(), 5, 14);
            }
            g.dispose();
        }
    }
}
