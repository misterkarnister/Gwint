package gwint.engine;

import gwint.Karta;

/** Resolves card ids (or names) to definitions. Implemented by CardCatalog. */
public interface CardLibrary {

    Karta card(String id);

    default Karta require(String id) {
        Karta card = card(id);
        if (card == null) {
            throw new IllegalArgumentException("Unknown card id: " + id);
        }
        return card;
    }
}
