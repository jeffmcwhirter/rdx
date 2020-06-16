/*
* Copyright (c) 2008-2019 Geode Systems LLC
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
* 
*     http://www.apache.org/licenses/LICENSE-2.0
* 
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/

package com.radiometrics.plugin;


import org.apache.commons.dbcp2.BasicDataSource;

import org.ramadda.repository.*;
import org.ramadda.repository.auth.*;
import org.ramadda.repository.metadata.*;
import org.ramadda.repository.search.*;
import org.ramadda.repository.type.*;
import org.ramadda.util.HtmlUtils;
import org.ramadda.util.Json;
import org.ramadda.util.Utils;
import org.ramadda.util.sql.Clause;
import org.ramadda.util.sql.SqlUtil;

import org.w3c.dom.*;

import ucar.unidata.util.Misc;
import ucar.unidata.util.StringUtil;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import java.util.ArrayList;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;



/**
 * Class description
 *
 *
 * @version        $version$, Sat, May 30, '20
 * @author         Enter your name here...
 */
public class InstrumentLog {

    /** _more_ */
    public static final String TABLE = "rdx_instrument_status_log";

    /** _more_ */
    public static final String COL_ENTRY_ID = "entry_id";

    /** _more_ */
    public static final String COL_DATE = "date";

    /** _more_ */
    public static final String COL_INSTRUMENT_ID = "instrument_id";

    /** _more_ */
    public static final String COL_ELAPSED_NETWORK_MINUTES =
        "elapsed_network_minutes";

    /** _more_ */
    public static final String COL_ELAPSED_DATA_MINUTES =
        "elapsed_data_minutes";

    /** _more_ */
    public static final String COL_ELAPSED_LDM_MINUTES =
        "elapsed_ldm_minutes";

    /** _more_ */
    private static String insert;




    /** _more_ */
    String entryId;

    /** _more_ */
    Date date;

    /** _more_ */
    int elapsedNetwork;

    /** _more_ */
    int elapsedData;

    /** _more_ */
    int elapsedLdm;

    /**
     * _more_
     *
     * @param repository _more_
     * @param results _more_
     *
     * @throws Exception _more_
     */
    public InstrumentLog(Repository repository, ResultSet results)
            throws Exception {
        entryId = results.getString(COL_ENTRY_ID);
        date = repository.getDatabaseManager().getTimestamp(results,
                COL_DATE, true);
        elapsedNetwork = results.getInt(COL_ELAPSED_NETWORK_MINUTES);
        elapsedData    = results.getInt(COL_ELAPSED_DATA_MINUTES);
        elapsedLdm     = results.getInt(COL_ELAPSED_LDM_MINUTES);
    }



    /**
     * _more_
     *
     * @return _more_
     */
    public Date getDate() {
        return date;
    }

    /**
     * _more_
     *
     * @return _more_
     */
    public int getElapsedNetwork() {
        return elapsedNetwork;
    }

    /**
     * _more_
     *
     * @return _more_
     */
    public int getElapsedData() {
        return elapsedData;
    }

    /**
     * _more_
     *
     * @return _more_
     */
    public int getElapsedLdm() {
        return elapsedLdm;
    }


    /**
     * _more_
     *
     * @param repository _more_
     * @param entry _more_
     *
     * @throws Exception _more_
     */
    public static void store(Repository repository, Entry entry)
            throws Exception {
        if (insert == null) {
            insert = SqlUtil.makeInsert(TABLE, new String[] {
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
     * _more_
     *
     * @param repository _more_
     * @param entry _more_
     *
     * @return _more_
     *
     * @throws Exception _more_
     */
    public static List<InstrumentLog> readInstrumentsLog(
            Repository repository, Entry entry)
            throws Exception {
        Connection connection =
            repository.getDatabaseManager().getConnection();
        List<InstrumentLog> instruments = new ArrayList<InstrumentLog>();
        Statement stmt =
            SqlUtil.select(connection, "*", Misc.newList(TABLE),
                           Clause.eq(COL_ENTRY_ID, entry.getId()),
                           SqlUtil.orderBy(COL_DATE, true), 5000);
        try {
            SqlUtil.Iterator iter = new SqlUtil.Iterator(stmt);
            ResultSet        results;
            while ((results = iter.getNext()) != null) {
                instruments.add(new InstrumentLog(repository, results));
            }
        } finally {
            stmt.close();
            connection.close();
        }

        return instruments;
    }




}
