package uk.ac.bris.cs.scotlandyard.model;

import com.google.common.collect.ImmutableList;
import javax.annotation.Nonnull;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import uk.ac.bris.cs.scotlandyard.model.Board.GameState;
import uk.ac.bris.cs.scotlandyard.model.ScotlandYard.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * cw-model
 */
public final class MyGameStateFactory implements Factory<GameState> {

    /**
     * Create an instance of the parameterised type given the parameters required for
     * ScotlandYard game
     *
     * @param setup the game setup
     * @param mrX MrX player
     * @param detectives detective players
     * @return an instance of the parameterised type
     */
    @Nonnull
    @Override
    public GameState build(
            GameSetup setup,
            Player mrX,
            ImmutableList<Player> detectives) {

        if (detectives == null) throw new NullPointerException("You must set up at least 1 detective");
        if (mrX == null) throw new NullPointerException("Failed to initialise MrX");
        if (mrX.isDetective()) throw new IllegalArgumentException("The first player must be Mr X.");
        if (setup.rounds.isEmpty()) throw new IllegalArgumentException("You must set up the number of rounds");
        if (setup.graph.nodes().isEmpty()) throw new IllegalArgumentException("You must set up the graph");

//        test location overlap and duplicate detectives
        for (final var p : detectives) {
            for (final var k : detectives) {
                if ((p != k) && (p.location() == k.location())) {
                    throw new IllegalArgumentException();
                }
            }
        }

        //	checks if detective has been given special tickets
        for (Player p : detectives) {
            if (p.has(Ticket.SECRET))
                throw new IllegalArgumentException("One of your detectives has a secret ticket");
            if (p.has(Ticket.DOUBLE))
                throw new IllegalArgumentException("One of your detectives has a x2 ticket");
        }

        checkColoursAndLocation(mrX, detectives);


        return new MyGameState(setup, ImmutableSet.of(mrX.piece()), ImmutableList.of(), mrX, detectives, "mrx", ImmutableSet.of());
    }

    private static class PlayerTicketBoard implements Board.TicketBoard {
        private final ImmutableMap<Ticket, Integer> tickets;

        public PlayerTicketBoard(ImmutableMap<Ticket, Integer> tickets) {

            this.tickets = tickets;
        }

        /**
         * @param ticket the ticket to check count for
         * @return the amount of ticket, always
         */
        @Override
        public int getCount(@Nonnull Ticket ticket) {
            if(!(tickets.containsKey(ticket))) throw new IllegalArgumentException();
            return tickets.get(ticket);
        }

    }
    private static final class MyGameState implements GameState {


        //initialise variables
        private final GameSetup setup;
        private final ImmutableSet<Piece> remaining; //which piece can still move in current round
        private final ImmutableList<LogEntry> log;   // to hold the travel log and count the rounds
        private final Player mrX;
        private final List<Player> detectives;
        private final ImmutableList<Player> everyone; //players still in game
        private final ImmutableSet<Move> moves;  //to hold the currently possible/available moves
        private final int roundNo;
        private final String currMover;
        private final ImmutableSet<Piece> havePlayed;


        //constructor for myGameState
        /**
         *
         * @param setup The current game setup
         * @param remaining The remaining pieces left to move this turn
         * @param log MrX's log
         * @param mrX MrX
         * @param detectives A list of all detectives
         * @param currMover The current mover, either "detectives" or "mrx"
         * @param havePlayed A set of detectives who have moved this turn
         */
        public MyGameState(final GameSetup setup,
                           final ImmutableSet<Piece> remaining,
                           final ImmutableList<LogEntry> log,
                           final Player mrX,
                           final List<Player> detectives,
                           final String currMover,
                           final ImmutableSet<Piece> havePlayed) {
            this.setup = setup;
            this.remaining = remaining;
            this.log = log;
            this.mrX = mrX;
            this.detectives = detectives;
            List<Player> everyone = new ArrayList<>(detectives);
            everyone.add(mrX);
            this.everyone = ImmutableList.copyOf(everyone);
            this.moves = getAvailableMoves();
            this.currMover = currMover;
            this.roundNo = getRoundNo();
            this.havePlayed = havePlayed;

        }

        /**
         * @param p The piece you want to turn into a Player
         * @return The Player that the piece is associated with
         */
        public Player pieceToPlayer(Piece p) {
            Player player = null;
            for (Player a : everyone) {
                if (a.piece() == p) player = a;
            }
            return player;
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
        public Optional<Integer> getDetectiveLocation(Piece.Detective detective) {
            Player d = pieceToPlayer(detective);
            if (d != null) return Optional.of(d.location());
            return Optional.empty();
        }

        /**
         * @param piece the player piece
         * @return the ticket board of the given player; empty if the player is not part of the game
         */
        @Nonnull
        @Override
        public Optional<TicketBoard> getPlayerTickets(Piece piece) {
            Player player = pieceToPlayer(piece);
            if (player != null) return Optional.of(new PlayerTicketBoard(player.tickets()));
            return Optional.empty();
        }

        /**
         * @return MrX's travel log as a list of {@link LogEntry}s.
         */
        @Nonnull
        @Override
        public ImmutableList<LogEntry> getMrXTravelLog() {
            return this.log;
        }


        private boolean isMrXCaptured() {
            boolean mrxCaptured = false;
            for(Player d : detectives) {
                if (d.location() == mrX.location()) {
                    mrxCaptured = true;
                    break;
                }
            }
            return mrxCaptured;
        }


        /**
         * @return the winner of this game; empty if the game has no winners yet
         * This is mutually exclusive with {@link #getAvailableMoves()}
         */
        @Nonnull
        @Override
        public ImmutableSet<Piece> getWinner() {
            boolean winD = false; // True if a detective wins
            boolean winX = false; // True if Mr X wins
            Set<Piece> output = new HashSet<>(Collections.emptySet());
            if (isMrXCaptured()) winD = true;
            else if (detectivesAllStuck() || roundNo > setup.rounds.size()) winX = true;
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

        /**
         * @param player the player
         * @return a list of theoretical moves that a detective could make
         * if it wasn't blocked by other detectives.
         * Useful because the game shouldn't end if this is true.
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
         * Finds if a detective is completely stuck due to no suitable tickets, or
         * being surrounded by other completely stuck players
         */
        private boolean isDefinitelyStuck(Player p) {
            boolean definitelyStuck = true; // assume they are definitely stuck
            var blockingPlayers = new ArrayList<Player>();
            for (Move.SingleMove m : theoreticalMoves(p)) {
                for (Player d : detectives) { // gather a list of detectives that are blocking p
                    if (d.location() == m.destination) blockingPlayers.add(d);
                }
            }
            if (makeSingleMoves(setup, detectives, p, p.location()).isEmpty()) { // if we could make moves but they're being blocked
                for (Player b : blockingPlayers)
                    if (!isDefinitelyStuck(b)) definitelyStuck = false;
            } else definitelyStuck = false; // however if p can make moves, p is not stuck
            return definitelyStuck;
        }
        /**
         * @return if all detectives are definitely stuck
         */
        private boolean detectivesAllStuck() {
            boolean allStuck = true;
            for (Player d : detectives) {
                allStuck = isDefinitelyStuck(d);
                if(!allStuck) break;
            }
            return allStuck;
        }

        /**
         * @return true if the game is over due to 1. MrX captured, 2. detectives all stuck 3. rounds run out
         * N.B. does not catch if MrX or the detectives is stuck - this is a helper function for {@link #getAvailableMoves()}
         */
        private boolean gameAlreadyOver() {
            boolean gameOver = false;
            if (isMrXCaptured() || detectivesAllStuck() || (roundNo > setup.rounds.size())) {
            gameOver = true;
            }
            return gameOver;
        }



        /**
         * @return the current available moves of the game.
         * This is mutually exclusive with {@link #getWinner()}
         */
        @Nonnull
        @Override
        public ImmutableSet<Move> getAvailableMoves() {
            Set<Move> availableMoves = new HashSet<>();
            if (!gameAlreadyOver()) {
                if (remaining.contains(mrX.piece())) {
                    int currLoc = mrX.location();
                    availableMoves.addAll(makeSingleMoves(setup, detectives, mrX, currLoc));
                    availableMoves.addAll(makeDoubleMoves(setup, detectives, mrX, currLoc));
                } else {
                    for (Piece p : remaining) {
                        Player current = pieceToPlayer(p);
                        int currLoc = current.location();
                        availableMoves.addAll(makeSingleMoves(setup, detectives, current, currLoc));
                    }
                }
            }
            return ImmutableSet.copyOf(availableMoves); // if there is a winner, this will just be null
        }

        /**
         * Helper function for {@link #getAvailableMoves()}
         * @param setup The current game setup
         * @param detectives The current list of detectives
         * @param player The player you want to get the moves of
         * @param source The origin of the player (needs to be explicit for dealing with DoubleMoves)
         * @return A set of possible SingleMoves available to the given player
         */
        private ImmutableSet<Move.SingleMove> makeSingleMoves(GameSetup setup, List<Player> detectives, Player player, int source) {
            final var singleMoves = new ArrayList<Move.SingleMove>();
            for (int destination : setup.graph.adjacentNodes(source)) {
                var occupied = false;
                for (Player d : detectives) { // finds if location is occupied by detective
                    if (d.location() == destination) {
                        occupied = true;
                        break; // If one of the detectives has caught Mr X, there is no need to continue
                    }
                }
                if (occupied) continue;
                for (Transport t : Objects.requireNonNull(setup.graph.edgeValueOrDefault(source, destination, ImmutableSet.of()))) {
                    if (player.has(t.requiredTicket())) {
                        singleMoves.add(new Move.SingleMove(player.piece(), source, t.requiredTicket(), destination));
                    }
                    // add moves to the destination via a Secret ticket if there are any left with the player
                    if (player.isMrX() && mrX.has(ScotlandYard.Ticket.SECRET)) {
                        singleMoves.add(new Move.SingleMove(player.piece(), source, ScotlandYard.Ticket.SECRET, destination));
                    }
                }
            }
            return ImmutableSet.copyOf(singleMoves);
        }

        /**
         * Helper function for {@link #getAvailableMoves()}
         * @param setup The current game setup
         * @param detectives The current list of detectives
         * @param player The player you want to get the moves of
         * @param source The origin of the player (needs to be explicit for dealing with the second move)
         * @return A set of possible DoubleMoves available to the given player
         */
        private ImmutableSet<Move.DoubleMove> makeDoubleMoves(GameSetup setup, List<Player> detectives, Player player, int source) {
            final var doubleMoves = new ArrayList<Move.DoubleMove>();
            if (mrX.has(ScotlandYard.Ticket.DOUBLE) && (roundNo + 1 <= setup.rounds.size())) { // if there are enough rounds:
                for (Move.SingleMove m : makeSingleMoves(setup, detectives, player, source)) { // for each available single move,
                    for (Move.SingleMove n : makeSingleMoves(setup, detectives, player, m.destination)) { // we want to get each available second single move
                        if (m.ticket != n.ticket || player.hasAtLeast(m.ticket, 2)) { // checks if MrX has enough tickets for both moves
                            doubleMoves.add(new Move.DoubleMove(player.piece(), source, m.ticket, m.destination, n.ticket, n.destination)); // adds tickets and destinations of both moves to doubleMoves
                        }
                    }
                }
            }
            return ImmutableSet.copyOf(doubleMoves);
        }


        /**
         * @return the number of the current round
          */
        private int getRoundNo() {
            int roundNo;
            if(remaining.contains(mrX.piece())) roundNo = log.size() + 1;
            else roundNo = log.size();
            return roundNo;
        }



        /**
         * Updates the log with the move provided, accounts for single or double moves
         * @param log log to update
         * @param move move to update the log with
         */
        private void updateLog(ArrayList<LogEntry> log, Move move) {
            move.visit(new Move.Visitor<Void>() {
                public Void visit(Move.SingleMove singleMove) {
                    updateSingleEntry(log, singleMove.ticket, singleMove.destination, roundNo);
                    return null;
                }
                public Void visit(Move.DoubleMove doubleMove) {
                    updateSingleEntry(log, doubleMove.ticket1, doubleMove.destination1, roundNo);
                    updateSingleEntry(log, doubleMove.ticket2, doubleMove.destination2, roundNo+1);
                    return null;
                }
            });
        }

        /**
         * Updates log for a single entry - this is a helper function for {@link #updateLog}
         * @param log log to update
         * @param ticket ticket to update the log with
         * @param destination destination to update the log with
         */
        private void updateSingleEntry(ArrayList<LogEntry> log, ScotlandYard.Ticket ticket, int destination, int roundNo) {
            if (setup.rounds.get(roundNo - 1)) {
                log.add(LogEntry.reveal(ticket, destination)); // finds if current Round is a reveal round. It's "roundNo - 1" to account for zero indexing
            } else {
                log.add(LogEntry.hidden(ticket)); // update hidden log
            }
        }

        /**
         * Computes the next game state given a move from {@link #getAvailableMoves()} has been
         * chosen and supplied as the parameter
         *
         * @param move the move to make
         * @return the game state of which the given move has been made
         * @throws IllegalArgumentException if the move was not a move from
         * {@link #getAvailableMoves()}
         */
        @Override
        public GameState advance(Move move) {
            if (!moves.contains(move)) throw new IllegalArgumentException("The move you chose: " + move + " is illegal");
            final Piece thisPiece = move.commencedBy();
            final Player thisPlayer = pieceToPlayer(thisPiece);
            Optional<Player> updatedPlayer;
            String newMover;
            Set<Piece> newRemaining = new HashSet<>(remaining);
            Set<Piece> havePlayed = Sets.newHashSet(this.havePlayed);
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
            if(thisPiece.isMrX()) {
                newMover = "detectives"; // currently processing mrX's move, so next is the detectives
                newMrX = mrX.use(move.tickets());
                newMrX = newMrX.at(newDestination);
                newRemaining.remove(mrX.piece());
                for (Player d : detectives) newRemaining.add(d.piece());
                updateLog(newLog, move);

            } else { // detectives' move
                newDetectives = detectives.stream().filter(d -> d != thisPlayer).collect(Collectors.toList());

                updatedPlayer = detectives.stream().filter(d -> d == thisPlayer).findFirst();
                if(updatedPlayer.isPresent()){
                    newDetectives.add(updatedPlayer.get().use(move.tickets()).at(newDestination));
                }

                // These update the newRemaining with everyone who hasn't played, but do have available moves
                havePlayed.add(thisPiece);
                for (Player p : detectives) {
                    newRemaining.add(p.piece());
                    if (makeSingleMoves(setup, newDetectives, p, p.location()).isEmpty()) newRemaining.remove(p.piece());
                }
                for (Piece p : havePlayed) {
                    newRemaining.remove(p);
                }

                newMrX = mrX.give(move.tickets());
                if (newRemaining.isEmpty()) { // End of detectives' turn
                    havePlayed.clear();
                    newRemaining.add(mrX.piece());
                    newMover = "mrx";
                } else newMover = "detectives"; // There are still detectives that need to make a move
            }

            return new MyGameState(setup, ImmutableSet.copyOf(newRemaining), ImmutableList.copyOf(newLog), newMrX, newDetectives, newMover, ImmutableSet.copyOf(havePlayed));

        }




    }

    /**
     * This throws exceptions if players passed in aren't valid
     * @param mrX MrX
     * @param detectives A list of all detectives
     */
    public void checkColoursAndLocation(Player mrX, ImmutableList<Player> detectives) {
        int[] colours = {0, 0, 0, 0, 0}; // Red, Green, Blue, White, Yellow.
        ArrayList<Integer> locations = new ArrayList<>(); // stores locations of the players.
        locations.add(mrX.location());
        for (Player p : detectives) {
            if (p == null) throw new NullPointerException("Failed to initialise a Player.");
            if (p.isMrX()) throw new IllegalArgumentException("You must have only one Mr X.");
            switch (p.piece().webColour()) {
                case "#f00": // Red
                    colours[0]++;
                    locations.add(p.location());
                    break;
                case "#0f0": // Green
                    colours[1]++;
                    locations.add(p.location());
                    break;
                case "#00f": // Blue
                    colours[2]++;
                    locations.add(p.location());
                    break;
                case "#fff":  // White
                    colours[3]++;
                    locations.add(p.location());
                    break;
                case "#ff0":  // Yellow
                    colours[4]++;
                    locations.add(p.location());
                    break;
                default:
                    throw new IllegalArgumentException("Funky colour detected");
            }
            for (int j = 0; j <= 4; j++) { // doing this in the for loop so it will throw the exception as soon as it occurs.
                if (colours[j] > 1)
                    throw new IllegalArgumentException("Only allowed one detective of each colour.");
            }
        }

        Set<Integer> set = new HashSet<>(locations);
        if (set.size() < locations.size()) {
            throw new IllegalArgumentException("Every player must have a unique starting position.");
        }
    }







}