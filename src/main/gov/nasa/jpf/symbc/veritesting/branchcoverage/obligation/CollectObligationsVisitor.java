package gov.nasa.jpf.symbc.veritesting.branchcoverage.obligation;

import com.ibm.wala.ssa.IR;
import gov.nasa.jpf.symbc.branchcoverage.obligation.Obligation;
import gov.nasa.jpf.symbc.branchcoverage.obligation.ObligationSide;
import gov.nasa.jpf.symbc.veritesting.ast.def.*;
import gov.nasa.jpf.symbc.veritesting.ast.transformations.Environment.DynamicRegion;
import gov.nasa.jpf.symbc.veritesting.ast.visitors.AstMapVisitor;
import gov.nasa.jpf.symbc.veritesting.ast.visitors.ExprMapVisitor;
import gov.nasa.jpf.symbc.veritesting.ast.visitors.ExprVisitor;
import za.ac.sun.cs.green.expr.Expression;
import za.ac.sun.cs.green.expr.Operation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Stack;

/**
 * This class collects the obligations and their corresponding expressions.
 */
public class CollectObligationsVisitor extends AstMapVisitor {


    public final HashMap<Obligation, ArrayList<Expression>> oblgToExprsMap = new HashMap<>();
    private ArrayList<Expression> innerPC = new ArrayList<>();
    private static IR ir;

    public CollectObligationsVisitor(ExprVisitor<Expression> exprVisitor, IR ir) {
        super(exprVisitor);
        CollectObligationsVisitor.ir = ir;
    }


    @Override
    public Stmt visit(IfThenElseStmt a) {

        innerPC.add(a.condition);
        Obligation thenOblg = VeriObligationMgr.createOblg(a.original, ObligationSide.THEN, ir);
        innerPC.remove(innerPC.size() - 1);
        Expression negCond = new Operation(Operation.Operator.NOT, a.condition);
        innerPC.add(negCond);
        Obligation elseOblg = VeriObligationMgr.createOblg(a.original, ObligationSide.ELSE, ir);
        innerPC.remove(innerPC.size() - 1);

        putOblgExprInMap(thenOblg, a.condition);
        putOblgExprInMap(elseOblg, negCond);

        return a;
    }

    private void putOblgExprInMap(Obligation oblg, Expression condition) {
        ArrayList<Expression> exprsList = oblgToExprsMap.get(oblg);
        if (exprsList == null)
            oblgToExprsMap.put(oblg, new ArrayList<>(Arrays.asList(condition)));
        else {
            exprsList.add(conjunctWithPc(0, condition));
        }
    }

    private Expression conjunctWithPc(int index, Expression condition) {
        if (index == innerPC.size() - 1)
            return condition;
        return new Operation(Operation.Operator.AND, innerPC.get(index), conjunctWithPc(++index, condition));
    }


    public static DynamicRegion execute(DynamicRegion dynRegion) {
        CollectObligationsVisitor isolateObligationsVisitor = new CollectObligationsVisitor(new ExprMapVisitor(), dynRegion.ir);
        Stmt dynStmt = dynRegion.dynStmt.accept(isolateObligationsVisitor);


        return new DynamicRegion(dynRegion,
                dynStmt,
                dynRegion.spfCaseList, dynRegion.regionSummary, dynRegion.spfPredicateSummary, dynRegion.earlyReturnResult);

    }
}
