package org.kuse.payloadbuilder.core.parser;

import static java.util.Arrays.asList;
import static org.kuse.payloadbuilder.core.utils.MapUtils.entry;
import static org.kuse.payloadbuilder.core.utils.MapUtils.ofEntries;

import org.junit.Test;
import org.kuse.payloadbuilder.core.QuerySession;
import org.kuse.payloadbuilder.core.catalog.CatalogRegistry;
import org.kuse.payloadbuilder.core.operator.Row;
import org.kuse.payloadbuilder.core.operator.TableAlias;
import org.kuse.payloadbuilder.core.operator.TableAlias.TableAliasBuilder;

/** Test {@link DereferenceExpression} */
public class DereferenceExpressionTest extends AParserTest
{
    @Test
    public void test_dereference_map()
    {
        ExecutionContext ctx = new ExecutionContext(new QuerySession(new CatalogRegistry()));

        TableAlias t = TableAliasBuilder.of(TableAlias.Type.TABLE, QualifiedName.of("table"), "t").columns(new String[] {"a"}).build();
        Row row = Row.of(t, 0, new Object[] {asList(
                ofEntries(entry("id", -1), entry("c", -10)),
                ofEntries(entry("id", 0), entry("c", 0)),
                ofEntries(entry("id", 1), entry("c", 10), entry("d", ofEntries(entry("key", "value")))),
                ofEntries(entry("id", 2), entry("c", 20)))
        });
        ctx.setTuple(row);

        Expression e;

        e = e("a.filter(b -> b.id > 0)[10].d");
        assertNull(e.eval(ctx));

        e = e("a.filter(b -> b.id > 0)[0].c");
        assertEquals(10, e.eval(ctx));

        e = e("a.filter(b -> b.id > 0)[0].d.key");
        assertEquals("value", e.eval(ctx));

        try
        {
            e = e("a.filter(b -> b.id > 0)[0].d.key.missing");
            e.eval(ctx);
            fail("Cannot dereference a string");
        }
        catch (IllegalArgumentException ee)
        {
        }
    }

    @Test
    public void test_dereference_tuple()
    {
        ExecutionContext ctx = new ExecutionContext(new QuerySession(new CatalogRegistry()));

        TableAlias t = TableAliasBuilder.of(TableAlias.Type.TABLE, QualifiedName.of("table"), "t")
                .columns(new String[] {"a"})
                .children(asList(
                        TableAliasBuilder.of(TableAlias.Type.TABLE, QualifiedName.of("child"), "c")
                                .columns(new String[] {"elite"})))
                .build();

        TableAlias child = t.getChildAliases().get(0);

        Row row = Row.of(t, 0, new Object[] {Row.of(child, 0, new Object[] {1337})});

        ctx.setTuple(row);

        Expression e = e("a.map(x -> x)[0].elite");
        assertEquals(1337, e.eval(ctx));
    }
}
