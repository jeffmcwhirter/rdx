-----------------------------------------------------------------------
--- The pending event notifications
-----------------------------------------------------------------------
CREATE TABLE rdx_notifications (entry_id varchar(200),
                  		   event_type varchar(200),
				   date ramadda.datetime);

CREATE TABLE rdx_instrument_status_log (
       entry_id varchar(200),
       date ramadda.datetime,
       instrument_id varchar(200),
       last_network_connection ramadda.datetime,
       last_data ramadda.datetime,
       network_up int,
       data_down int
);



--- alter table rdx_notifications add column description varchar(5000);

drop table rdx_test_instrument_status;

CREATE TABLE rdx_test_instrument_status (
       instrument_id varchar(200),
       type varchar(200),
       last_network_connection ramadda.datetime,
       last_data_time ramadda.datetime,
       network_up int,
       data_down int
);

