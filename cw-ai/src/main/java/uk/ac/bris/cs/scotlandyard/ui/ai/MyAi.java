package uk.ac.bris.cs.scotlandyard.ui.ai;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.graph.ValueGraph;

import javafx.util.Pair;
import uk.ac.bris.cs.scotlandyard.model.*;
import uk.ac.bris.cs.scotlandyard.model.ScotlandYard.Transport;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static uk.ac.bris.cs.scotlandyard.model.ScotlandYard.Ticket.BUS;
import static uk.ac.bris.cs.scotlandyard.model.ScotlandYard.Ticket.TAXI;
import static uk.ac.bris.cs.scotlandyard.model.ScotlandYard.Ticket.UNDERGROUND;
import static uk.ac.bris.cs.scotlandyard.model.ScotlandYard.Ticket.SECRET;
import static uk.ac.bris.cs.scotlandyard.model.ScotlandYard.Ticket.DOUBLE;

import static uk.ac.bris.cs.scotlandyard.model.Piece.MrX.MRX;

public class MyAi implements Ai {
	@Nonnull
	@Override
	public String name() {
		return "All Scooby Doo Baddies Ever";
	}


	/**
	 * Determines next node by finding the node with the smallest distance that hasn't been visited yet
	 * @param allNodes all <Node, distance> pairs in the map
	 * @return next node to be checked by Dijkstra's algorithm
	 */
	public int getNextNode(Set<Integer> unvisited, Map<Integer, Pair<Double, Player>> allNodes) {
		// getting our temp distances into a format we can compare
		Set<Pair<Integer, Double>> tempNodes = Sets.newHashSet();
		allNodes.forEach((k, v) -> tempNodes.add(new Pair<>(k, v.getKey())));

		return tempNodes.stream()
				.filter(pair -> unvisited.contains(pair.getKey()))
				.min(Comparator.comparing(Pair::getValue))
				.orElse(new Pair<>(0, 0.0)).getKey(); // .get gets the whole pair, so we want the key of that
	}


	/**
	 * Updates distance and returns if final distance has been found
	 * @param map as the game board
	 * @param allNodes Map of Nodes and Pairs of Doubles as distance to that node and Player requirements to get there
	 * @param current The current node currently being examined
	 * @param unvisited a set of unvisited nodes
	 * @param mrxLocation Int of mr X's location.
	 * @return final distance as a double
	 */
	// Is this specific to MrX, in which case, should we make that clear in the name?
	public Double updateDijkstras(ValueGraph<Integer, ImmutableSet<Transport>> map, Map<Integer, Pair<Double, Player>> allNodes, int current, Set<Integer> unvisited, int mrxLocation) {
		double currentDistance = allNodes.get(current).getKey();
		Player newPlayer = allNodes.get(current).getValue();
		if (newPlayer == null) return Double.MAX_VALUE; // Player turns to null if it has run out of tickets on this route. Therefore route is invalid.
		for (int neighbour : map.adjacentNodes(current)) {
			ScotlandYard.Ticket journeyType  = null;
			if (unvisited.contains(neighbour)) {
				// In theory, there will always be an edge between current and neighbour
				ImmutableSet<Transport> method = map.edgeValue(current, neighbour).orElse(ImmutableSet.of());
				if (method.contains(Transport.TAXI) && newPlayer.has(TAXI)) {
					journeyType = ScotlandYard.Ticket.TAXI;
				} else if (method.contains(Transport.BUS) && newPlayer.has(BUS)) {
					journeyType = ScotlandYard.Ticket.BUS;
				} else if (method.contains(Transport.UNDERGROUND) && newPlayer.has(UNDERGROUND)) {
					journeyType = ScotlandYard.Ticket.UNDERGROUND;
				} else if (method.contains(Transport.FERRY) && newPlayer.has(SECRET)) {
					journeyType = ScotlandYard.Ticket.SECRET;
				}
				if (journeyType  != null) {
					double newDistance = currentDistance + 1; // all tickets are weighted evenly
					double neighbourDistance = allNodes.get(neighbour).getKey();
					if (newDistance < neighbourDistance) {
						allNodes.replace(neighbour, new Pair<>(newDistance, newPlayer.use(journeyType)));
					}
				}
			}
		}
		if(current == mrxLocation) {
			return allNodes.get(current).getKey();
		} else {
			unvisited.remove(current);
			current = getNextNode(unvisited, allNodes);
			return updateDijkstras(map, allNodes, current, unvisited, mrxLocation);
		}
	}

	/**
	 * Sets up Dikstra's algorithm, and then passes to the recursive function that runs it.
	 * @param gameState Board of current gameState
	 * @param detective Player
	 * @param mrxLocation int
	 * @return Double of Final distance
	 */
	public double dijkstraMrX(Board gameState, Player detective, int mrxLocation)  {
		ValueGraph<Integer, ImmutableSet<Transport>> map = gameState.getSetup().graph;
		int source = detective.location();

		Map<Integer, Pair<Double, Player>> allNodes = new HashMap<>(Collections.emptyMap());

		for (int node : map.nodes()) {
			if (node != source) allNodes.put(node, new Pair<>(Double.MAX_VALUE, null));
			else allNodes.put(node, new Pair<>(0.0, detective));
		}

		return updateDijkstras(map, allNodes, source, Sets.newHashSet(map.nodes()), mrxLocation);
	}

	/**
	 * Finds the maximum value in a map, and returns the entire Entry
	 * @param map The map
	 * @param <T> The key of the map
	 * @return the entry in a map that has the minimum value
	 */
		private <T> Map.Entry<T, Double> maxOfMap(Map<T, Double> map) {
			Map.Entry <T, Double > max = null;
			for (Map.Entry<T, Double> entry : map.entrySet()) {
				if (max == null || max.getValue() < entry.getValue()) {
					max = entry;
				}
			}
		return max;
		}


	/**
	 * A function that creates the map of possible destinations from the set of available moves
	 * @param moves immutable set of available moves
	 * @return A map of nodes and the weightings they have, which at this point is 0
	 */
	private Map<Integer, Double> createCandidateDestinations(ImmutableList<Move> moves) {
		Map<Integer, Double> candidateDestinations = new HashMap<>();
		for (Move m : moves) {
			m.visit(new Move.Visitor<Void>() {
				public Void visit(Move.SingleMove singleMove) {
					candidateDestinations.put(singleMove.destination, 0.0);
					return null;
				}

				public Void visit(Move.DoubleMove doubleMove) {
					candidateDestinations.put(doubleMove.destination2, 0.0);
					return null;
				}
			});
		}
		return candidateDestinations;
	}


	/**
	 * Produces a set of all Players in the game
	 * @param board The current game board from model
	 * @param moves Immutable set of available moves
	 * @return A set of all game as Players
	 */
	private Set<Player> everyoneToPlayer(Board board, ImmutableList<Move> moves) {
		Set<Piece> pieces = board.getPlayers();
		Set<Player> everyone = new HashSet<>();
		Player mrx;
		int mrxLocation = moves.iterator().next().source();
		for(Piece p : pieces) {
			Map<ScotlandYard.Ticket, Integer> tickets = ticketGenerator(board, p);
			int location;
			if (p == MRX) {
				mrx = new Player(p, ImmutableMap.copyOf(tickets), mrxLocation);
				everyone.add(mrx);
			} else {
				location = board.getDetectiveLocation((Piece.Detective) p).orElse(0);
				Player detective = new Player(p, ImmutableMap.copyOf(tickets), location);
				everyone.add(detective);
			}
		}
		return everyone;
	}


	/**
	 * Gets MrX from the everyone
	 * @param everyone Set of all players
	 * @return MrX as a single Player from the set of everyone
	 */
	private Player getMrX(Set<Player> everyone) {
		Player mrx = null;
		for(Player p : everyone) {
			if(p.isMrX()) mrx = p;
		}
		return mrx;
	}


	/**
	 * Gets all detectives from everyone
	 * @param everyone Set of all players
	 * @return Set of players that are detectives
	 */
	private Set<Player> getDetectives(Set<Player> everyone) {
		Set<Player> detectives = new HashSet<>();
		for(Player p : everyone) {
			if(!p.isMrX()) detectives.add(p);
		}
		return detectives;
	}


	/**
	 * Generates a Map of Tickets and Integers for a piece
	 * @param board The current game board from model
	 * @param p a piece
	 * @return the piece's tickets
	 */
	private Map<ScotlandYard.Ticket, Integer> ticketGenerator(Board board, Piece p) {
		Set<ScotlandYard.Ticket> ticketTypes = new HashSet<>(List.of(TAXI, UNDERGROUND, BUS, SECRET, DOUBLE));
		Map<ScotlandYard.Ticket, Integer> tickets = new HashMap<>();
		for (ScotlandYard.Ticket t : ticketTypes) {
			tickets.put(t, board.getPlayerTickets(p).orElseThrow().getCount(t));
		}
		return tickets;
	}


	/**
	 * calculates new weightings for the candidate destinations
	 * @param board The current game board from model
	 * @param candidateDestinations A set of destinations and their current weightings as doubles
	 * @param everyone set of all players in the game
	 * @return A new set of destinations and their weightings
	 */
	private Map<Integer, Double> weightingsCalc(Board board, Map<Integer, Double> candidateDestinations, Set<Player> everyone) {
		var modelFactory = (new MyModelFactory());
		Player mrx = getMrX(everyone);
		Set<Player> detectives = getDetectives(everyone);
		for(int destination : candidateDestinations.keySet()){
			double total = 1;
			for(Player detective : detectives) {
				total = total * Math.log(dijkstraMrX(board, detective, destination));
			}
			//Creates a new board to see available moves at candidate destinations
			assert mrx != null;
			Player candidateMrX = mrx.at(destination);
			var candidateBoard = modelFactory.build(board.getSetup(), candidateMrX, ImmutableList.copyOf(detectives));
			int availableMoves = candidateBoard.getCurrentBoard().getAvailableMoves().size();

			double finalWeighting = total * Math.pow(availableMoves, 1.5);
			candidateDestinations.put(destination, finalWeighting);
		}
		return candidateDestinations;
	}


	/**
	 * Chooses a possible move based on how high the weighting is, also includes a double move penalty
	 * @param moves set of all possible moves
	 * @param weightedDestinations the map of destinations with their weights
	 * @return The best possible Move
	 */
	private Move chooseMove(ImmutableList<Move> moves, Map<Integer, Double> weightedDestinations) {
		Move chosenMove = null;
		Map<Move, Double> candidateMoves = new HashMap<>();
		for (Move m : moves) {
			m.visit(new Move.Visitor<Void>() {
				public Void visit(Move.SingleMove singleMove) {
					candidateMoves.put(singleMove, weightedDestinations.get(singleMove.destination));
					return null;
				}

				public Void visit(Move.DoubleMove doubleMove) {
					candidateMoves.put(doubleMove, .0666 * weightedDestinations.get(doubleMove.destination2));
					return null;
				}
			});
			chosenMove = maxOfMap(candidateMoves).getKey();
		}
		return chosenMove;
	}


	/**
	 * Runs multiple functions to produce a Move that the AI should pick.
	 * @param board The current game board from model
	 * @param terminate An atomic boolean saying if the program should be terminated
	 * @return A move
	 */
	@Nonnull
	@Override
	public Move pickMove(
			@Nonnull Board board,
			@Nonnull AtomicBoolean terminate) {

		var moves = board.getAvailableMoves().asList();
		Set<Player> everyone = everyoneToPlayer(board, moves);
		Map<Integer, Double> candidateDestinations = createCandidateDestinations(moves);
		Map<Integer, Double> weightedDestinations = weightingsCalc(board, candidateDestinations, everyone);
		Move chosenMove = chooseMove(moves, weightedDestinations);

		if(chosenMove == null) {
			chosenMove = moves.get(0);
		}
		return chosenMove;
	}
}