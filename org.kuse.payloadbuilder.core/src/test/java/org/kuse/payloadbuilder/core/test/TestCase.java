package org.kuse.payloadbuilder.core.test;

import static java.util.stream.Collectors.joining;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

/** Domain of a test case inside a {@link TestHarness} */
class TestCase
{
    private String name;
    private String query;
    private List<List<List<ColumnValue>>> expectedResultSets;

    String getName()
    {
        return name;
    }

    void setName(String name)
    {
        this.name = name;
    }

    String getQuery()
    {
        return query;
    }

    @SuppressWarnings("unchecked")
    void setQuery(Object query)
    {
        if (query instanceof String)
        {
            this.query = (String) query;
        }
        else if (query instanceof Collection)
        {
            this.query = ((Collection<Object>) query).stream().map(Object::toString).collect(joining(System.lineSeparator()));
        }
    }

    List<List<List<ColumnValue>>> getExpectedResultSets()
    {
        return expectedResultSets;
    }

    void setExpectedResultSets(List<List<List<ColumnValue>>> expectedResultSets)
    {
        this.expectedResultSets = expectedResultSets;
    }

    /** Result of a cell in a result set */
    static class ColumnValue
    {
        private String key;
        private Object value;

        ColumnValue()
        {
        }

        ColumnValue(String key, Object value)
        {
            this.key = key;
            this.value = value;
        }

        String getKey()
        {
            return key;
        }

        void setKey(String key)
        {
            this.key = key;
        }

        Object getValue()
        {
            return value;
        }

        void setValue(Object value)
        {
            this.value = value;
        }

        @Override
        public int hashCode()
        {
            return Objects.hash(key, value);
        }

        @Override
        public boolean equals(Object obj)
        {
            if (obj instanceof ColumnValue)
            {
                ColumnValue that = (ColumnValue) obj;
                return Objects.equals(key, that.key)
                    && Objects.equals(value, that.value);
            }
            return false;
        }

        @Override
        public String toString()
        {
            return key + "=" + value;
        }
    }
}
