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
 Keep a list of systems that are allowed to store waveforms.  For now, there
 is just RF, but this system will likely grow to include others.  Also, makes
 it easy to track which systems are storing waveform data here.
 */
CREATE TABLE waveforms.system_type (
    system_id int(2) NOT NULL AUTO_INCREMENT PRIMARY KEY,
    system_name varchar(16) NOT NULL UNIQUE
) ENGINE=InnoDB;

/*
 This table keeps track of the high level "save waveform" trigger events.
 Individual waveforms may be grouped together within a single event.  
 Each event is currently defined to occur within a single system.  The 
 location field offers a simple way to group events, and can be used flexibly.
 In the case of RF it will be the zone, but other systems may have different 
 location schemes.
 */
CREATE TABLE waveforms.event (
    event_id BIGINT NOT NULL AUTO_INCREMENT,
    event_time_utc datetime(1) NOT NULL,
    location varchar(10) NOT NULL,
    system_id int(2) NOT NULL,
    archive tinyint(1) NOT NULL DEFAULT 0,
    to_be_deleted tinyint(1) NOT NULL DEFAULT 0,
    PRIMARY KEY (event_id),
    UNIQUE KEY `event_time_utc` (`event_time_utc`,`location`,`system_id`, `classification`),
    INDEX i_location(location),
    INDEX i_event_time(event_time_utc),
    FOREIGN KEY fk_system_id (system_id) 
      REFERENCES waveforms.system_type (system_id)
      ON DELETE CASCADE
) ENGINE=InnoDB;

/*
 This table is used to track which waveforms an event contains.  Enables easy per 
 series lookup for UI.
 */
CREATE TABLE waveforms.event_waveforms (
  `es_id` bigint(20) NOT NULL AUTO_INCREMENT,
  `event_id` bigint(20) NOT NULL,
  `waveform_name` varchar(47) NOT NULL,
  PRIMARY KEY (`es_id`),
  UNIQUE KEY `waveform_name` (`event_id`,`waveform_name`),
  INDEX i_waveform_name (waveform_name),
  FOREIGN KEY fk_event_id (event_id)
    REFERENCES waveforms.event (`event_id`) 
    ON DELETE CASCADE
) ENGINE=InnoDB;


/*
 This set of tables holds the rules for looking up event series data by a name to 
 pattern matching routine.  This holds the patterns that are used to match a generic
 series name "GMES" to a specific series 'R1N1WFSGMES'
 */
CREATE TABLE waveforms.series (
series_id BIGINT NOT NULL AUTO_INCREMENT,
system_id INT(2) NOT NULL,
pattern VARCHAR(255) NOT NULL,
series_name VARCHAR(127) NOT NULL,
description varchar(2047)  DEFAULT NULL,
UNIQUE KEY `series_name` (series_name),
INDEX i_series_name(series_name),
PRIMARY KEY (series_id),
FOREIGN KEY fk_system_id (system_id)
    REFERENCES waveforms.system_type (system_id)
    ON DELETE CASCADE
) ENGINE=InnoDB;

/* This holds the list of named sets of series that a client may want to view together. */
CREATE TABLE waveforms.series_sets (
set_id BIGINT NOT NULL AUTO_INCREMENT,
system_id INT(2) NOT NULL,
set_name VARCHAR(127) NOT NULL,
description varchar(2047)  DEFAULT NULL,
UNIQUE KEY `set_name` (set_name),
INDEX i_set_name (set_name),
PRIMARY KEY (set_id),
FOREIGN KEY fk_system_id (system_id)
    REFERENCES waveforms.system_type (system_id)
    ON DELETE CASCADE
) ENGINE=InnoDB;

/* This is the lookup table of which named series are in a given series set. */
CREATE TABLE waveforms.series_set_contents (
content_id BIGINT NOT NULL AUTO_INCREMENT,
series_id BIGINT NOT NULL,
set_id BIGINT NOT NULL,
INDEX i_set_id (set_id),
PRIMARY KEY (content_id),
FOREIGN KEY fk_series_id (series_id)
    REFERENCES waveforms.series (series_id)
    ON DELETE CASCADE,
FOREIGN KEY fk_set_id (set_id)
    REFERENCES waveforms.series_sets (set_id)
    ON DELETE CASCADE
) ENGINE=InnoDB;


/*
 * Create the usual three user setup for this app wfb_owner, wfb_writer,
 * wfb_reader, (unlimited, read/write, and read only users)
 * Please change passwords.
 */
CREATE USER 'waveforms_owner' IDENTIFIED BY 'password';
GRANT ALL PRIVILEGES ON waveforms.* TO 'waveforms_owner';
CREATE USER 'waveforms_writer' IDENTIFIED BY 'passowrd';
GRANT SELECT,UPDATE,INSERT,DELETE ON waveforms.* to 'waveforms_writer';
CREATE USER 'waveforms_reader' IDENTIFIED BY 'password';
GRANT SELECT ON waveforms.* TO 'waveforms_reader';
