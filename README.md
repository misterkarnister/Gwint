# Gwint

Java implementation of the Witcher 3 minigame (simplified, power-only rules).
You build a deck, pick a leader, get 10 cards and exchange up to 2 of them,
then play cards turn by turn until one player wins two rounds.

## Current rules

- Choose one of four factions: Northern Kingdoms, Scoia'tael, Monsters or Nilfgaard.
- Deck: at least **22 non-special cards**, at most **10 special cards**. Neutral
  cards may be added to any faction. Per-card copy limits come from `karty.json`.
- Pick a leader. The leader is recorded but has no effect in this version.
- Draw 10 cards, exchange at most 2 with the remaining deck.
- On your turn: play a card or pass. A round ends when both players have passed.
  The higher total power wins the round; a tie awards no round win.
- Remaining cards carry over to the next round. First to two round wins takes
  the match; if nobody reaches two wins after three rounds, the player with
  more round wins wins, or the match is a draw.

Card abilities, weather and special-card effects are intentionally not
implemented yet — power sums only.

## Build and run

Requires JDK 27 (the NetBeans project is configured with source/target 27)
and Gson (bundled in `Gwint/lib`).

From the project directory:

```
cd Gwint
/home/temper/netbeans/extide/ant/bin/ant jar
java -jar dist/Gwint.jar
```

Or let NetBeans open `Gwint/` and use Run / Clean and Build.

Optional arguments:

```
java -jar dist/Gwint.jar --seed 42        # reproducible random AI
java -jar dist/Gwint.jar --preview        # open the Swing card preview at the end
```

## Console commands

Deck building:

- `list [units|specials]` — show available card ids
- `add <id> [amount]` / `remove <id> [amount]`
- `show` — show the current deck
- `auto` — generate a random legal deck
- `done` — finish (only works when the deck is legal)

During a round:

- `play <number>` — play a card from your hand
- `pass` — end your participation in this round
- `leader` — mark the leader ability as used (once per match)
- `help`

## Tests

```
cd Gwint
/home/temper/netbeans/extide/ant/bin/ant test
```

JUnit 4 and Hamcrest jars are in `Gwint/lib`.

## Layout

- `src/gwint/Karta.java` — card definition loaded from `assets/data/karty.json`
- `src/gwint/CardCatalog.java`, `LeaderCatalog.java` — data loading and validation
- `src/gwint/DeckList.java`, `CardInstance.java`, `Player.java`, `Leader.java` — model
- `src/gwint/Match.java`, `GameRules.java`, `Actions.java` — game engine
- `src/gwint/PlayerController.java`, `ConsoleHumanController.java`,
  `RandomAiController.java` — controllers
- `src/gwint/Gwint.java` — console entry point
- `src/gwint/GraphicView.java` — Swing card preview (not used for gameplay yet)
- `assets/data/leaders.json` — leader definitions
- `test/gwint/` — JUnit tests
