package gwint;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.Window;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JProgressBar;

final class UiTheme {

    static final Color BACKGROUND = new Color(25, 22, 20);
    static final Color PANEL = new Color(45, 38, 32);
    static final Color PANEL_LIGHT = new Color(62, 52, 43);
    static final Color WOOD = new Color(68, 45, 31);
    static final Color WOOD_DARK = new Color(30, 21, 16);
    static final Color GOLD = new Color(214, 174, 96);
    static final Color GOLD_LIGHT = new Color(255, 221, 143);
    static final Color BLUE = new Color(42, 88, 150);
    static final Color RED = new Color(150, 42, 48);
    static final Color CRIMSON = new Color(178, 43, 58);
    static final Color TEXT = new Color(238, 230, 214);
    static final Color MUTED = new Color(190, 180, 164);
    static final Color UNIT = new Color(112, 169, 211);
    static final Color SPECIAL = new Color(181, 120, 196);

    private UiTheme() {
    }

    static void fitToScreen(Window window, Dimension preferred) {
        if (preferred.width <= 0 || preferred.height <= 0) {
            return;
        }
        Rectangle bounds = GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getMaximumWindowBounds();
        int availableWidth = Math.max(1, bounds.width - 16);
        int availableHeight = Math.max(1, bounds.height - 16);
        double scale = Math.min(1.0, Math.min(availableWidth / (double) preferred.width,
                availableHeight / (double) preferred.height));
        int width = Math.min(availableWidth,
                Math.max(480, (int) Math.round(preferred.width * scale)));
        int height = Math.min(availableHeight,
                Math.max(360, (int) Math.round(preferred.height * scale)));
        window.setSize(width, height);
        window.setLocation(bounds.x + (bounds.width - width) / 2,
                bounds.y + (bounds.height - height) / 2);
    }

    static String faction(Karta.Faction faction) {
        if (faction == null) {
            return "";
        }
        return switch (faction) {
            case KROLESTWO_POLNOCY -> "Northern Kingdoms";
            case SCOIATEL -> "Scoia'tael";
            case POTWORY -> "Monsters";
            case KROLENSTWO_NILFGARDU -> "Nilfgaard";
            case NEUTRAL -> "Neutral";
        };
    }

    static String passive(Karta.Faction faction) {
        if (faction == null) {
            return "";
        }
        return switch (faction) {
            case KROLESTWO_POLNOCY -> "Disciplined formations and steadfast infantry. A reliable, straightforward style.";
            case SCOIATEL -> "Agile scouts and ambush tactics. Rewards careful positioning and timing.";
            case POTWORY -> "Relentless swarms and monstrous survivors. Builds pressure through numbers.";
            case KROLENSTWO_NILFGARDU -> "Spies and calculated control. Turns information into overwhelming advantage.";
            case NEUTRAL -> "No faction bonus.";
        };
    }

    static Color typeColor(Karta.Type type) {
        if (type == null) {
            return TEXT;
        }
        return switch (type) {
            case NORMAL -> UNIT;
            case HERO -> GOLD_LIGHT;
            case SPECIAL -> SPECIAL;
        };
    }

    static String typeLabel(Karta.Type type) {
        if (type == null) {
            return "";
        }
        return switch (type) {
            case NORMAL -> "Unit";
            case HERO -> "Hero";
            case SPECIAL -> "Special";
        };
    }

    static void styleButton(JButton button) {
        button.setFont(new Font("SansSerif", Font.BOLD, 13));
        button.setBackground(PANEL_LIGHT);
        button.setForeground(GOLD_LIGHT);
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(GOLD, 1),
                BorderFactory.createEmptyBorder(6, 12, 6, 12)));
        button.setFocusPainted(false);
        button.setOpaque(true);
    }

    static void styleProgress(JProgressBar bar) {
        bar.setStringPainted(true);
        bar.setFont(new Font("SansSerif", Font.BOLD, 12));
        bar.setBackground(WOOD_DARK);
        bar.setForeground(GOLD);
        bar.setBorder(BorderFactory.createLineBorder(GOLD, 1));
    }
}
