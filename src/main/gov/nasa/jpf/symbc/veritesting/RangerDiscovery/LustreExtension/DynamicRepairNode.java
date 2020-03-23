package gov.nasa.jpf.symbc.veritesting.RangerDiscovery.LustreExtension;

import gov.nasa.jpf.symbc.veritesting.RangerDiscovery.Config;
import gov.nasa.jpf.symbc.veritesting.RangerDiscovery.Util.DiscoveryUtil;
import jkind.lustre.*;

import java.util.ArrayList;
import java.util.List;


/**
 * The idea of this class is to create a dynamic repair library based on the passed inputs. Construction is done recursively with side effects collected on some class internal data-structures.
 */
public class DynamicRepairNode {

    private final String id;
    private final List<VarDecl> boolInputs = new ArrayList<>();
    private final List<VarDecl> intInputs = new ArrayList<>();
    private int holesCounter;
    private String holePrefixStr = "inner_hole_";
    private String constholePrefixStr = "const_hole_";

    //collecting side effects here starts here
    private final List<VarDecl> holeInputs = new ArrayList<>();
    private final List<VarDecl> outputs = new ArrayList<>();
    private final List<VarDecl> locals = new ArrayList<>();
    private final List<Equation> equations = new ArrayList<>();

    public DynamicRepairNode(String id) {
        this.id = id;
    }

    public RepairNode create(List<VarDecl> actualParamVarDecls) {
        populateBoolIntInputs(actualParamVarDecls);
        int balancedTreeDepth = Config.repairNodeDepth % 2 != 0 ? Config.repairNodeDepth + 1 : Config.repairNodeDepth;
        List<Character> pathLabel = new ArrayList<>();
        pathLabel.add((char) (balancedTreeDepth + '0'));
        outputs.add(defineTreeLevel(balancedTreeDepth, pathLabel));
        return new RepairNode(id, actualParamVarDecls, holeInputs, outputs, locals, equations, null, null);
    }

    private VarDecl defineTreeLevel(int balancedTreeDepth, List<Character> pathLabel) {
        if (balancedTreeDepth == 0) { //base case
            return createLeaf(pathLabel);
        } else {
            ArrayList<Character> leftPathLabel = new ArrayList<>(pathLabel);
            leftPathLabel.add('l');
            VarDecl leftVarDecl = defineTreeLevel(balancedTreeDepth - 1, leftPathLabel);
            IdExpr leftOperand = DiscoveryUtil.varDeclToIdExpr(leftVarDecl);

            ArrayList<Character> rightPathLabel = new ArrayList<>(pathLabel);
            rightPathLabel.add('r');
            VarDecl rightVarDecl = defineTreeLevel(balancedTreeDepth - 1, rightPathLabel);
            IdExpr rightOperand = DiscoveryUtil.varDeclToIdExpr(rightVarDecl);

            VarDecl myNodeNameVarDecl = constructPathLabelName(pathLabel);

            // variables constituting an inner node must be of type bool. Integers should only appear in the leaf.
            assert (leftVarDecl.type == NamedType.BOOL) && (rightVarDecl.type == NamedType.BOOL);

            Equation myEquation = new Equation(DiscoveryUtil.varDeclToIdExpr(myNodeNameVarDecl), constructInnerBoolNode(leftOperand, rightOperand));

            //populate my stuff
            locals.add(myNodeNameVarDecl);
            equations.add(myEquation);

            return myNodeNameVarDecl;
        }
    }

    /**
     * This has the same logic as the inner nodes, except it might require one extra hole to constraint int vars.
     *
     * @param pathLabel
     * @return
     */
    private VarDecl createLeaf(List<Character> pathLabel) {
        VarDecl selectionHoleVarDecl = createNewHole(true);
        IdExpr selectionHoleExpr = DiscoveryUtil.varDeclToIdExpr(selectionHoleVarDecl);

        List<Expr> leafSelectionExprs = new ArrayList<>();
        leafSelectionExprs.addAll((List<Expr>) ((List<?>) DiscoveryUtil.varDeclToIdExpr(boolInputs)));

        if (intInputs.size() > 0) {
            VarDecl constantHoleVarDecl = createNewHole(false); // creating a constantHoleVar
            for (VarDecl intVar : intInputs)
                leafSelectionExprs.addAll(constructLeafIntSelection(DiscoveryUtil.varDeclToIdExpr(intVar), DiscoveryUtil.varDeclToIdExpr(constantHoleVarDecl)));

        }

        VarDecl myNodeNameVarDecl = constructPathLabelName(pathLabel);

        Equation myEquation = new Equation(DiscoveryUtil.varDeclToIdExpr(myNodeNameVarDecl), createLeafSelectionExpr(selectionHoleExpr, leafSelectionExprs));

        locals.add(myNodeNameVarDecl);
        equations.add(myEquation);

        return myNodeNameVarDecl;
    }

    private Expr createLeafSelectionExpr(IdExpr selectionHoleExpr, List<Expr> leafSelectionExprs) {
        if (leafSelectionExprs.size() == 1)
            return leafSelectionExprs.get(0);

        return new IfThenElseExpr(new BinaryExpr(selectionHoleExpr, BinaryOp.EQUAL, new IntExpr(leafSelectionExprs.size())), leafSelectionExprs.get(0),
                createLeafSelectionExpr(selectionHoleExpr, leafSelectionExprs.subList(1, leafSelectionExprs.size())));
    }

    //makes a selection for every IntExpr in the input
    private List<BinaryExpr> constructLeafIntSelection(IdExpr leftIntExpr, IdExpr holeExpr) {
        List<BinaryExpr> selectionBinaryExpr = new ArrayList<>();
        selectionBinaryExpr.add(new BinaryExpr(leftIntExpr, BinaryOp.EQUAL, holeExpr));
        selectionBinaryExpr.add(new BinaryExpr(leftIntExpr, BinaryOp.LESS, holeExpr));
        selectionBinaryExpr.add(new BinaryExpr(leftIntExpr, BinaryOp.LESSEQUAL, holeExpr));
        selectionBinaryExpr.add(new BinaryExpr(leftIntExpr, BinaryOp.GREATER, holeExpr));
        selectionBinaryExpr.add(new BinaryExpr(leftIntExpr, BinaryOp.GREATEREQUAL, holeExpr));
        return selectionBinaryExpr;
    }


    private Expr constructInnerBoolNode(IdExpr leftOperand, IdExpr rightOperand) {
        VarDecl selectionHoleVarDecl = createNewHole(true);
        IdExpr sectionHoleExpr = DiscoveryUtil.varDeclToIdExpr(selectionHoleVarDecl);

        IfThenElseExpr expr =
                new IfThenElseExpr(new BinaryExpr(sectionHoleExpr, BinaryOp.EQUAL, new IntExpr(1)),
                        new BinaryExpr(leftOperand, BinaryOp.AND, rightOperand),
                        new IfThenElseExpr(new BinaryExpr(sectionHoleExpr, BinaryOp.EQUAL, new IntExpr(2)),
                                new BinaryExpr(leftOperand, BinaryOp.OR, rightOperand),
                                new IfThenElseExpr(new BinaryExpr(sectionHoleExpr, BinaryOp.EQUAL, new IntExpr(3)),
                                        new BinaryExpr(leftOperand, BinaryOp.IMPLIES, rightOperand),
                                        new BinaryExpr(sectionHoleExpr, BinaryOp.XOR, new IntExpr(3)))));
        return expr;
    }


    private VarDecl createNewHole(boolean innerHole) {
        VarDecl newHole;
        if (innerHole)
            newHole = new VarDecl(holePrefixStr + holesCounter, NamedType.INT);
        else
            newHole = new VarDecl(constholePrefixStr + holesCounter, NamedType.INT);
        holeInputs.add(newHole);
        ++holesCounter;
        return newHole;
    }

    private VarDecl constructPathLabelName(List<Character> pathLabel) {
        return new VarDecl(pathLabel.toString(), NamedType.BOOL);
    }

    private void populateBoolIntInputs(List<VarDecl> actualParamVarDecls) {
        for (VarDecl var : actualParamVarDecls) {
            if (var.type == NamedType.BOOL)
                boolInputs.add(var);
            else if (var.type == NamedType.INT)
                intInputs.add(var);
            else {
                System.out.println("unsupported type for dynamic repair library");
                assert false;
            }
        }
    }
}
