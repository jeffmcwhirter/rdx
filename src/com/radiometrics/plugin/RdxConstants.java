/*
* Copyright (c) 2020 Radiometrics Inc.
*
*/

package com.radiometrics.plugin;


/**
 * Holds constants
 */
public interface RdxConstants {

    /** page title prefix */
    public String TITLE = "Radiometrics NMPM";

    /** Id of page template */
    public String TEMPLATE_RADIOMETRICS = "radiometrics";

    /** lists instrument db. defined in api.xml */
    public String PATH_INSTRUMENTS = "/rdx/instruments";

    /** lists notifications. defined in api.xml */
    public String PATH_NOTIFICATIONS = "/rdx/notifications";

    /** lists settings. defined in api.xml */
    public String PATH_SETTINGS = "/rdx/settings";

    /** lists log. defined in api.xml */
    public String PATH_LOG = "/rdx/log";

    /** _more_ */
    public String PATH_SCHEMA = "/rdx/schema";

    /** randomly changes instruments. defined in api.xml */
    public String PATH_CHANGEINSTRUMENTS = "/rdx/changeinstruments";

    /** property id */
    public String PROP_TEST = "rdx.test";


    /** property id */
    public String PROP_MONITOR_INSTRUMENTS = "rdx.monitor.instruments";

    /** property id */
    public String PROP_MONITOR_NOTIFICATIONS = "rdx.monitor.notifications";

    /** URL arg */
    public String ARG_RANDOMIZE = "randomize";

    /** URL arg */
    public String ARG_DELETEALL = "deleteall";

    /** URL arg */
    public String ARG_TESTNOTIFICATIONS = "testnotifications";

    /** URL arg */
    public String ARG_DELETETIMESERIES = "deletetimeseries";

    /** URL arg */
    public String ARG_DELETE_ENTRY = "deleteentry";

    /** property id */
    public String PROP_RDX_TIMEZONE = "rdx.timezone";

    /** property for date format */
    public String PROP_DATEFORMAT = "rdx.dateformat";

    /** property id */
    public String PROP_THRESHOLD = "rdx.threshold";

    /** property id */
    public String PROP_THRESHOLD_NETWORK = "rdx.threshold.network";

    /** property id */
    public String PROP_THRESHOLD_DATA = "rdx.threshold.data";

    /** property id */
    public String PROP_THRESHOLD_LDM = "rdx.threshold.ldm";

    /** property id */
    public String PROP_INSTRUMENT_INTERVAL = "rdx.instruments.interval";

    /** property id */
    public String PROP_NOTIFICATIONS_INTERVAL = "rdx.notifications.interval";

    /** How many successive errors in the monitoring threads until we quit */
    public int MAX_ERRORS = 10;

    /** How many lines to keep in in memory log */
    public int LOG_SIZE = 500;

    /** property for logging flag */
    public String PROP_INSTRUMENT_LOG = "rdx.instrument.log";

    /** property for interval between logging */
    public String PROP_INSTRUMENT_LOG_INTERVAL =
        "rdx.instrument.log.interval";

    /** _more_ */
    public String PROP_INSTRUMENT_LOG_LASTTIME =
        "rdx.instrument.log.lasttime";


    /** defaults for wiki properties */
    public String DEFAULT_COLORS = "#008000,#FFFF00,#FF0000,#800080";

    /** defaults for wiki properties */
    public String DEFAULT_COLORSTEPS = "15,60,720";

    /** defaults for wiki properties */
    public String DEFAULT_COLORTABLELABELS =
        "0-15 minutes,15-60 minutes,1-12 hours,>12 hours";

    /** html space */
    public String SPACE = "&nbsp;&nbsp;";



}
