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
    public static final String COLUMN_ENTRY_ID = "entry_id";

    /** _more_ */
    public static final String COLUMN_DATE = "date";

    /** _more_ */
    public static final String COLUMN_INSTRUMENT_ID = "instrument_id";

    /** _more_ */
    public static final String COLUMN_LAST_NETWORK = "last_network_time";

    /** _more_ */
    public static final String COLUMN_LAST_DATA = "last_data_time";

    /** _more_ */
    public static final String COLUMN_LAST_LDM = "last_ldm_time";



    /** _more_ */
    Date date;

    /** _more_ */
    String entryId;

    /** _more_ */
    Date lastNetwork;

    /** _more_ */
    Date lastData;

    /** _more_ */
    Date lastLdm;

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
        entryId = results.getString(COLUMN_ENTRY_ID);
        date = repository.getDatabaseManager().getTimestamp(results,
                COLUMN_DATE, true);
        lastNetwork = repository.getDatabaseManager().getTimestamp(results,
                COLUMN_LAST_NETWORK, true);
        lastData = repository.getDatabaseManager().getTimestamp(results,
                COLUMN_LAST_DATA, true);
        lastLdm = repository.getDatabaseManager().getTimestamp(results,
                COLUMN_LAST_LDM, true);

    }


    /** _more_ */
    private static String insert;

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
                COLUMN_ENTRY_ID, COLUMN_DATE, COLUMN_INSTRUMENT_ID,
                COLUMN_LAST_NETWORK, COLUMN_LAST_DATA, COLUMN_LAST_LDM,
            });
        }

        repository.getDatabaseManager().executeInsert(insert, new Object[] {
            entry.getId(), new Date(),
            entry.getValue(RdxInstrumentTypeHandler.IDX_INSTRUMENT_ID),
            entry.getValue(RdxInstrumentTypeHandler.IDX_LAST_NETWORK),
            entry.getValue(RdxInstrumentTypeHandler.IDX_LAST_DATA),
            entry.getValue(RdxInstrumentTypeHandler.IDX_LAST_LDM),
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
        Statement stmt = SqlUtil.select(connection, "*", Misc.newList(TABLE),
                                        Clause.eq(COLUMN_ENTRY_ID,
                                            entry.getId()), "", 100);
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
