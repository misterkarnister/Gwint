package gwint;

/** Actions a player can choose during a round. */
public final class Actions {

    private Actions() {
    }

    public interface Action {
    }

    /** Play the card at the given index in the player's hand. */
    public static final class Play implements Action {
        public final int handIndex;

        public Play(int handIndex) {
            this.handIndex = handIndex;
        }

        @Override
        public String toString() {
            return "play " + (handIndex + 1);
        }
    }

    /** End participation in the current round. */
    public static final class Pass implements Action {
        @Override
        public String toString() {
            return "pass";
        }
    }

    /** Use the leader ability once per match; does not consume the turn. */
    public static final class Leader implements Action {
        @Override
        public String toString() {
            return "leader";
        }
    }
}
