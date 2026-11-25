package gwint;

import gwint.engine.BattleRow;
import gwint.engine.CardView;
import gwint.engine.InstanceIdSource;
import java.util.Objects;

/** A single physical copy of a card, with its own identity during a match. */
public final class CardInstance {

    private static final InstanceIdSource FALLBACK_IDS = InstanceIdSource.sequential();

    private final int uid;
    private Karta card;
    private BattleRow row;
    private boolean summonedByMuster;
    private boolean spawnedToken;
    private boolean transformed;

    /** Uses a process-wide fallback id source; prefer the catalog-aware constructor. */
    public CardInstance(Karta card) {
        this(card, FALLBACK_IDS.nextId());
    }

    public CardInstance(Karta card, InstanceIdSource ids) {
        this(card, Objects.requireNonNull(ids, "ids").nextId());
    }

    public CardInstance(Karta card, int uid) {
        this.card = Objects.requireNonNull(card, "card");
        this.uid = uid;
        this.card.normalize();
    }

    public int uid() {
        return uid;
    }

    /** Legacy accessor used by the console and Swing preview. */
    public Karta card() {
        return card;
    }

    public CardView definition() {
        return card;
    }

    public BattleRow row() {
        return row;
    }

    public void setRow(BattleRow row) {
        this.row = row;
    }

    public boolean isSummonedByMuster() {
        return summonedByMuster;
    }

    public void setSummonedByMuster(boolean summonedByMuster) {
        this.summonedByMuster = summonedByMuster;
    }

    public boolean isSpawnedToken() {
        return spawnedToken;
    }

    public void setSpawnedToken(boolean spawnedToken) {
        this.spawnedToken = spawnedToken;
    }

    public boolean isTransformed() {
        return transformed;
    }

    /** Replaces this instance's definition while preserving its identity and row. */
    public void transformTo(Karta target) {
        this.card = Objects.requireNonNull(target, "target");
        this.card.normalize();
        this.transformed = true;
    }

    @Override
    public String toString() {
        return card.toString();
    }
}
