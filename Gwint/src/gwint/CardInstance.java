package gwint;

import java.util.Objects;

/** A single physical copy of a card, with its own identity during a match. */
public final class CardInstance {

    private static int nextUid = 1;

    private final int uid;
    private final Karta card;

    public CardInstance(Karta card) {
        this.card = Objects.requireNonNull(card, "card");
        this.uid = nextUid++;
    }

    public int uid() {
        return uid;
    }

    public Karta card() {
        return card;
    }

    @Override
    public String toString() {
        return card.toString();
    }
}
