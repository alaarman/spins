package spins.promela.compiler.ltsmin.util;

import static spins.promela.compiler.ltsmin.util.LTSminUtil.assign;
import static spins.promela.compiler.ltsmin.util.LTSminUtil.chanContentsGuard;
import static spins.promela.compiler.ltsmin.util.LTSminUtil.chanLength;
import static spins.promela.compiler.ltsmin.util.LTSminUtil.channelBottom;
import static spins.promela.compiler.ltsmin.util.LTSminUtil.channelNext;
import static spins.promela.compiler.ltsmin.util.LTSminUtil.compare;
import static spins.promela.compiler.ltsmin.util.LTSminUtil.constant;
import static spins.promela.compiler.ltsmin.util.LTSminUtil.decr;
import static spins.promela.compiler.ltsmin.util.LTSminUtil.id;
import static spins.promela.compiler.ltsmin.util.LTSminUtil.incr;
import static spins.promela.compiler.parser.PromelaConstants.ASSIGN;
import static spins.promela.compiler.parser.PromelaConstants.DECR;
import static spins.promela.compiler.parser.PromelaConstants.EQ;
import static spins.promela.compiler.parser.PromelaConstants.GT;
import static spins.promela.compiler.parser.PromelaConstants.GTE;
import static spins.promela.compiler.parser.PromelaConstants.INCR;
import static spins.promela.compiler.parser.PromelaConstants.LT;
import static spins.promela.compiler.parser.PromelaConstants.LTE;
import static spins.promela.compiler.parser.PromelaConstants.NEQ;
import static spins.promela.compiler.parser.PromelaConstants.tokenImage;

import java.util.Iterator;
import java.util.List;

import spins.promela.compiler.ProcInstance;
import spins.promela.compiler.Proctype;
import spins.promela.compiler.actions.Action;
import spins.promela.compiler.actions.AssignAction;
import spins.promela.compiler.actions.ChannelReadAction;
import spins.promela.compiler.actions.ChannelSendAction;
import spins.promela.compiler.actions.ExprAction;
import spins.promela.compiler.actions.OptionAction;
import spins.promela.compiler.expression.AritmicExpression;
import spins.promela.compiler.expression.BooleanExpression;
import spins.promela.compiler.expression.ChannelLengthExpression;
import spins.promela.compiler.expression.ChannelOperation;
import spins.promela.compiler.expression.ChannelReadExpression;
import spins.promela.compiler.expression.CompareExpression;
import spins.promela.compiler.expression.ConstantExpression;
import spins.promela.compiler.expression.EvalExpression;
import spins.promela.compiler.expression.Expression;
import spins.promela.compiler.expression.Identifier;
import spins.promela.compiler.expression.RemoteRef;
import spins.promela.compiler.expression.RunExpression;
import spins.promela.compiler.ltsmin.LTSminDMWalker;
import spins.promela.compiler.ltsmin.LTSminPrinter;
import spins.promela.compiler.ltsmin.LTSminPrinter.ExprPrinter;
import spins.promela.compiler.ltsmin.matrix.DepMatrix;
import spins.promela.compiler.ltsmin.matrix.DepMatrix.DepRow;
import spins.promela.compiler.ltsmin.matrix.RWMatrix.RWDepRow;
import spins.promela.compiler.ltsmin.model.LTSminIdentifier;
import spins.promela.compiler.ltsmin.model.LTSminModel;
import spins.promela.compiler.ltsmin.model.LTSminTransition;
import spins.promela.compiler.ltsmin.model.ResetProcessAction;
import spins.promela.compiler.ltsmin.state.LTSminPointer;
import spins.promela.compiler.ltsmin.state.LTSminStateVector;
import spins.promela.compiler.parser.ParseException;
import spins.promela.compiler.variable.ChannelType;
import spins.promela.compiler.variable.Variable;
import spins.promela.compiler.variable.VariableType;


public class SimplePredicate {
    public SimplePredicate() {}
    public SimplePredicate(Expression e, Identifier id, int c) {
        this.comparison = e.getToken().kind;
        this.id = id;
        this.constant = c;
        this.e = e;
    }

    public Expression e;
    public int comparison;
    public Identifier id;
    public String ref = null;
    public int constant;

    public String getRef(LTSminModel model) {
        if (null!= ref)
            return ref;
        LTSminPointer svp = new LTSminPointer(model.sv, "");
        ExprPrinter p = new ExprPrinter(svp);
        ref = p.print(id);
        assert (!ref.equals(LTSminPrinter.SCRATCH_VARIABLE)); // write-only
        return ref;
    }
    public String toString() {
        String comp = tokenImage[comparison].replace('"', ' ');
        return id + comp + constant;
    }

    public SimplePredicate invert() {
        SimplePredicate copy = new SimplePredicate(e, id, constant);
        switch(comparison) {
            case LT:
                copy.comparison = GTE;
                break;
            case LTE:
                copy.comparison = GT;
                break;
            case EQ:
                copy.comparison = NEQ;
                break;
            case NEQ:
                copy.comparison = EQ;
                break;
            case GT:
                copy.comparison = LTE;
                break;
            case GTE:
                copy.comparison = LT;
                break;
        }
        return copy;
    }
    
    /**
     * Returns whether an action CONFLICTS with a simple predicate, e.g.:
     * x := 5 && x == 4
     * 
     * This is an under-estimation! Therefore, a negative result is useless. For
     * For testing on the negation, use the invert flag.
     * 
     * @param model
     * @param a the action
     * @param sp the simple predicate (x == 4)
     * @param invert if true: the action is inverted: x := 5 --> x := !5
     * @return true if conflict is found, FALSE IF UNKNOWN
     */
    public boolean conflicts (LTSminModel model, Action a, LTSminTransition t,
                              int g, boolean invert) {
        SimplePredicate sp = this;
        SimplePredicate sp1 = new SimplePredicate();
        if (a instanceof AssignAction) {
            AssignAction ae = (AssignAction)a;
            try {
                sp1.id = getConstantId(model, ae.getIdentifier(), null);
            } catch (ParseException e1) {
                return false;
            }
            switch (ae.getToken().kind) {
                case ASSIGN:
                    try {
                        sp1.constant = ae.getExpr().getConstantValue();
                    } catch (ParseException e) {
                        return false;
                    }
                    sp1.comparison = invert ? NEQ : EQ;
                    if (sp1.is_conflict_predicate(model, sp))
                        return true;
                    break;
                case INCR:
                    if (sp1.getRef(model).equals(sp.getRef(model)))
                        if (invert ? gt(sp) : lt(sp))
                            return true;
                    break;
                case DECR:
                    if (sp1.getRef(model).equals(sp.getRef(model)))
                        if (invert ? lt(sp) : gt(sp))
                            return true;
                    break;
                default:
                    throw new AssertionError("unknown assignment type");
            }
        } else if (a instanceof ResetProcessAction) {
            ResetProcessAction rpa = (ResetProcessAction)a;
            Variable pc = model.sv.getPC(rpa.getProcess());
            if (sp.conflicts(model, assign(pc, -1), t, g, invert))
                return true;
            if (sp.conflicts(model, decr(id(LTSminStateVector._NR_PR)), t, g, invert))
                return true;
            //return false;
            Expression e = sp.id.getVariable().getInitExpr();
            if (e == null)
                e = constant(0); 
            Action init = assign(sp.id, e);
            return sp.conflicts(model, init, t, g, invert);
        } else if (a instanceof ExprAction) {
            Expression expr = ((ExprAction)a).getExpression();
            if (expr.getSideEffect() == null) return false; // simple expressions are guards
            RunExpression re = (RunExpression)expr;
            
            if (sp.conflicts(model, incr(id(LTSminStateVector._NR_PR)), t, g, invert))
                return true;
    
            for (Proctype p : re.getInstances()) {
                for (ProcInstance instance : re.getInstances()) { // sets a pc to 0
                    Variable pc = model.sv.getPC(instance);
                    if (sp.conflicts(model, assign(pc, 0), t, g, invert)) {
                        return true;
                    }
                }
                //write to the arguments of the target process
                Iterator<Expression> rei = re.getExpressions().iterator();
                for (Variable v : p.getArguments()) {
                    Expression param = rei.next();
                    if (v.getType() instanceof ChannelType || v.isStatic())
                        continue; //passed by reference or already in state vector
                    try {
                        int val = param.getConstantValue();
                        if (sp.conflicts(model, assign(v, val), t, g, invert)) {
                            return true;
                        }
                    } catch (ParseException e) {}
                }
            }
            for (Action rea : re.getInitActions()) {
                if (sp.conflicts(model, rea, t, g, invert)) {
                    return true;
                }
            }
        } else if(a instanceof ChannelSendAction) {
            ChannelSendAction csa = (ChannelSendAction)a;
            Identifier id = csa.getIdentifier();
            for (int i = 0; i < csa.getExprs().size(); i++) {
                try {
                    int val = csa.getExprs().get(i).getConstantValue();
                    Identifier next = channelNext(id, i);
                    if (sp.conflicts(model, assign(next, constant(val)), t, g, invert)) {
                        return true;
                    }
                } catch (ParseException e) {}
            }
            return sp.conflicts(model, incr(chanLength(id)), t, g, invert);
        } else if(a instanceof OptionAction) { // options in a d_step sequence
            //OptionAction oa = (OptionAction)a;
            //for (Sequence seq : oa) { //TODO
                //Action act = seq.iterator().next(); // guaranteed by parser
                //if (act instanceof ElseAction)
            //}
        } else if(a instanceof ChannelReadAction) { //TODO: identifiers
            ChannelReadAction cra = (ChannelReadAction)a;
            Identifier id = cra.getIdentifier();
            if (!cra.isPoll()) {
                return sp.conflicts(model, decr(chanLength(id)), t, g, invert);
            }
        }
        return false;
    }
    
    private static boolean lt(SimplePredicate sp) {
        return sp.comparison == LT || 
            sp.comparison == LTE;
    }
    
    private static boolean gt(SimplePredicate sp) {
        return sp.comparison == GT || 
            sp.comparison == GTE;
    }
    
    /**
     * Collects all simple predicates in an expression e.
     * SimplePred ::= cvarref <comparison> constant | constant <comparison> cvarref
     * where cvarref is a reference to a singular (channel) variable or a
     * constant index in array variable.
     * @param invert Influences outcome of missed
     */
    public static boolean extract_conjunct_predicates(LTSminModel model,
                                               List<SimplePredicate> sp,
                                               Expression e, boolean invert) {
        int c;
        boolean missed = false;
        if (e instanceof CompareExpression) {
            CompareExpression ce1 = (CompareExpression)e;
            Identifier id;
            try {
                id = getConstantId(model, ce1.getExpr1(), null); // non-strict, since MCE/NES/NDS holds for the same state
                c = ce1.getExpr2().getConstantValue();
                sp.add(new SimplePredicate(e, id, c));
            } catch (ParseException pe) {
                try {
                    id = getConstantId(model, ce1.getExpr2(), null);
                    c = ce1.getExpr1().getConstantValue();
                    sp.add(new SimplePredicate(e, id, c));
                } catch (ParseException pe2) {
                    missed = true; // missed one!
                }
            }
        } else if (e instanceof ChannelReadExpression) {
            ChannelReadExpression cre = (ChannelReadExpression)e;
            Identifier id = cre.getIdentifier();
            missed |= extract_conjunct_predicates(model, sp, chanContentsGuard(id), invert);
            List<Expression> exprs = cre.getExprs();
            for (int i = 0; i < exprs.size(); i++) {
                try { // this is a conjunction of matchings
                    int val = exprs.get(i).getConstantValue();
                    Identifier read = channelBottom(id, i);
                    CompareExpression compare = compare(EQ,
                                                        read, constant(val));
                    missed |= extract_conjunct_predicates(model, sp, compare, invert);
                } catch (ParseException pe2) {
                    missed = true; // missed one!
                }
            }
        } else if (e instanceof ChannelOperation) {
            ChannelOperation co = (ChannelOperation)e;
            String name = co.getToken().image;
            Identifier id = (Identifier)co.getExpression();
            if (((ChannelType)id.getVariable().getType()).isRendezVous())
                return false; // Spin returns true in this case (see garp model)
            VariableType type = id.getVariable().getType();
            int buffer = ((ChannelType)type).getBufferSize();
            Expression left = chanLength(id);
            Expression right = null;
            int op = -1;
            if (name.equals("empty")) {
                op = EQ;
                right = constant (0);
            } else if (name.equals("nempty")) {
                op = NEQ;
                right = constant (0);
            } else if (name.equals("full")) {
                op = EQ;
                right = constant (buffer);
            } else if (name.equals("nfull")) {
                op = NEQ;
                right = constant (buffer);
            } else {
                throw new AssertionError();
            }
            missed |= extract_conjunct_predicates(model, sp, compare(op, left, right), invert);
        } else if (e instanceof RemoteRef) {
            RemoteRef rr = (RemoteRef)e;
            Expression labelExpr = rr.getLabelExpression(model);
            missed |= extract_conjunct_predicates(model, sp, labelExpr, invert);
        } else if (e instanceof ConstantExpression) {
            try {
                int val = e.getConstantValue();
                missed |= invert ? val != 0 : val == 0;
            } catch (ParseException e1) {
                throw new RuntimeException("Dynamic constants?");
            }
        } else if (e instanceof AritmicExpression ||
                   e instanceof Identifier) {
            try {
                int val = e.getConstantValue();
                missed |= invert ? val != 0 : val == 0;
            } catch (ParseException e1) {
                missed = true;
            }
        } else if (e instanceof EvalExpression) {
            EvalExpression eval = (EvalExpression) e;
            missed |= extract_conjunct_predicates(model, sp, eval.getExpression(), invert);
        } else if (e instanceof BooleanExpression) {
           throw new RuntimeException("Was expecting leaf");
        } else {
            return true; // missed one!
        }
        return missed;
    }

    /**
     * Tries to parse an expression as a reference to a singular (channel)
     * variable or a constant index in array variable (a cvarref).
     */
    private static Identifier getConstantId(LTSminModel model, Expression e,
                                     DepRow writes) throws ParseException {
        if (e instanceof LTSminIdentifier) {
        } else if (e instanceof Identifier) {
            Identifier id = (Identifier)e;
            Variable var = id.getVariable();
            if (var.isHidden())
                    throw new ParseException();
            Expression ar = id.getArrayExpr();
            if ((null == ar) != (-1 == var.getArraySize()))
                throw new AssertionError("Invalid array semantics in expression: "+ id);
            if (null != ar) { 
                try {
                    ar = constant(ar.getConstantValue());
                } catch (ParseException pe) {
                    if (writes != null)
                        depCheck(model, ar, writes); // may rethrow
                } // non-strict: do nothing. See getRef().
            }
            Identifier sub = null;
            if (null != id.getSub())
                sub = getConstantId(model, id.getSub(), writes);
            return id(var, ar, sub);
        } else if (e instanceof ChannelLengthExpression)  {
            ChannelLengthExpression cle = (ChannelLengthExpression)e;
            Identifier id = (Identifier)cle.getExpression();
            return getConstantId(model, chanLength(id), writes);
        }
        throw new ParseException();
    }

    public boolean is_conflict_predicate(LTSminModel model, SimplePredicate p2) {

        SimplePredicate p1 = this;
        // assume no conflict
        boolean no_conflict = true;
        // conflict only possible on same variable
        String ref1, ref2;
        try {
            ref1 = p1.getRef(model); // convert to c code string
            ref2 = p2.getRef(model);
        } catch (AssertionError ae) {
            throw new AssertionError("Serialization of expression "+ p1.id +" or "+ p2.id +" failed: "+ ae);
        }
        if (ref1.equals(ref2)) { // syntactic matching, this suffices if we assume expression is evaluated on the same state vector
            switch(p1.comparison) {
                case LT:
                    // no conflict if one of these cases
                    no_conflict =
                    (p2.constant < p1.constant - 1) ||
                    (p2.constant == p1.constant - 1 && p2.comparison != GT) ||
                    (lt(p2) || p2.comparison == NEQ);
                    break;
                case LTE:
                    // no conflict if one of these cases
                    no_conflict =
                    (p2.constant < p1.constant) ||
                    (p2.constant == p1.constant && p2.comparison != GT) ||
                    (lt(p2) || p2.comparison == NEQ);
                    break;
                case EQ:
                    // no conflict if one of these cases
                    no_conflict =
                    (p2.constant == p1.constant && (p2.comparison == EQ || p2.comparison == LTE || p2.comparison == GTE)) ||
                    (p2.constant != p1.constant && p2.comparison == NEQ) ||
                    (p2.constant < p1.constant && p2.comparison == GT || p2.comparison == GTE) ||
                    (p2.constant > p1.constant && lt(p2));
                    break;
                case NEQ:
                    // no conflict if one of these cases
                    no_conflict =
                    (p2.constant != p1.constant) ||
                    (p2.constant == p1.constant && p2.comparison != EQ);
                    break;
                case GT:
                    // no conflict if one of these cases
                    no_conflict =
                    (p2.constant > p1.constant + 1) ||
                    (p2.constant == p1.constant + 1 && p2.comparison != LT) ||
                    (gt(p2) || p2.comparison == NEQ);
                    break;
                case GTE:
                    // no conflict if one of these cases
                    no_conflict =
                    (p2.constant > p1.constant) ||
                    (p2.constant == p1.constant && p2.comparison != LT) ||
                    (gt(p2) || p2.comparison == NEQ);
                    break;
            }
        }
        return !no_conflict;
    }

    public static boolean depCheck(LTSminModel model, Expression e,
                                    RWDepRow rw) {
        if (e == null)
            return false;
        DepMatrix deps = new DepMatrix(1, model.sv.size());
        LTSminDMWalker.walkOneGuard(model, deps, e, 0);
        return rw.dependent(deps.getRow(0));
    }

    public static boolean depCheck(LTSminModel model, Expression e,
                                    DepRow rw) {
        if (e == null)
            return false;
        DepMatrix deps = new DepMatrix(1, model.sv.size());
        LTSminDMWalker.walkOneGuard(model, deps, e, 0);
        return rw.isDependent(deps.getRow(0));
    }
}