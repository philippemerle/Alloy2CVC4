package edu.uiowa.smt.smtAst;

import edu.uiowa.smt.printers.SmtAstVisitor;

import java.util.List;

public class UnsatCore extends SmtAst
{
    private final List<String> core;

    public UnsatCore(List<String> core)
    {
        this.core = core;
    }

    @Override
    public void accept(SmtAstVisitor visitor)
    {
        visitor.visit(this);
    }

    public List<String> getCore()
    {
        return core;
    }
}
