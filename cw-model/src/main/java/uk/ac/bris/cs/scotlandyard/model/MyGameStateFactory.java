package uk.ac.bris.cs.scotlandyard.model;

import com.google.common.collect.ImmutableList;

import javax.annotation.Nonnull;

import com.google.common.collect.ImmutableSet;
import uk.ac.bris.cs.scotlandyard.model.Board.GameState;
import uk.ac.bris.cs.scotlandyard.model.ScotlandYard.Factory;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * cw-model
 * Stage 1: Complete this class
 */
public final class MyGameStateFactory implements Factory<GameState> {


	@Nonnull @Override public GameState build(
			GameSetup setup,
			Player mrX,
			ImmutableList<Player> detectives) {
		return new MyGameState(setup, ImmutableSet.of(Piece.MrX.MRX),	ImmutableList.of(), mrX, detectives);
	}



	private static final class MyGameState implements GameState {

   		/*initialise variables*/
		private GameSetup setup;
		private ImmutableSet<Piece> remaining; //which piece can still move in current round
		private ImmutableList<LogEntry> log;   // to hold the travel log and count the rounds
		private Player mrX;
		private List<Player> detectives;
		private ImmutableList<Player> everyone; //players still in game
		private ImmutableSet<Move> moves;  //to hold the currently possible/available moves
		private ImmutableSet<Piece> winner; //current winner


		/*constructor for myGameState*/
		private MyGameState(final GameSetup setup, final ImmutableSet<Piece> remaining, final ImmutableList<LogEntry> log, final Player mrX, final List<Player> detectives) {
			this.setup = setup;
			this.remaining = remaining;
			this.log = log;
			this.mrX = mrX;
			this.detectives = detectives;

			if (setup.rounds.isEmpty()) throw new IllegalArgumentException("You must set up the nodes");
			if (setup.graph.nodes().isEmpty() || setup.graph.edges().isEmpty()) throw new IllegalArgumentException("You must set up the graph");
			if (detectives == null) throw new NullPointerException("You must set up at least 1 detective");
			if (mrX == null) throw new NullPointerException ("Failed to initialise MrX");
			if (mrX.isDetective()) throw new IllegalArgumentException("The first player must be Mr X.");


//			test location overlap and duplicate detectives
			for (final var p : detectives) {
				for (final var k : detectives) {
					if ((p != k) && (p.location() == k.location())) {
						throw new IllegalArgumentException();
					}
				}
			}

//			Set<Player> set = new HashSet<Player>();
//			for (final var p : detectives) {
//				if (set.contains(p)) throw new IllegalArgumentException();
//				set.add(p);
//			}

//			if (getWinner().isEmpty()) throw new IllegalArgumentException("Winner should be empty initially");


			//	checks if detective has been given special tickets
			for (Player p : detectives) {
				if (p.has(ScotlandYard.Ticket.SECRET))
					throw new IllegalArgumentException("One of your detectives has a secret ticket");
				if (p.has(ScotlandYard.Ticket.DOUBLE))
					throw new IllegalArgumentException("One of your detectives has a x2 ticket");
			}

		}



		@Nonnull @Override public GameSetup getSetup() { return setup; };

		@Nonnull @Override public ImmutableSet<Piece> getPlayers() {
			this.everyone = ImmutableList.<Player>builder().add(this.mrX).addAll(this.detectives).build();

			return ImmutableSet.<Piece>copyOf(
					this.everyone.stream().map(playerss->playerss.piece()).collect(Collectors.toSet())
			);
		};

		@Nonnull @Override public Optional<Integer> getDetectiveLocation(Piece.Detective detective) {
			for (final var p : detectives) {
				if (p.piece() == detective) return Optional.of(p.location());
			}
			return Optional.empty();
		}
		@Nonnull @Override public Optional<TicketBoard> getPlayerTickets(Piece piece) {
//			for (final var p: everyone){
//				if (p.piece() == piece){
//					return Optional.of(new p.tickets());
//
//				}
//			}
			return null;

		}
		@Nonnull @Override public ImmutableList<LogEntry> getMrXTravelLog() {return log;}
		@Nonnull @Override public ImmutableSet<Piece> getWinner() {return null;}  //leave until later
		@Nonnull @Override public ImmutableSet<Move> getAvailableMoves() {return null;}

		@Nonnull @Override public GameState advance(Move move) { return null; };

	}





}
