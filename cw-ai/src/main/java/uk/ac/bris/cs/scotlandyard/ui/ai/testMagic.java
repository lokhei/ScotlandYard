package uk.ac.bris.cs.scotlandyard.ui.ai;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.graph.ImmutableValueGraph;
import com.google.common.io.Resources;
import uk.ac.bris.cs.scotlandyard.model.*;
import uk.ac.bris.cs.scotlandyard.model.Board.GameState;

import javax.annotation.Nonnull;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

import static uk.ac.bris.cs.scotlandyard.model.Piece.Detective.BLUE;
import static uk.ac.bris.cs.scotlandyard.model.Piece.Detective.GREEN;
import static uk.ac.bris.cs.scotlandyard.model.Piece.Detective.RED;
import static uk.ac.bris.cs.scotlandyard.model.Piece.Detective.WHITE;
import static uk.ac.bris.cs.scotlandyard.model.Piece.Detective.YELLOW;
import static uk.ac.bris.cs.scotlandyard.model.Piece.MrX.MRX;
import static uk.ac.bris.cs.scotlandyard.model.ScotlandYard.*;
import static uk.ac.bris.cs.scotlandyard.model.ScotlandYard.Ticket.*;

public class testMagic {

    @Nonnull
    static GameSetup standard24RoundSetup() {
        ImmutableValueGraph<Integer, ImmutableSet<Transport>> defaultGraph;
        try {
            defaultGraph = readGraph(Resources.toString(Resources.getResource("graph.txt"), StandardCharsets.UTF_8));
        } catch (IOException e) { throw new RuntimeException("Unable to read game graph", e); }
        return new GameSetup(defaultGraph, STANDARD24ROUNDS);
    }

    public static void main(String[] args) throws Exception {
        MyAi ai = new MyAi();
        ImmutableMap<Ticket, Integer> whiteTixs = ImmutableMap.of(TAXI, 4, BUS, 5, UNDERGROUND, 0, SECRET, 0);
        ScotlandYard.Factory<GameState> gameStateFactory = new MyGameStateFactory();
        var mrX = new Player(MRX, defaultMrXTickets(), 106);
        var red = new Player(RED, defaultDetectiveTickets(), 91);
        var green = new Player(GREEN, defaultDetectiveTickets(), 29);
        var blue = new Player(BLUE, defaultDetectiveTickets(), 94);
        var white = new Player(WHITE, whiteTixs, 50);
        var yellow = new Player(YELLOW, defaultDetectiveTickets(), 138);
        GameState state = gameStateFactory.build(standard24RoundSetup(), mrX, red, green, blue, white, yellow);

        double distanceFromWhite = ai.dijkstraMrX(state, white, 106);
        if(distanceFromWhite != 7) throw new Exception("Calculated Distance is Incorrect");

        ImmutableMap<Ticket, Integer> newWhiteTixs = ImmutableMap.of(TAXI, 24, BUS, 24, UNDERGROUND, 0, SECRET, 0);
        white = new Player(WHITE, newWhiteTixs, 50);
        ai.dijkstraMrX(state, white, 106);
        if(white.tickets() != newWhiteTixs) throw new Exception("Error with number of tickets not matching as expected.");

        red = red.at(174);
        white = white.at(17);
        mrX = mrX.at(105);

        var modelFactory = (new MyModelFactory());
        var model = modelFactory.build(standard24RoundSetup(),
                mrX,
                white,
                red);

        var terminate = new AtomicBoolean(false);
        ai.pickMove(model.getCurrentBoard(), terminate);
    }
}
