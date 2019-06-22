/*
 * This file is part of alloy2smt.
 * Copyright (C) 2018-2019  The University of Iowa
 *
 * @author Mudathir Mohamed, Paul Meng
 *
 */

package edu.uiowa.alloy2smt.translators;

import edu.mit.csail.sdg.ast.*;
import edu.uiowa.smt.AbstractTranslator;
import edu.uiowa.smt.TranslatorUtils;
import edu.uiowa.smt.smtAst.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class FieldTranslator
{

    private final Alloy2SmtTranslator translator;

    public FieldTranslator(Alloy2SmtTranslator translator)
    {
        this.translator = translator;
    }

    void translateFields()
    {
        for (Sig sig : translator.reachableSigs)
        {
            List<Sig.Field> fields = sig.getFields().makeCopy();
            for (Sig.Field f : fields)
            {
                translate(f);
            }

            translateDisjointFields(sig, fields);

            translateDisjoint2FieldValues(sig, fields);
        }
    }

    private void translateDisjointFields(Sig sig, List<Sig.Field> fields)
    {
        // translate disjoint fields
        for (Decl decl: sig.getFieldDecls())
        {
            // disjoint fields
            if (decl.disjoint != null && decl.names.size() > 1)
            {
                for (int i = 0; i < decl.names.size() - 1; i++)
                {
                    Expression fieldI = getFieldExpression(fields, decl.names.get(i).label);
                    for (int j = i + 1; j < decl.names.size(); j++)
                    {
                        Expression fieldJ = getFieldExpression(fields, decl.names.get(j).label);
                        Expression intersect = new BinaryExpression(fieldI, BinaryExpression.Op.INTERSECTION, fieldJ);
                        Expression emptySet = UnaryExpression.Op.EMPTYSET.make(fieldI.getSort());
                        Expression equal = new BinaryExpression(intersect, BinaryExpression.Op.EQ, emptySet);
                        Assertion disjoint = new Assertion(String.format("disj %1$s, %2$s", decl.names.get(i), decl.names.get(j)), equal);
                        translator.smtProgram.addAssertion(disjoint);
                    }
                }
            }
        }
    }

    private void translateDisjoint2FieldValues(Sig sig, List<Sig.Field> fields)
    {
        // translate disjoint field values

        // sig S {f: disj e}
        // all a, b: S | a != b implies no a.f & b.f

        Expression signature = translator.signaturesMap.get(sig).getVariable();
        SetSort setSort = (SetSort) signature.getSort();
        VariableDeclaration a = new VariableDeclaration("__a__", setSort.elementSort, null);
        VariableDeclaration b = new VariableDeclaration("__b__", setSort.elementSort, null);
        Expression aMember = new BinaryExpression(a.getVariable(), BinaryExpression.Op.MEMBER, signature);
        Expression bMember = new BinaryExpression(b.getVariable(), BinaryExpression.Op.MEMBER, signature);
        Expression aSingleton = UnaryExpression.Op.SINGLETON.make(a.getVariable());
        Expression bSingleton = UnaryExpression.Op.SINGLETON.make(b.getVariable());

        Expression members = new BinaryExpression(aMember, BinaryExpression.Op.AND, bMember);
        Expression equal = new BinaryExpression(a.getVariable(), BinaryExpression.Op.EQ, b.getVariable());
        Expression notEqual = UnaryExpression.Op.NOT.make(equal);
        Expression antecedent = new BinaryExpression(members, BinaryExpression.Op.AND, notEqual);
        Expression consequent = new BoolConstant(true);

        for (Decl decl: sig.getFieldDecls())
        {
            if (decl.disjoint2 != null)
            {
                for (ExprHasName name: decl.names)
                {
                    Expression field = getFieldExpression(fields, name.label);
                    Expression aJoin = new BinaryExpression(aSingleton, BinaryExpression.Op.JOIN, field);
                    Expression bJoin = new BinaryExpression(bSingleton, BinaryExpression.Op.JOIN, field);
                    Expression intersect = new BinaryExpression(aJoin, BinaryExpression.Op.INTERSECTION, bJoin);
                    Expression emptySet = UnaryExpression.Op.EMPTYSET.make(intersect.getSort());
                    Expression isEmpty = new BinaryExpression(intersect, BinaryExpression.Op.EQ, emptySet);
                    consequent = new BinaryExpression(consequent, BinaryExpression.Op.AND, isEmpty);
                }
            }
        }

        Expression implies = new BinaryExpression(antecedent, BinaryExpression.Op.IMPLIES, consequent);
        Expression forAll = QuantifiedExpression.Op.FORALL.make(implies, a, b);

        Assertion disjoint2 = new Assertion(sig.label + " disjoint2", forAll);
        translator.smtProgram.addAssertion(disjoint2);
    }

    private Expression getFieldExpression(List<Sig.Field> fields, String label)
    {
        Optional<Sig.Field> field =  fields.stream()
            .filter(f -> f.label.equals(label))
            .findFirst();
        if(!field.isPresent())
        {
            throw new RuntimeException("Can not find field " + label);
        }
        Expression expression = translator.fieldsMap.get(field.get()).getVariable();
        return expression;
    }

    void translate(Sig.Field field)
    {

        String      fieldName   = TranslatorUtils.sanitizeName(field.sig.label + "/" + field.label);
        List<Sort>  fieldSorts  = new ArrayList<>();

        for (Sig sig : field.type().fold().get(0))
        {
            if(sig.type().is_int())
            {
                fieldSorts.add(AbstractTranslator.uninterpretedInt);
            }
            else
            {
                fieldSorts.add(AbstractTranslator.atomSort);
            }
        }

        FunctionDeclaration fieldDeclaration = new FunctionDeclaration(fieldName, new SetSort(new TupleSort(fieldSorts)));
        // declare a variable for the field
        translator.smtProgram.addFunction(fieldDeclaration);
        translator.fieldsMap.put(field, fieldDeclaration);
        translateMultiplicities(field);
    }

    private void translateMultiplicities(Sig.Field field)
    {
        Expr expr = field.decl().expr;
        if(expr instanceof ExprUnary)
        {
            ExprUnary exprUnary = (ExprUnary) expr;
            Expr A = field.sig;
            Expr B = exprUnary.sub;

            Expr multiplicity;
            switch (exprUnary.op)
            {
                case SOMEOF:
                {
                    multiplicity = ExprBinary.Op.ANY_ARROW_SOME.make(null, null, A, B);
                }break;
                case ONEOF:
                {
                    multiplicity = ExprBinary.Op.ANY_ARROW_ONE.make(null, null, A, B);
                }break;
                case LONEOF:
                {
                    multiplicity = ExprBinary.Op.ANY_ARROW_LONE.make(null, null, A, B);
                }break;
                case SETOF:
                {
                    multiplicity = ExprBinary.Op.ARROW.make(null, null, A, B);
                }break;
                default:
                    throw new UnsupportedOperationException();
            }
            Expression set = translator.exprTranslator.translateExpr(multiplicity);
            FunctionDeclaration fieldFunction =  translator.fieldsMap.get(field);
            Expression constraint;
            if(exprUnary.op == ExprUnary.Op.SETOF)
            {
                constraint = new BinaryExpression(fieldFunction.getVariable(), BinaryExpression.Op.SUBSET, set);
            }
            else
            {
                constraint = new BinaryExpression(fieldFunction.getVariable(), BinaryExpression.Op.EQ, set);
            }
            translator.smtProgram.addAssertion(new Assertion(field.toString() + " multiplicity", constraint));
        }
        else
        {
            // sig signature {field : expr}
            // all s: signature | s.field in expr

            ExprVar s = ExprVar.make(null, "_s", field.sig.type());
            Expr noopS = ExprUnary.Op.NOOP.make(null, s);
            Expr noopField = ExprUnary.Op.NOOP.make(null, field);
            Expr join = ExprBinary.Op.JOIN.make(null, null, noopS, noopField);
            Expr in = ExprBinary.Op.IN.make(null, null, join, expr);
            Expr noopSig = ExprUnary.Op.NOOP.make(null, field.sig);
            Decl decl = new Decl(null, null, null, Collections.singletonList(s), noopSig);
            Expr exprQt = ExprQt.Op.ALL.make(null, null, Collections.singletonList(decl), in);
            Expression multiplicity =  translator.exprTranslator.translateExpr(exprQt);
            translator.smtProgram.addAssertion(new Assertion(field.toString() + " multiplicity", multiplicity));
            Expr product = ExprBinary.Op.ARROW.make(null, null, noopSig, expr);
            Expr subsetExpr = ExprBinary.Op.IN.make(null, null, noopField, product);
            Expression subsetExpression = translator.exprTranslator.translateExpr(subsetExpr);
            translator.smtProgram.addAssertion(new Assertion(field.toString() + " subset", subsetExpression));
        }
    }
}
