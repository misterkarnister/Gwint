package gwint.engine;

/** Deterministic instance id supplier, starting at 1. */
public final class SequentialIds implements InstanceIdSource {

    private int next = 1;

    @Override
    public int nextId() {
        return next++;
    }
}
