/*
* Copyright (c) 2020 Radiometrics Inc.
*
*/

package com.radiometrics.plugin;


import org.apache.commons.dbcp2.BasicDataSource;

import org.ramadda.repository.*;
import org.ramadda.repository.type.*;
import org.ramadda.util.sql.Clause;
import org.ramadda.util.sql.SqlUtil;


import ucar.unidata.util.Misc;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;



/**
 * Holds one record from the instrument time series database
 *
 *
 * @author Jeff McWhirter
 */
public class RdxInstrumentLogImpl extends RdxInstrumentLog {

    /** sql insert string */
    private static String insert;


    /**
     * create the object from the db
     *
     * @param repository the repository
     * @param results db result set
     *
     * @throws Exception On badness
     */
    public RdxInstrumentLogImpl(Repository repository, ResultSet results)
            throws Exception {
        super(results);
    }

    /**
     * store the instrument
     *
     * @param repository the repository
     * @param entry the entry
     *
     * @throws Exception On badness
     */
    public static void store(Repository repository, Entry entry)
            throws Exception {
        if (insert == null) {
            insert = SqlUtil.makeInsert(DB_TABLE_NAME, new String[] {
                COL_ENTRY_ID, COL_DATE, COL_INSTRUMENT_ID,
                COL_ELAPSED_NETWORK_MINUTES, COL_ELAPSED_DATA_MINUTES,
                COL_ELAPSED_LDM_MINUTES,
            });
        }

        Date now = new Date();
        repository.getDatabaseManager().executeInsert(insert, new Object[] {
            entry.getId(), now,
            entry.getValue(RdxInstrumentTypeHandler.IDX_INSTRUMENT_ID),
            RdxApiHandler.getElapsedMinutes(
                now,
                (Date) entry.getValue(
                    RdxInstrumentTypeHandler.IDX_LAST_NETWORK)),
            RdxApiHandler.getElapsedMinutes(
                now,
                (Date) entry.getValue(
                    RdxInstrumentTypeHandler.IDX_LAST_DATA)),
            RdxApiHandler.getElapsedMinutes(
                now,
                (Date) entry.getValue(RdxInstrumentTypeHandler.IDX_LAST_LDM)),
        });

    }


    /**
     * Read the instrument log from the db for the given entry
     *
     * @param repository the repository
     * @param entry the entry
     *
     * @return List of instrumentlog objects
     *
     * @throws Exception On badness
     */
    public static List<RdxInstrumentLog> readInstrumentsLog(
            Repository repository, Entry entry)
            throws Exception {
        Connection connection =
            repository.getDatabaseManager().getConnection();
        List<RdxInstrumentLog> instruments =
            new ArrayList<RdxInstrumentLog>();
        Statement stmt =
            SqlUtil.select(connection, "*", Misc.newList(DB_TABLE_NAME),
                           Clause.eq(COL_ENTRY_ID, entry.getId()),
                           SqlUtil.orderBy(COL_DATE, true), 5000);
        try {
            SqlUtil.Iterator iter = new SqlUtil.Iterator(stmt);
            ResultSet        results;
            while ((results = iter.getNext()) != null) {
                instruments.add(new RdxInstrumentLog(results));
            }
        } finally {
            stmt.close();
            connection.close();
        }

        return instruments;
    }




}
