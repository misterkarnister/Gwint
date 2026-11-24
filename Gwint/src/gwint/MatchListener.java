package gwint;

public interface MatchListener {

    enum Event {
        ACTION,
        ROUND_END,
        MATCH_END
    }

    void onStateChanged(Match match, Event event);
}
