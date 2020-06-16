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
import org.ramadda.repository.admin.MailManager;
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

import java.text.SimpleDateFormat;

import java.util.ArrayList;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.TimeZone;


/**
 * Handles the monitoring of the instrument status db and provides a html page of the status.
 *
 * The file api.xml specifies the url path to method mapping. The core repository
 * creates a singleton of this class. It starts 2 threads - one to monitor the external RDX
 * instrument status database and one to monitor the internal notifications table.
 *
 * See the top level /rdx/rdx.properties file for setting configuration options
 */
public class RdxApiHandler extends RepositoryManager implements RequestHandler {


    /** _more_          */
    private static final String TITLE = "Radiometrics NMPM";

    /** lists instrument db. defined in api.xml */
    private static final String PATH_INSTRUMENTS = "/rdx/instruments";

    /** lists notifications. defined in api.xml */
    private static final String PATH_NOTIFICATIONS = "/rdx/notifications";

    /** _more_          */
    private static final String PATH_LOG = "/rdx/log";


    /** randomly changes instruments. defined in api.xml */
    private static final String PATH_CHANGEINSTRUMENTS =
        "/rdx/changeinstruments";


    /** property id */
    private static final String PROP_TEST = "rdx.test";


    /** property id */
    private static final String PROP_RUN = "rdx.monitor.run";

    /** property id */
    private static final String PROP_TIMEZONE = "rdx.timezone";

    /** _more_ */
    private static final String PROP_DATEFORMAT = "rdx.dateformat";


    /** property id */
    private static final String PROP_THRESHOLD = "rdx.threshold";

    /** property id */
    private static final String PROP_THRESHOLD_NETWORK =
        "rdx.threshold.network";

    /** property id */
    private static final String PROP_THRESHOLD_DATA = "rdx.threshold.data";

    /** property id */
    private static final String PROP_THRESHOLD_LDM = "rdx.threshold.ldm";

    /** property id */
    private static final String PROP_INSTRUMENT_INTERVAL =
        "rdx.instrument.interval";

    /** _more_          */
    private static final int LOG_SIZE = 500;

    /** _more_          */
    private boolean verbose = false;


    /** _more_          */
    private List<String> log = new ArrayList<String>();

    /** are we currently running the monitoring */
    private boolean monitorInstruments;

    /** threshold in minutes for notifications of network times */
    private int networkThreshold;

    /** threshold in minutes for notifications of data times */
    private int dataThreshold;

    /** threshold in minutes for notifications of ldm times */
    private int ldmThreshold;



    /** _more_ */
    private int notificationIntervalEmail;

    /** _more_ */
    private int notificationCountEmail;

    /** _more_ */
    private int notificationIntervalSms;

    /** _more_ */
    private int notificationCountSms;

    /** _more_ */
    private String messageEmail;

    /** _more_ */
    private String messageSms;


    /** _more_ */
    private TimeZone timeZone;

    /** _more_ */
    private SimpleDateFormat sdf;

    /**
     *     ctor
     *
     *     @param repository the repository
     *
     *     @throws Exception on badness
     */
    public RdxApiHandler(Repository repository) throws Exception {
        super(repository);
        timeZone =
            TimeZone.getTimeZone(getRepository().getProperty(PROP_TIMEZONE,
                "America/Denver"));

        sdf = new SimpleDateFormat(
            getRepository().getProperty(
                PROP_DATEFORMAT, "yyyy-MM-dd HH:mm z"));
        sdf.setTimeZone(timeZone);

        //Set the notification thresholds
        int threshold = getRepository().getProperty(PROP_THRESHOLD, 30);
        networkThreshold =
            getRepository().getProperty(PROP_THRESHOLD_NETWORK, threshold);
        dataThreshold = getRepository().getProperty(PROP_THRESHOLD_DATA,
                threshold);
        ldmThreshold = getRepository().getProperty(PROP_THRESHOLD_LDM,
                threshold);


        notificationIntervalEmail =
            getRepository().getProperty("rdx.notification.interval.email",
                                        1/*60 * 8*/);
        notificationCountEmail =
            getRepository().getProperty("rdx.notification.count.email", 4);

        notificationIntervalSms =
            getRepository().getProperty("rdx.notification.interval.sms",
                                        1/*60 * 24*/);
        notificationCountSms =
            getRepository().getProperty("rdx.notification.count.sms", 4);

        messageEmail =
            "Problem with instrument <a href='${url}'>${id}</a>\n<a href='${stopurl}'>Delete notification</a>\n${extra}";
        messageSms =
            "Problem with instrument: ${id}\nView: ${url}\nDelete notification: ${stopurl}";
        messageEmail =
            getRepository().getProperty("rdx.notification.email.template",
                                        messageEmail).replaceAll("\\n", "\n");
        messageSms =
            getRepository().getProperty("rdx.notification.sms.template",
                                        messageSms).replaceAll("\\n", "\n");


        //Are we monitoring instruments
        monitorInstruments = getRepository().getProperty(PROP_RUN, true);

        //Start running in 10 seconds to give the full repository time to start up
        int delayToStart = 10;

        //Change the test db
        if (getTestMode()) {
            processChangeInstruments(null);
        }

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

	getDatabaseManager().generateBeans(
					   "com.radiometrics.plugin",
					   "(rdx_notifications|xxx_rdx_instrument_status_log|xxx_rdx_test_instrument_status)");
    }


    /**
     * _more_
     *
     * @param d _more_
     *
     * @return _more_
     */
    public static String getColor(Date d) {
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




    private String displayDate(Date d) {
	return HtmlUtils.div(formatDate(d),HtmlUtils.style("background:" + getColor(d)+";"));
    }


    /**
     * _more_
     *
     * @param d _more_
     *
     * @return _more_
     */
    private String formatDate(Date d) {
        if (d == null) {
            return "--";
        }

        return sdf.format(d);
    }

    /**
     * Are we running in test mode
     *
     * @return running in test mode
     */
    public boolean getTestMode() {
        return getRepository().getProperty(PROP_TEST, false);
    }


    /**
     * _more_
     *
     * @param msg _more_
     */
    private void logVerbose(String msg) {
        if (verbose) {
            log(msg);
        }
    }


    /**
     * _more_
     *
     * @param path _more_
     *
     * @return _more_
     */
    private String url(String path) {
        return getRepository().getUrlBase() + path;
    }


    /**
     * Utility to log messages and errors
     *
     * @param msg The message
     */
    private void log(String msg) {
        String line = formatDate(new Date()) + " -- " + msg;
        if (log.size() > LOG_SIZE) {
            log.remove(0);
        }
        log.add(line);
        System.err.println("RDX: " + line);
    }

    /**
     * _more_
     *
     * @param sb _more_
     * @param msg _more_
     */
    private void log(StringBuilder sb, String msg) {
        if (sb != null) {
            sb.append(msg + "\n");
	}
	log(msg);
    }

    /**
     * Check the instrument status
     */
    private void runCheckInstruments() {
        int pause = getRepository().getProperty(PROP_INSTRUMENT_INTERVAL, 10);
        //TODO: how many errors until we stop?
        while (true) {
            try {
                if (monitorInstruments) {
                    logVerbose("Checking instruments");
                    checkInstruments(true);
                }
            } catch (Exception exc) {
                log("Error in runCheckInstruments:" + exc);
                exc.printStackTrace();
            }
            Misc.sleepSeconds(pause);
        }
    }


    /**
     * Runs the notification checker
     */
    private void runCheckNotifications() {
        //Check every 5 minutes
        while (true) {
            Misc.sleepSeconds(60 * 5);
            try {
                if (monitorInstruments) {
                    checkNotifications(null, false);
                }
            } catch (Exception exc) {
                log("Error:" + exc);
                exc.printStackTrace();
            }
        }
    }





    /**
     * Get the db connection to the instrument status database.
     * If we are running in test mode then use RAMADDA's internal DB
     * connection.
     *
     * @return db connection
     *
     * @throws Exception On badness
     */
    private Connection getRdxConnection() throws Exception {
        if (getTestMode()) {
            return getDatabaseManager().getConnection();
        }

        return getDatabaseManager().getExternalConnection("rdx", "db");
    }

    /**
     * Close the connection to the RDX db
     *
     * @param connection The connection to close
     *
     * @throws Exception On badness
     */
    private void closeRdxConnection(Connection connection) throws Exception {
        if (connection == null) {
            return;
        }
        if (getTestMode()) {
            getDatabaseManager().closeConnection(connection);
        } else {
            connection.close();
        }
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
            closeRdxConnection(connection);
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
     *
     * @param logSB _more_
     * @param force _more_
     * @throws Exception On badness
     */
    private void checkNotifications(StringBuilder logSB, boolean force)
            throws Exception {
        List<RdxNotifications> notifications = getNotifications(null);
        if ((notifications.size() == 0) && (logSB != null)) {
            logSB.append("No notifications");
        }

        int cnt = 0;
        for (RdxNotifications notification : notifications) {
            try {
                checkNotification(notification, logSB, force);
                cnt++;
                if ((logSB != null) && (cnt > 2)) {
                    break;
                }
            } catch (Exception exc) {
                log("Error sending notification:" + exc);
                if (logSB != null) {
                    logSB.append("Error sending notification:" + exc);
                }
                exc.printStackTrace();

                return;
            }
        }
    }


    /**
     * _more_
     *
     * @param notification _more_
     * @param logSB _more_
     * @param force _more_
     *
     * @return _more_
     *
     * @throws Exception _more_
     */
    private boolean checkNotification(RdxNotifications notification,
                                      StringBuilder logSB, boolean force)
            throws Exception {
        Date    now        = new Date();
        Request tmpRequest = getRepository().getTmpRequest();
        Entry entry = getEntryManager().getEntry(tmpRequest,
                          notification.getEntryId());
        if (entry == null) {
            log(logSB,
                "checkNotifications: Could not find entry from notification:"
                + notification.getEntryId());

            return false;
        }

        //If monitoring got turned off then delete the notification
        if ( !(boolean) entry.getValue(
                RdxInstrumentTypeHandler.IDX_MONITORING_ENABLED)) {
            deleteNotification(entry);

            return false;
        }


        //Are we ready to send the notification
        int     numberEmails = notification.getNumberEmails();
        int     numberSms    = notification.getNumberSms();
        boolean sendSms      = false;

        boolean shouldSend = false;
        if (numberEmails == 0) {
            shouldSend = true;
	    log(logSB,"First time");
        } else {
	    Date lastMessageDate = notification.getLastMessageDate();
	    //Shouldn't occur
	    if(lastMessageDate==null) lastMessageDate = notification.getStartDate();
	    int interval;
	    if (numberEmails < notificationCountEmail) {
		interval  = notificationIntervalEmail;
	    }  else {
		interval = notificationIntervalSms;
		sendSms = true;
	    }
	    //Check the time
	    long elapsedMinutes = getElapsedMinutes(now, lastMessageDate);
            shouldSend = elapsedMinutes>=interval;
	    log(logSB,"Elapsed minutes:" + elapsedMinutes + " interval:" + interval +" should send:" + shouldSend);
        }


        if (force) {
            shouldSend = true;
        }

        if ( !shouldSend) {
            return false;
        }

        String url = tmpRequest.getAbsoluteUrl(
                         tmpRequest.entryUrl(
                             getRepository().URL_ENTRY_SHOW, entry));
        String stopUrl = tmpRequest.getAbsoluteUrl(url(PATH_NOTIFICATIONS
                             + "?delete=" + entry.getId()));

        String instrumentId = (String) entry.getValue(
                                  RdxInstrumentTypeHandler.IDX_INSTRUMENT_ID);

        Date network =
            (Date) entry.getValue(RdxInstrumentTypeHandler.IDX_LAST_NETWORK);
        Date data =
            (Date) entry.getValue(RdxInstrumentTypeHandler.IDX_LAST_DATA);
        Date ldm =
            (Date) entry.getValue(RdxInstrumentTypeHandler.IDX_LAST_LDM);


        String extra = "Network: " + formatDate(network) + "\n" + "Data: "
                       + formatDate(data) + "\n" + "LDM: " + formatDate(ldm);

        String template = sendSms
                          ? messageSms
                          : messageEmail;
        String msg = template.replaceAll("\\$\\{url\\}", url).replaceAll(
                         "\\$\\{id\\}", instrumentId).replaceAll(
                         "\\$\\{stopurl\\}", stopUrl).replaceAll(
                         "\\$\\{extra\\}", extra);
        if (logSB != null) {
            logSB.append("\nSending notification: " + instrumentId + " sms:"
                         + sendSms
                         + " message:<div style='margin-left:30px;'>" + msg
                         + "</div>\n");
        }
	StringBuilder to = new StringBuilder();
        int cnt = sendNotification(tmpRequest, entry, logSB, to, instrumentId,
                                   sendSms, msg);
        if (cnt == 0) {
            log(logSB, "No messages sent for site:" + entry.getName());
            return false;
        }
        //Update the db
        if (sendSms) {
            notification.setNumberSms(notification.getNumberSms() + 1);
        } else {
            notification.setNumberEmails(notification.getNumberEmails() + 1);
        }
	log(logSB, "Notification sent:" + instrumentId +" emails:"  +notification.getNumberEmails() +" " +notification.getNumberSms());
        getDatabaseManager().update(
            RdxNotifications.DB_TABLE_NAME,
            RdxNotifications.COL_NODOT_ENTRY_ID, entry.getId(),
            new String[] { RdxNotifications.COL_NODOT_LAST_MESSAGE_DATE,
			   RdxNotifications.COL_NODOT_SEND_TO,
			   RdxNotifications.COL_NODOT_NUMBER_EMAILS,
                           RdxNotifications.COL_NODOT_NUMBER_SMS },
	    new Object[] {
		now,
		to.toString(),
		notification.getNumberEmails(),
		notification.getNumberSms() });
        return true;
    }

    //1hour late - email 1
    //
    






    /**
     * handle /changeinstruments request. This is used in test mode to radnomly change the network/ldm/data times
     * of the test InstrumentData table
     *
     * @param request the request
     *
     * @return the result
     *
     * @throws Exception On badness
     */
    public Result processChangeInstruments(Request request) throws Exception {
        if ( !getTestMode()) {
            throw new IllegalArgumentException(
                "Trying to change instruments while in test mode");
        }
        //Make sure only admins can do this
        if (request != null) {
            request.ensureAdmin();
        }

        boolean randomize       = (request == null)
                                  ? true
                                  : request.get("randomize", true);
        Date    now             = new Date();
        long    minutesToMillis = 60000;
        for (int i = 1; i <= 16; i++) {
            Date network = new Date(now.getTime()
                                    - (long) ((Math.random() * 60)
                                        * minutesToMillis));
            Date data = new Date(now.getTime()
                                 - (long) ((Math.random() * 120)
                                           * minutesToMillis));
            Date ldm = new Date(now.getTime()
                                - (long) ((650 + Math.random() * 140)
                                          * minutesToMillis));
            if ( !randomize) {
                network = data = ldm = new Date(now.getTime()
                        - 5 * minutesToMillis);
            }
            getDatabaseManager().update(
                InstrumentData.TABLE, InstrumentData.COLUMN_INSTRUMENT_ID,
                new Integer(i),
                new String[] { InstrumentData.COLUMN_LAST_NETWORK,
                               InstrumentData.COLUMN_LAST_DATA,
                               InstrumentData
                                   .COLUMN_LAST_LDM }, new Object[] { network,
                    data, ldm });
        }
        if (request == null) {
            return null;
        }
        checkInstruments(true);
        return processInstruments(request);
    }


    /**
     * Add header to the /instruments or /notifications page
     *
     * @param request The request
     * @param sb buffer to append to
     * @param path PATH_INSTRUMENTS or PATH_NOTIFICATIONS
     *
     * @throws Exception On badness
     */
    private void addHeader(Request request, Appendable sb, String path)
            throws Exception {
        request.put("template", "radiometrics");
        if (request.isAdmin()) {
            if (request.defined(PROP_RUN)) {
                monitorInstruments = request.get(PROP_RUN, true);
                getRepository().writeGlobal(PROP_RUN,
                                            "" + monitorInstruments);
            }
        }
        sb.append(HtmlUtils.sectionOpen(null, false));
        String on =
            HtmlUtils.style(
                "display:inline-block;background:#eee; font-size:16pt; padding-left:4px;padding-right:4px;margin-top:4px;");
        String off =
            HtmlUtils.style(
                "display:inline-block;font-size:16pt; padding-left:4px;padding-right:4px;margin-top:4px;");

        String notifications = path.equals(PATH_NOTIFICATIONS)
                               ? HtmlUtils.div("Notifications", on)
                               : HtmlUtils.href(url(PATH_NOTIFICATIONS),
                                   "Notifications", off);
        String instruments = path.equals(PATH_INSTRUMENTS)
                             ? HtmlUtils.div("Instruments", on)
                             : HtmlUtils.href(url(PATH_INSTRUMENTS),
                                 "Instruments", off);
        String log = path.equals(PATH_LOG)
                     ? HtmlUtils.div("Log", on)
                     : HtmlUtils.href(url(PATH_LOG), "Log", off);

        String sep = HtmlUtils.space(2) + "|" + HtmlUtils.space(2);
        HtmlUtils.sectionTitle(sb, "");
        sb.append(HtmlUtils.center(instruments + sep + notifications + sep
                                   + log));


        if (getTestMode()) {
            String link = !request.isAdmin()
                          ? ""
                          : HtmlUtils
                              .button(HtmlUtils
                                  .href(url(PATH_CHANGEINSTRUMENTS
                                      + "?randomize=true"), "Randomize timestamps")) + HtmlUtils
                                          .space(2) + HtmlUtils
                                          .button(HtmlUtils
                                              .href(url(PATH_CHANGEINSTRUMENTS
                                                  + "?randomize=false"), "Update timestamps"));
            HtmlUtils.center(
                sb,
                getPageHandler().showDialogNote(
                    "Running in test mode. " + HtmlUtils.space(2) + link));

        }



        if (request.isAdmin()) {
            String link =
                HtmlUtils.button(HtmlUtils.href(getRepository().getUrlBase()
                    + path + "?" + PROP_RUN + "="
                    + ( !monitorInstruments), monitorInstruments
                    ? "Turn off monitoring"
                    : "Turn on monitoring"));
            if (getTestMode()) {
                String test =
                    HtmlUtils.button(
                        HtmlUtils.href(
                            getRepository().getUrlBase() + PATH_NOTIFICATIONS
                            + "?testnotifications=true", "Test notifications"));
                link += HtmlUtils.space(2);
                link += test;
            }
            sb.append(HtmlUtils.center(link));
            sb.append("<br>");
        } else {
            if ( !monitorInstruments) {
                sb.append(
                    HtmlUtils.center("Not currently monitoring instruments"));
                sb.append("<br>");
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
        StringBuilder sb = new StringBuilder();
        addHeader(request, sb, PATH_NOTIFICATIONS);

        if (request.isAdmin()) {
            if (request.get("deleteall", false)) {
                deleteNotification(null);
                sb.append(
                    getRepository().getPageHandler().showDialogNote(
                        "All notifications deleted"));
                sb.append(HtmlUtils.sectionClose());

                return new Result(TITLE + " - Notifications", sb);
            }

            if (request.get("testnotifications", false)) {
                StringBuilder logSB = new StringBuilder();
                checkNotifications(logSB, true);
                sb.append("Test notifications:");
                sb.append(HtmlUtils.pre(logSB.toString().trim()));
            }


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
        }

	if(!getMailManager().isEmailEnabled()) {
	    sb.append("Note: email is not enabled<br>");
	}
	if(!getMailManager().isSmsEnabled()) {
	    sb.append("Note: SMS is not enabled<br>");
	}
        List<RdxNotifications> notifications = getNotifications(null);
        if (notifications.size() == 0) {
            sb.append(
                getRepository().getPageHandler().showDialogNote(
                    "No pending notifications"));
        }

        boolean admin = request.isAdmin();
        if (notifications.size() > 0) {
            sb.append(
                "<table class='stripe ramadda-table' table-ordering=true>");
            String deleteAllLink = !admin
                                   ? ""
                                   : HtmlUtils
                                       .href(url(PATH_NOTIFICATIONS
                                           + "?deleteall=true"), HtmlUtils
                                               .getIconImage("fa-trash",
                                                   "title",
                                                   "Delete all notifications"));

            HtmlUtils.thead(sb, deleteAllLink, HtmlUtils.b("Instrument"),
                            HtmlUtils.b("Start Date"),
			    HtmlUtils.b("Last Message Sent"),
			    HtmlUtils.b("To"),			    
                            HtmlUtils.b("Number emails sent"),
                            HtmlUtils.b("Number texts sent"));
            sb.append("<tbody>");
            for (RdxNotifications notification : notifications) {
                Entry entry = getEntryManager().getEntry(null,
                                  notification.getEntryId());
                if (entry == null) {
                    continue;
                }
                String url        = getEntryManager().getEntryUrl(null,
                                        entry);
                String deleteLink = !admin
                                    ? ""
                                    : HtmlUtils
                                        .href(url(PATH_NOTIFICATIONS
                                            + "?delete="
                                            + entry.getId()), HtmlUtils
                                                .getIconImage("fa-trash",
                                                    "title",
                                                    "Delete notification"));
		String to = notification.getSendTo().trim();
		to  = to.replaceAll("\n","<br>");
                sb.append(HtmlUtils.row(HtmlUtils.cols(new Object[] {
                    deleteLink + " ",
                    HtmlUtils.href(url, entry.getName()),
                    formatDate(notification.getStartDate()),
                    formatDate(notification.getLastMessageDate()),
		    to,
                    notification.getNumberEmails(),
                    notification.getNumberSms() })));
            }
            sb.append("</tbody></table>");
        }
        sb.append(HtmlUtils.sectionClose());

        return new Result(TITLE + " - Notifications", sb);
    }



    /**
     * Handle the /rdx/instruments request
     *
     * @param request The request
     *
     * @return The result
     *
     * @throws Exception On badness
     */
    public Result processInstruments(Request request) throws Exception {
        StringBuilder sb = new StringBuilder();
        addHeader(request, sb, PATH_INSTRUMENTS);
        boolean              test        = getTestMode();
        List<InstrumentData> instruments = readInstruments();
        if (instruments == null) {
            if ( !test) {
                sb.append(
                    getPageHandler().showDialogWarning(
                        "Failed to read instruments<br>Perhaps the external instrument database is not configured?"));
            }

            return new Result("", sb);
        }


        String id = request.getString("instrument_id");
        if (instruments.size() > 0) {
            sb.append(
                "<table class='stripe ramadda-table' table-ordering=true>");
            HtmlUtils.thead(sb, "Site ID", "Instrument ID",
                            "Last Network Connection", "Last Data",
                            "Last LDM");
            sb.append("<tbody>");
            for (InstrumentData instrument : instruments) {
                Entry entry = getInstrumentEntry(instrument);
                //TODO
                String label = instrument.siteId;
                if (entry != null) {
                    label =
                        HtmlUtils.href(getEntryManager().getEntryUrl(null,
                            entry), label);
                }

                sb.append(HtmlUtils.row(HtmlUtils.cols(new Object[] { label,
                        instrument.id, displayDate(instrument.network),
								      displayDate(instrument.data),
								      displayDate(instrument.ldm) })));
            }
            sb.append("<tbody></table>");
        } else {
            sb.append("No instruments found");
        }
        sb.append(HtmlUtils.sectionClose());

        return new Result(TITLE + " - Instruments", sb);
    }

    /**
     * _more_
     *
     * @param request _more_
     *
     * @return _more_
     *
     * @throws Exception _more_
     */
    public Result processLog(Request request) throws Exception {
        StringBuilder sb = new StringBuilder();
        addHeader(request, sb, PATH_LOG);
        sb.append(HtmlUtils.pre(Utils.join(log, "\n", true)));
        sb.append(HtmlUtils.sectionClose());

        return new Result(TITLE + " - Log", sb);
    }



    /** Tracks last time we stored the instrument status in the local database */
    private Date timeSinceLastInstrumentStore;

    /**
     * Check the instruments
     *
     *
     * @param doNotification _more_
     * @throws Exception on badness
     */
    private void checkInstruments(boolean doNotification) throws Exception {
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

	boolean testNotifications  = true;

        for (InstrumentData instrument : instruments) {
            checkInstrument(instrument, store, doNotification);
	    if(testNotifications)  {
		List<RdxNotifications> notifications = getNotifications(null);
		if(notifications.size()>0) break;
	    }
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


    /**
     * Utility to get the elapsed minutes
     *
     * @param date date
     *
     * @return Elapsed minutes
     */
    public static int getElapsedMinutes(Date date) {
        return getElapsedMinutes(new Date(), date);
    }

    /**
     * _more_
     *
     * @param now _more_
     * @param date _more_
     *
     * @return _more_
     */
    public static int getElapsedMinutes(Date now, Date date) {
        if (date == null) {
            return 0;
        }

        return (int) (now.getTime() - date.getTime()) / 1000 / 60;
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
        int network = getElapsedMinutes(
                          (Date) entry.getValue(
                              RdxInstrumentTypeHandler.IDX_LAST_NETWORK));
        int data = getElapsedMinutes(
                       (Date) entry.getValue(
                           RdxInstrumentTypeHandler.IDX_LAST_DATA));
        int ldm = getElapsedMinutes(
                      (Date) entry.getValue(
                          RdxInstrumentTypeHandler.IDX_LAST_LDM));

        return (network < networkThreshold) && (data < dataThreshold)
               && (ldm < ldmThreshold);
    }



    /**
     * Compare the instrument (from rdx db) to the internal RAMADDA instrument entry
     *
     * @param instrument The instrument
     * @param store Force storing the instrument state
     * @param doNotification _more_
     *
     * @throws Exception On badness
     */
    private boolean checkInstrument(InstrumentData instrument, boolean store,
				    boolean doNotification)
            throws Exception {
        //Find the station entries
        Entry entry = getInstrumentEntry(instrument);
        if (entry == null) {
            log("checkInstrument: Could not find instrument: "
                + instrument.siteId);

            return false;
        }


        //Are we doing monitoring of this instrument?
        if ( !(boolean) entry.getValue(
                RdxInstrumentTypeHandler.IDX_MONITORING_ENABLED)) {
            return false;
        }


        boolean changed = false;

        Date network =
            (Date) entry.getValue(RdxInstrumentTypeHandler.IDX_LAST_NETWORK);
        Date data =
            (Date) entry.getValue(RdxInstrumentTypeHandler.IDX_LAST_DATA);
        Date ldm =
            (Date) entry.getValue(RdxInstrumentTypeHandler.IDX_LAST_LDM);



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


        //TEST
        store = true;

        if (doNotification && (store || changed)) {
            //            System.err.println("\tStoring instrument log");
            InstrumentLog.store(repository, entry);
        }

        //        System.err.println("checkInstrument:" + entry + " changed:" + changed);

        if ( !changed) {
            //if no change then done
            return false;
        }

        //Save the ramadda entry
        getEntryManager().updateEntry(getRepository().getTmpRequest(), entry);
        if ( !doNotification) {
            return false;
        }

        boolean ok = isInstrumentOk(entry);
        if (ok) {
            //If all OK then remove any pending notification and return
            deleteNotification(entry);
            return false;
        }

        log("checkInstrument: adding notification for " + entry.getName());

        //Add the new notification if we don't have one already
        List<RdxNotifications> notifications = getNotifications(entry);
        if (notifications.size() == 0) {
            String insert =
                SqlUtil.makeInsert(RdxNotifications.DB_TABLE_NAME,
                                   new String[] {
                                       RdxNotifications.COL_NODOT_ENTRY_ID,
				       RdxNotifications.COL_NODOT_START_DATE,
				       RdxNotifications.COL_NODOT_SEND_TO,
                                       RdxNotifications.COL_NODOT_NUMBER_EMAILS,
                                       RdxNotifications.COL_NODOT_NUMBER_SMS });
            Date now = new Date();
            getDatabaseManager().executeInsert(insert,
                    new Object[] { entry.getId(),
                                   now, "",0, 0 });
        }
	return true;

    }


    /**
     * Delete any notifications for the given entry
     *
     * @param entry The entry
     *
     * @throws Exception On badness
     */
    private void deleteNotification(Entry entry) throws Exception {
        if (entry == null) {
            getDatabaseManager().delete(RdxNotifications.DB_TABLE_NAME, null);
        } else {
            getDatabaseManager().delete(
                RdxNotifications.DB_TABLE_NAME,
                Clause.eq(RdxNotifications.COL_ENTRY_ID, entry.getId()));
        }
    }


    /**
     * Send the notification either sms or email
     *
     * @param request the request
     * @param entry the entry
     * @param logSB _more_
     * @param instrumentId the instrument
     * @param sms send sms
     * @param msg the message
     *
     *
     * @return _more_
     * @throws Exception On badness
     */
    private int sendNotification(Request request, Entry entry,
                                 StringBuilder logSB, StringBuilder to,
				 String instrumentId,
                                 boolean sms, String msg)
	throws Exception {
	boolean testMode = logSB!=null;
        GregorianCalendar cal = new GregorianCalendar(timeZone);
        cal.setTime(new Date());
        boolean weekend = (cal.get(cal.DAY_OF_WEEK) == cal.SUNDAY)
                          || (cal.get(cal.DAY_OF_WEEK) == cal.SATURDAY);

        List<Metadata> metadataList =
            getMetadataManager().findMetadata(request, entry,
                "rdx_notification", true);
        if ((metadataList == null) || (metadataList.size() == 0)) {
            log(logSB, "Warning: No notifications found");
            return 0;
        }
        int cnt = 0;
	MailManager mailManager = getRepository().getMailManager();
	if(sms) {
	    if (!mailManager.isSmsEnabled()) {
		if(testMode) cnt++;
		to.append("sms not enabled\n");
		log(logSB, "Warning: SMS not enabled");
		return cnt;
	    }
	} else {
	    if (!mailManager.isEmailEnabled()) {
		if(testMode) cnt++;
		to.append("email not enabled\n");
		log(logSB, "Warning: Email not enabled");
		return cnt;
	    }
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
	    phone = phone.replaceAll("-", "").replaceAll(" ", "");
            log(logSB,
                "Notification:" + name + " email: " + email + " phone: "   + phone);
            if (sms) {
                if (phone.length() == 0) {
		    to.append("no phone: " + name);
		    continue;
		}
		log(logSB, "Sending site status sms: " + phone);
		if (testMode) {
		    cnt++;
		    to.append("test sms to: " + name +" phone: " + phone+"\n");
		    continue;
		}
		if (mailManager.sendTextMessage(null, phone, msg)) {
		    to.append("sms to: " + name +" phone: " + phone+"\n");
		    cnt++;
		} else { 
		    to.append( "Error: SMS failed sending to: " + phone+"\n");
		    log(logSB, "Error: SMS failed sending to: " + phone);
		}
            } else {
                if (email.length() == 0) {
		    to.append("no email: " + name);
		    continue;
		}
		log(logSB, "Sending site status email: " + email);
		if (testMode) {
		    cnt++;
		    to.append("test email to: " + name +" email: " + email+"\n");
		    continue;
		}
		mailManager.sendEmail(email, "Instrument status: " + instrumentId, msg, true);
		to.append("email to: " + name +" email: " + email+"\n");
		cnt++;
	    }
        }
        return cnt;
    }



    /**
     *  Hard code the rdx db type id to the ramadda type
     *
     * @param rdxType RDX DB instrument type
     *
     * @return RAMADDA type from rdxtypes.xml
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

        /** Other table to join with */
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

        /** Column selections */
        public static final String WHAT = SqlUtil.comma(COLUMN_SITE_ID,
                                              COLUMN_TYPE_ID,
                                              COLUMN_INSTRUMENT_ID,
                                              COLUMN_LAST_NETWORK,
                                              COLUMN_LAST_DATA,
                                              COLUMN_LAST_LDM);

        /** instrument attribute */
        String siteId;

        /** instrument attribute */
        String typeId;

        /** instrument attribute */
        int id;

        /** instrument attribute */
        Date network;

        /** instrument attribute */
        Date data;

        /** instrument attribute */
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
        public static final int MINUTES_START = 60;

        /** _more_ */
        public static final int MINUTES_EMAIL = 60;

        /** const value */
        public static final int MINUTES_TEXT = 60 * 11;

        /** _more_ */
        public static final String TYPE_START = "start";

        /** const value */
        public static final String TYPE_EMAIL = "email";

        /** const value */
        public static final String TYPE_TEXT = "text";


    }




}
