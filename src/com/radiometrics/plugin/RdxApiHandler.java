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
 * Handles the monitoring of the instrument status db and provides a html page of the status.
 *
 * The file api.xml specifies the url path to method mapping. The core repository
 * creates a singleton of this class. It starts 2 threads - one to monitor the external RDX
 * instrument status database and one to monitor the internal notifications table.
 *
 * The property (settable in the repository.properties file) rdx.monitor.run controls
 * running the monitor process
 *
 * The property (settable in the repository.properties file) rdx.monitor.interval defines the
 * interval (seconds) between checks of the external database.
 */
public class RdxApiHandler extends RepositoryManager implements RequestHandler {

    /** lists status. defined in api.xml */
    private static final String PATH_STATUS = "/rdx/status";

    /** lists notifications. defined in api.xml */
    private static final String PATH_NOTIFICATIONS = "/rdx/notifications";

    /** property id for running the monitor */
    private static final String PROP_RUN = "rdx.monitor.run";

    /** afre we currently running the monitoring */
    private boolean monitorInstruments;

    private int networkThreshold;
    private int dataThreshold;
    private int ldmThreshold;


    /**
     *     ctor
     *
     *     @param repository the repository
     *
     *     @throws Exception on badness
     */
    public RdxApiHandler(Repository repository) throws Exception {
        super(repository);

	//Set the noticiation thresholds
	int threshold = getRepository().getProperty("rdx.threshold",30);
	networkThreshold = getRepository().getProperty("rdx.threshold.network", threshold);
	dataThreshold = getRepository().getProperty("rdx.threshold.data", threshold);
	ldmThreshold = getRepository().getProperty("rdx.threshold.ldm", threshold);

	//Are we monitoring instruments
        monitorInstruments = getRepository().getProperty(PROP_RUN, true);

        //Start running in 10 seconds to give the full repository time to start up
        int delayToStart = 10;
	//Change the test db
	processChangeInstruments(null);
        Misc.run(new Runnable() {
            public void run() {
                Misc.sleepSeconds(delayToStart);
                runCheckInstruments();
            }
        });
        Misc.run(new Runnable() {
            public void run() {
                Misc.sleepSeconds(delayToStart);
                runCheckNotifications();
            }
        });

        //      getDatabaseManager().generateBeans("com.radiometrics.plugin",
        //                                         "(rdx_notifications|xxx_rdx_instrument_status_log|xxx_rdx_test_instrument_status)");
    }


    /**
     * Utility to log messages and errors
     *
     * @param msg The message
     */
    private void log(String msg) {
        System.err.println("RDX:" + msg);
    }



    /**
     * Check the instrument status
     */
    public void runCheckInstruments() {
        int pause = getRepository().getProperty("rdx.monitor.interval", 60);
        //TODO: how many errors until we stop?
        while (true) {
            try {
                if (monitorInstruments) {
                    log("Checking instruments");
                    checkInstruments();
                }
            } catch (Exception exc) {
                log("Error:" + exc);
                exc.printStackTrace();
            }
            Misc.sleepSeconds(pause);
        }
    }


    /**
     * Runs the notification checker
     */
    public void runCheckNotifications() {
        //Check every 15 minutes
        while (true) {
            Misc.sleepSeconds(60 * 15);
            try {
                if (monitorInstruments) {
                    checkNotifications();
                }
            } catch (Exception exc) {
                log("Error:" + exc);
                exc.printStackTrace();
            }
        }
    }



    /**
     * Get the db connection to the instrument status database
     *
     * @return db connection
     *
     * @throws Exception On badness
     */
    private Connection getRdxConnection() throws Exception {
        return getDatabaseManager().getExternalConnection("rdx", "db");
    }

    /**
     * Read the instruments from the external rdx db
     *
     * @return List of instruments
     *
     * @throws Exception On badness
     */
    private List<InstrumentData> readInstruments() throws Exception {
        Connection connection = getRdxConnection();
        if (connection == null) {
            log("Failed to get database connection");

            return null;
        }
        try {
            List<InstrumentData> instruments =
                new ArrayList<InstrumentData>();
            Statement stmt =
                SqlUtil.select(
                    connection, InstrumentData.WHAT,
                    Misc.newList(
                        InstrumentData.TABLE,
                        InstrumentData.TABLE2), Clause.join(
                            InstrumentData.TABLE + ".instrument_id",
                            InstrumentData.TABLE2 + ".instrument_id"), "",
                                100);
            try {
                SqlUtil.Iterator iter = new SqlUtil.Iterator(stmt);
                ResultSet        results;
                while ((results = iter.getNext()) != null) {
                    instruments.add(new InstrumentData(this, results));
                }
            } finally {
                stmt.close();
            }

            return instruments;
        } finally {
            connection.close();
        }
    }



    /**
     * Get the pending notifications for the given entry or all if entry is null
     *
     *
     * @param entry The entry. If null then get all notifications
     * @return List of notifications
     *
     * @throws Exception On badness
     */
    private List<RdxNotifications> getNotifications(Entry entry)
            throws Exception {
        List<RdxNotifications> notifications =
            new ArrayList<RdxNotifications>();
        Clause clause = (entry == null)
                        ? null
                        : Clause.eq(RdxNotifications.COL_ENTRY_ID,
                                    entry.getId());
        Statement stmt = getDatabaseManager().select("*",
                             RdxNotifications.DB_TABLE_NAME, clause, null);
        SqlUtil.Iterator iter = getDatabaseManager().getIterator(stmt);
        ResultSet        results;
        while ((results = iter.getNext()) != null) {
            notifications.add(new RdxNotifications(results));
        }

        return notifications;
    }



    /**
     * Run through the pending notifications
     *
     * @throws Exception On badness
     */
    private void checkNotifications() throws Exception {
        List<RdxNotifications> notifications = getNotifications(null);
        Date                   now           = new Date();
        Request                tmpRequest    =
            getRepository().getTmpRequest();
        for (RdxNotifications notification : notifications) {
            Entry entry = getEntryManager().getEntry(tmpRequest,
                              notification.getEntryId());
            if (entry == null) {
                log("checkNotifications: Could not find entry from notification:"
                    + notification.getEntryId());

                continue;
            }

            //If monitoring got turned off then delete the notification
            if ( !(boolean) entry.getValue(
                    RdxInstrumentTypeHandler.IDX_MONITORING_ENABLED)) {
                deleteNotification(entry);

                continue;
            }


            //Check the time
            long minutesDiff = (now.getTime()
                                - notification.getDate().getTime()) / 1000
                                    / 60;
            if (notification.getEventType().equals(Notification.TYPE_EMAIL)) {
                if (minutesDiff < Notification.MINUTES_EMAIL) {
                    continue;

                } else {
                    if (minutesDiff < Notification.MINUTES_TEXT) {
                        continue;
                    }
                }

                String url = tmpRequest.getAbsoluteUrl(
                                 tmpRequest.entryUrl(
                                     getRepository().URL_ENTRY_SHOW, entry));
                String instrumentId =
                    (String) entry.getValue(
                        RdxInstrumentTypeHandler.IDX_INSTRUMENT_ID);
                String msg;
                Date network = (Date) entry.getValue(
                                   RdxInstrumentTypeHandler.IDX_LAST_NETWORK);
                Date data = (Date) entry.getValue(
                                RdxInstrumentTypeHandler.IDX_LAST_DATA);
                Date ldm = (Date) entry.getValue(
                               RdxInstrumentTypeHandler.IDX_LAST_LDM);

                //TODO: make status message 
                if (notification.getEventType().equals(
                        Notification.TYPE_EMAIL)) {
                    msg = "Network for station:" + instrumentId
                          + " is down\n" + url;
                } else {
                    msg = "Network for station:" + instrumentId
                          + " is down\n" + url;
                }
                try {
                    sendNotification(tmpRequest, entry, instrumentId,
                                     notification.getEventType(), msg);
                } catch (Exception exc) {
                    System.err.println(
                        "RdxApiHandler: Error sending notification:" + exc);
                    exc.printStackTrace();
                }
            }
        }
    }



    /**
     * handle /test request
     *
     * @param request the request
     *
     * @return the result
     *
     * @throws Exception On badness
     */
    public Result processChangeInstruments(Request request) throws Exception {
	for(int i=1;i<=16;i++) {
	    Date now = new Date();
	    Date network = new Date(now.getTime()-(long)(Math.random()*120*60*1000));
	    Date data = new Date(now.getTime()-(long)(Math.random()*120*60*1000));
	    Date ldm = new Date(now.getTime()-(long)(Math.random()*120*60*1000));
	    getDatabaseManager().update(
					InstrumentData.TABLE, 
					InstrumentData.COLUMN_INSTRUMENT_ID,
					""+i,
					new String[] {
					    InstrumentData.COLUMN_LAST_NETWORK,
					    InstrumentData.COLUMN_LAST_DATA,
					    InstrumentData.COLUMN_LAST_LDM},
					new Object[] {network, data,ldm});
	}
	if(request == null) return null;
        return processStatus(request);
    }


    /**
     * Add header to the /status or /notifications page
     *
     * @param request The request
     * @param sb buffer to append to
     * @param path PATH_STATUS or PATH_NOTIFICATIONS
     *
     * @throws Exception On badness
     */
    private void addHeader(Request request, Appendable sb, String path)
            throws Exception {
        if (request.isAdmin()) {
            if (request.defined(PROP_RUN)) {
                monitorInstruments = request.get(PROP_RUN, true);
                getRepository().writeGlobal(PROP_RUN,
                                            "" + monitorInstruments);
            }
        }
        sb.append(HtmlUtils.sectionOpen(null, false));
        if (path.equals(PATH_STATUS)) {
            HtmlUtils.sectionTitle(sb, "Instrument Status");
            sb.append(
                HtmlUtils.center(
                    HtmlUtils.href(
                        getRepository().getUrlBase() + PATH_NOTIFICATIONS,
                        "Current Notifications")));
        } else {
            HtmlUtils.sectionTitle(sb, "Pending Notifications");
            sb.append(
                HtmlUtils.center(
                    HtmlUtils.href(
                        getRepository().getUrlBase() + PATH_STATUS,
                        "Instrument Status")));
        }


        if (request.isAdmin()) {
            String link =
                HtmlUtils.href(getRepository().getUrlBase() + path + "?"
                               + PROP_RUN + "="
                               + ( !monitorInstruments), monitorInstruments
                    ? "Turn off monitoring"
                    : "Turn on monitoring");
            if ( !monitorInstruments) {
                sb.append("Not currently monitoring instruments");
                sb.append(HtmlUtils.space(2));
            }
            sb.append(link);
            sb.append("<br>");
        } else {
            if ( !monitorInstruments) {
                sb.append("Not currently monitoring instruments");
            } else {
                sb.append("Currently monitoring instruments");
            }
        }

    }



    /**
     * Process the /notifications request
     *
     * @param request the request
     *
     * @return Result
     *
     * @throws Exception On badness
     */
    public Result processNotifications(Request request) throws Exception {
        request.put("template", "radiometrics");
        StringBuilder sb = new StringBuilder();

        addHeader(request, sb, PATH_NOTIFICATIONS);

        if (request.defined("delete")) {
            Entry entry = getEntryManager().getEntry(null,
                              request.getString("delete"));
            if (entry != null) {
                deleteNotification(entry);
                sb.append(
                    getRepository().getPageHandler().showDialogNote(
                        "Notification for instrument:" + entry.getName()
                        + " deleted"));
            }
        }
        List<RdxNotifications> notifications = getNotifications(null);
        if (notifications.size() == 0) {
            sb.append(
                getRepository().getPageHandler().showDialogNote(
                    "No pending notifications"));
        }


        sb.append(HtmlUtils.formTable());
        if (notifications.size() > 0) {
            sb.append(HtmlUtils.row(HtmlUtils.cols(new Object[] {
                HtmlUtils.b("Instrument"),
                HtmlUtils.b("Type"), HtmlUtils.b("Date") })));
        }
        for (RdxNotifications notification : notifications) {
            Entry entry = getEntryManager().getEntry(null,
                              notification.getEntryId());
            if (entry == null) {
                continue;
            }
            String url = getEntryManager().getEntryUrl(null, entry);
            String deleteLink = HtmlUtils.href(
                                    getRepository().getUrlBase()
                                    + "/rdx/notifications?delete="
                                    + entry.getId(), HtmlUtils.getIconImage(
                                        "fa-trash", "title",
                                        "Delete notification"));
            sb.append(HtmlUtils.row(HtmlUtils.cols(new Object[] {
                deleteLink + " " + HtmlUtils.href(url, entry.getName()),
                notification.getEventType(), notification.getDate() })));
        }
        sb.append(HtmlUtils.formTableClose());
        sb.append(HtmlUtils.sectionClose());

        return new Result("", sb);
    }




    /**
     * Handle the /rdx/status request
     *
     * @param request The request
     *
     * @return The result
     *
     * @throws Exception On badness
     */
    public Result processStatus(Request request) throws Exception {
        request.put("template", "radiometrics");
        StringBuilder sb = new StringBuilder();
        addHeader(request, sb, PATH_STATUS);
        List<InstrumentData> instruments = readInstruments();
        if (instruments == null) {
            sb.append(
                getPageHandler().showDialogWarning(
                    "Failed to read instruments"));

            return new Result("", sb);
        }

        sb.append(HtmlUtils.formTable());
        String id = request.getString("instrument_id");
        if (instruments.size() > 0) {
            sb.append(HtmlUtils.row(HtmlUtils.headerCols(new Object[] {
                "Site ID",
                "Instrument ID", "Last Network Connection", "Last Data",
                "Last LDM" })));
        }
        for (InstrumentData instrument : instruments) {
            Entry entry = getInstrumentEntry(instrument);
            //TODO
            String label = instrument.siteId;
            if (entry != null) {
                label = HtmlUtils.href(getEntryManager().getEntryUrl(null,
                        entry), label);
            }


            sb.append(HtmlUtils.row(HtmlUtils.cols(new Object[] { label,
                    instrument.id, instrument.network, instrument.data,
                    instrument.ldm })));
        }
        sb.append(HtmlUtils.formTableClose());
        if (instruments.size() == 0) {
            sb.append("No instruments found");
        }
        sb.append(HtmlUtils.sectionClose());

        return new Result("", sb);
    }

    /** Tracks last time we stored the instrument status in the local database */
    private Date timeSinceLastInstrumentStore;

    /**
     * Check the instruments
     *
     * @throws Exception on badness
     */
    private void checkInstruments() throws Exception {
        List<InstrumentData> instruments = readInstruments();
        if (instruments == null) {
            return;
        }
        boolean store = false;
        Date    now   = new Date();
        if (timeSinceLastInstrumentStore != null) {
            store = (now.getTime() - timeSinceLastInstrumentStore.getTime())
                    / 1000 / 60 >= 60;
        }
        for (InstrumentData instrument : instruments) {
            checkInstrument(instrument, store);
        }
        timeSinceLastInstrumentStore = now;
    }



    /**
     * Utility to get the RAMADDA entry that corresponds to the given instrument
     *
     * @param instrument The instrument created from the external RDX database
     *
     * @return RAMADDA's entry
     *
     * @throws Exception On Badness
     */
    private Entry getInstrumentEntry(InstrumentData instrument)
            throws Exception {
        //Find the station entries
        Request tmpRequest = getRepository().getTmpRequest();
        tmpRequest.put("type", instrument.typeId);
        String id = instrument.siteId;
        tmpRequest.put("search.rdx_instrument.instrument_id", id);
        List[]      result  = getEntryManager().getEntries(tmpRequest);
        List<Entry> entries = new ArrayList<Entry>();
        entries.addAll((List<Entry>) result[0]);
        entries.addAll((List<Entry>) result[1]);
        if (entries.size() == 0) {
            return null;
        }
        return entries.get(0);
    }


    private int getMinutes(Date date) {
	if(date == null) return 0;
	return (int) (new Date().getTime()-date.getTime())/1000/60;
    }

    /**
     * Checks whether we need to send notifications for the given instrument entry
     *
     * @param entry The instrument
     *
     * @return Whether to send a notification
     *
     * @throws Exception On badness
     */
    private boolean isInstrumentOk(Entry entry) throws Exception {
        int network = getMinutes((Date) entry.getValue(RdxInstrumentTypeHandler.IDX_LAST_NETWORK));
        int data = getMinutes((Date) entry.getValue(RdxInstrumentTypeHandler.IDX_LAST_DATA));
        int  ldm = getMinutes((Date) entry.getValue(RdxInstrumentTypeHandler.IDX_LAST_LDM));
        return network<networkThreshold && data<dataThreshold &&ldm<ldmThreshold;
    }



    /**
     * Compare the instrument (from rdx db) to the internal RAMADDA instrument entry
     *
     * @param instrument The instrument
     * @param store Force storing the instrument state
     *
     * @throws Exception On badness
     */
    private void checkInstrument(InstrumentData instrument, boolean store)
            throws Exception {
        //Find the station entries
        Entry entry = getInstrumentEntry(instrument);
        if (entry == null) {
            log("checkInstrument: Could not find instrument: "
                + instrument.siteId);
            return;
        }


        //Are we doing monitoring of this instrument?
        if ( !(boolean) entry.getValue(
                RdxInstrumentTypeHandler.IDX_MONITORING_ENABLED)) {
            return;
        }


        boolean changed = false;

        Date network =
            (Date) entry.getValue(RdxInstrumentTypeHandler.IDX_LAST_NETWORK);
        Date data =
            (Date) entry.getValue(RdxInstrumentTypeHandler.IDX_LAST_DATA);
        Date ldm =
            (Date) entry.getValue(RdxInstrumentTypeHandler.IDX_LAST_LDM);

        System.err.println("checkInstrument:" + entry + " " + network + " " + instrument.network);
	//                           + data + " " + ldm);

        if ( !Misc.equals(network, instrument.network)) {
            changed = true;
            entry.setValue(RdxInstrumentTypeHandler.IDX_LAST_NETWORK,
                           instrument.network);
        }
        if ( !Misc.equals(data, instrument.data)) {
            changed = true;
            entry.setValue(RdxInstrumentTypeHandler.IDX_LAST_DATA,
                           instrument.data);
        }

        if ( !Misc.equals(ldm, instrument.ldm)) {
            changed = true;
            entry.setValue(RdxInstrumentTypeHandler.IDX_LAST_LDM,
                           instrument.ldm);
        }

        if (store || changed) {
            System.err.println("\tStoring instrument log");
            InstrumentLog.store(repository, entry);
        }

        if ( !changed) {
            //if no change then done
            return;
        }

	boolean ok = isInstrumentOk(entry);
        System.err.println("\tchanged:" + changed +" ok:" + ok);
        //Save the ramadda entry
        getEntryManager().updateEntry(getRepository().getTmpRequest(), entry);

        if (ok) {
            //If all OK then remove any pending notification and return
            deleteNotification(entry);
            return;
        }

        //Add the new notification if we don't have one already
        List<RdxNotifications> notifications = getNotifications(entry);
        if (notifications.size() == 0) {
            String insert =
                SqlUtil.makeInsert(RdxNotifications.DB_TABLE_NAME,
                                   new String[] {
                                       RdxNotifications.COL_ENTRY_ID,
                                       RdxNotifications.COL_EVENT_TYPE,
                                       RdxNotifications.COL_DATE });
            getDatabaseManager().executeInsert(insert,
                    new Object[] { entry.getId(),
                                   Notification.TYPE_EMAIL, new Date() });
        }

    }


    /**
     * Delete any notifications for the given entry
     *
     * @param entry The entry
     *
     * @throws Exception On badness
     */
    private void deleteNotification(Entry entry) throws Exception {
        getDatabaseManager().delete(RdxNotifications.DB_TABLE_NAME,
                                    Clause.eq(RdxNotifications.COL_ENTRY_ID,
                                        entry.getId()));
    }


    /**
     * Send the notification either sms or email
     *
     * @param request the request
     * @param entry the entry
     * @param instrumentId the instrument
     * @param type email or sms
     * @param msg the message
     *
     * @throws Exception On badness
     */
    private void sendNotification(Request request, Entry entry,
                                  String instrumentId, String type,
                                  String msg)
            throws Exception {
        GregorianCalendar cal = new GregorianCalendar();
        cal.setTime(new Date());
        boolean weekend = (cal.get(cal.DAY_OF_WEEK) == cal.SUNDAY)
                          || (cal.get(cal.DAY_OF_WEEK) == cal.SATURDAY);

        List<Metadata> metadataList =
            getMetadataManager().findMetadata(request, entry,
                "rdx_notification", true);
        if ((metadataList == null) || (metadataList.size() == 0)) {
            System.err.println("RdxApiHandler: no notifications found");

            return;
        }

        for (Metadata metadata : metadataList) {
            String when = metadata.getAttr(5);
            if (when.equals("weekend") && !weekend) {
                continue;
            }
            boolean enabled = Misc.equals(metadata.getAttr1(), "true");
            if ( !enabled) {
                continue;
            }
            String name  = metadata.getAttr2();
            String email = Utils.trim(metadata.getAttr3());
            String phone = Utils.trim(metadata.getAttr4());
            log("notification:" + name + " email:" + email + " phone:"
                + phone);
            if (type.equals(Notification.TYPE_EMAIL)) {
                if (email.length() > 0) {
                    if ( !getRepository().getMailManager().isEmailCapable()) {
                        System.err.println(
                            "RdxApiHandler: Error: Email is not enabled");

                        continue;
                    }
                    System.err.println(
                        "RdxApiHandler: Sending site status email:" + email);
                    getRepository().getMailManager().sendEmail(email,
                            "Instrument status:" + instrumentId, msg, true);
                }
            } else {
                phone = phone.replaceAll("-", "").replaceAll(" ", "");
                if (phone.length() > 0) {
                    if ( !getRepository().getMailManager()
                            .sendTextEnabled()) {
                        log("Error: SMS is not enabled");

                        return;
                    }
                    log("Sending site status sms:" + phone);
                    if (getRepository().getMailManager().sendTextMessage(
                            null, phone, msg)) {
                        log("Error: SMS failed sending to:" + phone);
                    }
                }
            }
        }
    }



    /**
     *  Hard code the rdx db type id to the ramadda type
     *
     * @param rdxType _more_
     *
     * @return _more_
     */
    public static String getRamaddaType(int rdxType) {
        if (rdxType == 1) {
            return "rdx_instrument_radiometer";
        }
        if (rdxType == 2) {
            return "rdx_instrument_windprofiler";
        }
        if (rdxType == 3) {
            return "rdx_instrument_sodar";
        }

        throw new IllegalArgumentException("Unknown instrument type:"
                                           + rdxType);
    }




    /**
     * Class description
     *
     *
     * @version        $version$, Sat, May 23, '20
     * @author         Enter your name here...
     */
    public static class InstrumentData {

        /** db table name */
        public static final String TABLE = "rdx_test_instrument_data";

        /** _more_          */
        public static final String TABLE2 = "rdx_test_instrument_metadata";

        /** db column name */
        public static final String COLUMN_SITE_ID = TABLE2 + ".site_id";

        /** db column name */
        public static final String COLUMN_TYPE_ID = TABLE2 + ".type_id";

        /** db column name */
        public static final String COLUMN_INSTRUMENT_ID = TABLE
                                                          + ".instrument_id";

        /** db column name */
        public static final String COLUMN_LAST_NETWORK =
            TABLE + ".last_network_time";

        /** db column name */
        public static final String COLUMN_LAST_DATA = TABLE
                                                      + ".last_data_time";

        /** db column name */
        public static final String COLUMN_LAST_LDM = TABLE + ".last_ldm_time";

        /** _more_          */
        public static final String WHAT = SqlUtil.comma(COLUMN_SITE_ID,
                                              COLUMN_TYPE_ID,
                                              COLUMN_INSTRUMENT_ID,
                                              COLUMN_LAST_NETWORK,
                                              COLUMN_LAST_DATA,
                                              COLUMN_LAST_LDM);

        /** _more_          */
        String siteId;

        /** _more_          */
        String typeId;

        /** attribute */
        int id;

        /** attribute */
        Date network;

        /** attribute */
        Date data;

        /** attribute */
        Date ldm;


        /**
         * ctor
         *
         * @param api handler
         * @param results db resulset
         *
         * @throws Exception On badness
         */
        public InstrumentData(RdxApiHandler api, ResultSet results)
                throws Exception {
            int idx = 1;
            siteId  = results.getString(idx++);
            typeId  = getRamaddaType(results.getInt(idx++));
            id      = results.getInt(idx++);
            network = results.getTimestamp(idx++, Repository.calendar);
            data    = results.getTimestamp(idx++, Repository.calendar);
            ldm     = results.getTimestamp(idx++, Repository.calendar);
        }
    }



    /**
     * Class description
     *
     *
     * @version        $version$, Sun, May 24, '20
     * @author         Enter your name here...
     */
    public static class Notification {

        /** const value */
        public static final int MINUTES_EMAIL = 60;

        /** const value */
        public static final int MINUTES_TEXT = 60 * 11;


        /** const value */
        public static final String TYPE_EMAIL = "email";

        /** const value */
        public static final String TYPE_TEXT = "text";


    }




}
