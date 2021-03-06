/*
 * This file is part of alloy2smt.
 * Copyright (C) 2018-2019  The University of Iowa
 *
 * @author Mudathir Mohamed, Paul Meng
 *
 */

package edu.uiowa.smt.parser;

import edu.uiowa.smt.Environment;
import edu.uiowa.smt.smtAst.*;
import edu.uiowa.smt.parser.antlr.SmtBaseVisitor;
import edu.uiowa.smt.parser.antlr.SmtParser;
import edu.uiowa.smt.AbstractTranslator;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class SmtModelVisitor extends SmtBaseVisitor<SmtAst>
{
    private Environment root = new Environment();

    @Override
    public SmtAst visitModel(SmtParser.ModelContext ctx)
    {
        SmtModel model = new SmtModel();

        for (SmtParser.SortDeclarationContext context: ctx.sortDeclaration())
        {
            model.addSort((Sort) this.visitSortDeclaration(context));
        }

        for (SmtParser.FunctionDefinitionContext context: ctx.functionDefinition())
        {
            // ignore named formulas
            if(context.getText().contains("\"filename\":"))
            {
                //ToDo: support functions of named formulas
                continue;
            }
            FunctionDefinition definition = (FunctionDefinition) this.visitFunctionDefinition(context);
            model.addFunction(definition);
            if(definition.getInputVariables().size() == 0)
            {
                root.put(definition.getName(), definition.getVariable());
            }
            //ToDo: handle functions and lambda expressions
        }

        return model;
    }

    @Override
    public SmtAst visitSortDeclaration(SmtParser.SortDeclarationContext ctx)
    {
        String  sortName    = ctx.sortName().getText();
        int     arity       = Integer.parseInt(ctx.arity().getText());
        Sort    sort        = new Sort(sortName, arity);
        return sort;
    }

    @Override
    public SmtAst visitSort(SmtParser.SortContext ctx)
    {
        if(ctx.sortName() != null)
        {
            switch (ctx.sortName().getText())
            {
                case AbstractTranslator.atom: return AbstractTranslator.atomSort;
                case AbstractTranslator.intSortName: return AbstractTranslator.intSort;
                case AbstractTranslator.uninterpretedIntName: return AbstractTranslator.uninterpretedInt;
                case AbstractTranslator.boolSortName: return AbstractTranslator.boolSort;
                default:
                    throw new UnsupportedOperationException(String.format("Unknown sort '%s'", ctx.sortName().getText()));
            }
        }

        if(ctx.tupleSort() != null)
        {
            return this.visitTupleSort(ctx.tupleSort());
        }

        if(ctx.setSort() != null)
        {
            return this.visitSetSort(ctx.setSort());
        }
        throw new UnsupportedOperationException();
    }

    @Override
    public SmtAst visitTupleSort(SmtParser.TupleSortContext ctx)
    {
        List<Sort> sorts    = new ArrayList<>(ctx.sort().size());

        for (SmtParser.SortContext sortContext: ctx.sort())
        {
            Sort sort = (Sort) this.visitSort(sortContext);
            sorts.add(sort);
        }

        return new TupleSort(sorts);
    }

    @Override
    public SmtAst visitSetSort(SmtParser.SetSortContext ctx)
    {
        Sort elementSort = (Sort) this.visitSort(ctx.sort());
        return new SetSort(elementSort);
    }

    @Override
    public SmtAst visitFunctionDefinition(SmtParser.FunctionDefinitionContext ctx)
    {
        String name = ctx.functionName().getText();
        name = processName(name);
        List<VariableDeclaration> variableDeclarations = ctx.variableDeclaration().stream()
                                                   .map(argument -> (VariableDeclaration) this.visitVariableDeclaration(argument))
                                                   .collect(Collectors.toList());
        Map<String, Expression> arguments = variableDeclarations
                .stream()
                .collect(Collectors
                        .toMap(v -> v.getName(), v -> v.getVariable()));
        Environment environment = new Environment(root);
        environment.putAll(arguments);
        Sort returnSort = (Sort) visitSort(ctx.sort());

        Expression expression = (Expression) this.visitExpression(ctx.expression(), environment);

        FunctionDefinition definition   = new FunctionDefinition(name, variableDeclarations, returnSort,  expression, true);

        return definition;
    }

    private String processName(String name)
    {
        return name.replaceAll("\\|", "").trim();
    }

    @Override
    public SmtAst visitVariableDeclaration(SmtParser.VariableDeclarationContext ctx)
    {
        String name = processName(ctx.variableName().getText());
        Sort   sort = (Sort) this.visitSort(ctx.sort());
        return new VariableDeclaration(name, sort, true);
    }

    public SmtAst visitExpression(SmtParser.ExpressionContext ctx, Environment environment)
    {
        if(ctx.constant() != null)
        {
            return this.visitConstant(ctx.constant());
        }
        if(ctx.variable() != null)
        {
            return this.visitVariable(ctx.variable(), environment);
        }
        if(ctx.unaryExpression() != null)
        {
            return this.visitUnaryExpression(ctx.unaryExpression(), environment);
        }
        if(ctx.binaryExpression() != null)
        {
            return this.visitBinaryExpression(ctx.binaryExpression(), environment);
        }
        if(ctx.ternaryExpression() != null)
        {
            return this.visitTernaryExpression(ctx.ternaryExpression(), environment);
        }
        if(ctx.multiArityExpression() != null)
        {
            return this.visitMultiArityExpression(ctx.multiArityExpression(), environment);
        }
        if(ctx.quantifiedExpression() != null)
        {
            return this.visitQuantifiedExpression(ctx.quantifiedExpression(), environment);
        }
        if(ctx.functionCallExpression() != null)
        {
            return this.visitFunctionCallExpression(ctx.functionCallExpression(), environment);
        }
        if(ctx.expression() != null)
        {
            return this.visitExpression(ctx.expression(), environment);
        }
        throw new UnsupportedOperationException();
    }

    public SmtAst visitUnaryExpression(SmtParser.UnaryExpressionContext ctx, Environment environment)
    {
        Expression expression       = (Expression) this.visitExpression(ctx.expression(), environment);
        UnaryExpression.Op operator = UnaryExpression.Op.getOp(ctx.UnaryOperator().getText());
        return operator.make(expression);
    }

    public SmtAst visitBinaryExpression(SmtParser.BinaryExpressionContext ctx, Environment environment)
    {
        Expression left   = (Expression) this.visitExpression(ctx.expression(0), environment);
        Expression right  = (Expression) this.visitExpression(ctx.expression(1), environment);

        BinaryExpression.Op operator = BinaryExpression.Op.getOp(ctx.BinaryOperator().getText());
        return operator.make(left, right);
    }

    public SmtAst visitTernaryExpression(SmtParser.TernaryExpressionContext ctx, Environment environment)
    {
        List<Expression> expressions = ctx.expression().stream()
                                          .map(expression -> (Expression) this.visitExpression(expression, environment))
                                          .collect(Collectors.toList());

        return new ITEExpression(expressions.get(0), expressions.get(1), expressions.get(2));
    }

    public SmtAst visitMultiArityExpression(SmtParser.MultiArityExpressionContext ctx, Environment environment)
    {
        List<Expression> expressions = ctx.expression().stream()
                                          .map(expression -> (Expression) this.visitExpression(expression, environment))
                                          .collect(Collectors.toList());

        MultiArityExpression.Op operator = MultiArityExpression.Op.getOp(ctx.MultiArityOperator().getText());
        return operator.make(expressions);
    }

    public SmtAst visitQuantifiedExpression(SmtParser.QuantifiedExpressionContext ctx, Environment environment)
    {
        List<VariableDeclaration> variableDeclarations = ctx.variableDeclaration().stream()
                                                   .map(argument -> (VariableDeclaration) this.visitVariableDeclaration(argument))
                                                   .collect(Collectors.toList());
        Map<String, Expression> variables = variableDeclarations
                .stream()
                .collect(Collectors
                        .toMap(v -> v.getName(), v -> v.getVariable()));
        Environment newEnvironment = new Environment(environment);
        newEnvironment.putAll(variables);
        Expression expression = (Expression) this.visitExpression(ctx.expression(), newEnvironment);

        QuantifiedExpression.Op operator = QuantifiedExpression.Op.getOp(ctx.Quantifier().getText());
        return operator.make(expression, variableDeclarations);
    }

    public SmtAst visitFunctionCallExpression(SmtParser.FunctionCallExpressionContext ctx, Environment environment)
    {
        List<Expression> expressions = ctx.expression().stream()
                                          .map(expression -> (Expression) this.visitExpression(expression, environment))
                                          .collect(Collectors.toList());
        Variable function = (Variable) environment.get(processName(ctx.Identifier().getText()));
        Expression call = new FunctionCallExpression((FunctionDeclaration) function.getDeclaration(), expressions);
        return call;
    }

    @Override
    public SmtAst visitConstant(SmtParser.ConstantContext ctx)
    {
        if(ctx.boolConstant() != null)
        {
            return this.visitBoolConstant(ctx.boolConstant());
        }
        if(ctx.integerConstant() != null)
        {
            return this.visitIntegerConstant(ctx.integerConstant());
        }
        if(ctx.uninterpretedConstant() != null)
        {
            return this.visitUninterpretedConstant(ctx.uninterpretedConstant());
        }
        if(ctx.emptySet() != null)
        {
            return this.visitEmptySet(ctx.emptySet());
        }
        throw new UnsupportedOperationException();
    }

    @Override
    public SmtAst visitBoolConstant(SmtParser.BoolConstantContext ctx)
    {
        if(ctx.True() != null)
        {
            return BoolConstant.True;
        }
        else
        {
            return BoolConstant.False;
        }
    }

    @Override
    public SmtAst visitIntegerConstant(SmtParser.IntegerConstantContext ctx)
    {
        int constant = Integer.parseInt(ctx.getText());
        return IntConstant.getInstance(constant);
    }

    @Override
    public SmtAst visitUninterpretedConstant(SmtParser.UninterpretedConstantContext ctx)
    {
        if(ctx.AtomPrefix() != null)
        {
            return new UninterpretedConstant(ctx.getText(), AbstractTranslator.atomSort);
        }
        if(ctx.UninterpretedIntPrefix() != null)
        {
            return new UninterpretedConstant(ctx.getText(), AbstractTranslator.uninterpretedInt);
        }
        throw new UnsupportedOperationException(String.format("Unknown constant value '%s'", ctx.getText()));
    }

    @Override
    public SmtAst visitEmptySet(SmtParser.EmptySetContext ctx)
    {
        Sort elementSort = (Sort) this.visitSort(ctx.sort());
        Sort setSort = new SetSort(elementSort);
        return UnaryExpression.Op.EMPTYSET.make(setSort);
    }

    public SmtAst visitVariable(SmtParser.VariableContext ctx, Environment environment)
    {
        String variableName = processName(ctx.getText());
        if(!environment.containsKey(variableName))
        {
            throw new RuntimeException(String.format("The variable '%s' is undefined", variableName));
        }
        Expression variable = environment.get(variableName);
        return variable;
    }

    @Override
    public SmtAst visitGetValue(SmtParser.GetValueContext ctx)
    {
        List<ExpressionValue> values = new ArrayList<>();

        for(int i = 0; i < ctx.expression().size() ; i = i + 2)
        {
            Expression expression = (Expression) visitExpression(ctx.expression(i), root);
            Expression value = (Expression) visitExpression(ctx.expression(i + 1), root);
            ExpressionValue expressionValue = new ExpressionValue(expression, value);
            values.add(expressionValue);
        }

        return new SmtValues(values);
    }

    @Override
    public SmtAst visitGetUnsatCore(SmtParser.GetUnsatCoreContext ctx)
    {
        List<String> core = ctx.Identifier().stream()
                               .map(i -> processName(i.getText()))
                               .collect(Collectors.toList());

        return new SmtUnsatCore(core);
    }

    @Override
    public SmtAst visitExpression(SmtParser.ExpressionContext ctx)
    {
        throw new UnsupportedOperationException("Use the overloaded method visitExpression(SmtParser.ExpressionContext ctx, Map<String, Variable> arguments)");
    }
}