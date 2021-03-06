package org.kuse.payloadbuilder.core.operator;

import static java.util.Arrays.asList;

import org.junit.Test;

/** Test building of nested loops */
public class OperatorBuilderNestedLoopJoinTest extends AOperatorTest
{
    @Test
    public void test_nested_loop_with_pushdown()
    {
        String query = "select s.id1, a.id2 from source s inner join article a on a.active_flg and (a.art_id = s.art_id or s.id1 > 0)";
        QueryResult result = getQueryResult(query);

        Operator expected = new NestedLoopJoin(
                4,
                "",
                result.tableOperators.get(0),
                new CachingOperator(3, new FilterOperator(2, result.tableOperators.get(1), new ExpressionPredicate(e("a.active_flg = true")))),
                new ExpressionPredicate(e("a.art_id = s.art_id or s.id1 > 0")),
                DefaultTupleMerger.DEFAULT,
                false,
                false);

        //        System.out.println(expected.toString(1));
        //        System.err.println( result.operator.toString(1));

        assertEquals(expected, result.operator);

        assertEquals(new ObjectProjection(asList("id1", "id2"),
                asList(
                        new ExpressionProjection(e("s.id1")),
                        new ExpressionProjection(e("a.id2")))),
                result.projection);
    }

    @Test
    public void test_nested_loop_with_populate_and_pushdown()
    {
        String query = "select s.id1, a.id2 from source s inner join (from article a where a.internet_flg ) a with(populate=true) on a.active_flg and (a.art_id = s.art_id or s.id1 > 0)";
        QueryResult result = getQueryResult(query);

        Operator expected = new NestedLoopJoin(
                4,
                "INNER JOIN",
                result.tableOperators.get(0),
                new CachingOperator(3,
                        new SubQueryOperator(
                                new FilterOperator(2, result.tableOperators.get(1), new ExpressionPredicate(e("a.internet_flg = true AND a.active_flg = true"))),
                                "a")),
                new ExpressionPredicate(e("a.art_id = s.art_id or s.id1 > 0")),
                DefaultTupleMerger.DEFAULT,
                true,
                false);

        //        System.out.println(expected.toString(1));
        //        System.err.println(result.operator.toString(1));

        assertEquals(expected, result.operator);

        assertEquals(new ObjectProjection(asList("id1", "id2"),
                asList(
                        new ExpressionProjection(e("s.id1")),
                        new ExpressionProjection(e("a.id2")))),
                result.projection);
    }
}
