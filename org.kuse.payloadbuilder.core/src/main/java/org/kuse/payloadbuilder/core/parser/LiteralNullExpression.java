package org.kuse.payloadbuilder.core.parser;

import org.kuse.payloadbuilder.core.codegen.CodeGeneratorContext;
import org.kuse.payloadbuilder.core.codegen.ExpressionCode;

/** Literal null */
public class LiteralNullExpression extends LiteralExpression
{
    public static final LiteralNullExpression NULL_LITERAL = new LiteralNullExpression();

    private LiteralNullExpression()
    {
        super(null);
    }

    @Override
    public ExpressionCode generateCode(CodeGeneratorContext context, ExpressionCode parentCode)
    {
        ExpressionCode code = ExpressionCode.code(context);
        String template = "Object %s = null;\n"
            + "boolean %s = true;\n";
        code.setCode(String.format(template, code.getResVar(), code.getIsNull()));
        return code;
    }

    @Override
    public <TR, TC> TR accept(ExpressionVisitor<TR, TC> visitor, TC context)
    {
        return visitor.visit(this, context);
    }

    @Override
    public String toString()
    {
        return "null";
    }
}
