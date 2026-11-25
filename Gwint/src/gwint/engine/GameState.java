package gwint.engine;

import gwint.CardInstance;
import gwint.Karta;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Complete mutable game state: two sides, shared weather, and pending spawns. */
public final class GameState {

    /** Why a unit was removed. Only Scorch and cleanup trigger Summon Avenger. */
    public enum RemovalReason {
        SCORCH,
        ROUND_END,
        DECOY
    }

    public record WeatherCard(CardInstance card, SideState owner) {
    }

    public record PendingSpawn(SideState owner, Karta token) {
    }

    private final SideState[] sides;
    private final CardLibrary library;
    private final InstanceIdSource ids;
    private final EnumSet<WeatherType> weather = EnumSet.noneOf(WeatherType.class);
    private final List<WeatherCard> weatherCards = new ArrayList<>();
    private final List<PendingSpawn> pendingSpawns = new ArrayList<>();

    public GameState(SideState first, SideState second, CardLibrary library, InstanceIdSource ids) {
        this.sides = new SideState[]{Objects.requireNonNull(first), Objects.requireNonNull(second)};
        this.library = Objects.requireNonNull(library, "library");
        this.ids = Objects.requireNonNull(ids, "ids");
    }

    public SideState side(int index) {
        return sides[index];
    }

    public SideState opponent(SideState side) {
        if (side == sides[0]) {
            return sides[1];
        }
        if (side == sides[1]) {
            return sides[0];
        }
        throw new IllegalArgumentException("Side does not belong to this game");
    }

    public CardLibrary library() {
        return library;
    }

    public CardInstance newInstance(String cardId) {
        return new CardInstance(library.require(cardId), ids);
    }

    public int nextId() {
        return ids.nextId();
    }

    public Set<WeatherType> weather() {
        return java.util.Collections.unmodifiableSet(weather);
    }

    public List<WeatherCard> weatherCards() {
        return List.copyOf(weatherCards);
    }

    public List<PendingSpawn> pendingSpawns() {
        return List.copyOf(pendingSpawns);
    }

    public boolean isWeatherActive(WeatherType type) {
        return weather.contains(type);
    }

    public void addWeatherCard(CardInstance card, SideState owner) {
        weatherCards.add(new WeatherCard(card, owner));
        for (Ability ability : card.definition().abilities()) {
            weather.addAll(WeatherType.forAbility(ability));
        }
    }

    /** Discards every active weather card and clears all weather flags. */
    public int clearWeather() {
        int removed = weatherCards.size();
        for (WeatherCard entry : weatherCards) {
            entry.owner().addToDiscard(entry.card());
        }
        weatherCards.clear();
        weather.clear();
        return removed;
    }

    public CardInstance activeSpecial(SideState side, BattleRow row) {
        return side.special(row);
    }

    public SideState ownerOf(CardInstance card) {
        for (SideState side : sides) {
            if (side.findUnit(card.uid()) != null) {
                return side;
            }
            List<CardInstance> specials = new ArrayList<>();
            for (BattleRow row : BattleRow.values()) {
                CardInstance special = side.special(row);
                if (special != null) {
                    specials.add(special);
                }
            }
            for (CardInstance special : specials) {
                if (special.uid() == card.uid()) {
                    return side;
                }
            }
        }
        return null;
    }

    /**
     * Moves a unit to its owner's discard. Scorch and round cleanup trigger
     * Summon Avenger; Decoy does not.
     */
    public void removeUnitToDiscard(SideState owner, CardInstance unit, RemovalReason reason) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(unit, "unit");
        if (!owner.removeUnit(unit)) {
            throw new IllegalArgumentException("Unit is not on " + owner.name() + "'s board: " + unit);
        }
        owner.addToDiscard(unit);

        String tokenId = unit.definition().avengerTokenId();
        if (tokenId == null || tokenId.isBlank() || unit.isSpawnedToken()) {
            return;
        }
        Karta token = library.require(tokenId);
        if (reason == RemovalReason.ROUND_END) {
            pendingSpawns.add(new PendingSpawn(owner, token));
        } else if (reason == RemovalReason.SCORCH) {
            CardInstance spawned = new CardInstance(token, ids);
            spawned.setSpawnedToken(true);
            owner.placeUnit(spawned, token.preferredRow());
        }
    }

    /** Transforms Berserkers in one row when Mardroeme is active there. */
    public int transformBerserkers(SideState side, BattleRow row) {
        int transformed = 0;
        for (CardInstance unit : new ArrayList<>(side.units(row))) {
            CardView def = unit.definition();
            if (!def.has(Ability.BERSERKER)
                    || def.transformsToId() == null
                    || def.transformsToId().isBlank()) {
                continue;
            }
            unit.transformTo(library.require(def.transformsToId()));
            transformed++;
        }
        return transformed;
    }

    /** Discards all board units and specials after a round has been scored. */
    public void discardRoundBoard() {
        for (SideState side : sides) {
            for (CardInstance unit : new ArrayList<>(side.allUnits())) {
                removeUnitToDiscard(side, unit, RemovalReason.ROUND_END);
            }
            for (CardInstance special : side.takeSpecialsForCleanup()) {
                side.addToDiscard(special);
            }
        }
        clearWeather();
    }

    /** Places tokens queued by round-end cleanup before the next round starts. */
    public void beginNextRound() {
        for (PendingSpawn spawn : pendingSpawns) {
            CardInstance token = new CardInstance(spawn.token(), ids);
            token.setSpawnedToken(true);
            spawn.owner().placeUnit(token, spawn.token().preferredRow());
        }
        pendingSpawns.clear();
    }
}
