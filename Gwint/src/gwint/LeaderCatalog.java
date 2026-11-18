package gwint;

import com.google.gson.Gson;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Loads the available leaders, with a built-in fallback if the file is absent. */
public final class LeaderCatalog {

    private static final String RESOURCE = "assets/data/leaders.json";
    private static final String[] CWD_PATHS = {
        "assets/data/leaders.json",
        "Gwint/assets/data/leaders.json"
    };

    private final List<Leader> leaders;

    private LeaderCatalog(List<Leader> leaders) {
        this.leaders = Collections.unmodifiableList(new ArrayList<>(leaders));
    }

    public static LeaderCatalog load() {
        String json = readJson();
        if (json == null) {
            return new LeaderCatalog(defaults());
        }
        Leader[] parsed = new Gson().fromJson(json, Leader[].class);
        if (parsed == null || parsed.length == 0) {
            return new LeaderCatalog(defaults());
        }
        List<Leader> result = new ArrayList<>();
        for (Leader leader : parsed) {
            if (leader != null && leader.getName() != null && leader.getFaction() != null) {
                result.add(leader);
            }
        }
        return result.isEmpty() ? new LeaderCatalog(defaults()) : new LeaderCatalog(result);
    }

    private static String readJson() {
        try (InputStream in = LeaderCatalog.class.getClassLoader().getResourceAsStream(RESOURCE)) {
            if (in != null) {
                try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                    return readAll(reader);
                }
            }
        } catch (IOException e) {
            return null;
        }
        for (String path : CWD_PATHS) {
            if (Files.exists(Path.of(path))) {
                try {
                    return Files.readString(Path.of(path), StandardCharsets.UTF_8);
                } catch (IOException e) {
                    return null;
                }
            }
        }
        return null;
    }

    private static String readAll(Reader reader) throws IOException {
        StringBuilder sb = new StringBuilder();
        char[] buffer = new char[4096];
        int n;
        while ((n = reader.read(buffer)) != -1) {
            sb.append(buffer, 0, n);
        }
        return sb.toString();
    }

    public List<Leader> all() {
        return leaders;
    }

    public List<Leader> forFaction(Karta.Faction faction) {
        List<Leader> result = new ArrayList<>();
        for (Leader leader : leaders) {
            if (leader.getFaction() == faction) {
                result.add(leader);
            }
        }
        return result;
    }

    private static List<Leader> defaults() {
        List<Leader> list = new ArrayList<>();
        list.add(new Leader(1, "Vernon Roche", Karta.Faction.KROLESTWO_POLNOCY, "Seasoned commander", "NONE"));
        list.add(new Leader(2, "John Natalis", Karta.Faction.KROLESTWO_POLNOCY, "Tactician", "NONE"));
        list.add(new Leader(3, "Esterad Thyssen", Karta.Faction.KROLESTWO_POLNOCY, "Wealthy patron", "NONE"));
        list.add(new Leader(4, "Emhyr var Emreis", Karta.Faction.KROLENSTWO_NILFGARDU, "Emperor", "NONE"));
        list.add(new Leader(5, "Morvran Voorhis", Karta.Faction.KROLENSTWO_NILFGARDU, "Ambitious general", "NONE"));
        list.add(new Leader(6, "Tibor Eggebracht", Karta.Faction.KROLENSTWO_NILFGARDU, "Proud noble", "NONE"));
        list.add(new Leader(7, "Eithne", Karta.Faction.SCOIATEL, "Ancient sage", "NONE"));
        list.add(new Leader(8, "Isengrim Faoiltiarna", Karta.Faction.SCOIATEL, "Guerrilla leader", "NONE"));
        list.add(new Leader(9, "Iorveth", Karta.Faction.SCOIATEL, "Radical elf", "NONE"));
        list.add(new Leader(10, "Dagon", Karta.Faction.POTWORY, "Primitive druid", "NONE"));
        list.add(new Leader(11, "The Unseen Elder", Karta.Faction.POTWORY, "Vampire elder", "NONE"));
        list.add(new Leader(12, "Kayran", Karta.Faction.POTWORY, "Ancient beast", "NONE"));
        return list;
    }
}
