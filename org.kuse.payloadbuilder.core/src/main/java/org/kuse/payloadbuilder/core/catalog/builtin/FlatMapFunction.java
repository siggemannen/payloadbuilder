package org.kuse.payloadbuilder.core.catalog.builtin;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;

import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import org.apache.commons.collections.Transformer;
import org.apache.commons.collections.iterators.ObjectGraphIterator;
import org.apache.commons.lang3.tuple.Pair;
import org.kuse.payloadbuilder.core.catalog.Catalog;
import org.kuse.payloadbuilder.core.catalog.LambdaFunction;
import org.kuse.payloadbuilder.core.catalog.ScalarFunctionInfo;
import org.kuse.payloadbuilder.core.codegen.CodeGeneratorContext;
import org.kuse.payloadbuilder.core.codegen.ExpressionCode;
import org.kuse.payloadbuilder.core.operator.TableAlias;
import org.kuse.payloadbuilder.core.parser.ExecutionContext;
import org.kuse.payloadbuilder.core.parser.Expression;
import org.kuse.payloadbuilder.core.parser.LambdaExpression;
import org.kuse.payloadbuilder.core.utils.CollectionUtils;

/** Flat map function. Flat maps input */
class FlatMapFunction extends ScalarFunctionInfo implements LambdaFunction
{
    FlatMapFunction(Catalog catalog)
    {
        super(catalog, "flatmap");
    }

    @Override
    public Set<TableAlias> resolveAlias(Set<TableAlias> parentAliases, List<Expression> arguments, Function<Expression, Set<TableAlias>> aliasResolver)
    {
        // Result of flat map is the result of the lambda
        return aliasResolver.apply(arguments.get(1));
    }

    @Override
    public List<Pair<Expression, LambdaExpression>> getLambdaBindings(List<Expression> arguments)
    {
        return singletonList(Pair.of(arguments.get(0), (LambdaExpression) arguments.get(1)));
    }

    @Override
    public Class<?> getDataType()
    {
        return Iterator.class;
    }

    @Override
    public List<Class<? extends Expression>> getInputTypes()
    {
        return asList(Expression.class, LambdaExpression.class);
    }

    @Override
    public Object eval(ExecutionContext context, List<Expression> arguments)
    {
        Object argResult = arguments.get(0).eval(context);
        if (argResult == null)
        {
            return null;
        }
        LambdaExpression le = (LambdaExpression) arguments.get(1);
        int lambdaId = le.getLambdaIds()[0];
        return new ObjectGraphIterator(CollectionUtils.getIterator(argResult),
                //CSOFF
                new Transformer()
                //CSON
                {
                    private Iterator<Object> it;

                    @Override
                    public Object transform(Object input)
                    {
                        if (it == null)
                        {
                            context.setLambdaValue(lambdaId, input);
                            Object value = le.getExpression().eval(context);
                            it = CollectionUtils.getIterator(value);
                            Object result = it;
                            if (!it.hasNext())
                            {
                                it = null;
                            }
                            return result;
                        }
                        else if (!it.hasNext())
                        {
                            it = null;
                        }
                        return input;
                    }
                });
    }

    @Override
    public ExpressionCode generateCode(
            CodeGeneratorContext context,
            ExpressionCode parentCode,
            List<Expression> arguments)
    {
        ExpressionCode inputCode = arguments.get(0).generateCode(context, parentCode);
        ExpressionCode code = ExpressionCode.code(context, inputCode);
        code.addImport("org.kuse.payloadbuilder.core.utils.CollectionUtils");
        code.addImport("java.util.Iterator");
        code.addImport("org.apache.commons.collections.iterators.ObjectGraphIterator");
        code.addImport("org.apache.commons.collections.Transformer");

        LambdaExpression le = (LambdaExpression) arguments.get(1);

        context.addLambdaParameters(le.getIdentifiers());
        ExpressionCode lambdaCode = le.getExpression().generateCode(context, parentCode);
        context.removeLambdaParameters(le.getIdentifiers());

        String template = "%s"
            + "boolean %s = true;\n"
            + "Iterator %s = null;\n"
            + "if (!%s)\n"
            + "{\n"
            + "  %s = new ObjectGraphIterator(IteratorUtils.getIterator(%s), new Transformer()\n"
            + "  {\n"
            + "    Iterator<Object> it;\n"
            + "    public Object transform(Object input)\n"
            + "    {\n"
            + "      if (it == null)\n"
            + "      {\n"
            + "        Object %s = input;\n"
            + "        %s"
            + "        it = IteratorUtils.getIterator(%s);\n"
            + "        return it;\n"
            + "      }\n"
            + "      else if (!it.hasNext())\n"
            + "      {\n"
            + "        it=null;\n"
            + "      }\n"
            + "      return input;\n"
            + "    }\n"
            + "  });\n"
            + "  %s = false;\n"
            + "}\n";

        code.setCode(String.format(template,
                inputCode.getCode(),
                code.getIsNull(),
                code.getResVar(),
                inputCode.getIsNull(),
                code.getResVar(), inputCode.getResVar(),
                le.getIdentifiers().get(0),
                lambdaCode.getCode(),
                lambdaCode.getResVar(),
                code.getIsNull()));

        return code;
    }
}
