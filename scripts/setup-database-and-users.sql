/* 
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
/**
 * Author:  adamc
 * Created: Aug 30, 2018
 */

CREATE DATABASE waveforms CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

/*
 * Keep a list of systems that are allowed to store waveforms.  For now, there is just RF, but this system will likely grow to
 * include others.  Also, makes it easy to track which systems are storing waveform data here.
 */
CREATE TABLE waveforms.system_type (
    system_id int(2) NOT NULL AUTO_INCREMENT PRIMARY KEY,
    system_name varchar(16) NOT NULL
) ENGINE=InnoDB;

/*
 * This table keeps track of the high level "save waveform" trigger events.  Individual waveforms may be grouped together within
 * a single event.  Each event is currently defined to occur within a single system.  The location field offers a simple way to
 * group events, and can be used flexibly.  In the case of RF it will be the zone, but other systems may have different location schemes.
 */
CREATE TABLE waveforms.event (
    event_id int(15) NOT NULL AUTO_INCREMENT,
    event_time_utc datetime(6) NOT NULL,
    location varchar(10) NOT NULL,
    system_id int(2) NOT NULL,
    archive tinyint(1) DEFAULT NULL,
    PRIMARY KEY (event_id),
    INDEX i_location(location),
    INDEX i_event_time(event_time),
    FOREIGN KEY fk_system_id (system_id) 
      REFERENCES waveforms.system_type (system_id)
      ON DELETE CASCADE
) ENGINE=InnoDB;

/*
 * This is the main data table.  Each waveform is made up of many (currently 8000) points defined by a value and an time offset
 * from the save waveform trigger.  Currently (Aug 2018) these offsets are only synchronized within an RF zone, but that may change
 *
 * Currently the only events we have to size against are RF which dumps a zone's worth of waveform data.  This is around
 * 8 cavities * 8000 datapoints/waveform * ~10 waveforms = 640,000 points per event.  Since we're sizing for 10^15 events,
* 640,000 * 10^15 = 6.4 * 10^20.  Just go with 10^20 since 10^15 is already outlandishly large for what we're doing.
 */
CREATE TABLE waveforms.data (
    data_id int(20) NOT NULL AUTO_INCREMENT,
    event_id int(15) NOT NULL,
    series_name varchar(24) NOT NULL,
    time_offset double NOT NULL,
    val double NOT NULL,
    PRIMARY KEY (data_id),
    FOREIGN KEY fk_event_id (event_id)
        REFERENCES waveforms.event (event_id)
        ON DELETE CASCADE
) ENGINE=InnoDB;

/*
 * Create the usual three user setup for this app wfb_owner, wfb_writer, wfb_reader, (unlimited, read/write, and read only users)
 * Please change passwords.
 */
CREATE USER 'wfb_owner' IDENTIFIED BY 'password';
GRANT ALL PRIVILEGES ON waveforms.* TO 'wfb_owner';
CREATE USER wfb_writer IDENTIFIED BY 'password';
GRANT SELECT,UPDATE,INSERT,DELETE ON waveforms.* to 'wfb_writer';
CREATE USER wfb_reader IDENTIFIED BY 'password';
GRANT SELECT ON waveforms.* TO 'wfb_reader';
