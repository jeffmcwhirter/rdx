/*
* Copyright (c) 2020 Radiometrics Inc.
*
*/

package com.radiometrics.plugin;


import org.ramadda.repository.*;
import org.ramadda.repository.admin.MailManager;
import org.ramadda.repository.metadata.*;
import org.ramadda.util.HtmlUtils;
import org.ramadda.util.Utils;
import org.ramadda.util.sql.Clause;
import org.ramadda.util.sql.SqlUtil;

import ucar.unidata.util.LogUtil;
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
 * Handles the monitoring of the instrument status db, sending of notifications and
 * provides the top-level /rdx/instruments, /rdx/notifications, /rdx/settings and /rdx/log pages
 *
 * The file api.xml specifies the url path to method mapping. The repository
 * creates a singleton of this class. It starts 2 threads - one to monitor the external RDX
 * instrument status database and one to monitor the internal notifications table.
 *
 * See the top level /rdx/rdx.properties file for setting configuration options
 */
public class RdxApiHandler extends RepositoryManager implements RdxConstants,
        RequestHandler {

    /** rolling in memory log */
    private List<String> log = new ArrayList<String>();

    /** Last status of the instrument monitor */
    private String instrumentMonitorStatus;

    /** Last status of the notifications monitor */
    private String notificationsMonitorStatus;

    /** are we currently running the monitoring */
    private boolean monitorInstruments;

    /** threshold in minutes for notifications of network times */
    private int networkThreshold;

    /** threshold in minutes for notifications of data times */
    private int dataThreshold;

    /** threshold in minutes for notifications of ldm times */
    private int ldmThreshold;

    /** interval in seconds between checks of instrument db */
    private int instrumentInterval;

    /** interval in seconds between checks of notifications */
    private int notificationsInterval;

    /** interval in minutes between sending notification emails */
    private int notificationIntervalEmail;

    /** total number of email notifications sent */
    private int notificationCountEmail;

    /** interval in minutes before start sending notification sms */
    private int notificationFirstIntervalSms;

    /** interval in minutes between sending notification sms */
    private int notificationIntervalSms;

    /** total number of sms notifications sent */
    private int notificationCountSms;

    /** Template for notification email subject */
    private String messageSubject;

    /** Template for notification email */
    private String messageEmail;

    /** Template for notification sms */
    private String messageSms;

    /** Tracks last time we stored the instrument status in the local database */
    private Date timeSinceLastInstrumentStore;

    /** do we store instrument status time series */
    private boolean storeInstrumentStatus;

    /** interval in minutes between storing time series */
    private int storeInterval;

    /** preferrred timeszone for displaying dates and for figuring out when it is the weekend */
    private TimeZone timeZone;

    /** preferrred format for displaying dates */
    private SimpleDateFormat sdf;


    /** wiki attribute */
    public static List<String> colors;

    /** wiki attribute */
    public static List<Integer> colorSteps;

    /** Flag for singleton */
    private static boolean haveCreatedApiHandler;

    /**
     * This gets created by the core RAMADDA Repository through the api.xml file definitions. This should be a singleton.
     *
     * @param repository the repository
     *
     * @throws Exception on badness
     */
    public RdxApiHandler(Repository repository) throws Exception {

        super(repository);
        //Ensure the singleton
        if (haveCreatedApiHandler) {
            return;
        }
        haveCreatedApiHandler = true;
        timeZone = TimeZone.getTimeZone(
            getRepository().getProperty(PROP_RDX_TIMEZONE, "America/Denver"));

        colors =
            StringUtil.split(getRepository().getProperty("rdx.wiki.colors",
                DEFAULT_COLORS), ",", true, true);

        colorSteps = new ArrayList<Integer>();
        for (String step :
                StringUtil.split(
                    getRepository().getProperty(
                        "rdx.wiki.colorBySteps", DEFAULT_COLORSTEPS), ",",
                            true, true)) {
            colorSteps.add(new Integer(step));
        }


        sdf = new SimpleDateFormat(
            getRepository().getProperty(
                PROP_DATEFORMAT, "yyyy-MM-dd HH:mm z"));
        sdf.setTimeZone(timeZone);

        instrumentInterval =
            getRepository().getProperty(PROP_INSTRUMENT_INTERVAL, 5);

        notificationsInterval =
            getRepository().getProperty(PROP_NOTIFICATIONS_INTERVAL, 5);

        //Set the notification thresholds
        int threshold = getRepository().getProperty(PROP_THRESHOLD, 60);
        networkThreshold =
            getRepository().getProperty(PROP_THRESHOLD_NETWORK, threshold);
        dataThreshold = getRepository().getProperty(PROP_THRESHOLD_DATA,
                threshold);
        ldmThreshold = getRepository().getProperty(PROP_THRESHOLD_LDM,
                threshold);

        notificationIntervalEmail =
            getRepository().getProperty("rdx.notification.interval.email",
                                        60 * 8);
        notificationCountEmail =
            getRepository().getProperty("rdx.notification.count.email", 4);

        notificationFirstIntervalSms =
            getRepository().getProperty("rdx.notification.firstinterval.sms",
                                        60);

        notificationIntervalSms =
            getRepository().getProperty("rdx.notification.interval.sms",
                                        60 * 24);

        notificationCountSms =
            getRepository().getProperty("rdx.notification.count.sms", 4);

        messageSubject =
            getRepository().getProperty("rdx.notification.email.subject",
                                        "Instrument status: ${id}");

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

        //Do we store the instrument status as a time series
        storeInstrumentStatus =
            getRepository().getProperty(PROP_INSTRUMENT_LOG, true);

        //If so, how often do we store
        storeInterval =
            getRepository().getProperty(PROP_INSTRUMENT_LOG_INTERVAL, 60 * 6);


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

        if (false) {
            //This generates the RdxNotifications and RdxInstrumentLog database beans if needed
            getDatabaseManager().generateBeans("com.radiometrics.plugin",
                    Utils.makeHashtable(),
                    "(rdx_notifications|rdx_instrument_log)");
        }

    }



    /**
     * gets the status color of dates, e.g., green, yellow, red, purple
     *
     * @param d the date
     *
     * @return the color
     */
    public static String getColor(Date d) {
        int minutes = (int) ((new Date().getTime() - d.getTime()) / 1000
                             / 60);
        for (int i = 0; i < colorSteps.size(); i++) {
            int step = colorSteps.get(i);
            if (minutes <= step) {
                return colors.get(i);
            }
        }

        return colors.get(colors.size() - 1);
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
     * Log the message. If sb is non-null then append the message. Used for testing
     *
     * @param sb buffer to append to
     * @param msg the message
     */
    private void log(StringBuilder sb, String msg) {
        if (sb != null) {
            sb.append(msg + "\n");
        }
        log(msg);
    }

    /**
     * Format the date and return a HTML div with appropriate background color
     *
     * @param d the date
     *
     * @return html with background color
     */
    private String displayDate(Date d) {
        return HtmlUtils.div(formatDate(d),
                             HtmlUtils.style("background:" + getColor(d)
                                             + ";"));
    }


    /**
     * Format the date with the SimpleDateFormat
     *
     * @param d The date
     *
     * @return formatted date
     */
    private String formatDate(Date d) {
        if (d == null) {
            return "--";
        }

        return sdf.format(d);
    }


    /**
     * Make the full path RAMADDA url
     *
     * @param path path
     *
     * @return url
     */
    private String fullPath(String path) {
        return getRepository().getUrlBase() + path;
    }



    /**
     * Check the instrument status
     */
    private void runCheckInstruments() {
        int errorCount = 0;
        while (true) {
            try {
                if (monitorInstruments) {
                    log("Checking instruments");

                    instrumentMonitorStatus =
                        "Last ran instrument monitor at "
                        + formatDate(new Date());
                    checkInstruments(false);
                    errorCount = 0;
                }
            } catch (Exception exc) {
                log("Error in runCheckInstruments:" + exc);
                exc.printStackTrace();
                errorCount++;
                if (errorCount > MAX_ERRORS) {
                    instrumentMonitorStatus =
                        "Too many errors. Have stopped monitoring. Last error:"
                        + exc;

                    return;
                }
            }
            Misc.sleepSeconds(instrumentInterval * 60);
        }
    }


    /**
     * Runs the notification checker
     */
    private void runCheckNotifications() {
        int errorCount = 0;
        while (true) {
            try {
                if (monitorInstruments) {
                    notificationsMonitorStatus =
                        "Last ran notifications monitor at "
                        + formatDate(new Date());
                    checkNotifications(null, false);
                    errorCount = 0;
                }
            } catch (Exception exc) {
                log("Error:" + exc);
                exc.printStackTrace();
                errorCount++;
                if (errorCount > MAX_ERRORS) {
                    notificationsMonitorStatus =
                        "Too many errors. Have stopped monitoring. Last error:"
                        + exc;

                    return;
                }
            }
            Misc.sleepSeconds(notificationsInterval * 60);
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
     * @param logSB buffer for testing
     * @param force
     * @throws Exception On badness
     */
    private void checkNotifications(StringBuilder logSB, boolean force)
            throws Exception {
        List<RdxNotifications> notifications = getNotifications(null);
        if ((notifications.size() == 0) && (logSB != null)) {
            logSB.append("No notifications");
        }
        boolean test = logSB != null;
        int     cnt  = 0;
        for (RdxNotifications notification : notifications) {
            try {
                checkNotification(notification, logSB, force);
                cnt++;
                if (test && (cnt > 2)) {
                    break;
                }
            } catch (Exception exc) {
                log(logSB, "Error sending notification:" + exc);
                exc.printStackTrace();

                return;
            }
        }
    }


    /**
     * Checks if the notification is ready to send
     *
     * @param notification The notification
     * @param logSB For testing
     * @param force If true then always send the notification. For testing
     *
     * @return Did we send the notification
     *
     * @throws Exception On badness
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

        boolean shouldSend   = false;
        //Check for first time
        if ((numberEmails == 0) && (notificationCountEmail > 0)) {
            shouldSend = true;
        }
        if ( !shouldSend && (numberSms == 0) && (notificationCountSms > 0)) {
            shouldSend = true;
            sendSms    = true;
        }

        //If not first time then check if we're scheduled to send
        if ( !shouldSend) {
            Date lastMessageDate = notification.getLastMessageDate();
            //Shouldn't occur
            if (lastMessageDate == null) {
                lastMessageDate = notification.getStartDate();
            }
            int interval;
            if (numberEmails < notificationCountEmail) {
                interval = notificationIntervalEmail;
            } else {
                if (numberSms == 0) {
                    interval = notificationFirstIntervalSms;
                } else {
                    interval = notificationIntervalSms;
                }
                if (numberSms >= notificationCountSms) {
                    if (numberSms == notificationCountSms) {
                        notification.setSendTo("Done sending notifications");
                        updateNotification(notification, now);
                    }

                    return false;
                }
                sendSms = true;
            }
            //Check the time
            long elapsedMinutes = getElapsedMinutes(now, lastMessageDate);
            shouldSend = elapsedMinutes >= interval;
            log(logSB,
                "Elapsed minutes:" + elapsedMinutes + " interval:" + interval
                + " should send:" + shouldSend);
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
        String stopUrl = tmpRequest.getAbsoluteUrl(
                             HtmlUtils.url(
                                 fullPath(PATH_NOTIFICATIONS),
                                 ARG_DELETE_ENTRY, entry.getId()));

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
        StringBuilder to      = new StringBuilder();
        String        subject = messageSubject.replace("${id}", instrumentId);

        try {
            int cnt = sendNotification(tmpRequest, entry, logSB, to, sendSms,
                                       msg, subject);
            if (cnt == 0) {
                log(logSB,
                    "No messages sent for site:" + entry.getName()
                    + " sendSms:" + sendSms);

                return false;
            }
        } catch (Exception exc) {
            Throwable thr = LogUtil.getInnerException(exc);
            notification.setSendTo("Error:" + thr.getMessage());
            updateNotification(notification, now);

            return false;
        }
        //Update the db
        if (sendSms) {
            notification.setNumberSms(notification.getNumberSms() + 1);
        } else {
            notification.setNumberEmails(notification.getNumberEmails() + 1);
        }
        log(logSB,
            "Notification sent:" + instrumentId + " emails:"
            + notification.getNumberEmails() + " "
            + notification.getNumberSms());

        notification.setSendTo(to.toString());
        updateNotification(notification, now);

        return true;

    }


    /**
     * Write the notification back to the DB
     *
     * @param notification The notification
     * @param now now
     *
     * @throws Exception On badness
     */
    private void updateNotification(RdxNotifications notification, Date now)
            throws Exception {
        getDatabaseManager().update(
            RdxNotifications.DB_TABLE_NAME,
            RdxNotifications.COL_NODOT_ENTRY_ID, notification.getEntryId(),
            new String[] { RdxNotifications.COL_NODOT_LAST_MESSAGE_DATE,
                           RdxNotifications.COL_NODOT_SEND_TO,
                           RdxNotifications.COL_NODOT_NUMBER_EMAILS,
                           RdxNotifications
                               .COL_NODOT_NUMBER_SMS }, new Object[] { now,
                notification.getSendTo(), notification.getNumberEmails(),
                notification.getNumberSms() });

    }







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
                                  : request.get(ARG_RANDOMIZE, true);
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
     *
     * @return false if the calling method should not generates its normal web page
     * @throws Exception On badness
     */
    private boolean addHeader(Request request, Appendable sb, String path)
            throws Exception {

        request.put(ARG_TEMPLATE, TEMPLATE_RADIOMETRICS);
        sb.append(HtmlUtils.sectionOpen(null, false));
        String on =
            HtmlUtils.style(
                "display:inline-block;background:#eee; font-size:16pt; padding-left:4px;padding-right:4px;margin-top:4px;");
        String off =
            HtmlUtils.style(
                "display:inline-block;font-size:16pt; padding-left:4px;padding-right:4px;margin-top:4px;");

        String notifications = path.equals(PATH_NOTIFICATIONS)
                               ? HtmlUtils.div("Notifications", on)
                               : HtmlUtils.href(fullPath(PATH_NOTIFICATIONS),
                                   "Notifications", off);
        String instruments = path.equals(PATH_INSTRUMENTS)
                             ? HtmlUtils.div("Instruments", on)
                             : HtmlUtils.href(fullPath(PATH_INSTRUMENTS),
                                 "Instruments", off);
        String settings = path.equals(PATH_SETTINGS)
                          ? HtmlUtils.div("Settings", on)
                          : HtmlUtils.href(fullPath(PATH_SETTINGS),
                                           "Settings", off);
        String log = path.equals(PATH_LOG)
                     ? HtmlUtils.div("Log", on)
                     : HtmlUtils.href(fullPath(PATH_LOG), "Log", off);

        String sep = SPACE + "|" + SPACE;
        HtmlUtils.sectionTitle(sb, "");
        HtmlUtils.center(sb,
                         instruments + sep + notifications + sep + settings
                         + sep + log);

        if (getTestMode()) {
            String link = !request.isAdmin()
                          ? ""
                          : HtmlUtils
                              .button(HtmlUtils
                                  .href(HtmlUtils
                                      .url(fullPath(PATH_CHANGEINSTRUMENTS),
                                          ARG_RANDOMIZE,
                                          "true"), "Randomize timestamps")) + HtmlUtils
                                              .space(2) + HtmlUtils
                                              .button(HtmlUtils
                                                  .href(HtmlUtils
                                                      .url(fullPath(PATH_CHANGEINSTRUMENTS),
                                                          ARG_RANDOMIZE,
                                                          "false"), "Update timestamps"));
            HtmlUtils.center(sb,
                             messageNote("Running in test mode. " + SPACE
                                         + link));
        }

        if (request.isAdmin()) {
            if (request.defined(PROP_RUN)) {
                monitorInstruments = request.get(PROP_RUN, true);
                getRepository().writeGlobal(PROP_RUN,
                                            "" + monitorInstruments);
            }
            String fullPath = getRepository().getUrlBase() + path;
            String link =
                HtmlUtils.button(HtmlUtils.href(HtmlUtils.url(fullPath,
                    PROP_RUN, monitorInstruments
                              ? "false"
                              : "true"), monitorInstruments
                                         ? "Turn off monitoring"
                                         : "Turn on monitoring"));

            link += SPACE
                    + HtmlUtils.button(HtmlUtils.href(HtmlUtils.url(fullPath,
                        ARG_DELETETIMESERIES,
                        "true"), "Delete logged time series"));

            if (getTestMode()) {
                String test = HtmlUtils.button(
                                  HtmlUtils.href(
                                      HtmlUtils.url(
                                          fullPath(PATH_NOTIFICATIONS),
                                          ARG_TESTNOTIFICATIONS,
                                          "true"), "Test notifications"));
                link += SPACE;
                link += test;
            }
            HtmlUtils.center(sb, link);
            sb.append("<br>");
            if (request.get(ARG_DELETETIMESERIES, false)) {
                if (request.get(ARG_OK, false)) {
                    getDatabaseManager().delete(
                        RdxInstrumentLog.DB_TABLE_NAME, null);
                    HtmlUtils.center(
                        sb, messageNote("Ok, all time series are deleted"));
                } else {
                    HtmlUtils.center(
                        sb,
                        messageQuestion(
                            "Are you sure you want to delete all stored time series logs?",
                            HtmlUtils.button(
                                HtmlUtils.href(
                                    HtmlUtils.url(
                                        fullPath, ARG_DELETETIMESERIES,
                                        "true", ARG_OK,
                                        "true"), "Yes")) + SPACE
                                            + HtmlUtils.button(
                                                HtmlUtils.href(
                                                    fullPath, "Cancel"))));

                    return false;
                }
            }
        } else {
            if ( !monitorInstruments) {
                HtmlUtils.center(sb, "Not currently monitoring instruments");
                sb.append("<br>");
            }
        }

        return true;

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
        String        title = TITLE + " - Notifications";
        StringBuilder sb    = new StringBuilder();
        if ( !addHeader(request, sb, PATH_NOTIFICATIONS)) {
            return new Result(title, sb);
        }

        if (request.isAdmin()) {
            if (request.get(ARG_DELETEALL, false)) {
                deleteNotification(null);
                HtmlUtils.center(sb,
                                 messageNote("All notifications deleted"));
                sb.append(HtmlUtils.sectionClose());

                return new Result(title, sb);
            }

            if (request.get(ARG_TESTNOTIFICATIONS, false)) {
                StringBuilder logSB = new StringBuilder();
                checkNotifications(logSB, true);
                sb.append("Test notifications:");
                sb.append(HtmlUtils.pre(logSB.toString().trim()));
            }


            if (request.defined(ARG_DELETE_ENTRY)) {
                Entry entry = getEntryManager().getEntry(null,
                                  request.getString(ARG_DELETE_ENTRY));
                if (entry != null) {
                    deleteNotification(entry);
                    HtmlUtils.center(
                        sb,
                        messageNote(
                            "Notification for instrument: " + entry.getName()
                            + " deleted"));
                }
            }
        }
        List<RdxNotifications> notifications = getNotifications(null);
        if (notifications.size() == 0) {
            HtmlUtils.center(sb, messageNote("No pending notifications"));
        }

        boolean admin = request.isAdmin();
        if (notifications.size() > 0) {
            sb.append(
                "<table class='stripe ramadda-table' table-ordering=true>");
            String deleteAllLink = !admin
                                   ? ""
                                   : HtmlUtils
                                       .href(HtmlUtils
                                           .url(fullPath(PATH_NOTIFICATIONS),
                                               ARG_DELETEALL,
                                               "true"), HtmlUtils
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
            String path = fullPath(PATH_NOTIFICATIONS);
            for (RdxNotifications notification : notifications) {
                Entry entry = getEntryManager().getEntry(null,
                                  notification.getEntryId());
                if (entry == null) {
                    continue;
                }
                String deleteLink = !admin
                                    ? ""
                                    : HtmlUtils
                                        .href(HtmlUtils
                                            .url(path, ARG_DELETE_ENTRY,
                                                entry.getId()), HtmlUtils
                                                    .getIconImage("fa-trash",
                                                        "title",
                                                        "Delete notification"));
                String entryUrl = getEntryManager().getEntryUrl(null, entry);
                String to       = notification.getSendTo().trim();
                to = to.replaceAll("\n", "<br>");
                sb.append(HtmlUtils.row(HtmlUtils.cols(new Object[] {
                    deleteLink + " ",
                    HtmlUtils.href(entryUrl, entry.getName()),
                    formatDate(notification.getStartDate()),
                    formatDate(notification.getLastMessageDate()), to,
                    notification.getNumberEmails(),
                    notification.getNumberSms()
                })));
            }
            sb.append("</tbody></table>");
        }
        sb.append(HtmlUtils.sectionClose());

        return new Result(title, sb);
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
        String        title = TITLE + " - Instruments";
        StringBuilder sb    = new StringBuilder();
        if ( !addHeader(request, sb, PATH_INSTRUMENTS)) {
            return new Result(title, sb);
        }
        boolean              test        = getTestMode();
        List<InstrumentData> instruments = readInstruments();
        if (instruments == null) {
            if ( !test) {
                HtmlUtils.center(
                    sb,
                    messageWarning(
                        "Failed to read instruments<br>Perhaps the external instrument database is not configured?"));
            }

            return new Result("", sb);
        }


        if (instruments.size() > 0) {
            sb.append(
                "<table class='stripe ramadda-table' table-ordering=true>");
            HtmlUtils.thead(sb, "Site ID", "Instrument ID",
                            "Last Network Connection", "Last Data",
                            "Last LDM");
            sb.append("<tbody>");
            for (InstrumentData instrument : instruments) {
                Entry  entry = getInstrumentEntry(instrument);
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

        return new Result(title, sb);
    }

    /**
     * Handle the /rdx/log request
     *
     * @param request The request
     *
     * @return The result
     *
     * @throws Exception On badness
     */
    public Result processLog(Request request) throws Exception {
        String        title = TITLE + " - Log";
        StringBuilder sb    = new StringBuilder();
        if ( !addHeader(request, sb, PATH_LOG)) {
            return new Result(title, sb);
        }
        if (instrumentMonitorStatus != null) {
            sb.append("Instrument monitor status: "
                      + instrumentMonitorStatus);
            sb.append("<br>");
        }
        if (notificationsMonitorStatus != null) {
            sb.append("Notifications monitor status: "
                      + notificationsMonitorStatus);
            sb.append("<br>");
        }
        sb.append(HtmlUtils.pre(Utils.join(log, "\n", true)));
        sb.append(HtmlUtils.sectionClose());

        return new Result(title, sb);
    }

    /**
     * Handle the  /rdx/settings request
     *
     * @param request The request
     *
     * @return The result
     *
     * @throws Exception On badness
     */
    public Result processSettings(Request request) throws Exception {
        String        title = TITLE + " - Settings";
        StringBuilder sb    = new StringBuilder();
        addHeader(request, sb, PATH_SETTINGS);
        sb.append(HtmlUtils.formTable());
        HtmlUtils.formEntries(sb, "Monitoring:", monitorInstruments
                ? "On"
                : "Off", "RDX DB scan interval:",
                         instrumentInterval + " minutes",
                         "Network threshold:", networkThreshold + " minutes",
                         "Data threshold:", dataThreshold + " minutes",
                         "LDM threshold:", ldmThreshold + " minutes",
                         "Email enabled:", getMailManager().isEmailEnabled()
                                           ? "Yes"
                                           : "No", "SMS enabled:",
                                           getMailManager().isSmsEnabled()
                                           ? "Yes"
                                           : "No", "# Emails to send:", notificationCountEmail, "Interval between emails:", notificationIntervalEmail
                                           + " minutes", "Interval until first SMS sent:", notificationFirstIntervalSms
                                               + " minutes", "Interval between SMS messages:", notificationIntervalSms
                                                   + " minutes", "# SMS messages to send:", notificationCountSms);
        sb.append(HtmlUtils.formTableClose());
        sb.append(HtmlUtils.sectionClose());

        return new Result(title, sb);
    }



    /**
     * Check the instruments
     *
     *
     * @param test test mode
     * @throws Exception on badness
     */
    private void checkInstruments(boolean test) throws Exception {
        List<InstrumentData> instruments = readInstruments();
        if (instruments == null) {
            return;
        }

        boolean store = false;
        Date    now   = new Date();
        if (timeSinceLastInstrumentStore != null) {
            int elapsedTime =
                (int) (now.getTime()
                       - timeSinceLastInstrumentStore.getTime()) / 1000 / 60;
            store = elapsedTime > storeInterval;
        }

        boolean addNotification = true;
        for (InstrumentData instrument : instruments) {
            checkInstrument(instrument, store, addNotification);
            if (test && addNotification) {
                List<RdxNotifications> notifications = getNotifications(null);
                if (notifications.size() > 0) {
                    addNotification = false;
                }
            }
        }
        if (store) {
            timeSinceLastInstrumentStore = now;
        }
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
        tmpRequest.put(ARG_TYPE, instrument.typeId);
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
     * Utility to the the minutes between the given dates
     *
     * @param now date 1
     * @param date date 2
     *
     * @return elapsed minutes
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
     * @param storeTimeseries Force storing the instrument state
     * @param addNotification Add a notification if needed
     *
     * @return true if instrument is out of date
     * @throws Exception On badness
     */
    private boolean checkInstrument(InstrumentData instrument,
                                    boolean storeTimeseries,
                                    boolean addNotification)
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

        //TODO: test for now
        storeTimeseries = true;

        if (storeInstrumentStatus && (storeTimeseries || changed)) {
            //            System.err.println("\tStoring instrument log");
            RdxInstrumentLogImpl.store(repository, entry);
        }

        //        System.err.println("checkInstrument:" + entry + " changed:" + changed);

        if ( !changed) {
            //if no change then done
            return false;
        }

        //Save the ramadda entry
        getEntryManager().updateEntry(getRepository().getTmpRequest(), entry);

        if ( !addNotification) {
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
                                   now, "", 0, 0 });
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
     * @param logSB for testing
     * @param to what was sent
     * @param sms send sms
     * @param msg the message
     * @param subject email subject
     *
     * @return how many messages were sent
     * @throws Exception On badness
     */
    private int sendNotification(Request request, Entry entry,
                                 StringBuilder logSB, StringBuilder to,
                                 boolean sms, String msg, String subject)
            throws Exception {

        boolean testMode = logSB != null;
        //TODO:
        //      testMode = true;
        //      if(testMode) return 1;

        GregorianCalendar cal = new GregorianCalendar(timeZone);
        cal.setTime(new Date());
        boolean weekend = (cal.get(cal.DAY_OF_WEEK) == cal.SUNDAY)
                          || (cal.get(cal.DAY_OF_WEEK) == cal.SATURDAY);

        List<Metadata> metadataList =
            getMetadataManager().findMetadata(request, entry,
                "rdx_notification", true);

        //Find valid notifcations
        if (metadataList != null) {
            List<Metadata> tmp = new ArrayList<Metadata>();
            for (Metadata metadata : metadataList) {
                String when = metadata.getAttr(4);
                if (when.equals("weekend") && !weekend) {
                    continue;
                }
                if (when.equals("weekdays") && weekend) {
                    continue;
                }
                boolean enabled = Misc.equals(metadata.getAttr(5), "true");
                if ( !enabled) {
                    continue;
                }
                tmp.add(metadata);
            }
            metadataList = tmp;
        }
        if ((metadataList == null) || (metadataList.size() == 0)) {
            log(logSB, "Warning: No notifications found");

            return 0;
        }

        int         cnt         = 0;
        MailManager mailManager = getRepository().getMailManager();
        if (sms) {
            if ( !mailManager.isSmsEnabled()) {
                if (testMode) {
                    cnt++;
                }
                to.append("sms not enabled\n");
                log(logSB, "Warning: SMS not enabled");

                return cnt;
            }
        } else {
            if ( !mailManager.isEmailEnabled()) {
                if (testMode) {
                    cnt++;
                }
                to.append("email not enabled\n");
                log(logSB, "Warning: Email not enabled");

                return cnt;
            }
        }

        for (Metadata metadata : metadataList) {
            String name  = metadata.getAttr1();
            String email = Utils.trim(metadata.getAttr2());
            String phone = Utils.trim(metadata.getAttr3());
            phone = phone.replaceAll("-", "").replaceAll(" ", "");
            log(logSB,
                "Notification:" + name + " email: " + email + " phone: "
                + phone);

            if (sms) {
                if (phone.length() == 0) {
                    to.append("no phone: " + name);

                    continue;
                }
                log(logSB, "Sending site status sms: " + phone);
                if (testMode) {
                    cnt++;
                    //              to.append("test sms to: " + name +" phone: " + phone+"\n");
                    //              continue;
                }

                if (mailManager.sendTextMessage(null, phone, msg)) {
                    to.append("SMS to: " + name + " phone: " + phone + "\n");
                    cnt++;
                } else {
                    to.append("Error: SMS failed sending to: " + phone
                              + "\n");
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
                    //              to.append("test email to: " + name +" email: " + email+"\n");
                    //              continue;
                }
                msg = msg.replaceAll("\n", "<br>");
                mailManager.sendEmail(email, subject, msg, true);
                to.append("Email to: " + name + " email: " + email + "\n");
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
    private static class InstrumentData {

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


}
