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


import org.ramadda.data.point.text.*;
import org.ramadda.data.point.text.CsvFile;

import org.ramadda.data.record.*;
import org.ramadda.data.services.PointTypeHandler;


import org.ramadda.repository.*;
import org.ramadda.repository.type.*;

import org.ramadda.util.HtmlUtils;


import org.w3c.dom.*;

import ucar.unidata.util.StringUtil;

import java.io.*;

import java.text.SimpleDateFormat;

import java.util.Date;
import java.util.List;


/**
 *
 *
 */
public class RdxInstrumentTypeHandler extends PointTypeHandler {

    /** _more_ */
    private static int IDX = 0;

    /** _more_ */
    public static final int IDX_INSTRUMENT_ID = IDX++;

    /** _more_ */
    public static final int IDX_IPADDRESS = IDX++;

    /** _more_ */
    public static final int IDX_COMPUTEROS = IDX++;

    /** _more_ */
    public static final int IDX_CONTACT_NAME = IDX++;

    /** _more_ */
    public static final int IDX_CONTACT_EMAIL = IDX++;

    /** _more_ */
    public static final int IDX_CITY = IDX++;

    /** _more_ */
    public static final int IDX_STATE = IDX++;

    /** _more_ */
    public static final int IDX_MONITORING_ENABLED = IDX++;

    /** _more_ */
    public static final int IDX_LAST_MAINTENANCE = IDX++;

    /** _more_ */
    public static final int IDX_LAST_NETWORK = IDX++;

    /** _more_ */
    public static final int IDX_LAST_DATA = IDX++;

    /** _more_ */
    public static final int IDX_LAST_LDM = IDX++;




    /**
     * _more_
     *
     * @param repository _more_
     * @param entryNode _more_
     *
     * @throws Exception _more_
     */
    public RdxInstrumentTypeHandler(Repository repository, Element entryNode)
            throws Exception {
        super(repository, entryNode);
    }


    /**
     * _more_
     *
     * @param entry _more_
     *
     * @return _more_
     */
    @Override
    public boolean canCache(Entry entry) {
        return false;
    }

    /**
     * _more_
     *
     * @return _more_
     */
    @Override
    public boolean isGroup() {
        return true;
    }

    /**
     * _more_
     *
     * @param request _more_
     * @param entry _more_
     *
     * @return _more_
     *
     * @throws Exception _more_
     */
    @Override
    public RecordFile doMakeRecordFile(Request request, Entry entry)
            throws Exception {
        return new RdxRecordFile(request, entry);
    }


    /**
     * Class description
     *
     *
     * @version        $version$, Tue, Jun 9, '20
     * @author         Enter your name here...
     */
    public class RdxRecordFile extends CsvFile {

        /** _more_ */
        private Request request;

        /** _more_ */
        private Entry entry;

        /**
         * _more_
         *
         * @param request _more_
         * @param entry _more_
         *
         * @throws IOException _more_
         */
        public RdxRecordFile(Request request, Entry entry)
                throws IOException {
            this.request = request;
            this.entry   = entry;
        }

        /**
         * _more_
         *
         * @param visitInfo _more_
         * @param record _more_
         * @param howMany _more_
         *
         * @return _more_
         *
         * @throws Exception _more_
         */
        @Override
        public boolean skip(VisitInfo visitInfo, Record record, int howMany)
                throws Exception {
            //noop as the DB call does this
            return true;
        }

        /**
         * _more_
         *
         * @param buffered _more_
         *
         * @return _more_
         *
         * @throws Exception _more_
         */
        @Override
        public InputStream doMakeInputStream(boolean buffered)
                throws Exception {
            boolean debug = false;
            makeFields(request);
            SimpleDateFormat sdf =
                RepositoryUtil.makeDateFormat("yyyyMMdd'T'HHmmss");
            StringBuilder s = new StringBuilder("#converted stream\n");
            List<InstrumentLog> instrumentLogs =
                InstrumentLog.readInstrumentsLog(
                    entry.getTypeHandler().getRepository(), entry);
            ByteArrayInputStream bais =
                new ByteArrayInputStream(s.toString().getBytes());

            return bais;
        }

        /**
         * _more_
         *
         * @param request _more_
         *
         * @throws Exception _more_
         */
        private void makeFields(Request request) throws Exception {
            boolean       debug  = false;
            StringBuilder fields = new StringBuilder();
            fields.append(makeField("latitude",
                                    attrType(RecordField.TYPE_DOUBLE),
                                    attrLabel("Latitude")));
            fields.append(",");
            putProperty(PROP_FIELDS, fields.toString());
        }
    }



    /**
     * _more_
     *
     * @param request _more_
     * @param entry _more_
     *
     * @return _more_
     *
     * @throws Exception _more_
     */
    @Override
    public String getEntryIconUrl(Request request, Entry entry)
            throws Exception {
        return super.getEntryIconUrl(request, entry);
        //        return getIconUrl(icon);
    }


    /**
     * _more_
     *
     * @param entry _more_
     * @param attr _more_
     *
     * @return _more_
     */
    @Override
    public String getDisplayAttribute(Entry entry, String attr) {
        if (attr.equals("mapFillColor")) {
            Date d = (Date) entry.getValue(IDX_LAST_DATA);
            if (d == null) {
                return null;
            }

            return getColor(d);
        }

        return super.getDisplayAttribute(entry, attr);
    }

    /**
     * _more_
     *
     * @param d _more_
     *
     * @return _more_
     */
    private String getColor(Date d) {
        int minutes = (int) ((new Date().getTime() - d.getTime()) / 1000
                             / 60);
        if (minutes <= 15) {
            return "green";
        } else if (minutes <= 60) {
            return "yellow";
        } else if (minutes <= 720) {
            return "red";
        } else {
            return "purple";
        }
    }


    /**
     * _more_
     *
     * @param request _more_
     * @param entry _more_
     * @param column _more_
     * @param s _more_
     *
     * @return _more_
     */
    @Override
    public String decorateValue(Request request, Entry entry, Column column,
                                String s) {
        if (column.getName().equals("last_network")
                || column.getName().equals("last_data")
                || column.getName().equals("last_ldm")) {
            Date d = (Date) entry.getValue(column.getOffset());
            int minutes = (int) ((new Date().getTime() - d.getTime()) / 1000
                                 / 60);
            if (d != null) {
                String color = getColor(d);

                return HtmlUtils.span(
                    "&nbsp;" + s + " (" + minutes + " minutes ago)&nbsp;",
                    HtmlUtils.style("background:" + color + ";"));
            }
        }

        return super.decorateValue(request, entry, column, s);
    }



}
