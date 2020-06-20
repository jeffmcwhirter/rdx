-----------------------------------------------------------------------
--SQL for RAMADDA's notifications and log of instrument status
-----------------------------------------------------------------------

--For testing
--DROP TABLE rdx_notifications;
--DROP TABLE rdx_instrument_log;

-- The pending event notifications
CREATE TABLE rdx_notifications (entry_id varchar(200),
			   start_date ramadda.datetime,
			   last_message_date ramadda.datetime,
			   send_to varchar(500),			   
			   number_emails int,
			   number_sms int);



-- The time series of instrument status
CREATE TABLE rdx_instrument_log (
       entry_id varchar(200),
       date ramadda.datetime,
       instrument_id varchar(200),
       elapsed_network_minutes int,
       elapsed_data_minutes int,
       elapsed_ldm_minutes int
);

CREATE INDEX rdx_instrument_log_entry_index ON rdx_instrument_log (entry_id);



-----------------------------------------------------------------------
--This is the schema that mimics RDX's instrument status database
-----------------------------------------------------------------------
DROP TABLE rdx_test_instrument_type;
CREATE TABLE rdx_test_instrument_type (
       type_id int,
       instrument_name varchar(200)
);

INSERT INTO rdx_test_instrument_type (type_id, instrument_name)
values  (1,'Radiometer'),
(2,'Wind Profiler'),
(3,'Sodar');


DROP TABLE rdx_test_instrument_metadata;
CREATE TABLE rdx_test_instrument_metadata (
      instrument_id int,
      site_id varchar(200),
      type_id int
);


INSERT INTO rdx_test_instrument_metadata (site_id, instrument_id, type_id)
values ('ALBNM',1,2),
('BLTMD',2,2),
('BLDRM',3,1),
('CCAFS',4,1),
('CDRWS',5,1),
('HASNE',6,1),
('HPLMD',7,2),
('LNGMT',8,1),
('LNGMT',9,2),
('NOROK',10,1),
('PNRMD',11,2),
('STICA',12,1),
('SNFLS',13,1),
('SJSFW',14,1),
('VNTRP',15,1),
('VCAPCD',16,2);


DROP TABLE rdx_test_instrument_data;
CREATE TABLE rdx_test_instrument_data (
       instrument_id int,
       last_network_time ramadda.datetime,
       last_data_time ramadda.datetime,
       last_ldm_time ramadda.datetime
);

INSERT INTO rdx_test_instrument_data (instrument_id, last_network_time,last_data_time, last_ldm_time) 
values 
(1,'2020-05-30 11:03:00','2020-05-30 11:03:00','2020-05-30 11:03:00'),
(2,'2020-05-30 11:03:00','2020-05-30 11:03:00','2020-05-30 11:03:00'),
(3,'2020-05-30 11:03:00','2020-05-30 11:03:00','2020-05-30 11:03:00'),
(4,'2020-05-30 11:03:00','2020-05-30 11:03:00','2020-05-30 11:03:00'),
(5,'2020-05-30 11:03:00','2020-05-30 11:03:00','2020-05-30 11:03:00'),
(6,'2020-05-30 11:03:00','2020-05-30 11:03:00','2020-05-30 11:03:00'),
(7,'2020-05-30 11:03:00','2020-05-30 11:03:00','2020-05-30 11:03:00'),
(8,'2020-05-30 11:03:00','2020-05-30 11:03:00','2020-05-30 11:03:00'),
(9,'2020-05-30 11:03:00','2020-05-30 11:03:00','2020-05-30 11:03:00'),
(10,'2020-05-30 11:03:00','2020-05-30 11:03:00','2020-05-30 11:03:00'),
(11,'2020-05-30 11:03:00','2020-05-30 11:03:00','2020-05-30 11:03:00'),
(12,'2020-05-30 11:03:00','2020-05-30 11:03:00','2020-05-30 11:03:00'),
(13,'2020-05-30 11:03:00','2020-05-30 11:03:00','2020-05-30 11:03:00'),
(14,'2020-05-30 11:03:00','2020-05-30 11:03:00','2020-05-30 11:03:00'),
(15,'2020-05-30 11:03:00','2020-05-30 11:03:00','2020-05-30 11:03:00'),
(16,'2020-05-30 11:03:00','2020-05-30 11:03:00','2020-05-30 11:03:00');

