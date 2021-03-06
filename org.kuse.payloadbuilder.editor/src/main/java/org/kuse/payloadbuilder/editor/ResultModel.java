package org.kuse.payloadbuilder.editor;

import static java.util.Objects.requireNonNull;
import static org.apache.commons.lang3.ArrayUtils.EMPTY_STRING_ARRAY;

import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

import javax.swing.SwingUtilities;
import javax.swing.table.AbstractTableModel;

import org.apache.commons.lang3.StringUtils;
import org.kuse.payloadbuilder.editor.QueryFileModel.Output;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter.Indenter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;

/** Resulting model of a query */
class ResultModel extends AbstractTableModel
{
    static final ObjectWriter WRITER;
    static final ObjectReader READER;
    static
    {
        DefaultPrettyPrinter printer = new DefaultPrettyPrinter();
        printer.indentArraysWith(new Indenter()
        {
            @Override
            public void writeIndentation(JsonGenerator g, int level) throws IOException
            {
                DefaultIndenter.SYSTEM_LINEFEED_INSTANCE.writeIndentation(g, level);
            }

            @Override
            public boolean isInline()
            {
                return false;
            }
        });

        ObjectMapper mapper = new ObjectMapper();
        WRITER = mapper.writer(printer);
        READER = mapper.readerFor(Object.class);
    }

    private final QueryFileModel file;
    private final List<PayloadbuilderService.ObjectWriter.PairList> rows = new ArrayList<>(50);
    private String[] columns = EMPTY_STRING_ARRAY;
    private boolean complete;
    private int lastNotifyRowIndex = -1;

    ResultModel(QueryFileModel file)
    {
        this.file = file;
    }

    /** Add row */
    void addRow(PayloadbuilderService.ObjectWriter.PairList row)
    {
        if (complete)
        {
            throw new IllegalArgumentException("This result model is completed");
        }

        rows.add(row);
    }

    /** Called when result is completed. */
    void done()
    {
        complete = true;
        notifyChanges();
    }

    boolean isComplete()
    {
        return complete;
    }

    /** Set columns */
    void setColumns(String[] columns)
    {
        this.columns = requireNonNull(columns);
        SwingUtilities.invokeLater(() -> fireTableStructureChanged());
    }

    /** A non table model method to get row count */
    int getActualRowCount()
    {
        return rows.size();
    }

    @Override
    public int getRowCount()
    {
        if (file.getOutput() == Output.NONE)
        {
            return 0;
        }

        return rows.size();
    }

    @Override
    public int getColumnCount()
    {
        return columns.length;
    }

    @Override
    public String getColumnName(int column)
    {
        return columns[column];
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex)
    {
        PayloadbuilderService.ObjectWriter.PairList row = rows.get(rowIndex);
        if (row == null)
        {
            return null;
        }

        String column = columns[columnIndex];
        int index = row.getColumns().indexOf(column);
        if (index < 0 || index >= row.size())
        {
            return null;
        }
        return row.get(index).getValue();
    }

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex)
    {
        return false;
    }

    /** Notifies changes since last notify */
    public void notifyChanges()
    {
        int size = rows.size() - 1;
        if (size > lastNotifyRowIndex)
        {
            if (SwingUtilities.isEventDispatchThread())
            {
                super.fireTableRowsInserted(lastNotifyRowIndex, size);
            }
            else
            {
                SwingUtilities.invokeLater(() -> super.fireTableRowsInserted(lastNotifyRowIndex, size));
            }
            lastNotifyRowIndex = size;
        }
    }

    /**
     * Get cell label for provided object. Produces a minimal json for array and map objects
     */
    static String getLabel(Object value, int size)
    {
        StringWriter sw = new StringWriter(size);
        try (JsonGenerator generator = WRITER.getFactory().createGenerator(sw))
        {
            if (value instanceof List)
            {
                generator.writeStartArray();
                @SuppressWarnings("unchecked")
                List<Object> list = (List<Object>) value;

                for (Object obj : list)
                {
                    generator.writeObject(obj);
                    if (sw.getBuffer().length() > size)
                    {
                        return sw.toString().substring(0, size) + "...";
                    }
                }
            }
            else
            {
                generator.writeObject(value);
            }
        }
        catch (IOException e)
        {
        }

        return sw.getBuffer().toString();
    }

    /** Return pretty json for provided value */
    static String getPrettyJson(Object value)
    {
        try
        {
            return WRITER.writeValueAsString(value);
        }
        catch (JsonProcessingException e)
        {
            return StringUtils.EMPTY;
        }
    }
}
