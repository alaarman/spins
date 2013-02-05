package spins.promela.compiler.ltsmin.matrix;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import spins.promela.compiler.expression.Expression;
import spins.util.StringWriter;

/**
 *
 * @author FIB
 */
public class LTSminGuardNand extends LTSminGuardBase implements LTSminGuardContainer {
	public List<LTSminGuardBase> guards;

	public LTSminGuardNand() {
		guards = new ArrayList<LTSminGuardBase>();
	}

	public void addGuard(LTSminGuardBase guard) {
//		if(!guard.isDefinitelyTrue()) {
			guards.add(guard);
//		}
	}

	public void addGuard(Expression e) {
		addGuard(new LTSminGuard(e));
	}

	public void prettyPrint(StringWriter w) {
		if(guards.size()>0) {
			boolean first = true;
			w.appendLine("!(");
			w.indent();
			for(LTSminGuardBase g: guards) {
				if(!first) w.append(" && ");
				first = false;
				g.prettyPrint(w);
			}
			w.outdent();
			w.appendLine(")");
		} else {
			w.appendLine("false _GNAND_ ");
		}
	}

	public boolean isDefinitelyTrue() {
		for(LTSminGuardBase g: guards) {
			if(g.isDefinitelyFalse()) {
				return true;
			}
		}
		return false;
	}

	public boolean isDefinitelyFalse() {
		for(LTSminGuardBase g: guards) {
			if(!g.isDefinitelyTrue()) {
				return false;
			}
		}
		return true;
	}

	@Override
	public Iterator<LTSminGuardBase> iterator() {
		return guards.iterator();
	}

	@Override
	public int guardCount() {
		return guards.size();
	}

    public void addGuards(List<LTSminGuardBase> guards2) {
        guards.addAll(guards2);
    }
}