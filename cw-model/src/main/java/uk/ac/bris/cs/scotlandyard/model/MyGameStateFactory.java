package uk.ac.bris.cs.scotlandyard.model;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.util.*;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;

import com.google.common.collect.Sets;
import uk.ac.bris.cs.scotlandyard.model.Board.GameState;
import uk.ac.bris.cs.scotlandyard.model.Move.*;
import uk.ac.bris.cs.scotlandyard.model.Piece.*;

import uk.ac.bris.cs.scotlandyard.model.ScotlandYard.Factory;

/**
 * cw-model
 */
public final class MyGameStateFactory implements Factory<GameState> {

	private static class PlayerTicketBoard implements Board.TicketBoard {
		private final ImmutableMap<ScotlandYard.Ticket, Integer> tickets;

		public PlayerTicketBoard(ImmutableMap<ScotlandYard.Ticket, Integer> tickets) {
			this.tickets = tickets;
		}

		/**
		 * @param ticket the ticket to check count for
		 * @return the amount of ticket, always
		 */
		@Override
		public int getCount(@Nonnull ScotlandYard.Ticket ticket) {
			if (!(tickets.containsKey(ticket))) throw new IllegalArgumentException();
			return tickets.get(ticket);
		}
	}


	private static final class MyGameState implements GameState {
		private final GameSetup setup;
		private final ImmutableSet<Piece> remaining;
		private final ImmutableList<LogEntry> log;
		private final Player mrX;
		private final List<Player> detectives;
		private final ImmutableList<Player> everyone;
		private final ImmutableSet<Move> moves;
		private final int roundNum;
		private final ImmutableSet<Piece> played;
		private final String currMover;


		private MyGameState(final GameSetup setup,
							final ImmutableSet<Piece> remaining,
							final ImmutableList<LogEntry> log,
							final Player mrX,
							final List<Player> detectives,
							final String currMover,
							final ImmutableSet<Piece> played) {

			this.setup = setup;
			this.remaining = remaining;
			this.log = log;
			this.mrX = mrX;
			this.detectives = detectives;
			this.everyone = ImmutableList.<Player>builder().add(this.mrX).addAll(this.detectives).build();
			this.moves = getAvailableMoves();
			this.played = played;
			this.roundNum = getRoundNum();
			this.currMover = currMover;

		}


		/**
		 * @return the current game setup
		 */
		@Nonnull
		@Override
		public GameSetup getSetup() {
			return this.setup;
		}


		/**
		 * @return all players in the game
		 */
		@Nonnull
		@Override
		public ImmutableSet<Piece> getPlayers() {
			Set<Piece> output = new HashSet<>();
			for (Player p : everyone) {
				output.add(p.piece());
			}
			return ImmutableSet.copyOf(output);
		}


		/**
		 * @param detective the detective
		 * @return the location of the given detective; empty if the detective is not part of the game
		 */
		@Nonnull
		@Override
		public Optional<Integer> getDetectiveLocation(Detective detective) {
			for (final var p : detectives) {
				if (p.piece() == detective) return Optional.of(p.location());
			}
			return Optional.empty();
		}


		/**
		 * @param piece the player piece
		 * @return the ticket board of the given player; empty if the player is not part of the game
		 */
		@Nonnull
		@Override
		public Optional<TicketBoard> getPlayerTickets(Piece piece) {
			for (final var p : everyone) {
				if (p.piece() == (piece)) return Optional.of(new PlayerTicketBoard(p.tickets()));
			}
			return Optional.empty();
		}

		@Nonnull
		@Override
		public ImmutableList<LogEntry> getMrXTravelLog() {
			return this.log;
		}


		private boolean mrXCaptured() {
			for(Player d : detectives) {
				if (d.location() == mrX.location()) {
					return true;
				}
			}
			return false;
		}

		/**
		 * @param player the player
		 * @return a list of theoretical moves that a detective could make
		 * if it wasn't blocked by other detectives.
		 * The game shouldn't end if this is true.
		 */
		private ImmutableSet<Move.SingleMove> theoreticalMoves(Player player) {
			final var theoreticalMoves = new ArrayList<Move.SingleMove>();
			for (int destination : setup.graph.adjacentNodes(player.location())) {
				for (ScotlandYard.Transport t : Objects.requireNonNull(setup.graph.edgeValueOrDefault(player.location(), destination, ImmutableSet.of()))) {
					if (player.has(t.requiredTicket())) {
						theoreticalMoves.add(new Move.SingleMove(player.piece(), player.location(), t.requiredTicket(), destination));
					}
				}
			}
			return ImmutableSet.copyOf(theoreticalMoves);
		}

		/**
		 * Finds if a detective is not stuck - if it has no suitable tickets, or
		 * is not surrounded by other completely stuck players
		 */
		private boolean notStuck(Player p) {
			var blockingPlayers = new ArrayList<Player>();
			for (Move.SingleMove m : theoreticalMoves(p)) {
				for (Player d : detectives) { // gather a list of detectives that are blocking p
					if (d.location() == m.destination) blockingPlayers.add(d);
				}
			}
			if (makeSingleMoves(setup, detectives, p, p.location()).isEmpty()) { // if we could make moves but they're being blocked
				for (Player b : blockingPlayers)
					if (notStuck(b)) return true;
			} else return true; // however if p can make moves, p is not stuck
			return false;
		}


		private boolean detectivesAllStuck() {
			for (Player d : detectives) {
				if (notStuck(d)) return false;
			}
			return true;
		}

		@Nonnull
		@Override
		public ImmutableSet<Piece> getWinner() {
			boolean winD = false; // True if a detective wins
            boolean winX = false; // True if Mr X wins
            Set<Piece> output = new HashSet<>(Collections.emptySet());
            if (mrXCaptured()) winD = true;
            else if (detectivesAllStuck() || roundNum > setup.rounds.size()) winX = true;
            else if (getAvailableMoves().isEmpty()) {
                if (currMover.equals("detectives")) winX = true;
                if (currMover.equals("mrx")) winD = true;
            }
            if (winD) {
                for (Player d : detectives) {
                    output.add(d.piece());
                }
            }
            if (winX) output.add(mrX.piece());
            return ImmutableSet.copyOf(output);
		}

		private boolean gameAlreadyOver() {
			return (mrXCaptured() || detectivesAllStuck() || (roundNum > setup.rounds.size()));
		}


		/**
		 * @param setup      The current game setup
		 * @param detectives The current list of detectives
		 * @param player     The player you want to get the moves of
		 * @param source     The origin of the player
		 * @return A set of possible SingleMoves available to the given player
		 */
		private ImmutableSet<SingleMove> makeSingleMoves(GameSetup setup, List<Player> detectives, Player player, int source) {
			final var singleMoves = new ArrayList<SingleMove>();
			for (int destination : setup.graph.adjacentNodes(source)) {
				var occupied = false;

				for (Player d : detectives) { // finds if location is occupied by detective
					if (d.location() == destination) {
						occupied = true;
						break;
					}
				}
				if (occupied) continue;
				for (ScotlandYard.Transport t : Objects.requireNonNull(setup.graph.edgeValueOrDefault(source, destination, ImmutableSet.of()))) {
					if (player.has(t.requiredTicket()))
						singleMoves.add(new SingleMove(player.piece(), source, t.requiredTicket(), destination));
				}
				// add moves to the destination via a Secret ticket if there are any left with the player
				if (player.isMrX() && mrX.has(ScotlandYard.Ticket.SECRET)) {
					singleMoves.add(new Move.SingleMove(player.piece(), source, ScotlandYard.Ticket.SECRET, destination));
				}
			}
			return ImmutableSet.copyOf(singleMoves);
		}


		/**
		 * @param setup      The current game setup
		 * @param detectives The current list of detectives
		 * @param player     The player you want to get the moves of
		 * @param source     The origin of the player
		 * @return A set of possible DoubleMoves available to the given player
		 */
		private ImmutableSet<Move.DoubleMove> makeDoubleMoves(GameSetup setup, List<Player> detectives, Player player, int source) {
			final var doubleMoves = new ArrayList<Move.DoubleMove>();
			if (player.has(ScotlandYard.Ticket.DOUBLE) && (setup.rounds.size() > roundNum)) {
				for (Move.SingleMove m : makeSingleMoves(setup, detectives, player, source)) {
					for (Move.SingleMove n : makeSingleMoves(setup, detectives, player, m.destination)) {
						if (m.ticket != n.ticket || player.hasAtLeast(m.ticket, 2)) { // checks if MrX has enough tickets for both moves
							doubleMoves.add(new Move.DoubleMove(player.piece(), source, m.ticket, m.destination, n.ticket, n.destination)); // adds tickets and destinations of both moves to doubleMoves
						}
					}
				}
			}
			return ImmutableSet.copyOf(doubleMoves);
		}


		/**
		 * @return all moves players can make for a given GameState
		 */
		@Nonnull
		@Override
		public ImmutableSet<Move> getAvailableMoves() {
			final Set<Move> allMoves = new HashSet<>();
			if (!gameAlreadyOver()) {
				for (final var p : everyone) {
					if (remaining.contains(p.piece())) {
						allMoves.addAll(makeSingleMoves(setup, detectives, p, p.location()));
						if (p.isMrX() && p.has(ScotlandYard.Ticket.DOUBLE) && setup.rounds.size() > roundNum) {
							allMoves.addAll(makeDoubleMoves(setup, detectives, p, p.location()));
						}
					}
				}
			}
			return ImmutableSet.copyOf(allMoves);
		}


		/**
		 * Updates log for a single entry (helper function for {@link #updateLog})
		 * @param log         log to update
		 * @param ticket      ticket to update the log with
		 * @param destination destination to update the log with
		 */
		private void updateSingleEntry(ArrayList<LogEntry> log, ScotlandYard.Ticket ticket, int destination, int roundNo) {
			if (setup.rounds.get(roundNo - 1)) {
				log.add(LogEntry.reveal(ticket, destination)); // finds if current Round is a reveal round. It's "roundNo - 1" to account for zero indexing
			} else {
				log.add(LogEntry.hidden(ticket)); // update hidden log
			}
		}


		private int getRoundNum() {
			if(remaining.contains(mrX.piece())) return log.size() + 1;
			return log.size();
		}

		/**
		 * Updates the log with the move provided, accounts for single or double moves
		 * @param log  log to update
		 * @param move move to update the log with
		 */
		private void updateLog(ArrayList<LogEntry> log, Move move) {
			move.visit(new Move.Visitor<Void>() {
				public Void visit(Move.SingleMove singleMove) {
					updateSingleEntry(log, singleMove.ticket, singleMove.destination, roundNum);
					return null;
				}

				public Void visit(Move.DoubleMove doubleMove) {
					updateSingleEntry(log, doubleMove.ticket1, doubleMove.destination1, roundNum);
					updateSingleEntry(log, doubleMove.ticket2, doubleMove.destination2, roundNum + 1);
					return null;
				}
			});
		}


		/**
		 * Computes the next game state given a move from {@link #getAvailableMoves()} has been
		 * chosen and supplied as the parameter
		 *
		 * @param move the move to make
		 * @return the game state of which the given move has been made
		 * @throws IllegalArgumentException if the move was not a move from {@link #getAvailableMoves()}
		 */
		@Override
		public GameState advance(Move move) {
			if (!moves.contains(move)) throw new IllegalArgumentException("The move you chose: " + move + " is illegal");
			final Piece thisPiece = move.commencedBy();
			Player thisPlayer = null;
			for (final var p : everyone) {
				if (p.piece() == thisPiece) thisPlayer = p;
			}
			Optional<Player> updatedPlayer;
			String nextMover;
			Set<Piece> newRemaining = new HashSet<>(remaining);
			Set<Piece> played = Sets.newHashSet(this.played);
			ArrayList<LogEntry> newLog = new ArrayList<>(log);
			List<Player> newDetectives = new ArrayList<>(detectives);
			Player newMrX;

			int newDestination = move.visit(new Move.Visitor<>() {
				@Override
				public Integer visit(Move.SingleMove singleMove) {
					return singleMove.destination;
				}

				@Override
				public Integer visit(Move.DoubleMove doubleMove) {
					return doubleMove.destination2;
				}
			});


			//updating game and player states
			if (thisPiece.isMrX()) {
				nextMover = "detectives";
				newMrX = mrX.use(move.tickets());
				newMrX = newMrX.at(newDestination);
				newRemaining.remove(mrX.piece());
				for (Player d : detectives) newRemaining.add(d.piece());
				updateLog(newLog, move);

			} else { // detectives' move
				Player finalThisPlayer = thisPlayer;
				newDetectives = detectives.stream().filter(d -> d != finalThisPlayer).collect(Collectors.toList());

				Player finalThisPlayer1 = thisPlayer;
				updatedPlayer = detectives.stream().filter(d -> d == finalThisPlayer1).findFirst();
				if (updatedPlayer.isPresent()) {
					newDetectives.add(updatedPlayer.get().use(move.tickets()).at(newDestination));
				}

				// These update the newRemaining with everyone who hasn't played, but do have available moves
				played.add(thisPiece);
				for (Player p : detectives) {
					newRemaining.add(p.piece());
					if (makeSingleMoves(setup, newDetectives, p, p.location()).isEmpty())
						newRemaining.remove(p.piece());
				}
				for (Piece p : played) {
					newRemaining.remove(p);
				}

				newMrX = mrX.give(move.tickets());
				if (newRemaining.isEmpty()) { // End of detectives' turn
					played.clear();
					newRemaining.add(mrX.piece());
					nextMover = "mrx";
				} else nextMover = "detectives"; // There are still detectives that need to make a move
			}

			return new MyGameState(setup, ImmutableSet.copyOf(newRemaining), ImmutableList.copyOf(newLog), newMrX, newDetectives, nextMover, ImmutableSet.copyOf(played));
		}
	}

	/**
	 * Create an instance of the parameterised type given the parameters required for
	 * ScotlandYard game
	 */
	@Nonnull @Override public GameState build(
		GameSetup setup,
		Player mrX,
		ImmutableList<Player> detectives) {

		if (detectives == null) throw new NullPointerException("You must set up at least 1 detective");
		if (mrX == null) throw new NullPointerException("Failed to initialise MrX");
		if (mrX.isDetective()) throw new IllegalArgumentException("The first player must be Mr X.");
		if (setup.rounds.isEmpty()) throw new IllegalArgumentException("You must set up the number of rounds");
		if (setup.graph.nodes().isEmpty()) throw new IllegalArgumentException("You must set up the graph");


		// test location overlap
		for (final var p : detectives) {
			for (final var k : detectives) {
				if ((p != k) && (p.location() == k.location())) {
					throw new IllegalArgumentException();
				}
			}
			//	checks if detective has been given special tickets
			if (p.has(ScotlandYard.Ticket.SECRET))
				throw new IllegalArgumentException("One of your detectives has a secret ticket");
			if (p.has(ScotlandYard.Ticket.DOUBLE))
				throw new IllegalArgumentException("One of your detectives has a x2 ticket");
		}

		return new MyGameState(setup, ImmutableSet.of(mrX.piece()), ImmutableList.of(), mrX, detectives, "mrx", ImmutableSet.of());
	}

}
