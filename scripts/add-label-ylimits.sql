/*
 * This adds two columns to the series table that can optional default axis limits.
 * If NULL, the plotting code should determine a the axis limits.
 *
 * This was added to in version 1.6, and the setup-database-and-user.sql script was updated to include them
 * in that version as well.
 */

ALTER TABLE waveforms.series ADD ymin DOUBLE DEFAULT NULL;
ALTER TABLE waveforms.series ADD ymax DOUBLE DEFAULT NULL;