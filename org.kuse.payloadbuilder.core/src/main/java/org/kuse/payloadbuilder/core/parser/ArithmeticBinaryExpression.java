package org.kuse.payloadbuilder.core.parser;

import static java.util.Objects.requireNonNull;
import static org.kuse.payloadbuilder.core.parser.ExpressionMath.add;
import static org.kuse.payloadbuilder.core.parser.ExpressionMath.divide;
import static org.kuse.payloadbuilder.core.parser.ExpressionMath.modulo;
import static org.kuse.payloadbuilder.core.parser.ExpressionMath.multiply;
import static org.kuse.payloadbuilder.core.parser.ExpressionMath.subtract;
import static org.kuse.payloadbuilder.core.parser.LiteralNullExpression.NULL_LITERAL;

import org.kuse.payloadbuilder.core.codegen.CodeGeneratorContext;
import org.kuse.payloadbuilder.core.codegen.ExpressionCode;

/** Arithmetic binary expression */
public class ArithmeticBinaryExpression extends Expression
{
    private final Type type;
    private final Expression left;
    private final Expression right;

    public ArithmeticBinaryExpression(Type type, Expression left, Expression right)
    {
        this.type = requireNonNull(type, "type");
        this.left = requireNonNull(left, "left");
        this.right = requireNonNull(right, "right");
    }

    public Type getType()
    {
        return type;
    }

    public Expression getLeft()
    {
        return left;
    }

    public Expression getRight()
    {
        return right;
    }

    @Override
    public boolean isConstant()
    {
        return left.isConstant() && right.isConstant();
    }

    @Override
    public Expression fold()
    {
        if (left instanceof LiteralNullExpression || right instanceof LiteralNullExpression)
        {
            return NULL_LITERAL;
        }

        boolean ll = left instanceof LiteralExpression;
        boolean rl = right instanceof LiteralExpression;

        if (ll || rl)
        {
            if (ll && rl)
            {
                return LiteralExpression.create(evalInternal(
                        ((LiteralExpression) left).getObjectValue(),
                        ((LiteralExpression) right).getObjectValue()));
            }

            // TODO: more folding. multiply 0, 1
            //                     divide 1
        }

        return this;
    }

    @Override
    public boolean isNullable()
    {
        return left.isNullable() || right.isNullable();
    }

    @Override
    public Class<?> getDataType()
    {
        return left.getDataType();
    }

    @Override
    public Object eval(ExecutionContext context)
    {
        Object leftResult = left.eval(context);
        Object rightResult = right.eval(context);
        return evalInternal(leftResult, rightResult);
    }

    private Object evalInternal(Object leftResult, Object rightResult)
    {
        if (leftResult == null || rightResult == null)
        {
            return null;
        }

        switch (type)
        {
            case ADD:
                return add(leftResult, rightResult);
            case SUBTRACT:
                return subtract(leftResult, rightResult);
            case MULTIPLY:
                return multiply(leftResult, rightResult);
            case DIVIDE:
                return divide(leftResult, rightResult);
            case MODULUS:
                return modulo(leftResult, rightResult);
            default:
                throw new IllegalArgumentException("Unknown operator " + type);
        }
    }

    @Override
    public ExpressionCode generateCode(CodeGeneratorContext context, ExpressionCode parentCode)
    {
        ExpressionCode leftCode = left.generateCode(context, parentCode);
        ExpressionCode rightCode = right.generateCode(context, parentCode);
        ExpressionCode code = ExpressionCode.code(context);

        String method = null;
        //CSOFF
        switch (type)
        //CSON
        {
            case ADD:
                method = "ExpressionMath.add";
                break;
            case SUBTRACT:
                method = "ExpressionMath.subtract";
                break;
            case MULTIPLY:
                method = "ExpressionMath.multiply";
                break;
            case DIVIDE:
                method = "ExpressionMath.divide";
                break;
            case MODULUS:
                method = "ExpressionMath.modulo";
                break;
        }

        String template = "%s"
            + "Object %s = null;\n"
            + "boolean %s = true;\n"
            + "if (!%s)\n"
            + "{\n"
            + "  %s"
            + "  if (!%s)\n"
            + "  {"
            + "    %s = %s(%s, %s);\n"
            + "    %s = false;\n"
            + "  }\n"
            + "}\n";

        code.setCode(String.format(template,
                leftCode.getCode(),
                code.getResVar(),
                code.getIsNull(),
                leftCode.getIsNull(),
                rightCode.getCode(),
                rightCode.getIsNull(),
                code.getResVar(), method, leftCode.getResVar(), rightCode.getResVar(),
                code.getIsNull()));

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
        return left.toString() + " " + type.value + " " + right.toString();
    }

    @Override
    public int hashCode()
    {
        //CSOFF
        int hashCode = 17;
        hashCode = hashCode * 37 + left.hashCode();
        hashCode = hashCode * 37 + right.hashCode();
        return hashCode;
        //CSON
    }

    @Override
    public boolean equals(Object obj)
    {
        if (obj instanceof ArithmeticBinaryExpression)
        {
            ArithmeticBinaryExpression e = (ArithmeticBinaryExpression) obj;
            return left.equals(e.getLeft())
                &&
                right.equals(e.getRight())
                &&
                type == e.type;
        }
        return false;
    }

    /** Type */
    public enum Type
    {
        ADD("+"),
        SUBTRACT("-"),
        MULTIPLY("*"),
        DIVIDE("/"),
        MODULUS("%");

        private final String value;

        Type(String value)
        {
            this.value = value;
        }

        public String getValue()
        {
            return value;
        }
    }
}
