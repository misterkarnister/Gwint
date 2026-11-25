package gwint.engine;

/** Supplies unique, deterministic instance ids. */
@FunctionalInterface
public interface InstanceIdSource {

    int nextId();

    static InstanceIdSource sequential() {
        return new SequentialIds();
    }
}
