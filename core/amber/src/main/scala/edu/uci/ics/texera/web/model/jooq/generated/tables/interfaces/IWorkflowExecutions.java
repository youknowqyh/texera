/*
 * This file is generated by jOOQ.
 */
package edu.uci.ics.texera.web.model.jooq.generated.tables.interfaces;


import java.io.Serializable;
import java.sql.Timestamp;

import org.jooq.types.UInteger;


/**
 * This class is generated by jOOQ.
 */
@SuppressWarnings({ "all", "unchecked", "rawtypes" })
public interface IWorkflowExecutions extends Serializable {

    /**
     * Setter for <code>texera_db.workflow_executions.eid</code>.
     */
    public void setEid(UInteger value);

    /**
     * Getter for <code>texera_db.workflow_executions.eid</code>.
     */
    public UInteger getEid();

    /**
     * Setter for <code>texera_db.workflow_executions.wid</code>.
     */
    public void setWid(UInteger value);

    /**
     * Getter for <code>texera_db.workflow_executions.wid</code>.
     */
    public UInteger getWid();

    /**
     * Setter for <code>texera_db.workflow_executions.vid</code>.
     */
    public void setVid(UInteger value);

    /**
     * Getter for <code>texera_db.workflow_executions.vid</code>.
     */
    public UInteger getVid();

    /**
     * Setter for <code>texera_db.workflow_executions.status</code>.
     */
    public void setStatus(Byte value);

    /**
     * Getter for <code>texera_db.workflow_executions.status</code>.
     */
    public Byte getStatus();

    /**
     * Setter for <code>texera_db.workflow_executions.result</code>.
     */
    public void setResult(String value);

    /**
     * Getter for <code>texera_db.workflow_executions.result</code>.
     */
    public String getResult();

    /**
     * Setter for <code>texera_db.workflow_executions.starting_time</code>.
     */
    public void setStartingTime(Timestamp value);

    /**
     * Getter for <code>texera_db.workflow_executions.starting_time</code>.
     */
    public Timestamp getStartingTime();

    /**
     * Setter for <code>texera_db.workflow_executions.completion_time</code>.
     */
    public void setCompletionTime(Timestamp value);

    /**
     * Getter for <code>texera_db.workflow_executions.completion_time</code>.
     */
    public Timestamp getCompletionTime();

    /**
     * Setter for <code>texera_db.workflow_executions.bookmarked</code>.
     */
    public void setBookmarked(Byte value);

    /**
     * Getter for <code>texera_db.workflow_executions.bookmarked</code>.
     */
    public Byte getBookmarked();

    /**
     * Setter for <code>texera_db.workflow_executions.name</code>.
     */
    public void setName(String value);

    /**
     * Getter for <code>texera_db.workflow_executions.name</code>.
     */
    public String getName();

    // -------------------------------------------------------------------------
    // FROM and INTO
    // -------------------------------------------------------------------------

    /**
     * Load data from another generated Record/POJO implementing the common interface IWorkflowExecutions
     */
    public void from(edu.uci.ics.texera.web.model.jooq.generated.tables.interfaces.IWorkflowExecutions from);

    /**
     * Copy data into another generated Record/POJO implementing the common interface IWorkflowExecutions
     */
    public <E extends edu.uci.ics.texera.web.model.jooq.generated.tables.interfaces.IWorkflowExecutions> E into(E into);
}
