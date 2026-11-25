package gwint;

import com.google.gson.Gson;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import gwint.engine.CardLibrary;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Loads and validates the card definitions from {@code assets/data/karty.json}.
 * The file is looked up first as a classpath resource (works from the jar and
 * from the NetBeans build directory) and then relative to the working
 * directory (works when launched from the project root or its parent).
 */
public final class CardCatalog implements CardLibrary {

    private static final String RESOURCE = "assets/data/karty.json";
    private static final String[] CWD_PATHS = {
        "assets/data/karty.json",
        "Gwint/assets/data/karty.json"
    };

    private static final Set<Karta.Faction> PLAYABLE_FACTIONS = EnumSet.of(
            Karta.Faction.KROLESTWO_POLNOCY,
            Karta.Faction.SCOIATEL,
            Karta.Faction.POTWORY,
            Karta.Faction.KROLENSTWO_NILFGARDU
    );

    private final List<Karta> cards;
    private final Map<Integer, Karta> byId;
    private final Map<String, Karta> byKey;

    private CardCatalog(List<Karta> cards) {
        this.cards = Collections.unmodifiableList(new ArrayList<>(cards));
        Map<Integer, Karta> index = new LinkedHashMap<>();
        Map<String, Karta> keys = new LinkedHashMap<>();
        for (Karta k : cards) {
            index.put(k.getId(), k);
            keys.put(String.valueOf(k.getId()), k);
            keys.put(normalizeName(k.getName()), k);
        }
        this.byId = Collections.unmodifiableMap(index);
        this.byKey = Collections.unmodifiableMap(keys);
        validateLinks();
    }

    private static String normalizeName(String name) {
        StringBuilder sb = new StringBuilder();
        boolean dash = false;
        for (char ch : name.toLowerCase(Locale.ROOT).toCharArray()) {
            if ((ch >= 'a' && ch <= 'z') || (ch >= '0' && ch <= '9')) {
                if (dash && sb.length() > 0) {
                    sb.append('-');
                }
                sb.append(ch);
                dash = false;
            } else {
                dash = true;
            }
        }
        return sb.toString();
    }

    private void validateLinks() {
        for (Karta k : cards) {
            String transform = k.getTransformsTo();
            if (transform != null && !transform.isBlank() && !byKey.containsKey(transform)) {
                throw new IllegalStateException("Card " + k.getName()
                        + " transforms into unknown card " + transform);
            }
            String avenger = k.getAvengerTokenId();
            if (avenger != null && !avenger.isBlank() && !byKey.containsKey(avenger)) {
                throw new IllegalStateException("Card " + k.getName()
                        + " spawns unknown token " + avenger);
            }
        }
    }

    public static CardCatalog load() {
        String json = readJson();
        Karta[] parsed = new Gson().fromJson(json, Karta[].class);
        if (parsed == null || parsed.length == 0) {
            throw new IllegalStateException("Card catalog is empty: " + RESOURCE);
        }
        List<Karta> list = new ArrayList<>();
        for (int i = 0; i < parsed.length; i++) {
            Karta k = parsed[i];
            if (k == null) {
                throw new IllegalStateException("Null card at index " + i + " in " + RESOURCE);
            }
            if (k.getName() == null || k.getName().isBlank()) {
                throw new IllegalStateException("Card at index " + i + " has no name");
            }
            if (k.getPower() < 0) {
                throw new IllegalStateException("Card " + k.getName() + " has negative power");
            }
            if (k.getCount() < 1) {
                throw new IllegalStateException("Card " + k.getName() + " has invalid count " + k.getCount());
            }
            if (k.getFaction() == null || k.getType() == null) {
                throw new IllegalStateException("Card " + k.getName() + " has no faction or type");
            }
            k.normalize();
            list.add(k);
        }
        return new CardCatalog(list);
    }

    private static String readJson() {
        try (InputStream in = CardCatalog.class.getClassLoader().getResourceAsStream(RESOURCE)) {
            if (in != null) {
                try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                    return readAll(reader);
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read classpath resource " + RESOURCE, e);
        }
        for (String path : CWD_PATHS) {
            if (Files.exists(Path.of(path))) {
                try {
                    return Files.readString(Path.of(path), StandardCharsets.UTF_8);
                } catch (IOException e) {
                    throw new IllegalStateException("Cannot read " + path, e);
                }
            }
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

    public List<Karta> all() {
        return cards;
    }

    public Karta byId(int id) {
        return byId.get(id);
    }

    /** Resolves numeric ids and normalized names for transforms/tokens. */
    @Override
    public Karta card(String id) {
        if (id == null) {
            return null;
        }
        return byKey.get(id);
    }

    /** Cards that a player who chose {@code faction} is allowed to put in the deck. */
    public List<Karta> allowedFor(Karta.Faction faction) {
        List<Karta> result = new ArrayList<>();
        for (Karta k : cards) {
            if (!k.isToken() && (k.getFaction() == faction
                    || k.getFaction() == Karta.Faction.NEUTRAL)) {
                result.add(k);
            }
        }
        return result;
    }

    public List<Karta> unitsFor(Karta.Faction faction) {
        List<Karta> result = new ArrayList<>();
        for (Karta k : allowedFor(faction)) {
            if (!k.isSpecial()) {
                result.add(k);
            }
        }
        return result;
    }

    public List<Karta> specials() {
        List<Karta> result = new ArrayList<>();
        for (Karta k : cards) {
            if (k.isSpecial()) {
                result.add(k);
            }
        }
        return result;
    }

    public List<Karta.Faction> playableFactions() {
        return new ArrayList<>(PLAYABLE_FACTIONS);
    }
}
