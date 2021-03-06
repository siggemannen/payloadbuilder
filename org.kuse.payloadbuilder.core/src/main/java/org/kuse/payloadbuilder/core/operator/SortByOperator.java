package org.kuse.payloadbuilder.core.operator;

import static java.util.Arrays.asList;
import static java.util.Objects.requireNonNull;
import static org.kuse.payloadbuilder.core.utils.MapUtils.entry;
import static org.kuse.payloadbuilder.core.utils.MapUtils.ofEntries;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.kuse.payloadbuilder.core.parser.ExecutionContext;

/** Operator sorting target operator */
class SortByOperator extends AOperator
{
    private final Operator target;
    private final TupleComparator comparator;

    SortByOperator(int nodeId, Operator target, TupleComparator comparator)
    {
        super(nodeId);
        this.target = requireNonNull(target, "target");
        this.comparator = requireNonNull(comparator, "comparator");
    }

    @Override
    public List<Operator> getChildOperators()
    {
        return asList(target);
    }

    @Override
    public String getName()
    {
        return "Order by";
    }

    @Override
    public Map<String, Object> getDescribeProperties(ExecutionContext context)
    {
        return ofEntries(true,
                entry("Values", comparator.toString()));
    }

    @Override
    public RowIterator open(ExecutionContext context)
    {
        List<Tuple> tuples = new ArrayList<>();
        RowIterator it = target.open(context);
        while (it.hasNext())
        {
            tuples.add(it.next());
        }
        it.close();
        Collections.sort(tuples, (a, b) -> comparator.compare(context, a, b));
        return RowIterator.wrap(tuples.iterator());
    }

    @Override
    public int hashCode()
    {
        return target.hashCode();
    }

    @Override
    public boolean equals(Object obj)
    {
        if (obj instanceof SortByOperator)
        {
            SortByOperator that = (SortByOperator) obj;
            return target.equals(that.target)
                && comparator.equals(that.comparator);
        }

        return false;
    }

    @Override
    public String toString(int indent)
    {
        String indentString = StringUtils.repeat("  ", indent);
        return String.format("ORDER BY (ID: %d, VALUES: %s)", nodeId, comparator) + System.lineSeparator()
            +
            indentString + target.toString(indent + 1);
    }
}
