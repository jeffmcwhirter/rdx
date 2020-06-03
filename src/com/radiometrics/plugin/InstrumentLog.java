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

    /** _more_          */
    public static final String COLUMN_ENTRY_ID = "entry_id";

    /** _more_ */
    public static final String COLUMN_DATE = "date";

    /** _more_ */
    public static final String COLUMN_INSTRUMENT_ID = "instrument_id";

    /** _more_ */
    public static final String COLUMN_LAST_NETWORK_CONNECTION =
        "last_network_connection";

    /** _more_ */
    public static final String COLUMN_LAST_DATA_TIME = "last_data_time";

    /** _more_ */
    public static final String COLUMN_NETWORK_IS_UP = "network_is_up";

    /** _more_ */
    public static final String COLUMN_DATA_DOWN = "data_down";



    /** _more_ */
    Date date;

    /** _more_          */
    String entryId;

    /** _more_ */
    int dataDown;

    /** _more_ */
    boolean networkIsUp;

    /** _more_ */
    Date lastNetworkConnection;

    /** _more_ */
    Date lastDataTime;

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
        networkIsUp = 1 == results.getInt(COLUMN_NETWORK_IS_UP);
        dataDown    = results.getInt(COLUMN_DATA_DOWN);
        lastNetworkConnection =
            repository.getDatabaseManager().getTimestamp(results,
                COLUMN_LAST_NETWORK_CONNECTION, true);
        lastDataTime = repository.getDatabaseManager().getTimestamp(results,
                COLUMN_LAST_DATA_TIME, true);

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
