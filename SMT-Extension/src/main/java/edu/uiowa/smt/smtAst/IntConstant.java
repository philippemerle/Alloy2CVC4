/*
 * This file is part of alloy2smt.
 * Copyright (C) 2018-2019  The University of Iowa
 *
 * @author Mudathir Mohamed, Paul Meng
 *
 */

package edu.uiowa.smt.smtAst;

import edu.uiowa.smt.printers.SmtAstVisitor;
import edu.uiowa.smt.AbstractTranslator;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class IntConstant extends Constant
{
    private final BigInteger value;

    private IntConstant(int value)
    {
        this.value = new BigInteger(String.valueOf(value));
    }

    public static IntConstant getInstance(int value)
    {
        return new IntConstant(value);
    }

    public static Expression getSingletonTuple(int value)
    {
        Expression tuple = new MultiArityExpression(MultiArityExpression.Op.MKTUPLE,
                new IntConstant(value));
        Expression singleton = UnaryExpression.Op.SINGLETON.make(tuple);
        return singleton;
    }

    public static Expression getSingletonTuple(IntConstant intConstant)
    {
        Expression tuple = new MultiArityExpression(MultiArityExpression.Op.MKTUPLE,
                intConstant);
        Expression singleton = UnaryExpression.Op.SINGLETON.make(tuple);
        return singleton;
    }
    
    public IntConstant(String value)
    {
        this.value = new BigInteger(value);
    }  
    
    public String getValue()
    {
        return this.value.toString();
    }
    
    @Override
    public void accept(SmtAstVisitor visitor) {
        visitor.visit(this);
    }

    @Override
    public Sort getSort()
    {
        return AbstractTranslator.intSort;
    }

    @Override
    public Expression evaluate(Map<String, FunctionDefinition> functions)
    {
        return this;
    }
    @Override
    public boolean equals(Object object)
    {
        if(object == this)
        {
            return true;
        }
        if(!(object instanceof IntConstant))
        {
            return false;
        }
        IntConstant intConstant = (IntConstant) object;
        return value.equals(intConstant.value);
    }

    @Override
    public List<Variable> getFreeVariables()
    {
        return new ArrayList<>();
    }

    @Override
    public Expression substitute(Variable oldVariable, Variable newVariable)
    {
        return this;
    }

    @Override
    public Expression replace(Expression oldExpression, Expression newExpression)
    {
        if(oldExpression.equals(this))
        {
            return newExpression;
        }
        return this;
    }
}
