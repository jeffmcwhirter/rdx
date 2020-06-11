-----------------------------------------------------------------------
--SQL for RAMADDA's notifications and log of instrument status
-----------------------------------------------------------------------

-- The pending event notifications
DROP TABLE rdx_notifications;
CREATE TABLE rdx_notifications (entry_id varchar(200),
                       		   event_type varchar(200),
				   date ramadda.datetime,
				   testint int,
				   testdouble ramadda.double);

-- The time series of instrument status
DROP TABLE rdx_instrument_status_log;
CREATE TABLE rdx_instrument_status_log (
       entry_id varchar(200),
       date ramadda.datetime,
       instrument_id varchar(200),
       last_network_time ramadda.datetime,
       last_data_time ramadda.datetime,
       last_ldm_time ramadda.datetime
);


-- alter table rdx_notifications add column description varchar(5000);

--test table
DROP TABLE rdx_test_instrument_status;
CREATE TABLE rdx_test_instrument_status (
       instrument_id varchar(200),
       type varchar(200),
       last_network_time ramadda.datetime,
       last_data_time ramadda.datetime,
       last_ldm_time ramadda.datetime
);

