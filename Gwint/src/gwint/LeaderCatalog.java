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
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Loads and validates the leaders defined in {@code assets/data/leaders.json}. */
public final class LeaderCatalog {

    private static final String RESOURCE = "assets/data/leaders.json";
    private static final String[] FILE_PATHS = {
        "Gwint/assets/data/leaders.json",
        "assets/data/leaders.json"
    };
    private static final Set<Karta.Faction> PLAYABLE_FACTIONS = EnumSet.of(
            Karta.Faction.KROLESTWO_POLNOCY,
            Karta.Faction.SCOIATEL,
            Karta.Faction.POTWORY,
            Karta.Faction.KROLENSTWO_NILFGARDU
    );

    private final List<Leader> leaders;

    private LeaderCatalog(List<Leader> leaders) {
        this.leaders = Collections.unmodifiableList(new ArrayList<>(leaders));
    }

    public static LeaderCatalog load() {
        String json = readJson();
        Leader[] parsed = new Gson().fromJson(json, Leader[].class);
        if (parsed == null || parsed.length == 0) {
            throw new IllegalStateException("Leader catalog is empty: " + RESOURCE);
        }
        List<Leader> result = new ArrayList<>();
        Set<Integer> ids = new HashSet<>();
        for (int i = 0; i < parsed.length; i++) {
            Leader leader = parsed[i];
            if (leader == null) {
                throw new IllegalStateException("Null leader at index " + i + " in " + RESOURCE);
            }
            if (leader.getName() == null || leader.getName().isBlank()) {
                throw new IllegalStateException("Leader at index " + i + " has no name");
            }
            if (leader.getFaction() == null || !PLAYABLE_FACTIONS.contains(leader.getFaction())) {
                throw new IllegalStateException("Leader " + leader.getName() + " has no playable faction");
            }
            if (leader.getDescription() == null || leader.getDescription().isBlank()) {
                throw new IllegalStateException("Leader " + leader.getName() + " has no description");
            }
            if (leader.getId() <= 0) {
                throw new IllegalStateException("Leader " + leader.getName() + " has invalid id " + leader.getId());
            }
            if (!ids.add(leader.getId())) {
                throw new IllegalStateException("Duplicate leader id " + leader.getId());
            }
            result.add(leader);
        }
        return new LeaderCatalog(result);
    }

    private static String readJson() {
        for (String path : FILE_PATHS) {
            if (Files.exists(Path.of(path))) {
                try {
                    return Files.readString(Path.of(path), StandardCharsets.UTF_8);
                } catch (IOException e) {
                    throw new IllegalStateException("Cannot read " + path, e);
                }
            }
        }
        try (InputStream in = LeaderCatalog.class.getClassLoader().getResourceAsStream(RESOURCE)) {
            if (in != null) {
                try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                    return readAll(reader);
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read classpath resource " + RESOURCE, e);
        }
        throw new IllegalStateException("Cannot find " + RESOURCE + " on classpath or in working directory");
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
}
