// Copyright 2010, University of Twente, Formal Methods and Tools group
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//    http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package spins.promela.compiler.automaton;

import java.util.Collections;
import java.util.Iterator;

import spins.promela.compiler.Proctype;
import spins.promela.compiler.actions.Action;
import spins.promela.compiler.actions.ActionContainer;
import spins.promela.compiler.actions.ChannelSendAction;
import spins.promela.compiler.actions.ExprAction;
import spins.promela.compiler.expression.ConstantExpression;
import spins.promela.compiler.expression.Expression;
import spins.util.StringWriter;

/**
 * This abstract class describes a Transition that can be part of a {@link Automaton}. Each
 * {@link Transition} must go from one {@link State} to an other. Also each {@link Transition} that
 * is created gets its own unique identifier.
 * 
 * @author Marc de Jonge
 */
public abstract class Transition implements ActionContainer {
	private static class TransitionIdCounter {
		private static int id = 0;

		private static synchronized int nextId() {
			return TransitionIdCounter.id++;
		}
	}

	private State from, to;

	private final int transId;

	private int priority = -1;

	protected Transition(final State from, final State to) {
		if (from == null) {
			throw new IllegalArgumentException("A transition must always come from somewhere!");
		}
		if (to != null && from.getAutomaton() != to.getAutomaton()) {
			throw new IllegalArgumentException("Both States must be part of the same Automaton!");
		}
		this.from = from;
		from.addOut(this);
		this.to = to;
		if (to != null) {
			to.addIn(this);
		}
		transId = TransitionIdCounter.nextId();
	}

	/**
	 * Constructor of Transition. Creates a {@link Transition} from one {@link State} to an other.
	 * Both the from and to state must be part of the same automaton.
	 * 
	 * @param from
	 *            The {@link State} where the {@link Transition} must start.
	 * @param to
	 *            The {@link State} where the {@link Transition} must end.
	 */
	public Transition(Transition t, final State from, final State to) {
		this(from, to);
		if (t != null) {
			this.priority = t.priority;
		}
	}

	/**
	 * Adds a new action to this Transition. The default implementation always throws an
	 * {@link IllegalArgumentException}, because by default these can not be added (some subclasses
	 * may though).
	 * 
	 * @param action
	 *            The action that may be added.
	 */
	public void addAction(Action action) {
		throw new IllegalArgumentException(
			"Please do not add actions to a transition that doesn't support actions");
	}

	/**
	 * Returns the action with the given index. Always returns an {@link IndexOutOfBoundsException}
	 * with the default implementation, because there are no action available.
	 * 
	 * @param index
	 *            The index of the to be retrieved action.
	 * @return The retrieved action.
	 */
	public Action getAction(int index) {
		throw new IndexOutOfBoundsException();
	}

	/**
	 * Changes the State where this Transition originally started from.
	 * 
	 * @param from
	 *            The new {@link State} where this {@link Transition} should start from.
	 */
	public final void changeFrom(final State from) {
		this.from.removeOut(this);
		this.from = from;
		this.from.addOut(this);
	}

	/**
	 * Changes the State when this Transition originally started from. This transition is added
	 * directly after the after transition.
	 * @param from
	 *            The new {@link State} where this {@link Transition} should start from.
	 * @param after
	 *            The {@link Transition} after which this {@link Transition} should be placed.
	 */
	public final void changeFrom(final State from, final Transition after) {
		this.from.removeOut(this);
		this.from = from;
		this.from.addOut(this, after);
	}

	/**
	 * Changes the State where this Transition originally ended in.
	 * 
	 * @param to
	 *            The now {@link State} where this Transition should ended in.
	 */
	public final void changeTo(final State to) {
		State oldTo = this.to;
		this.to = to;
		if (this.to != null) {
			to.addIn(this);
		}
		if (oldTo != null) {
			oldTo.removeIn(this);
		}
	}

	/**
	 * Removes this {@link Transition} completely.
	 */
	public final void delete() {
		from.removeOut(this);
		from = null;
		if (to != null) {
			to.removeIn(this);
			to = null;
		}
	}

	/**
	 * @return true when one of the actions on this transition is handling a channel.
	 */
	public boolean hasChannelSendAction() {
		for (int i = 0; i < getActionCount(); i++) {
			Action action = getAction(i);
			if (action instanceof ChannelSendAction) {
				return true;
			}
		}
		return false;
	}

	void setTo(State to) {
		this.to = to;
	}

	void setFrom(State from) {
		this.from = from;
	}

	/**
	 * Duplicates this Transition. After this function returns, the from State should have to
	 * different Transitions as output that behave exactly the same.
	 * @param from TODO
	 * 
	 * @return The duplicated Transition.
	 */
	public abstract Transition duplicateFrom(State from);

	/**
	 * Returns the number of action that were added to this {@link Transition}. By default zero is
	 * always returned.
	 * 
	 * @return The number of action that were added to this {@link Transition}.
	 */
	public int getActionCount() {
		return 0;
	}

	/**
	 * Returns The number of bytes that are needed to make a backup of the changes that this
	 * transition makes. The default implementation always return zero.
	 * 
	 * @return The number of bytes that are needed to make a backup of the changes that this
	 *         transition makes.
	 */
	public int getBackupSize() {
		return 0;
	}

	/**
	 * @return The state where this state should start from.
	 */
	public State getFrom() {
		return from;
	}

	/**
	 * @return A textual representation of actions that this Transition does.
	 */
	public abstract String getText();

	/**
	 * @return The state where this state should end in.
	 */
	public State getTo() {
		return to;
	}

	/**
	 * @return The unique identifier that can be used to identify this Transition.
	 */
	public int getTransId() {
		return transId;
	}

	/**
	 * Returns whether this Transition can be considered a local action or not. The default
	 * implementation always returns false.
	 * 
	 * @return whether this Transition can be considered a local action or not.
	 */
	public boolean isLocal() {
		return false;
	}

	/**
	 * @return True when in the next state the atomic token should be taken.
	 */
	public final boolean takesAtomicToken() {
		return ((getTo() != null) && getTo().isInAtomic());
	}

	/**
	 * Returns whether this Transition does something useful or not. The default implementation
	 * always return false.
	 * 
	 * @return whether this Transition does something useful or not.
	 */
	public boolean isUseless() {
		return false;
	}

	/**
	 * @see java.lang.Iterable#iterator()
	 */
	@SuppressWarnings("unchecked")
	public Iterator<Action> iterator() {
		return Collections.EMPTY_LIST.iterator();
	}

	/**
	 * Creates a useful textual representation of this Transition, including the states where it
	 * started from and goes to. Useful for debugging.
	 * 
	 * @see java.lang.Object#toString()
	 */
	@Override
	public final String toString() {
		return new StringWriter().appendIf(takesAtomicToken(), "Atomic ").append(
			getClass().getSimpleName()).append(" from ").append(
			from == null ? "nowhere" : from.getStateId()).append(" to ").append(
			to == null ? "nowhere" : to.getStateId()).append(" ").append(
			getText()).toString();
	}

	public boolean isAlwaysEnabled() {
		return false;
	}

	public boolean isAtomic() {
		return (getTo() != null && getTo().isInAtomic());
	}

	public boolean isSkip() {
		Action a;
		try {
			a = getAction(0);
		} catch (IndexOutOfBoundsException iobe) {
			return false;
		}
		if (a instanceof ExprAction) {
			Expression e = ((ExprAction)a).getExpression();
			if (!(e instanceof ConstantExpression)) return false;
			return e.getToken().image.equals("skip");
		}
		return false;
	}

	public Proctype getProc() {
		return getFrom().getAutomaton().getProctype();
	}

	public int getUnlessPriority() {
        return priority;
    }

    public void setUnlessPriority(int p) {
        priority = p;
    }
}

