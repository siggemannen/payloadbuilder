package org.kuse.payloadbuilder.core.operator;

import static java.util.Arrays.asList;
import static org.kuse.payloadbuilder.core.parser.QualifiedName.of;
import static org.kuse.payloadbuilder.core.utils.MapUtils.entry;
import static org.kuse.payloadbuilder.core.utils.MapUtils.ofEntries;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.lang3.mutable.MutableObject;
import org.junit.Test;
import org.kuse.payloadbuilder.core.catalog.TableFunctionInfo;
import org.kuse.payloadbuilder.core.operator.PredicateAnalyzer.AnalyzePair;
import org.kuse.payloadbuilder.core.operator.TableAlias.TableAliasBuilder;
import org.kuse.payloadbuilder.core.operator.TableAlias.Type;
import org.kuse.payloadbuilder.core.parser.Expression;
import org.kuse.payloadbuilder.core.parser.ParseException;
import org.kuse.payloadbuilder.core.parser.QualifiedName;
import org.kuse.payloadbuilder.core.parser.SortItem;
import org.kuse.payloadbuilder.core.parser.SortItem.NullOrder;
import org.kuse.payloadbuilder.core.parser.SortItem.Order;

/** Test of {@link OperatorBuilder} */
public class OperatorBuilderTest extends AOperatorTest
{
    @Test
    public void test_invalid_alias_hierarchy()
    {
        try
        {
            getQueryResult("select a from tableA a inner join tableB a on a.id = a.id ");
            fail("Alias already exists in parent hierarchy");
        }
        catch (ParseException e)
        {
            assertTrue(e.getMessage(), e.getMessage().contains("Alias a already exists in scope."));
        }

        try
        {
            getQueryResult("select a from tableA a inner join tableB b on b.id = a.id inner join tableC b on b.id = a.id");
            fail("defined multiple times for parent");
        }
        catch (ParseException e)
        {
            assertTrue(e.getMessage(), e.getMessage().contains("Alias b already exists in scope."));
        }
    }

    @Test
    public void test_batch_limit_operator()
    {
        String query = "select a.art_id from source s with (batch_limit=250) inner join article a on a.art_id = s.art_id";
        QueryResult queryResult = getQueryResult(query);

        Operator expected = new BatchRepeatOperator(
                4,
                1,
                new HashJoin(
                        3,
                        "",
                        new BatchLimitOperator(
                                1,
                                queryResult.tableOperators.get(0),
                                e("250")),
                        queryResult.tableOperators.get(1),
                        new ExpressionHashFunction(asList(e("s.art_id"))),
                        new ExpressionHashFunction(asList(e("a.art_id"))),
                        new ExpressionPredicate(e("a.art_id = s.art_id")),
                        DefaultTupleMerger.DEFAULT,
                        false,
                        false));

        //                System.out.println(queryResult.operator.toString(1));
        //                System.err.println(expected.toString(1));

        assertEquals(expected, queryResult.operator);
    }

    @Test
    public void test_sortBy()
    {
        String query = "select a.art_id from article a order by a.art_id";
        QueryResult queryResult = getQueryResult(query);

        Operator expected = new SortByOperator(
                1,
                queryResult.tableOperators.get(0),
                new ExpressionTupleComparator(asList(new SortItem(e("a.art_id"), Order.ASC, NullOrder.UNDEFINED))));

        //                System.out.println(queryResult.operator.toString(1));
        //                System.err.println(expected.toString(1));

        assertEquals(expected, queryResult.operator);
    }

    @Test
    public void test_top()
    {
        String query = "select top 10 a.art_id from article a";
        QueryResult queryResult = getQueryResult(query);

        Operator expected = new TopOperator(
                1,
                queryResult.tableOperators.get(0),
                e("10"));

        //                System.out.println(queryResult.operator.toString(1));
        //                System.err.println(expected.toString(1));

        assertEquals(expected, queryResult.operator);
    }

    @Test
    public void test_groupBy()
    {
        String query = "select a.art_id from article a group by a.art_id";
        QueryResult queryResult = getQueryResult(query);

        Operator expected = new GroupByOperator(
                1,
                queryResult.tableOperators.get(0),
                ofEntries(entry("art_id", QualifiedName.of("a", "art_id"))),
                new ExpressionValuesExtractor(asList(e("a.art_id"))),
                1);

        //                System.out.println(queryResult.operator.toString(1));
        //                System.err.println(expected.toString(1));

        assertEquals(expected, queryResult.operator);
    }

    @Test
    public void test_no_push_down_from_where_to_join_when_accessing_nested_alias()
    {
        String query = "select * from source s inner join (from article a inner join articleBrand ab with(populate=true) on ab.art_id = a.art_id) a with(populate=true) on a.art_id = s.art_id where a.ab.active_flg";
        QueryResult queryResult = getQueryResult(query);

        Operator expected = new FilterOperator(
                5,
                new HashJoin(
                        4,
                        "INNER JOIN",
                        queryResult.tableOperators.get(0),
                        new SubQueryOperator(
                                new HashJoin(
                                        3,
                                        "INNER JOIN",
                                        queryResult.tableOperators.get(1),
                                        queryResult.tableOperators.get(2),
                                        new ExpressionHashFunction(asList(e("a.art_id"))),
                                        new ExpressionHashFunction(asList(e("ab.art_id"))),
                                        new ExpressionPredicate(e("ab.art_id = a.art_id")),
                                        DefaultTupleMerger.DEFAULT,
                                        true,
                                        false),
                                "a"),
                        new ExpressionHashFunction(asList(e("s.art_id"))),
                        new ExpressionHashFunction(asList(e("a.art_id"))),
                        new ExpressionPredicate(e("a.art_id = s.art_id")),
                        DefaultTupleMerger.DEFAULT,
                        true,
                        false),
                new ExpressionPredicate(e("a.ab.active_flg = true")));

        //        System.out.println(queryResult.operator.toString(1));
        //        System.err.println(expected.toString(1));

        assertEquals(expected, queryResult.operator);
    }

    @Test
    public void test_no_push_down_from_left_join_on_predicate_accessing_previous_alias()
    {
        String query = "select raa.sku_id "
            + "from source s "
            + "inner join article a "
            + "  on a.art_id = s.art_id "
            + "left join related_article raa "
            + "  on raa.art_id = a.art_id "
            + "  and a.articleType = 'type'";

        QueryResult queryResult = getQueryResult(query);

        Operator expected = new HashJoin(
                4,
                "LEFT JOIN",
                new HashJoin(
                        2,
                        "INNER JOIN",
                        queryResult.tableOperators.get(0),
                        queryResult.tableOperators.get(1),
                        new ExpressionHashFunction(asList(e("s.art_id"))),
                        new ExpressionHashFunction(asList(e("a.art_id"))),
                        new ExpressionPredicate(e("a.art_id = s.art_id")),
                        DefaultTupleMerger.DEFAULT,
                        false,
                        false),
                queryResult.tableOperators.get(2),
                new ExpressionHashFunction(asList(e("a.art_id"))),
                new ExpressionHashFunction(asList(e("raa.art_id"))),
                new ExpressionPredicate(e("raa.art_id = a.art_id AND a.articleType = 'type'")),
                DefaultTupleMerger.DEFAULT,
                false,
                true);

        //        System.out.println(queryResult.operator.toString(1));
        //        System.err.println(expected.toString(1));

        assertEquals(expected, queryResult.operator);
    }

    @Test
    public void test_push_down_from_where_to_join()
    {
        String query = "select * from source s inner join article a on a.art_id = s.art_id where a.active_flg";
        QueryResult queryResult = getQueryResult(query);

        Operator expected = new HashJoin(
                3,
                "INNER JOIN",
                queryResult.tableOperators.get(0),
                new FilterOperator(2, queryResult.tableOperators.get(1), new ExpressionPredicate(e("a.active_flg = true"))),
                new ExpressionHashFunction(asList(e("s.art_id"))),
                new ExpressionHashFunction(asList(e("a.art_id"))),
                new ExpressionPredicate(e("a.art_id = s.art_id")),
                DefaultTupleMerger.DEFAULT,
                false,
                false);

        //        System.out.println(queryResult.operator.toString(1));
        //        System.err.println(expected.toString(1));

        assertEquals(expected, queryResult.operator);
    }

    @Test
    public void test_push_down_from_where_to_join_when_left()
    {
        String query = "select * from source s left join article a on a.art_id = s.art_id where a.active_flg";
        QueryResult queryResult = getQueryResult(query);

        Operator expected = new HashJoin(
                3,
                "LEFT JOIN",
                queryResult.tableOperators.get(0),
                new FilterOperator(2, queryResult.tableOperators.get(1), new ExpressionPredicate(e("a.active_flg = true"))),
                new ExpressionHashFunction(asList(e("s.art_id"))),
                new ExpressionHashFunction(asList(e("a.art_id"))),
                new ExpressionPredicate(e("a.art_id = s.art_id")),
                DefaultTupleMerger.DEFAULT,
                false,
                true);

        //                System.out.println(queryResult.operator.toString(1));
        //                System.err.println(expected.toString(1));

        assertEquals(expected, queryResult.operator);
    }

    @Test
    public void test_table_function()
    {
        String query = "select r.Value * r1.Value * r2.Value mul, r.Value r, r1.filter(x -> x.Value > 10).map(x -> x.Value) r1, r2.Value r2, array(Value from r1) r1A from range(randomInt(100), randomInt(100) + 100) r inner join range(randomInt(100)) r1 with (populate=true) on r1.Value <= r.Value inner join range(randomInt(100), randomInt(100) + 100) r2 on r2.Value = r.Value";
        QueryResult queryResult = getQueryResult(query);

        TableFunctionInfo range = (TableFunctionInfo) session.getCatalogRegistry().getBuiltin().getFunction("range");

        TableAlias root = TableAliasBuilder.of(Type.TABLE, QualifiedName.of("ROOT"), "ROOT")
                .children(asList(
                        TableAliasBuilder.of(Type.FUNCTION, QualifiedName.of("range"), "r").columns(new String[] {"Value"}),
                        TableAliasBuilder.of(Type.FUNCTION, QualifiedName.of("range"), "r1").columns(new String[] {"Value"}),
                        TableAliasBuilder.of(Type.FUNCTION, QualifiedName.of("range"), "r2").columns(new String[] {"Value"})))
                .build();

        TableAlias r = root.getChildAliases().get(0);
        TableAlias r1 = root.getChildAliases().get(1);
        TableAlias r2 = root.getChildAliases().get(2);

        Operator expected = new HashJoin(
                5,
                "INNER JOIN",
                new NestedLoopJoin(
                        3,
                        "INNER JOIN",
                        new TableFunctionOperator(0, "", r, range, asList(
                                e("randomInt(100)"),
                                e("randomInt(100) + 100"))),
                        new CachingOperator(2, new TableFunctionOperator(1, "", r1, range, asList(
                                e("randomInt(100)")))),
                        new ExpressionPredicate(e("r1.Value <= r.Value")),
                        DefaultTupleMerger.DEFAULT,
                        true,
                        false),
                new TableFunctionOperator(4, "", r2, range, asList(
                        e("randomInt(100)"),
                        e("randomInt(100) + 100"))),
                new ExpressionHashFunction(asList(e("r.Value"))),
                new ExpressionHashFunction(asList(e("r2.Value"))),
                new ExpressionPredicate(e("r2.Value = r.Value")),
                DefaultTupleMerger.DEFAULT,
                false,
                false);

        //                                                        System.err.println(expected.toString(1));
        //                                                        System.out.println(queryResult.operator.toString(1));

        assertEquals(expected, queryResult.operator);

        Projection expectedProjection = new ObjectProjection(asList("mul", "r", "r1", "r2", "r1A"), asList(
                new ExpressionProjection(e("r.Value * r1.Value * r2.Value")),
                new ExpressionProjection(e("r.Value")),
                new ExpressionProjection(e("r1.filter(x -> x.Value > 10).map(x -> x.Value)")),
                new ExpressionProjection(e("r2.Value")),
                new ArrayProjection(asList(
                        new ExpressionProjection(e("Value"))), new ExpressionOperator(6, e("r1")))));

        //                                System.err.println(expected.toString(1));
        //                                System.out.println(queryResult.operator.toString(1));

        assertEquals(expectedProjection, queryResult.projection);
    }

    @Test
    public void test_mixed_populate()
    {
        String query = "select aa.sku_id "
            + "from source s "
            + "inner join article a with(populate=true) "
            + "  on a.art_id = s.art_id "
            + "inner join "
            + "("
            + "  from articleAttribute aa"
            + "  inner join articlePrice ap"
            + "    on ap.sku_id = aa.sku_id"
            + "  inner join attribute1 a1 with(populate=true) "
            + "    on a1.attr1_id = aa.attr1_id "
            + "  where active_flg "
            + "  and ap.price_sales > 0 "
            + ") aa with(populate=true)"
            + "  on aa.art_id = s.art_id"
            + "   "
            + "";

        QueryResult queryResult = getQueryResult(query);

        TableAlias root = TableAliasBuilder.of(Type.TABLE, of("ROOT"), "ROOT")
                .children(asList(
                        TableAliasBuilder.of(Type.TABLE, of("source"), "s").columns(new String[] {"art_id"}),
                        TableAliasBuilder.of(Type.TABLE, of("article"), "a").columns(new String[] {"art_id"}),
                        TableAliasBuilder.of(Type.SUBQUERY, of("SubQuery"), "aa")
                                .children(asList(
                                        TableAliasBuilder.of(Type.TABLE, of("articleAttribute"), "aa").columns(new String[] {"sku_id", "attr1_id", "art_id", "active_flg"}),
                                        TableAliasBuilder.of(Type.TABLE, of("articlePrice"), "ap").columns(new String[] {"price_sales", "sku_id"}),
                                        TableAliasBuilder.of(Type.TABLE, of("attribute1"), "a1").columns(new String[] {"attr1_id"})))))
                .build();

        TableAlias source = root.getChildAliases().get(0);

        //                                                        System.out.println(source.getParent().printHierarchy(0));
        //                                                        System.out.println(queryResult.alias.printHierarchy(0));

        assertTrue("Alias hierarchy should be equal", source.getParent().isEqual(queryResult.alias));

        Operator expected = new HashJoin(
                10,
                "INNER JOIN",
                new HashJoin(
                        2,
                        "",
                        queryResult.tableOperators.get(0),
                        queryResult.tableOperators.get(1),
                        new ExpressionHashFunction(asList(e("s.art_id"))),
                        new ExpressionHashFunction(asList(e("a.art_id"))),
                        new ExpressionPredicate(e("a.art_id = s.art_id")),
                        DefaultTupleMerger.DEFAULT,
                        true,
                        false),
                new SubQueryOperator(
                        new HashJoin(
                                9,
                                "INNER JOIN",
                                new HashJoin(
                                        7,
                                        "INNER JOIN",
                                        new FilterOperator(4, queryResult.tableOperators.get(2), new ExpressionPredicate(e("active_flg = true"))),
                                        new FilterOperator(6, queryResult.tableOperators.get(3), new ExpressionPredicate(e("ap.price_sales > 0"))),
                                        new ExpressionHashFunction(asList(e("aa.sku_id"))),
                                        new ExpressionHashFunction(asList(e("ap.sku_id"))),
                                        new ExpressionPredicate(e("ap.sku_id = aa.sku_id")),
                                        DefaultTupleMerger.DEFAULT,
                                        false,
                                        false),
                                queryResult.tableOperators.get(4),
                                new ExpressionHashFunction(asList(e("aa.attr1_id"))),
                                new ExpressionHashFunction(asList(e("a1.attr1_id"))),
                                new ExpressionPredicate(e("a1.attr1_id = aa.attr1_id")),
                                DefaultTupleMerger.DEFAULT,
                                true,
                                false),
                        "aa"),
                new ExpressionHashFunction(asList(e("s.art_id"))),
                new ExpressionHashFunction(asList(e("aa.art_id"))),
                new ExpressionPredicate(e("aa.art_id = s.art_id")),
                DefaultTupleMerger.DEFAULT,
                true,
                false);

        //                                                System.err.println(expected.toString(1));
        //                                                System.out.println(queryResult.operator.toString(1));

        assertEquals(expected, queryResult.operator);

        Projection expectedProjection = new ObjectProjection(asList("sku_id"),
                asList(new ExpressionProjection(e("aa.sku_id"))));

        assertEquals(expectedProjection, queryResult.projection);
    }

    @Test
    public void test_columns_collecting()
    {
        String query = "select r.a1.attr1_code"
            + ", art_id"
            + ", idx_id"
            + ", object"
            + "  ("
            + "    pluno, "
            + "    object "
            + "    ("
            + "      aa.a1.rgb_code"
            + "    ) attribute1, "
            + "    object "
            + "    ("
            + "      aa.a1.colorGroup "
            + "      from a1 "
            + "      where aa.a1.group_flg "
            + "    ) attribute1Group "
            + "    from aa "
            + "    order by aa.internet_date_start"
            + "  ) obj "
            + ", array"
            + "  ("
            + "    attr2_code "
            + "    from aa.map(aa -> aa.a2) "
            + "    where aa.ean13 != ''"
            + "  ) arr "
            + ", array"
            + "  ("
            + "    art_id,"
            + "    note_id "
            + "    from aa.concat(aa.ap)"
            + "  ) arr2 "
            + "from article a "
            + "inner join "
            + "("
            + "  from articleAttribute aa "
            + "  inner join articlePrice ap with(populate=true) "
            + "    on ap.sku_id = aa.sku_id "
            + "    and ap.price_sales > 0 "
            + "  inner join attribute1 a1 with(populate=true) "
            + "    on a1.attr1_id = aa.attr1_id "
            + "    and a1.lang_id = 1 "
            + "  inner join attribute2 a2 with(populate=true) "
            + "    on a2.attr2_id = aa.attr2_id "
            + "    and a2.lang_id = 1 "
            + "  inner join attribute3 a3 with(populate=true) "
            + "    on a3.attr3_id = aa.attr3_id "
            + "    and a3.lang_id = 1 "
            + "  where ap.price_org > 0"
            + "  order by a2.attr2_no "
            + ") aa with(populate=true)"
            + "  on aa.art_id = a.art_id "
            + "  and aa.active_flg "
            + "  and aa.internet_flg "
            + "inner join "
            + "("
            + "  from articleProperty "
            + "  group by propertykey_id "
            + ") ap with(populate=true) "
            + "  on ap.art_id = a.art_id "
            + "cross apply "
            + "("
            + "  from range(10) r "
            + "  inner join attribute1 a1 with(populate=true)"
            + "      on a1.someId = r.Value "
            + ") r with(populate=true) "
            + "where not a.add_on_flg and a.articleType = 'regular' "
            + "group by a.note_id "
            + "order by a.stamp_dat_cr";

        QueryResult result = getQueryResult(query);

        TableAlias root = TableAliasBuilder
                .of(TableAlias.Type.TABLE, QualifiedName.of("ROOT"), "ROOT")
                .children(asList(
                        TableAliasBuilder.of(TableAlias.Type.TABLE, of("article"), "a").columns(new String[] {"stamp_dat_cr", "art_id", "add_on_flg", "articleType", "note_id", "idx_id"}),
                        TableAliasBuilder.of(TableAlias.Type.SUBQUERY, of("SubQuery"), "aa")
                                .children(asList(
                                        TableAliasBuilder.of(TableAlias.Type.TABLE, of("articleAttribute"), "aa")
                                                .columns(new String[] {"internet_flg", "internet_date_start", "sku_id", "attr1_id", "art_id", "pluno", "active_flg", "ean13", "attr3_id", "note_id",
                                                        "attr2_id"}),
                                        TableAliasBuilder.of(TableAlias.Type.TABLE, of("articlePrice"), "ap").columns(new String[] {"price_sales", "sku_id", "art_id", "price_org", "note_id"}),
                                        TableAliasBuilder.of(TableAlias.Type.TABLE, of("attribute1"), "a1")
                                                .columns(new String[] {"colorGroup", "attr1_id", "rgb_code", "lang_id", "group_flg"}),
                                        TableAliasBuilder.of(TableAlias.Type.TABLE, of("attribute2"), "a2").columns(new String[] {"attr2_code", "lang_id", "attr2_no", "attr2_id"}),
                                        TableAliasBuilder.of(TableAlias.Type.TABLE, of("attribute3"), "a3").columns(new String[] {"lang_id", "attr3_id"}))),
                        TableAliasBuilder.of(TableAlias.Type.SUBQUERY, of("SubQuery"), "ap")
                                .children(asList(
                                        TableAliasBuilder.of(TableAlias.Type.TABLE, of("articleProperty"), "").columns(new String[] {"propertykey_id", "art_id"}))),
                        TableAliasBuilder.of(TableAlias.Type.SUBQUERY, of("SubQuery"), "r")
                                .children(asList(
                                        TableAliasBuilder.of(TableAlias.Type.FUNCTION, of("range"), "r")
                                                .columns(new String[] {"Value"}),
                                        TableAliasBuilder.of(TableAlias.Type.TABLE, of("attribute1"), "a1").columns(new String[] {"someId", "attr1_code"})))))
                .build();

        //        System.out.println(root.printHierarchy(1));
        //        System.out.println(result.alias.printHierarchy(1));

        assertTrue("Alias hierarchy should be equal", root.isEqual(result.alias));
    }

    @Test
    public void test_single_table()
    {
        String query = "select s.id1, s.id2 from source s";
        QueryResult result = getQueryResult(query);

        TableAlias root = TableAliasBuilder.of(Type.TABLE, QualifiedName.of("ROOT"), "ROOT")
                .children(asList(
                        TableAliasBuilder.of(TableAlias.Type.TABLE, QualifiedName.of("source"), "s").columns(new String[] {"id2", "id1"})))
                .build();

        //        System.out.println(root.printHierarchy(1));
        //        System.out.println(result.alias.printHierarchy(1));

        assertTrue(root.isEqual(result.alias));

        assertEquals(result.tableOperators.get(0), result.operator);
        assertEquals(new ObjectProjection(asList("id1", "id2"),
                asList(
                        new ExpressionProjection(e("s.id1")),
                        new ExpressionProjection(e("s.id2")))),
                result.projection);
    }

    @Test
    public void test_catalog_supported_predicates()
    {
        String query = "select s.id1, s.flag1 from source s where s.flag1 and s.flag2";

        MutableObject<Expression> catalogPredicate = new MutableObject<>();

        QueryResult result = getQueryResult(query, p ->
        {
            // flag1 is supported as filter
            Iterator<AnalyzePair> it = p.iterator();
            while (it.hasNext())
            {
                AnalyzePair pair = it.next();
                if (pair.getColumn("s").equals("flag1"))
                {
                    catalogPredicate.setValue(pair.getPredicate());
                    it.remove();
                }
            }
        }, null);

        assertEquals(e("s.flag1 = true"), catalogPredicate.getValue());

        Operator expected = new FilterOperator(
                1,
                result.tableOperators.get(0),
                new ExpressionPredicate(e("s.flag2 = true")));

        //        System.out.println(expected.toString(1));
        //        System.out.println(result.operator.toString(1));

        assertEquals(expected, result.operator);
        assertEquals(new ObjectProjection(asList("id1", "flag1"),
                asList(
                        new ExpressionProjection(e("s.id1")),
                        new ExpressionProjection(e("s.flag1")))),
                result.projection);
    }

    @Test
    public void test_catalog_supported_order_by()
    {
        String query = "select s.id1, s.flag1 from source s order by s.id1";

        MutableObject<List<SortItem>> catalogOrderBy = new MutableObject<>();

        QueryResult result = getQueryResult(query, null, s ->
        {
            catalogOrderBy.setValue(new ArrayList<>(s));
            s.clear();
        });

        List<SortItem> expectedSortItems = asList(new SortItem(e("s.id1"), Order.ASC, NullOrder.UNDEFINED));
        assertEquals(expectedSortItems, catalogOrderBy.getValue());

        Operator expected = result.tableOperators.get(0);

        //                System.out.println(expected.toString(1));
        //                System.out.println(result.operator.toString(1));

        assertEquals(expected, result.operator);
        assertEquals(new ObjectProjection(asList("id1", "flag1"),
                asList(
                        new ExpressionProjection(e("s.id1")),
                        new ExpressionProjection(e("s.flag1")))),
                result.projection);
    }

    @Test
    public void test_pushdown_mixed_alias_aliasless()
    {
        String query = "select s.id from source s where s.flag and flag2";
        QueryResult result = getQueryResult(query);

        Operator expected = new FilterOperator(
                1,
                result.tableOperators.get(0),
                new ExpressionPredicate(e("s.flag = true and flag2 = true")));

        //                                System.out.println(expected.toString(1));
        //                                System.err.println(result.operator.toString(1));

        assertEquals(expected, result.operator);
    }

    @Test
    public void test_select_item_with_filter()
    {
        String query = "select object(s.id1, a.id2 from s where s.id4 > 0) arr from source s inner join (from article where note_id > 0) a with(populate=true) on a.art_id = s.art_id and a.active_flg where s.id3 > 0";
        QueryResult result = getQueryResult(query);

        Operator expected = new HashJoin(
                4,
                "INNER JOIN",
                new FilterOperator(1, result.tableOperators.get(0), new ExpressionPredicate(e("s.id3 > 0"))),
                new SubQueryOperator(
                        new FilterOperator(3, result.tableOperators.get(1), new ExpressionPredicate(e("note_id > 0 and a.active_flg = true"))),
                        "a"),
                new ExpressionHashFunction(asList(e("s.art_id"))),
                new ExpressionHashFunction(asList(e("a.art_id"))),
                new ExpressionPredicate(e("a.art_id = s.art_id")),
                DefaultTupleMerger.DEFAULT,
                true,
                false);

        //        System.out.println(expected.toString(1));
        //        System.err.println(result.operator.toString(1));

        assertEquals(expected, result.operator);

        assertEquals(
                new ObjectProjection(asList("arr"),
                        asList(new ObjectProjection(asList("id1", "id2"),
                                asList(
                                        new ExpressionProjection(e("s.id1")),
                                        new ExpressionProjection(e("a.id2"))),
                                new FilterOperator(
                                        6,
                                        new ExpressionOperator(5, e("s")),
                                        new ExpressionPredicate(e("s.id4 > 0")))))),
                result.projection);
    }

    @Test
    public void test_correlated()
    {
        String query = "SELECT s.art_id "
            + "FROM source s "
            + "INNER JOIN "
            + "("
            + "  from article a"
            + "  INNER JOIN articleAttribute aa with(populate=true)"
            + "    ON aa.art_id = a.art_id "
            + "    AND s.id "
            + ") a with(populate=true)"
            + "  ON a.art_id = s.art_id";

        QueryResult result = getQueryResult(query);

        Operator expected =
                // Correlated => nested loop
                new NestedLoopJoin(
                        4,
                        "INNER JOIN",
                        result.tableOperators.get(0),
                        new SubQueryOperator(
                                new HashJoin(
                                        3,
                                        "INNER JOIN",
                                        result.tableOperators.get(1),
                                        result.tableOperators.get(2),
                                        new ExpressionHashFunction(asList(e("a.art_id"))),
                                        new ExpressionHashFunction(asList(e("aa.art_id"))),
                                        new ExpressionPredicate(e("aa.art_id = a.art_id AND s.id = true")),
                                        DefaultTupleMerger.DEFAULT,
                                        true,
                                        false),
                                "a"),
                        new ExpressionPredicate(e("a.art_id = s.art_id")),
                        DefaultTupleMerger.DEFAULT,
                        true,
                        false);

        //        System.err.println(expected.toString(1));
        //        System.out.println(result.operator.toString(1));

        assertEquals(expected, result.operator);

        assertEquals(
                new ObjectProjection(asList("art_id"),
                        asList(new ExpressionProjection(e("s.art_id")))),
                result.projection);
    }
}
