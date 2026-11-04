package gwint;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import javax.swing.*;


public class GraphicView {

    // Rozmiar jednej karty w pikselach
    static final int W = 160, H = 240;
    // Ile kart w jednym rzedzie okna
    static final int KART_W_RZEDZIE = 5;

    // Szuka grafik wzgledem biezacego katalogu (dziala i z folderu Gwint, i z folderu nadrzednego)
    private static final String[] KATALOGI = {"assets/images/cards/", "Gwint/assets/images/cards/"};
    private static final Map<String, BufferedImage> CACHE = new HashMap<>();

    private static BufferedImage grafika(String nazwa) {
        if (CACHE.containsKey(nazwa)) return CACHE.get(nazwa);
        BufferedImage img = null;
        for (String dir : KATALOGI) {
            File f = new File(dir + nazwa);
            if (f.exists()) {
                try { img = ImageIO.read(f); } catch (Exception e) { img = null; }
                break;
            }
        }
        CACHE.put(nazwa, img);
        return img;
    }

    /** Otwiera okno z podanymi kartami. Konsola/gra dziala dalej normalnie. */
    public static void pokaz(List<Karta> karty) {
        SwingUtilities.invokeLater(() -> {
            JFrame okno = new JFrame("Gwint - podglad kart");
            okno.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            JPanel panel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 10));
            panel.setBackground(new Color(25, 22, 20));
            int rzedy = (int) Math.ceil(karty.size() / (double) KART_W_RZEDZIE);
            panel.setPreferredSize(new Dimension(
                    KART_W_RZEDZIE * (W + 10) + 10, rzedy * (H + 10) + 10));
            for (Karta k : karty) panel.add(new KartaPanel(k));
            okno.setContentPane(panel);
            okno.pack();
            okno.setLocationRelativeTo(null);
            okno.setVisible(true);
        });
    }

    // ---------------------------------------------------------------

    private static class KartaPanel extends JPanel {
        private final Karta karta;

        KartaPanel(Karta karta) {
            this.karta = karta;
            setPreferredSize(new Dimension(W, H));
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

            boolean hero = karta.type == Karta.Type.HERO;
            boolean special = karta.type == Karta.Type.SPECIAL;

            // tlo karty
            g.setColor(new Color(45, 38, 32));
            g.fillRoundRect(0, 0, W, H, 14, 14);

            // ilustracja
            rysuj(g, "art_placeholder.png", 8, 8, W - 16, H - 16);
            // rama
            rysuj(g, "frame_basic.png", 0, 0, W, H);

            // nazwa na dole (bez paska, wiekszy font, cien dla czytelnosci)
            Font f = new Font("SansSerif", Font.BOLD, 26);
            String nazwa = karta.name == null ? "?" : karta.name;
            while (g.getFontMetrics(f).stringWidth(nazwa) > W - 16 && f.getSize() > 9) {
                f = f.deriveFont((float) f.getSize() - 1);
            }
            g.setFont(f);
            g.setColor(Color.BLACK);
            srodek(g, nazwa, W / 2 + 2, H - 28);
            g.setColor(Color.WHITE);
            srodek(g, nazwa, W / 2, H - 30);

            // kolko z moca (karty specjalne nie maja mocy)
            if (!special) {
                rysuj(g, hero ? "circle_hero.png" : "circle_normal.png", 6, 6, 42, 42);
                g.setFont(new Font("SansSerif", Font.BOLD, 22));
                // bohater: bialy numer na ciemnym kole, zwykla: czarny na jasnym
                g.setColor(Color.BLACK);
                srodek(g, String.valueOf(karta.power), 27, 28);
            }

            // ikona rzedu (melee/range/siege/special)
            String ikona = ikonaRzedu();
            if (ikona != null) rysuj(g, ikona, 10, 56, 34, 34);

            // umiejetnosc: pod ikona rzedu - kolko circle_special z litera (np. M)
            if (maUmiejetnosc()) {
                BufferedImage kolko = grafika("circle_special.png");
                String plik = (kolko != null) ? "circle_special.png" : "icon_special.png";
                rysuj(g, plik, 10, 96, 34, 34);
                g.setFont(new Font("SansSerif", Font.BOLD, 16));
                g.setColor(Color.WHITE);
                srodek(g, literaUmiejetnosci(), 27, 114);
            }

            // dodatkowa zlota obwodka dla bohaterow
            if (hero) {
                g.setColor(new Color(255, 200, 60));
                g.setStroke(new BasicStroke(2f));
                g.drawRoundRect(1, 1, W - 3, H - 3, 14, 14);
            }
            g.dispose();
        }

        private String ikonaRzedu() {
            if (karta.row == null) return null;
            switch (karta.row) {
                case PRZOD:   return "icon_melee.png";
                case SRODEK:  return "icon_range.png";
                case TYL:     return "icon_siege.png";
                case OTHER:   return "icon_special.png";
                default:      return null;
            }
        }

        private String literaUmiejetnosci() {
            switch (karta.abills) {
                case ZWINNOSC: return "Z";
                case MEDYK:    return "M";
                case MORAL:    return "MO";
                case GRUPA:    return "G";
                case SZPIEG:   return "S";
                case BRAT:     return "B";
                default:       return "?";
            }
        }

        // BOHATER nie liczy sie jako umiejetnosc - bohatera widac po kole z moca
        private boolean maUmiejetnosc() {
            return karta.abills != null && karta.abills != Karta.Abillities.BOHATER;
        }

        private void rysuj(Graphics2D g, String plik, int x, int y, int w, int h) {
            BufferedImage img = grafika(plik);
            if (img != null) g.drawImage(img, x, y, w, h, null);
        }

        private void srodek(Graphics2D g, String tekst, int cx, int cy) {
            FontMetrics fm = g.getFontMetrics();
            g.drawString(tekst, cx - fm.stringWidth(tekst) / 2, cy + (fm.getAscent() - fm.getDescent()) / 2);
        }
    }
}