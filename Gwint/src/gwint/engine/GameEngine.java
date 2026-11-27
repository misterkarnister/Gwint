package gwint.engine;

import gwint.CardInstance;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Resolves card plays and round cleanup. All decisions are exposed as
 * {@link ChoiceRequest}s, so the engine never blocks on an input device.
 */
public final class GameEngine {

    /** Scores recorded before a round's board is cleared. */
    public record RoundResult(int firstScore, int secondScore, Integer winnerIndex) {
    }

    private final GameState state;

    public GameEngine(GameState state) {
        this.state = Objects.requireNonNull(state, "state");
    }

    public GameState state() {
        return state;
    }

    public int effectiveStrength(CardInstance unit) {
        return StrengthCalculator.effectiveStrength(state, unit);
    }

    public int rowStrength(SideState side, BattleRow row) {
        return StrengthCalculator.rowStrength(state, side, row);
    }

    public int totalStrength(SideState side) {
        return StrengthCalculator.totalStrength(state, side);
    }

    /** Explanation of one unit's current strength. */
    public StrengthBreakdown breakdown(CardInstance unit) {
        return StrengthCalculator.evaluate(state, unit);
    }

    /** Explanation of every effect currently active on one row. */
    public RowModifiers rowModifiers(SideState side, BattleRow row) {
        return RowModifiers.of(state, side, row);
    }

    /** Starts resolving a card played from a player's hand. */
    public PlayOutcome playFromHand(SideState actor, CardInstance card) {
        if (card == null || !actor.hand().contains(card)) {
            throw new IllegalPlayException("Card is not in " + actor.name() + "'s hand");
        }
        ResolutionSession session = new ResolutionSession(state);
        session.enqueue(new BeginTaskStep(playTask(actor, card, PlayOrigin.HAND, false)));
        return session.advance();
    }

    private static PlayTask playTask(SideState actor, CardInstance card,
                                     PlayOrigin origin, boolean suppressMuster) {
        return new PlayTask(actor, card, origin, suppressMuster,
                Set.copyOf(card.definition().abilities()));
    }

    private static void enqueuePlay(PlayContext context, SideState actor, CardInstance card,
                                    PlayOrigin origin, boolean suppressMuster) {
        context.steps.addLast(new BeginTaskStep(playTask(actor, card, origin, suppressMuster)));
    }

    /** Scores the current board, then discards all units, specials and weather. */
    public RoundResult finishRound() {
        int first = totalStrength(state.side(0));
        int second = totalStrength(state.side(1));
        Integer winner = first > second
                ? Integer.valueOf(0)
                : second > first ? Integer.valueOf(1) : null;
        state.discardRoundBoard();
        return new RoundResult(first, second, winner);
    }

    /** Places avenger tokens queued by round-end cleanup. */
    public void beginNextRound() {
        state.beginNextRound();
    }

    // ------------------------------------------------------------------
    // Resolution steps. Each step keeps its own PlayTask because a play
    // can queue several inner tasks (Muster / Medic) at the same time.
    // ------------------------------------------------------------------

    private static final class BeginTaskStep implements ResolutionStep {

        private final PlayTask task;

        BeginTaskStep(PlayTask task) {
            this.task = task;
        }

        @Override
        public StepResult execute(GameState state, PlayContext context) {
            if (!context.visited.add(task.card.uid())) {
                return StepResult.CONTINUE;
            }

            if (task.card.definition().cardKind() == CardKind.SPECIAL) {
                if (task.has(Ability.DECOY)) {
                    context.steps.addLast(new ChooseDecoyStep(task));
                } else if (task.has(Ability.COMMANDERS_HORN) || task.has(Ability.MARDROEME)) {
                    context.steps.addLast(new ChooseSpecialRowStep(task));
                }
            } else {
                context.steps.addLast(new ChoosePlacementRowStep(task));
            }
            context.steps.addLast(new RemoveAndPlaceStep(task));
            context.steps.addLast(new ResolveAbilitiesStep(task));
            return StepResult.CONTINUE;
        }
    }

    private static final class ChoosePlacementRowStep implements ResolutionStep {

        private final PlayTask task;

        ChoosePlacementRowStep(PlayTask task) {
            this.task = task;
        }

        @Override
        public StepResult execute(GameState state, PlayContext context) {
            if (task.rowChoice != null) {
                return StepResult.CONTINUE;
            }
            List<BattleRow> options = task.card.definition().playableRows();
            if (options.isEmpty()) {
                throw new IllegalPlayException(task.card.definition().name() + " has no row");
            }
            if (options.size() == 1) {
                task.rowChoice = options.get(0);
                return StepResult.CONTINUE;
            }
            return StepResult.awaitChoice(new RowChoice(
                    task.actor, task.card, options, RowPurpose.PLACEMENT));
        }

        @Override
        public void answer(Object choice) {
            task.rowChoice = (BattleRow) choice;
        }
    }

    private static final class ChooseSpecialRowStep implements ResolutionStep {

        private final PlayTask task;

        ChooseSpecialRowStep(PlayTask task) {
            this.task = task;
        }

        @Override
        public StepResult execute(GameState state, PlayContext context) {
            if (task.rowChoice != null) {
                return StepResult.CONTINUE;
            }
            List<BattleRow> options = new ArrayList<>();
            for (BattleRow row : BattleRow.values()) {
                if (task.actor.special(row) == null) {
                    options.add(row);
                }
            }
            if (options.isEmpty()) {
                throw new IllegalPlayException("All special slots are occupied");
            }
            return StepResult.awaitChoice(new RowChoice(
                    task.actor, task.card, options, RowPurpose.SPECIAL));
        }

        @Override
        public void answer(Object choice) {
            task.rowChoice = (BattleRow) choice;
        }
    }

    private static final class ChooseDecoyStep implements ResolutionStep {

        private final PlayTask task;

        ChooseDecoyStep(PlayTask task) {
            this.task = task;
        }

        @Override
        public StepResult execute(GameState state, PlayContext context) {
            if (task.cardChoice != null) {
                return StepResult.CONTINUE;
            }
            List<CardInstance> options = new ArrayList<>();
            for (CardInstance candidate : task.actor.allUnits()) {
                CardView def = candidate.definition();
                if (!def.isHero() && StrengthCalculator.isUnitLike(candidate)
                        && !def.has(Ability.DECOY)) {
                    options.add(candidate);
                }
            }
            if (options.isEmpty()) {
                throw new IllegalPlayException("No valid unit to replace with Decoy");
            }
            return StepResult.awaitChoice(new CardChoice(
                    task.actor, task.card, options, CardPurpose.DECOY));
        }

        @Override
        public void answer(Object choice) {
            task.cardChoice = (CardInstance) choice;
        }
    }

    private static final class RemoveAndPlaceStep implements ResolutionStep {

        private final PlayTask task;

        RemoveAndPlaceStep(PlayTask task) {
            this.task = task;
        }

        @Override
        public StepResult execute(GameState state, PlayContext context) {
            if (task.origin == PlayOrigin.HAND && !task.actor.removeFromHand(task.card)) {
                throw new IllegalPlayException("Card is no longer in hand: " + task.card);
            }

            CardView def = task.card.definition();
            if (def.cardKind() == CardKind.SPECIAL) {
                placeSpecial(state, def);
            } else {
                placeUnit(state);
            }
            return StepResult.CONTINUE;
        }

        private void placeSpecial(GameState state, CardView def) {
            if (task.has(Ability.DECOY)) {
                CardInstance target = task.cardChoice;
                if (target == null || target.row() == null) {
                    throw new IllegalPlayException("Decoy has no target");
                }
                BattleRow row = target.row();
                if (!task.actor.removeUnit(target)) {
                    throw new IllegalPlayException("Decoy target is no longer on the board");
                }
                task.actor.addToHand(target);
                task.actor.placeUnit(task.card, row);
                task.placedRow = row;
                return;
            }
            if (task.has(Ability.COMMANDERS_HORN) || task.has(Ability.MARDROEME)) {
                BattleRow row = task.rowChoice;
                if (row == null || !task.actor.setSpecial(row, task.card)) {
                    throw new IllegalPlayException("Special slot is already occupied: " + row);
                }
                task.card.setRow(row);
                task.placedRow = row;
                if (task.has(Ability.MARDROEME)) {
                    state.transformBerserkers(task.actor, row);
                }
                return;
            }
            // Weather and global Scorch spend their card from the resolution in
            // their own ability step.
        }

        private void placeUnit(GameState state) {
            SideState boardOwner = task.has(Ability.SPY)
                    ? state.opponent(task.actor) : task.actor;
            BattleRow row = task.rowChoice != null ? task.rowChoice
                    : task.card.definition().preferredRow();
            if (row == null) {
                throw new IllegalPlayException(task.card.definition().name() + " has no row");
            }
            boardOwner.placeUnit(task.card, row);
            task.placedRow = row;
            if (task.origin == PlayOrigin.MUSTER) {
                task.card.setSummonedByMuster(true);
            }
            if (task.origin == PlayOrigin.TOKEN) {
                task.card.setSpawnedToken(true);
            }

            CardInstance slot = state.activeSpecial(boardOwner, row);
            if (slot != null && slot.definition().has(Ability.MARDROEME)) {
                state.transformBerserkers(boardOwner, row);
            }
        }
    }

    private static final class ResolveAbilitiesStep implements ResolutionStep {

        private final PlayTask task;

        ResolveAbilitiesStep(PlayTask task) {
            this.task = task;
        }

        @Override
        public StepResult execute(GameState state, PlayContext context) {
            if (task.has(Ability.SPY)) {
                context.steps.addLast(new SpyStep(task.actor));
            }
            if (task.has(Ability.MEDIC)) {
                context.steps.addLast(new MedicStep(task));
            }
            if (task.has(Ability.MUSTER)) {
                context.steps.addLast(new MusterStep(task));
            }
            if (task.has(Ability.SCORCH_GLOBAL)) {
                context.steps.addLast(new GlobalScorchStep());
            }
            if (task.has(Ability.SCORCH_MELEE)) {
                context.steps.addLast(new RowScorchStep(task.actor, BattleRow.MELEE));
            }
            if (task.has(Ability.SCORCH_RANGED)) {
                context.steps.addLast(new RowScorchStep(task.actor, BattleRow.RANGED));
            }
            if (task.has(Ability.SCORCH_SIEGE)) {
                context.steps.addLast(new RowScorchStep(task.actor, BattleRow.SIEGE));
            }
            if (task.has(Ability.MARDROEME)
                    && task.card.definition().cardKind() == CardKind.UNIT) {
                context.steps.addLast(new UnitMardroemeStep(task));
            }
            if (hasWeatherAbility()) {
                context.steps.addLast(new WeatherStep(task));
            }
            if (task.card.definition().cardKind() == CardKind.SPECIAL
                    && (task.has(Ability.SCORCH_GLOBAL) || task.has(Ability.CLEAR_WEATHER))) {
                context.steps.addLast(new DiscardPlayedSpecialStep(task));
            }
            return StepResult.CONTINUE;
        }

        private boolean hasWeatherAbility() {
            for (Ability ability : task.abilities) {
                if (!WeatherType.forAbility(ability).isEmpty()
                        || ability == Ability.CLEAR_WEATHER) {
                    return true;
                }
            }
            return false;
        }
    }

    private static final class SpyStep implements ResolutionStep {

        private final SideState actor;

        SpyStep(SideState actor) {
            this.actor = actor;
        }

        @Override
        public StepResult execute(GameState state, PlayContext context) {
            actor.draw(2);
            return StepResult.CONTINUE;
        }
    }

    private static final class MedicStep implements ResolutionStep {

        private final PlayTask task;

        MedicStep(PlayTask task) {
            this.task = task;
        }

        @Override
        public StepResult execute(GameState state, PlayContext context) {
            if (task.cardChoice != null) {
                CardInstance target = task.cardChoice;
                if (!task.actor.removeFromDiscard(target)) {
                    throw new IllegalPlayException("Revival target is not in the discard pile");
                }
                enqueuePlay(context, task.actor, target, PlayOrigin.DISCARD, false);
                task.cardChoice = null;
                return StepResult.CONTINUE;
            }

            List<CardInstance> targets = new ArrayList<>();
            for (CardInstance candidate : task.actor.discard()) {
                CardView def = candidate.definition();
                if (def.cardKind() == CardKind.UNIT && !def.isHero()) {
                    targets.add(candidate);
                }
            }
            if (targets.isEmpty()) {
                return StepResult.CONTINUE;
            }
            return StepResult.awaitChoice(new CardChoice(
                    task.actor, task.card, targets, CardPurpose.REVIVE));
        }

        @Override
        public void answer(Object choice) {
            task.cardChoice = (CardInstance) choice;
        }
    }

    private static final class MusterStep implements ResolutionStep {

        private final PlayTask task;

        MusterStep(PlayTask task) {
            this.task = task;
        }

        @Override
        public StepResult execute(GameState state, PlayContext context) {
            if (task.suppressMuster) {
                return StepResult.CONTINUE;
            }
            String group = task.card.definition().musterGroupId();
            if (group == null || group.isBlank()) {
                return StepResult.CONTINUE;
            }

            List<CardInstance> found = new ArrayList<>();
            for (CardInstance candidate : new ArrayList<>(task.actor.deck())) {
                if (matches(candidate, group) && !context.visited.contains(candidate.uid())) {
                    task.actor.removeFromDeck(candidate);
                    found.add(candidate);
                }
            }
            for (CardInstance candidate : new ArrayList<>(task.actor.hand())) {
                if (matches(candidate, group) && !context.visited.contains(candidate.uid())) {
                    task.actor.removeFromHand(candidate);
                    found.add(candidate);
                }
            }
            for (CardInstance card : found) {
                enqueuePlay(context, task.actor, card, PlayOrigin.MUSTER, true);
            }
            return StepResult.CONTINUE;
        }

        private boolean matches(CardInstance card, String group) {
            return group.equals(card.definition().musterGroupId());
        }
    }

    private static final class GlobalScorchStep implements ResolutionStep {

        @Override
        public StepResult execute(GameState state, PlayContext context) {
            List<CardInstance> candidates = new ArrayList<>();
            for (SideState side : new SideState[]{state.side(0), state.side(1)}) {
                for (BattleRow row : BattleRow.values()) {
                    for (CardInstance unit : side.units(row)) {
                        if (StrengthCalculator.isUnitLike(unit)
                                && !unit.definition().isHero()) {
                            candidates.add(unit);
                        }
                    }
                }
            }
            destroyStrongest(state, candidates);
            return StepResult.CONTINUE;
        }
    }

    private static final class RowScorchStep implements ResolutionStep {

        private final SideState actor;
        private final BattleRow targetRow;

        RowScorchStep(SideState actor, BattleRow targetRow) {
            this.actor = actor;
            this.targetRow = targetRow;
        }

        @Override
        public StepResult execute(GameState state, PlayContext context) {
            SideState opponent = state.opponent(actor);
            if (StrengthCalculator.rowStrength(state, opponent, targetRow) < 10) {
                return StepResult.CONTINUE;
            }
            List<CardInstance> candidates = new ArrayList<>();
            for (CardInstance unit : opponent.units(targetRow)) {
                if (StrengthCalculator.isUnitLike(unit)
                        && !unit.definition().isHero()) {
                    candidates.add(unit);
                }
            }
            destroyStrongest(state, candidates);
            return StepResult.CONTINUE;
        }
    }

    private static void destroyStrongest(GameState state, List<CardInstance> candidates) {
        if (candidates.isEmpty()) {
            return;
        }
        int max = Integer.MIN_VALUE;
        for (CardInstance unit : candidates) {
            max = Math.max(max, StrengthCalculator.effectiveStrength(state, unit));
        }
        for (CardInstance unit : new ArrayList<>(candidates)) {
            if (StrengthCalculator.effectiveStrength(state, unit) == max) {
                SideState owner = state.ownerOf(unit);
                if (owner != null) {
                    state.removeUnitToDiscard(owner, unit, GameState.RemovalReason.SCORCH);
                }
            }
        }
    }

    private static final class UnitMardroemeStep implements ResolutionStep {

        private final PlayTask task;

        UnitMardroemeStep(PlayTask task) {
            this.task = task;
        }

        @Override
        public StepResult execute(GameState state, PlayContext context) {
            if (task.placedRow != null) {
                state.transformBerserkers(task.actor, task.placedRow);
            }
            return StepResult.CONTINUE;
        }
    }

    private static final class WeatherStep implements ResolutionStep {

        private final PlayTask task;

        WeatherStep(PlayTask task) {
            this.task = task;
        }

        @Override
        public StepResult execute(GameState state, PlayContext context) {
            if (task.has(Ability.CLEAR_WEATHER)) {
                state.clearWeather();
                return StepResult.CONTINUE;
            }
            for (Ability ability : task.abilities) {
                if (!WeatherType.forAbility(ability).isEmpty()) {
                    state.addWeatherCard(task.card, task.actor);
                    break;
                }
            }
            return StepResult.CONTINUE;
        }
    }

    private static final class DiscardPlayedSpecialStep implements ResolutionStep {

        private final PlayTask task;

        DiscardPlayedSpecialStep(PlayTask task) {
            this.task = task;
        }

        @Override
        public StepResult execute(GameState state, PlayContext context) {
            task.actor.addToDiscard(task.card);
            return StepResult.CONTINUE;
        }
    }
}
