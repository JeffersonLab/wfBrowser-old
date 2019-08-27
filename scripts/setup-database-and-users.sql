/* 
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
/**
 * Author:  adamc
 * Created: Aug 30, 2018
 */

/* No DROP USER IF EXISTS in this version! */

/*
###### For testing - i.e., JUnit, not the development server #########
DROP USER 'wftest_owner';
DROP USER 'wftest_writer';
DROP USER 'wftest_reader';
DROP DATABASE waveformstest;

CREATE DATABASE waveformstest CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE waveformstest;
*/

/*
*/
###### For production #########
DROP USER 'waveforms_owner';
DROP USER 'waveforms_writer';
DROP USER 'waveforms_reader';

CREATE DATABASE waveforms CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE waveforms;

/* THIS IS ALL THE SAME REGARDLESS OF TESTING VS. PRODUCTION */


/*
 Keep a list of systems that are allowed to store waveforms.  For now, there
 is just RF, but this system will likely grow to include others.  Also, makes
 it easy to track which systems are storing waveform data here.
 */
CREATE TABLE system_type (
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
CREATE TABLE event (
    event_id BIGINT NOT NULL AUTO_INCREMENT,
    event_time_utc datetime(1) NOT NULL,
    location varchar(23) NOT NULL,
    classification varchar(16) NOT NULL,
    system_id int(2) NOT NULL,
    archive tinyint(1) NOT NULL DEFAULT 0,
    to_be_deleted tinyint(1) NOT NULL DEFAULT 0,
    grouped tinyint(1) NOT NULL DEFAULT 0,
    PRIMARY KEY (event_id),
    UNIQUE KEY `event_time_utc` (`event_time_utc`,`location`,`system_id`, `classification`),
    INDEX i_location(location),
    INDEX i_event_time(event_time_utc),
    FOREIGN KEY fk_system_id (system_id) 
      REFERENCES system_type (system_id)
      ON DELETE CASCADE
) ENGINE=InnoDB;

/*
 This table is used to track which capture files map to an event and metadata about
 those capture files.
 sample_start  - first time value of the file
 sample_end  - last time value of the file
 sample_step - the difference in time between values (rows) of the file
 */
CREATE TABLE capture (
  capture_id bigint(20) NOT NULL AUTO_INCREMENT,
  event_id bigint(20) NOT NULL,
  filename varchar(63) NOT NULL,
  sample_start double NOT NULL,
  sample_end double NOT NULL,
  sample_step double NOT NULL,
  PRIMARY KEY (`capture_id`),
  UNIQUE KEY (`capture_id`, `filename`),
  UNIQUE KEY (`event_id`,`filename`),
  FOREIGN KEY fk_event_id (event_id)
    REFERENCES event (`event_id`) 
    ON DELETE CASCADE
) ENGINE=InnoDB;

/*
 This table is used to map waveforms to a capture file which maps back to an event.
 */
CREATE TABLE capture_wf (
  cwf_id bigint NOT NULL AUTO_INCREMENT,
  capture_id bigint NOT NULL,
  waveform_name varchar(63) NOT NULL,
  PRIMARY KEY (`cwf_id`),
  UNIQUE KEY `waveform_name` (`capture_id`,`waveform_name`),
  FOREIGN KEY fk_capture_id (capture_id)
    REFERENCES capture (`capture_id`)
    ON DELETE CASCADE
) ENGINE=InnoDB;


/*
 A table used to track PV metadata assocatied with capture files
 */
CREATE TABLE capture_meta (
  meta_id bigint(20) NOT NULL AUTO_INCREMENT,
  capture_id bigint(20) NOT NULL, 
  meta_name varchar(63) NOT NULL, 
  `type` ENUM('number', 'string', 'unarchived', 'unavailable') NOT NULL, 
  value varchar(63),
  offset DOUBLE,
  `start` DOUBLE,
  PRIMARY KEY (`meta_id`),
  UNIQUE KEY `cf_meta` (`capture_id`, `meta_name`, `offset`),
  FOREIGN KEY fk_capture_id_2 (capture_id)
    REFERENCES capture (`capture_id`)
    ON DELETE CASCADE
) ENGINE=InnoDB;

/*
 This table defines a tag as a name matched to a rule, where the rule should be
 a string containing metadata names and operators generating a boolean outcome
 when interpreted by an application, i.e., the tag should be applied or not.
 A null rule implies that the tag will be manually applied.
 */
CREATE TABLE tag (
  tag_id bigint NOT NULL AUTO_INCREMENT,
  name varchar(23) NOT NULL,
  rule varchar(255) DEFAULT NULL,
  set_rule ENUM('all', 'some', 'none') NOT NULL,
  PRIMARY KEY (`tag_id`)
) ENGINE=InnoDB;

/*
 This table is used to map a tag list to an event.
 */
CREATE TABLE event_tag (
  event_tag_id bigint NOT NULL AUTO_INCREMENT,
  event_id bigint NOT NULL,
  tag_id bigint NOT NULL,
  PRIMARY KEY (`event_tag_id`),
  UNIQUE KEY `event_tag` (`event_id`, `tag_id`),
  FOREIGN KEY fk_event_id_2 (event_id)
    REFERENCES event (`event_id`)
    ON DELETE CASCADE,
  FOREIGN KEY fk_tag_id (tag_id)
    REFERENCES tag (`tag_id`)
    ON DELETE CASCADE
) ENGINE=InnoDB;

/*
 This table holds the metadata read from the capture files.  Metadata should be
 key/value pairs with an associated time (offset from trigger time).  Null value
 would imply the metadata value could not be determine (e.g., PV unavailable).
 Values can be of three types, number, string, or null.  null implies that the
 value could not be determined, so it's type could not be determined.
 */
/*
CREATE TABLE capture_meta (
  meta_id bigint NOT NULL AUTO_INCREMENT,
  capture_id bigint NOT NULL,
  name varchar(23) NOT NULL, 
  value varchar(255) DEFAULT NULL,
  offset_seconds DOUBLE NOT NULL,
  class ENUM('number', 'string') DEFAULT NULL,
  PRIMARY KEY (`meta_id`),
  UNIQUE KEY `name` (`capture_id`, `name`),
  FOREIGN KEY fk_capture_id (capture_id)
    REFERENCES capture (`capture_id`)
    ON DELETE CASCADE
) ENGINE=InnoDB;
*/

/*
 This table is used to define sets of metadata filters.  These can be used to
 select events based on metadata.
 modifier - return events that have all, some, or no(ne) capture files passing
            the metadata filter set
 */
/*
CREATE TABLE meta_filter_set (
  mf_set_id bigint NOT NULL AUTO_INCREMENT,
  system_id int(2) NOT NULL,
  modifier  ENUM('all','some','none') NOT NULL,
  PRIMARY KEY (`mf_set_id`),
  FOREIGN KEY fk_system_id (system_id)
    REFERENCES system_type (system_id)
    ON DELETE CASCADE
) ENGINE=InnoDB;
*/

/*
 This table defines individual metadata filters.
 key       - value of the capture_meta field to filter on
 operation - logical operation to perform on the value corresponding to key
 target    - value upon which to compare the value corresponding to key
 */
/*
CREATE TABLE meta_filter (
  mf_id bigint NOT NULL AUTO_INCREMENT,
  mf_set_id bigint NOT NULL,
  name varchar(255) NOT NULL,
  operation varchar(15) NOT NULL,
  target varchar(255) DEFAULT NULL,
  PRIMARY KEY (`mf_id`),
  FOREIGN KEY fk_mf_set_id (mf_set_id)
    REFERENCES meta_filter_set (`mf_set_id`)
    ON DELETE CASCADE
) ENGINE=InnoDB;
*/

/*
 This set of tables holds the rules for looking up event series data by a name to 
 pattern matching routine.  This holds the patterns that are used to match a generic
 series name "GMES" to a specific series 'R1N1WFSGMES'
 */
CREATE TABLE series (
  series_id BIGINT NOT NULL AUTO_INCREMENT,
  system_id INT(2) NOT NULL,
  pattern VARCHAR(255) NOT NULL,
  series_name VARCHAR(127) NOT NULL,
  units VARCHAR(23) NULL,
  description varchar(2047)  DEFAULT NULL,
  UNIQUE KEY `series_name` (series_name),
  INDEX i_series_name(series_name),
  PRIMARY KEY (series_id),
  FOREIGN KEY fk_system_id_2 (system_id)
    REFERENCES system_type (system_id)
    ON DELETE CASCADE
) ENGINE=InnoDB;


/* This holds the list of named sets of series that a client may want to view together. */
CREATE TABLE series_sets (
  set_id BIGINT NOT NULL AUTO_INCREMENT,
  system_id INT(2) NOT NULL,
  set_name VARCHAR(127) NOT NULL,
  description varchar(2047)  DEFAULT NULL,
  UNIQUE KEY `set_name` (set_name),
  INDEX i_set_name (set_name),
  PRIMARY KEY (set_id),
  FOREIGN KEY fk_system_id_3 (system_id)
    REFERENCES system_type (system_id)
    ON DELETE CASCADE
) ENGINE=InnoDB;

/* This is the lookup table of which named series are in a given series set. */
CREATE TABLE series_set_contents (
  content_id BIGINT NOT NULL AUTO_INCREMENT,
  series_id BIGINT NOT NULL,
  set_id BIGINT NOT NULL,
  INDEX i_set_id (set_id),
  PRIMARY KEY (content_id),
  FOREIGN KEY fk_series_id (series_id)
    REFERENCES series (series_id)
    ON DELETE CASCADE,
  FOREIGN KEY fk_set_id (set_id)
    REFERENCES series_sets (set_id)
    ON DELETE CASCADE
) ENGINE=InnoDB;


/*
 * Create the usual three user setup for this app wfb_owner, wfb_writer,
 * wfb_reader, (unlimited, read/write, and read only users)
 * Please change passwords.
 */

/*
*/
####### FOR PRODUCTION -- update passwords ########

CREATE USER 'waveforms_owner' IDENTIFIED BY 'password';
GRANT ALL PRIVILEGES ON waveforms.* TO 'waveforms_owner';
CREATE USER 'waveforms_writer' IDENTIFIED BY 'password';
GRANT SELECT,UPDATE,INSERT,DELETE ON waveforms.* to 'waveforms_writer';
CREATE USER 'waveforms_reader' IDENTIFIED BY 'password';
GRANT SELECT ON waveforms.* TO 'waveforms_reader';

INSERT INTO system_type (system_name) VALUES ('rf') ;
INSERT INTO series (system_id, pattern, series_name, description) VALUES(1, 'R%GSET', 'GSET', 'All cavities\' GSET');


/*
###### FOR TESTING ########
Add a "system" for testing and some data in addition to the users

CREATE USER 'wftest_owner' IDENTIFIED BY 'password';
GRANT ALL PRIVILEGES ON waveformstest.* TO 'wftest_owner';
CREATE USER 'wftest_writer' IDENTIFIED BY 'password';
GRANT SELECT,UPDATE,INSERT,DELETE ON waveformstest.* to 'wftest_writer';
CREATE USER 'wftest_reader' IDENTIFIED BY 'password';
GRANT SELECT ON waveformstest.* TO 'wftest_reader';

INSERT INTO system_type (system_name) VALUES ('test');

INSERT INTO series (system_id, pattern, series_name, description) VALUES(1, 't%', 'Test Series - All', 'Should match all of the test waveforms');
INSERT INTO series (system_id, pattern, series_name, description) VALUES(1, 't1%1', 'Test Series - test1', 'Should match test1');
*/

