package gwint;

import gwint.engine.Ability;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import javax.swing.JPanel;

final class CardPanel extends JPanel {

    static final int W = 160;
    static final int H = 240;

    private final Karta karta;
    private final boolean faceDown;
    private final int width;
    private final int height;
    private boolean selected;

    CardPanel(Karta karta) {
        this(karta, false, W, H);
    }

    CardPanel(Karta karta, boolean faceDown) {
        this(karta, faceDown, W, H);
    }

    CardPanel(Karta karta, boolean faceDown, int width, int height) {
        this.karta = karta;
        this.faceDown = faceDown;
        this.width = width;
        this.height = height;
        setPreferredSize(new java.awt.Dimension(width, height));
        setOpaque(false);
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
        g.scale((double) width / W, (double) height / H);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

        if (faceDown) {
            draw(g, "card_back.png", 0, 0, W, H);
        } else {
            paintFace(g);
        }
        if (selected) {
            draw(g, "selection_highlight.png", 0, 0, W, H);
        }
        g.dispose();
    }

    private void paintFace(Graphics2D g) {
        boolean hero = karta.getType() == Karta.Type.HERO;
        boolean special = karta.getType() == Karta.Type.SPECIAL;

        g.setColor(new Color(45, 38, 32));
        g.fillRoundRect(0, 0, W, H, 14, 14);

        draw(g, "art_placeholder.png", 8, 8, W - 16, H - 16);
        draw(g, "frame_basic.png", 0, 0, W, H);

        Font font = new Font("SansSerif", Font.BOLD, 26);
        String name = karta.getName() == null ? "?" : karta.getName();
        while (g.getFontMetrics(font).stringWidth(name) > W - 16 && font.getSize() > 9) {
            font = font.deriveFont((float) font.getSize() - 1);
        }
        g.setFont(font);
        g.setColor(Color.BLACK);
        center(g, name, W / 2 + 2, H - 28);
        g.setColor(Color.WHITE);
        center(g, name, W / 2, H - 30);

        if (!special) {
            draw(g, hero ? "circle_hero.png" : "circle_normal.png", 6, 6, 42, 42);
            g.setFont(new Font("SansSerif", Font.BOLD, 22));
            g.setColor(Color.BLACK);
            center(g, String.valueOf(karta.getPower()), 27, 28);
        }

        String icon = rowIcon();
        if (icon != null) {
            draw(g, icon, 10, 56, 34, 34);
        }

        if (hasAbility()) {
            draw(g, "circle_special.png", 10, 96, 34, 34);
            g.setFont(new Font("SansSerif", Font.BOLD, 16));
            g.setColor(Color.WHITE);
            center(g, abilityLetter(), 27, 114);
        }

        if (hero) {
            g.setColor(new Color(255, 200, 60));
            g.setStroke(new BasicStroke(2f));
            g.drawRoundRect(1, 1, W - 3, H - 3, 14, 14);
        }
    }

    private String rowIcon() {
        if (karta.getRow() == null) {
            return null;
        }
        return switch (karta.getRow()) {
            case PRZOD -> "icon_melee.png";
            case SRODEK -> "icon_range.png";
            case TYL -> "icon_siege.png";
            case OTHER -> "icon_special.png";
        };
    }

    private String abilityLetter() {
        if (karta.abilities().contains(Ability.AGILE)) {
            return "Z";
        }
        if (karta.getAbills() == null) {
            return "?";
        }
        return switch (karta.getAbills()) {
            case MEDYK -> "M";
            case MORAL -> "MO";
            case GRUPA -> "G";
            case SZPIEG -> "S";
            case BRAT -> "B";
            case BOHATER -> "H";
            default -> "?";
        };
    }

    private boolean hasAbility() {
        return karta.getAbills() != null && karta.getAbills() != Karta.Abillities.BOHATER;
    }

    private void draw(Graphics2D g, String file, int x, int y, int w, int h) {
        BufferedImage image = TextureLoader.get(file);
        if (image != null) {
            g.drawImage(image, x, y, w, h, null);
        }
    }

    private void center(Graphics2D g, String text, int cx, int cy) {
        FontMetrics fontMetrics = g.getFontMetrics();
        g.drawString(text, cx - fontMetrics.stringWidth(text) / 2,
                cy + (fontMetrics.getAscent() - fontMetrics.getDescent()) / 2);
    }
}
