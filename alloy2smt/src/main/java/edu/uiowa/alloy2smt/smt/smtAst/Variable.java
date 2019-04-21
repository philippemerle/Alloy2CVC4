/*
 * This file is part of alloy2smt.
 * Copyright (C) 2018-2019  The University of Iowa
 *
 * @author Mudathir Mohamed, Paul Meng
 *
 */

package edu.uiowa.alloy2smt.smt.smtAst;

import edu.uiowa.alloy2smt.smt.printers.SmtAstVisitor;

import java.util.Map;

public class Variable extends Expression
{
    private final Declaration declaration;

    public Variable(Declaration declaration)
    {
        this.declaration = declaration;
    }
    
    public String getName()
    {
        return this.declaration.getName();
    }

    public Declaration getDeclaration()
    {
        return this.declaration;
    }

    @Override
    public void accept(SmtAstVisitor visitor) {
        visitor.visit(this);
    }

    @Override
    public Sort getSort()
    {
        return declaration.getSort();
    }
    @Override
    public Expression evaluate(Map<String, FunctionDefinition> functions)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean equals(Object object)
    {
        if(object == this)
        {
            return true;
        }
        if(!(object instanceof Variable))
        {
            return false;
        }
        Variable constantObject = (Variable) object;
        return declaration.equals(constantObject.declaration);
    }
}