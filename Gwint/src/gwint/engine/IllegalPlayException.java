package gwint.engine;

/** Thrown when a play has no legal target and must not consume the turn. */
public class IllegalPlayException extends RuntimeException {

    public IllegalPlayException(String message) {
        super(message);
    }
}
