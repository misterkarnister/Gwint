package gwint;
import java.util.Scanner;
import java.util.ArrayList;
import com.google.gson.Gson;
import java.io.FileReader;
import java.io.IOException;

public class Gwint {
    
    static Karta[] talia;
    static ArrayList<Karta> Player1_Deck = new ArrayList<>();
    
    static ArrayList<Karta> Player2_Deck = new ArrayList<>();
    
    static ArrayList<Karta> Player1_Back = new ArrayList<>();
    static ArrayList<Karta> Player2_Back = new ArrayList<>();
    static ArrayList<Karta> Player1_Mid = new ArrayList<>();
    static ArrayList<Karta> Player2_Mid = new ArrayList<>();
    static ArrayList<Karta> Player1_Front = new ArrayList<>();
    static ArrayList<Karta> Player2_Front = new ArrayList<>();
    public static void deck_show(ArrayList<Karta> deck) {
        int s = deck.size();
        for(int i =0;i<s;i++) {
            System.out.println(i+1+"."+deck.get(i).name+"|Moc:"+deck.get(i).power+"|Rzad:"+deck.get(i).row+"|Typ:"+deck.get(i).type);
        }
    }
    public static void board_show() {
        
    }
    public static void main(String[] args) {
        
        Scanner scanner = new Scanner(System.in);
        
        Gson gson = new Gson();

        try (FileReader reader = new FileReader("assets/data/karty.json")) {
            // Odczyt pliku bezpośrednio do obiektu
            talia = gson.fromJson(reader, Karta[].class);
            
        } catch (IOException e) {
            e.printStackTrace();
        }
        
        for(int i = 0;i<10;i++) {
            int s = talia.length;
            double los = Math.ceil(Math.random()*s);
            Player1_Deck.add(talia[(int)los]);
        }
        
        deck_show(Player1_Deck);
        
    }
    
}
