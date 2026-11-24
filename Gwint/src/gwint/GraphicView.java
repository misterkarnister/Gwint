package gwint;

import java.awt.Color;
import java.awt.FlowLayout;
import java.util.List;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

public class GraphicView {

    private static final int KART_W_RZEDZIE = 5;

    /** Otwiera okno z podanymi kartami. Konsola/gra dziala dalej normalnie. */
    public static void pokaz(List<Karta> karty) {
        SwingUtilities.invokeLater(() -> {
            JFrame okno = new JFrame("Gwint - podglad kart");
            okno.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            JPanel panel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 10));
            panel.setBackground(new Color(25, 22, 20));
            int rzedy = (int) Math.ceil(karty.size() / (double) KART_W_RZEDZIE);
            panel.setPreferredSize(new java.awt.Dimension(
                    KART_W_RZEDZIE * (CardPanel.W + 10) + 10, rzedy * (CardPanel.H + 10) + 10));
            for (Karta k : karty) {
                panel.add(new CardPanel(k));
            }
            okno.setContentPane(panel);
            okno.pack();
            okno.setLocationRelativeTo(null);
            okno.setVisible(true);
        });
    }
}
