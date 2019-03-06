package org.logicng.explanations.backbones.algorithms;

import org.logicng.collections.LNGIntVector;
import org.logicng.datastructures.Tristate;
import org.logicng.explanations.backbones.Backbone;
import org.logicng.explanations.backbones.BackboneConfig;
import org.logicng.formulas.Formula;
import org.logicng.formulas.FormulaFactory;
import org.logicng.formulas.Literal;
import org.logicng.formulas.Variable;
import org.logicng.solvers.datastructures.MSClause;
import org.logicng.solvers.datastructures.MSWatcher;
import org.logicng.solvers.sat.MiniSat2Solver;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.SortedSet;
import java.util.Stack;
import java.util.TreeSet;

/**
 * An extension of MiniSat to compute the backbone of a formula.
 *
 * The algorithm iteratively checks each variable of an initial model (candidates) whether the variable
 * is a backbone variable. For each check the SAT solving procedure is called. Thus, at the number of SAT calls is at
 * most the number of variables.
 *
 * Reference: Algorithm 3 in M. Janota, I. Lynce, J. Marques-Silva, Algorithms for Computing Backbones of Propositional
 * Formulae, AI Communications, Volume 28(2), 161-177, 2015.
 */
public class MiniSatBackbone extends MiniSat2Solver {

    // TODO experimental backbone solver and not extensively tested yet!

    private final FormulaFactory f;
    private final BackboneConfig config;

    private Stack<Integer> candidates;
    private List<Integer> positiveBackbone;
    private List<Integer> negativeBackbone;

    public MiniSatBackbone(FormulaFactory f, BackboneConfig config) {
        this.f = f;
        this.config = config;
    }

    public MiniSatBackbone(FormulaFactory f) {
        this(f, new BackboneConfig.Builder().build());
    }

    /**
     * Adds an arbitrary formula to the propagator.
     *
     * @param formula the formula
     */
    public void add(final Formula formula) {
        final Formula cnf = formula.cnf();
        switch (cnf.type()) {
            case TRUE:
                break;
            case FALSE:
            case LITERAL:
            case OR:
                this.addClause(generateClauseVector(cnf), null);
                break;
            case AND:
                for (final Formula op : cnf) {
                    this.addClause(generateClauseVector(op), null);
                }
                break;
            default:
                throw new IllegalStateException("Unexpected formula type in CNF: " + cnf.type());
        }
    }

    /**
     * Computes the backbone.
     *
     * @param relevantVariables relevant variables
     * @return the backbone projected to the relevant variables
     */
    public Backbone compute(List<Formula> restrictions, final Collection<Variable> relevantVariables) {
        int[] state = this.saveState();
        for (Formula formula : restrictions) {
            this.add(formula.cnf());
        }

        final boolean initSAT = solve(null) == Tristate.TRUE;
        if (!initSAT) {
            loadState(state);
            return null;
        }
        compute(toVarIndices(relevantVariables));
        if (positiveBackbone == null && negativeBackbone == null) {
            loadState(state);
            return null;
        } else {
            final SortedSet<Variable> backboneLiteralsPos = new TreeSet<>();
            final SortedSet<Variable> backboneLiteralsNeg = new TreeSet<>();
            for (final Integer lit : positiveBackbone) {
                backboneLiteralsPos.add(intLiteralToVariable(lit));
            }
            for (final Integer lit : negativeBackbone) {
                backboneLiteralsNeg.add(intLiteralToVariable(lit));
            }
            loadState(state);
            return new Backbone(backboneLiteralsPos, backboneLiteralsNeg, computeOptionalVariables(new TreeSet<>(relevantVariables), backboneLiteralsPos, backboneLiteralsNeg));
        }
    }

    /**
     * Computes the variables from the formula that are satisfiable but not in the backbone.
     * @param relevantVariablesCopy the relevant variables for the backbone computation
     * @param backboneLiteralsPos the computed positive backbone literals
     * @param backboneLiteralsNeg the computed negative backbone literals
     * @return the difference between the two input sets which are the variables that are satisfiable but not in the
     * backbone
     */
    private Collection<Variable> computeOptionalVariables(Collection<Variable> relevantVariablesCopy, SortedSet<Variable> backboneLiteralsPos, SortedSet<Variable> backboneLiteralsNeg) {
        Collection<Variable> backboneVariables = new TreeSet<>();
        for (Literal backboneLiteral : backboneLiteralsPos) {
            backboneVariables.add(backboneLiteral.variable());
        }
        for (Literal backboneLiteral : backboneLiteralsNeg) {
            backboneVariables.add(backboneLiteral.variable());
        }
        relevantVariablesCopy.removeAll(backboneVariables);
        return relevantVariablesCopy;
    }

    private List<Integer> toVarIndices(final Collection<Variable> variables) {
        final List<Integer> varIndices = new ArrayList<>(variables.size());
        for (final Variable v : variables) {
            // TODO removal of unknown variables may lead to buggy result
            Integer idx = name2idx.get(v.name());
            if (idx != null) {
                varIndices.add(idx);
            }
        }
        return varIndices;
    }

    private void init() {
        this.candidates = new Stack<>();
        this.positiveBackbone = new ArrayList<>();
        this.negativeBackbone = new ArrayList<>();
    }

    private boolean isUPZeroLit(final int var) {
        return this.vars.get(var).level() == 0;
    }

    private boolean isUnit(final int lit, final MSClause clause) {
        for (int i = 0; i < clause.size(); ++i) {
            final int clauseLit = clause.get(i);
            // TODO this unit check can surely be improved
            if (lit != clauseLit && this.model.get((var(clauseLit))) != sign(clauseLit)) {
                return false;
            }
        }
        return true;
    }

    private boolean isRotatable(final int lit) {
        // Unit propagated literals cannot be rotatable
        if (v(var(lit)).reason() != null) {
            return false;
        }
        for (final MSWatcher watcher : this.watches.get(not(lit))) {
            if (isUnit(lit, watcher.clause())) {
                return false;
            }
        }
        return true;
    }

    private void addBackboneLiteral(final int backbonelit) {
        if (sign(backbonelit)) {
            negativeBackbone.add(backbonelit);
        } else {
            positiveBackbone.add(backbonelit);
        }
        addClause(backbonelit, null);
    }

    private Stack<Integer> createInitialCandidates(List<Integer> relevantVariables) {
        for (final Integer var : relevantVariables) {
            if (config.isInitialLBCheckForUPZeroLiterals() && isUPZeroLit(var)) {
                int backboneLit = mkLit(var, !this.model.get(var));
                if (sign(backboneLit)) {
                    negativeBackbone.add(backboneLit);
                } else {
                    positiveBackbone.add(backboneLit);
                }
            } else {
                final int lit = mkLit(var, !this.model.get(var));
                if (!config.isInitialUBCheckForRotatableLiterals() || !isRotatable(lit)) {
                    candidates.add(lit);
                }
            }
        }
        return candidates;
    }

    private void refineUpperBound() {
        for (final Integer candidateLit : new ArrayList<>(candidates)) {
            if (config.isCheckForUPZeroLiterals() && isUPZeroLit(var(candidateLit))) {
                candidates.remove(candidateLit);
                addBackboneLiteral(candidateLit);
            } else if (config.isCheckForComplementModelLiterals() && this.model.get(var(candidateLit)) == sign(candidateLit)) {
                candidates.remove(candidateLit);
            } else if (config.isCheckForRotatableLiterals() && isRotatable(candidateLit)) {
                candidates.remove(candidateLit);
            }
        }
    }

    private void compute(final List<Integer> relevantVariables) {
        init();
        final Stack<Integer> candidates = createInitialCandidates(relevantVariables);
        while (candidates.size() > 0) {
            final int lit = candidates.pop();
            final LNGIntVector assumptions = new LNGIntVector(1);
            assumptions.push(not(lit));
            final boolean sat = solve(null, assumptions) == Tristate.TRUE;
            if (!sat) {
                addBackboneLiteral(lit);
            } else {
                refineUpperBound();
            }
        }
    }

    /**
     * Transforms a solver integer literal to the corresponding formula literal.
     *
     * @param lit the solver literal
     * @return the formula literal
     */
    private Variable intLiteralToVariable(final int lit) {
        return f.variable(this.nameForIdx(var(lit)));
    }

    /**
     * Generates a solver vector of a clause.
     *
     * @param clause the clause
     * @return the solver vector
     */
    private LNGIntVector generateClauseVector(final Formula clause) {
        final LNGIntVector clauseVec = new LNGIntVector(clause.numberOfOperands());
        for (final Literal lit : clause.literals()) {
            int index = this.idxForName(lit.name());
            if (index == -1) {
                index = this.newVar(true, true);
                this.addName(lit.name(), index);
            }
            final int litNum = lit.phase() ? index * 2 : (index * 2) ^ 1;
            clauseVec.push(litNum);
        }
        return clauseVec;
    }
}