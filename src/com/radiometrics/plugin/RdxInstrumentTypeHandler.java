/*
* Copyright (c) 2020 Radiometrics Inc.
*
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

import java.util.ArrayList;
import java.util.Date;
import java.util.List;


/**
 *
 *
 */
public class RdxInstrumentTypeHandler extends PointTypeHandler {

    /** indices correspond to the column definitions in rdxtypes.xml */
    private static int IDX = PointTypeHandler.IDX_LAST + 1;

    /** index */
    public static final int IDX_INSTRUMENT_ID = IDX++;

    /** index */
    public static final int IDX_CONTACT_NAME = IDX++;

    /** index */
    public static final int IDX_CONTACT_EMAIL = IDX++;

    /** index */
    public static final int IDX_CITY = IDX++;

    /** index */
    public static final int IDX_STATE = IDX++;

    /** index */
    public static final int IDX_COMPUTEROS = IDX++;


    /** index */
    public static final int IDX_LAST_MAINTENANCE = IDX++;

    /** index */
    public static final int IDX_MONITORING_ENABLED = IDX++;

    /** index */
    public static final int IDX_LAST_NETWORK = IDX++;

    /** index */
    public static final int IDX_LAST_DATA = IDX++;

    /** index */
    public static final int IDX_LAST_LDM = IDX++;




    /**
     * constructor
     *
     * @param repository The repository
     * @param entryNode xml node from rdxtypes.xml
     *
     * @throws Exception On badness
     */
    public RdxInstrumentTypeHandler(Repository repository, Element entryNode)
            throws Exception {
        super(repository, entryNode);
    }


    /**
     * _more_
     *
     * @param column _more_
     *
     * @return _more_
     */
    @Override
    public boolean getEditable(Column column) {
        if (column.getName().equals("properties")) {
            return false;
        }
        if (column.getName().equals("numberofpoints")) {
            return false;
        }

        return super.getEditable(column);
    }


    /**
     * _more_
     *
     * @param column _more_
     *
     * @return _more_
     */
    @Override
    public boolean getCanDisplay(Column column) {
        return getEditable(column);
    }



    /**
     * get the columns to be used for point json. Skip the numberofpoints and properties
     *
     * @return columns to include in point json
     */
    @Override
    public List<Column> getColumnsForPointJson() {
        List<Column> tmp = super.getColumnsForPointJson();
        if (tmp == null) {
            return null;
        }
        List<Column> columns = new ArrayList<Column>();
        for (Column c : tmp) {
            if (c.getName().equals("numberofpoints")
                    || c.getName().equals("properties")) {
                continue;
            }
            columns.add(c);
        }

        return columns;
    }

    /**
     * Can entry objects be cache
     *
     * @param entry The entry
     *
     * @return false. We always want to go the db
     */
    @Override
    public boolean getCanCache(Entry entry) {
        return false;
    }

    /**
     * Is this entry type a group
     *
     * @return true
     */
    @Override
    public boolean isGroup() {
        return true;
    }

    /**
     * Make the RecordFile for the instrument status time series
     *
     * @param request The request
     * @param entry The entry
     *
     * @return record file
     *
     * @throws Exception On badness
     */
    @Override
    public RecordFile doMakeRecordFile(Request request, Entry entry)
            throws Exception {
        return new RdxRecordFile(request, entry);
    }


    /**
     * Processes the instrument status time series
     *
     *
     * @author Jeff McWhirter
     */
    public class RdxRecordFile extends CsvFile {

        /** The request */
        private Request request;

        /** The entry */
        private Entry entry;

        /**
         * ctor
         *
         * @param request The request
         * @param entry The entry
         *
         * @throws IOException On badness
         */
        public RdxRecordFile(Request request, Entry entry)
                throws IOException {
            this.request = request;
            this.entry   = entry;
        }

        /**
         * Skip over records. Does nothing
         *
         * @param visitInfo record file visit info
         * @param record The record
         * @param howMany How many to skip
         *
         * @return OK
         *
         * @throws Exception On badness
         */
        @Override
        public boolean skip(VisitInfo visitInfo, Record record, int howMany)
                throws Exception {
            //noop as the DB call does this
            return true;
        }

        /**
         * Make the time series input stream from the db
         *
         * @param buffered noop
         *
         * @return input stream
         *
         * @throws Exception On badness
         */
        @Override
        public InputStream doMakeInputStream(boolean buffered)
                throws Exception {
            boolean debug = false;
            makeFields(request);
            SimpleDateFormat sdf =
                RepositoryUtil.makeDateFormat("yyyyMMdd'T'HHmmss");
            StringBuilder s = new StringBuilder("#converted stream\n");
            List<RdxInstrumentLog> logs =
                RdxInstrumentLogImpl.readInstrumentsLog(
                    entry.getTypeHandler().getRepository(), entry);

            long now = new Date().getTime();
            for (int i = logs.size() - 1; i >= 0; i--) {
                RdxInstrumentLog log = logs.get(i);
                s.append(sdf.format(log.getDate()));
                s.append(",");
                s.append(entry.getLatitude());
                s.append(",");
                s.append(entry.getLongitude());
                s.append(",");
                s.append(log.getElapsedNetworkMinutes());
                s.append(",");
                s.append(log.getElapsedDataMinutes());
                s.append(",");
                s.append(log.getElapsedLdmMinutes());
                s.append("\n");
            }
            ByteArrayInputStream bais =
                new ByteArrayInputStream(s.toString().getBytes());

            return bais;
        }

        /**
         * Make the metadata fields
         *
         * @param request request
         *
         * @throws Exception On badness
         */
        private void makeFields(Request request) throws Exception {
            StringBuilder fields = new StringBuilder();
            fields.append(makeField("date", attrType(RecordField.TYPE_DATE),
                                    attrLabel("Date"),
                                    attrFormat("yyyyMMdd'T'HHmmss")));
            fields.append(",");
            fields.append(makeField("latitude",
                                    attrType(RecordField.TYPE_DOUBLE),
                                    attrLabel("Latitude")));
            fields.append(",");
            fields.append(makeField("longitude",
                                    attrType(RecordField.TYPE_DOUBLE),
                                    attrLabel("Longitude")));
            fields.append(",");
            fields.append(makeField("last_network_minutes",
                                    attrType(RecordField.TYPE_DOUBLE),
                                    attrLabel("Last Network Minutes")));
            fields.append(",");
            fields.append(makeField("last_data_minutes",
                                    attrType(RecordField.TYPE_DOUBLE),
                                    attrLabel("Last Data Minutes")));
            fields.append(",");
            fields.append(makeField("last_ldm_minutes",
                                    attrType(RecordField.TYPE_DOUBLE),
                                    attrLabel("Last LDM Minutes")));

            putProperty(PROP_FIELDS, fields.toString());
        }
    }



    /**
     * Hook in case we need to change the entry icon on the fly
     *
     * @param request request
     * @param entry entry
     *
     * @return url to image icon
     *
     * @throws Exception On badness
     */
    @Override
    public String getEntryIconUrl(Request request, Entry entry)
            throws Exception {
        return super.getEntryIconUrl(request, entry);
        //        return getIconUrl(icon);
    }


    /**
     * return the color for the instrument
     *
     * @param entry instrument entry
     * @param attr what
     *
     * @return color
     */
    @Override
    public String getDisplayAttribute(Entry entry, String attr) {
        if (attr.equals("mapFillColor")) {
            Date d = (Date) entry.getValue(IDX_LAST_DATA);
            if (d == null) {
                return null;
            }

            return RdxApiHandler.getColor(d);
        }

        return super.getDisplayAttribute(entry, attr);
    }



    /**
     * decorate the attribute value. this returns a span with the appropriate background color for dates
     *
     * @param request the request
     * @param entry the entry
     * @param column what attribute
     * @param s the incoming value
     *
     * @return decorated value
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
                String color = RdxApiHandler.getColor(d);

                return HtmlUtils.span(
                    "&nbsp;" + s + " (" + minutes + " minutes ago)&nbsp;",
                    HtmlUtils.style("background:" + color + ";"));
            }
        }

        return super.decorateValue(request, entry, column, s);
    }



}
