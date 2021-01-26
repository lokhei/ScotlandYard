package uk.ac.bris.cs.scotlandyard.model;

import com.google.common.collect.ImmutableList;

import javax.annotation.Nonnull;

import com.google.common.collect.ImmutableSet;
import uk.ac.bris.cs.scotlandyard.model.ScotlandYard.Factory;

import java.util.*;


/**
 * cw-model
 * Stage 2: Complete this class
 */
public final class MyModelFactory implements Factory<Model> {

	private static final class MyModel implements Model {

		private final List<Observer> observers;
		private Board.GameState modelState;


		/**
		 * @return the current game board
		 */
		@Nonnull
		@Override
		public Board getCurrentBoard() {
			return modelState;
		}


		/**
		 * Registers an observer to the model. It is an error to register the same observer more than
		 * once.
		 *
		 * @param observer the observer to register
		 */
		@Override
		public void registerObserver(@Nonnull Observer observer) {
			if(observer == null) throw new NullPointerException("Observer argument was null");
			if(observers.contains(observer)) throw new IllegalArgumentException(observer + "already exists");
			observers.add(observer);
		}

		/**
		 * Unregisters an observer to the model. It is an error to unregister an observer not
		 * previously registered with {@link #registerObserver(Model.Observer)}.
		 *
		 * @param observer the observer to register
		 */
		@Override
		public void unregisterObserver(@Nonnull Observer observer) {
			if(observer == null) throw new NullPointerException("Observer argument was null");
			if(!observers.contains(observer)) throw new IllegalArgumentException(observer + "doesn't exist");
			observers.remove(observer);
		}

		/**
		 * @return all currently registered observers of the model
		 */
		@Nonnull
		public ImmutableSet<Observer> getObservers(){
			return ImmutableSet.copyOf(observers);
		}


		/**
		 * @param move delegates the move to the underlying
		 *             {@link uk.ac.bris.cs.scotlandyard.model.Board.GameState}
		 */
		@Override
		public void chooseMove(@Nonnull Move move) {
			modelState = modelState.advance(move);
			Observer.Event event;
			if(modelState.getWinner().isEmpty()){
				event = Observer.Event.MOVE_MADE;
			} else event = Observer.Event.GAME_OVER;
			for (Observer o : observers) o.onModelChanged(modelState, event);
		}

		/**
		 * Constructor
		 * @param setup the game setup
		 * @param mrX mrX
		 * @param detectives the detectives
		 */
		public MyModel(final GameSetup setup,
					   final Player mrX,
					   final ImmutableList<Player> detectives){
			this.observers = new ArrayList<>();
			Factory<Board.GameState> gameStateFactory = new MyGameStateFactory();
			this.modelState = gameStateFactory.build(setup, mrX, detectives);
		}

	}

	/**
	 * Create an instance of the parameterised type given the parameters required for
	 * ScotlandYard game
	 *
	 * @param setup the game setup
	 * @param mrX MrX player
	 * @param detectives detective players
	 * @return an instance of the parameterised type
	 */
	@Nonnull @Override public Model build(GameSetup setup,
										  Player mrX,
										  ImmutableList<Player> detectives) {
		return new MyModel(setup, mrX, detectives);
	}
}
