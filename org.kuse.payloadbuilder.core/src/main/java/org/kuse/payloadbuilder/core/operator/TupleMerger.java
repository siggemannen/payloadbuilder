package org.kuse.payloadbuilder.core.operator;

/** Definition of a {@link Tuple} merger that merges two tuples. */
public interface TupleMerger
{
    /**
     * Merge outer and inner tuples.
     *
     * @param outer Outer tuple that is joined
     * @param inner Inner tuple that is joined
     * @param populating True if the join is populating. ie inner row is added to outer without creating a new tuple
     * @param nodeId Id of the operator node that this merge call belongs to.
     **/
    Tuple merge(Tuple outer, Tuple inner, boolean populating, int nodeId);
}
