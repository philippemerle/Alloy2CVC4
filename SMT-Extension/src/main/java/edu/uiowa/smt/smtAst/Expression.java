/*
 * This file is part of alloy2smt.
 * Copyright (C) 2018-2019  The University of Iowa
 *
 * @author Mudathir Mohamed, Paul Meng
 *
 */

package edu.uiowa.smt.smtAst;

import edu.uiowa.smt.printers.SmtLibPrinter;

import java.util.List;
import java.util.Map;

public abstract class Expression extends SmtAst
{
    @Override
    public String toString()
    {
        SmtLibPrinter printer = new SmtLibPrinter();
        printer.visit(this);
        return printer.getSmtLib();
    }

    public abstract Sort getSort();
    protected void checkTypes(){}

    public abstract Expression evaluate(Map<String, FunctionDefinition> functions);

    @Override
    public abstract boolean equals(Object object);

    public abstract List<Variable> getFreeVariables();

    public abstract Expression substitute(Variable oldVariable, Variable newVariable);

    public abstract Expression replace(Expression oldExpression, Expression newExpression);
}
