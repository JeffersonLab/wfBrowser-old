script_version = "1.1.1"

suppressMessages(library(htmlwidgets))
suppressMessages(library(htmltools))
suppressMessages(library(reshape2))
suppressMessages(library(ggplot2))
suppressMessages(library(dygraphs))
options(warn = 1)

# Multiple plot function
#
# ggplot objects can be passed in ..., or to plotlist (as a list of ggplot objects)
# - cols:   Number of columns in layout
# - layout: A matrix specifying the layout. If present, 'cols' is ignored.
#
# If the layout is something like matrix(c(1,2,3,3), nrow=2, byrow=TRUE),
# then plot 1 will go in the upper left, 2 will go in the upper right, and
# 3 will go all the way across the bottom.
#
# If title is supplied, then an extra row is generated in the viewport layout
# where the title will be placed.
#
multiplot <- function(..., plotlist=NULL, file, cols=1, layout=NULL, title=NULL) {
  library(grid)
  
  # Make a list from the ... arguments and plotlist
  plots <- c(list(...), plotlist)
  
  numPlots = length(plots)
  
  # If layout is NULL, then use 'cols' to determine layout
  if (is.null(layout)) {
    # Make the panel
    # ncol: Number of columns of plots
    # nrow: Number of rows needed, calculated from # of cols
    if (is.null(title)) {
      layout <- matrix(seq(1, cols * ceiling(numPlots/cols)),
                       ncol = cols, nrow = ceiling(numPlots/cols))
    } else {
      # Add an empty row to allow a title
      layout <- matrix(seq(1, cols * ceiling(numPlots/cols)+1),
                       ncol = cols, nrow = ceiling(numPlots/cols)+1)  
    }
  }
  
  # Set up the page
  grid.newpage()
  if (is.null(title)) {
    pushViewport(viewport(layout = grid.layout(nrow(layout), ncol(layout))))
  } else {
    pushViewport(viewport(layout = grid.layout(nrow(layout), ncol(layout), heights = c(0.5, rep(5,numPlots)))))
  }
  
  # If there is a title, add an extra row and change the for loop range
  plotIndex <- 1:numPlots
  offset <- 0
  if (! is.null(title)) {
    plotIndex <- plotIndex + cols
    offset <- cols
    grid.text(title, vp = viewport(layout.pos.row = 1, layout.pos.col = 1:cols))
  }
  
  # Make each plot, in the correct location
  for (i in plotIndex) {
    # Get the i,j matrix positions of the regions that contain this subplot
    matchidx <- as.data.frame(which(layout == i, arr.ind = TRUE))
    
    print(plots[[i-offset]], vp = viewport(layout.pos.row = matchidx$row,
                                           layout.pos.col = matchidx$col))
  }
}


# This generates fake data that can be used with the plot generation functions
generate_data <- function(n) {
  x=1:n
  Time = rep(x, 8)
  
  # Setup different data series
  Cavity <- c(rep("cav1", length(x)), rep("cav2", length(x)), rep("cav3", length(x)), rep("cav4", length(x)),
              rep("cav5", length(x)), rep("cav6", length(x)), rep("cav7", length(x)), rep("cav8", length(x)))
  GMES <- c()
  DETA2 <- c()
  FWDP <- c()
  for(i in 1:8) {
    GMES  <- c(GMES,  runif(1)*10 + runif(length(x))*0.5)
    DETA2 <- c(DETA2, runif(1)*180 + runif(length(x))*5)
    FWDP  <- c(FWDP,  runif(1)*3000 + runif(length(x))*20)
  }
  
  # Combine in data structure and return
  data.frame(Time, GMES, DETA2, FWDP, Cavity)
}


# This function takes a the path to a directory containing a set of cavity waveform data
# files.  Each file is read in to dataframe and a cavity id added.  These are stored together
# in a long form dataframe, then returned as a wide format that is used elsewhere.  These
# conversions from wide to long to wide formats should take care of any differences in
# waveform column order or non-standard waveform sets.
get_wf_data <- function(dirpath) {
  files <- list.files(dirpath)
  
  
  waveforms <- data.frame()
  for (file in files) {
    # Parse information about the data we're reading
    zone <- substring(text = file, first = 1, last = 3)
    cavity <- substring(text = file, first = 1, last = 4)
    prefix <- paste(cavity[1], "WFS", sep="")
    
    # It's possible that there are duplicate waveforms for a cavity.  The file names
    # are listed in alphabetical order.  Since the files are well named, this maps
    # to sorting on zone, then chronological order within a zone.  E.g.,
    # R2Q1WFSharv.2018_04_26_142850.3.txt
    # R2Q2WFSharv.2018_04_26_142850.8.txt
    # R2Q3WFSharv.2018_04_26_142850.1.txt
    # R2Q3WFSharv.2018_04_26_142853.0.txt
    # ...
    if (cavity %in% waveforms$Cavity) next
    
    # Get and format the data
    wf <- read.csv(file = file.path(dirpath, file), sep = "\t", header = TRUE)
    names(wf) <- gsub(x = names(wf), pattern = prefix, replacement = "")
    wf <- cbind(wf, Cavity=rep(cavity, dim(wf)[1]))
    
    # Update the waveform data.  Use the melt command to put the data in long format
    # where columns are Time, Cavity, variable, value.  Here cavities can easily have
    # different sets of waveforms and the column order on the file doesn't need to 
    # match.  Later we cast it back to a wide format where NAs are autofilled and the
    # order is set to be the same for all cavities.
    # to a wide format an
    waveforms <- rbind(waveforms, melt(wf, id=c("Time", "Cavity")))
  }
  
  # Return the waveform data after casting it to a wide format.  Manually set the factor levels
  # so that all cavities will have a consistent color scheme, even if some cavities are not
  # present in the data
  waveforms <- dcast(waveforms, Time + Cavity ~ variable)
  waveforms$Cavity <- factor(waveforms$Cavity, levels = paste(zone, 1:8, sep=""))
  waveforms
}


# This function generates a combination plot using ggplot from the supplied data.
# It expects data to be in a wide format dataframe (as generated by get_wf_data).
# Each column specified in signals is plotted against "Time" in it's own subplot
# with a series per cavity that has non NA data available for that signal.  One
# downside from this approach is that should a single NA appear in a signal, the
# gap will not be present in the plot.  However, I have been informed that this
# should not happen which means the only NAs to appear will be because a cavity
# had NO values for that waveform since it wasn't included in its data file.
# Which means that cavity without data will be completely excluded from the plot,
# accurately representing the state of the data.
generate_plot_ggplot2 <- function(data, signals, title = NULL, filename = NULL) {
  colors = c("#7FC97F", "#BEAED4", "#FDC086", "#000000", "#386CB0", "#F0027F", "#BF5B17", "#666666")
  height = max(650, 1000 / length(signals))
  plots = list()
  i = 1
  for (s in signals) {
    # NaN is actual data from the harvester (floating point NaN), while NA is from the data processing that indicates
    # the signal didn't exist for that cavity.  Since we're only ploting a single signal at a time and a row is for
    # a single cavity, we can safely subset out any rows where the desired signal has an NA.  Note: is.na returns true
    # for NaN as well so we need to check that is.na = F or is.nan = T (logically equivalent to NOT(is.na=T and is.nan=F) )    
    dat <- subset(data, (!is.na(data[,s]) | is.nan(data[,s])))
    p <- ggplot() + 
      geom_line(data = dat, aes_string(x = "Time", y= s, group="Cavity", color="Cavity")) +
      scale_colour_manual(values = colors[as.numeric(unique(dat$Cavity))])
    plots[[i]] = p
    i = i + 1
  }
  if ( ! is.null(filename) ) {
    png(filename=filename, height=height, width=1000, units="px")
    multiplot(plotlist=plots, title=title)
    dev.off()
  } else {
    multiplot(plotlist=plots, title=title)
  }
}


# This generates interactive HTML charts and optionally saves them to a self contained html file.
generate_plot_dygraphs <- function(data, signals, xlabels = c(rep("", length(signals) - 1), "Time (ms)"), ylabels = NULL, title = NULL,
                                   filename = NULL, group = NULL, data_url = NULL, main_libdir) {
  if ( !is.null(xlabels) && (length(xlabels) != length(signals)) ) {
    stop("Error: xlabels and signals have different lengths")
  }
  if ( !is.null(ylabels) && (length(ylabels) != length(signals)) ) {
    stop("Error: ylabels and signals have different lengths")
  }
  
  #colors <- c("#7FC97F", "#BEAED4", "#FDC086", "#000000", "#386CB0", "#F0027F", "#BF5B17", "#666666")
  #colors <- c('#1b9e77','#d95f02','#7570b3','#e7298a','#66a61e','#e6ab02','#a6761d','#666666')  
  #colors <- c('#0a8d66','#c84e01','#6460a2','#d61879','#55950d','#d59a01','#95650c','#555555')
  colors <- c('#0000ff','#c84e01','#6460a2','#d61879','#55950d','#d59a01','#95650c','#000000')
  height <- max(200, 800 / length(signals))
  width <- 1000
  plots <- list()
  
  i <- 1
  for (s in signals) {
    
    # Get the ylabels
    ylab <- s
    if ( !is.null(ylabels) && ylabels[s == signals] != "" ) {
      ylab <- ylabels[s == signals]
    }
    xlab <- ""
    if ( !is.null(xlabels) ) {
      xlab <- xlabels[s == signals]
    }
    
    # NaN is actual data from the harvester (floating point NaN), while NA is from the data processing that indicates
    # the signal didn't exist for that cavity.  Since we're only ploting a single signal at a time and a row is for
    # a single cavity, we can safely subset out any rows where the desired signal has an NA.  Note: is.na returns true
    # for NaN as well so we need to check that is.na = F or is.nan = T (logically equivalent to NOT(is.na=T and is.nan=F) )    
    dat <- subset(data, (!is.na(data[,s]) | is.nan(data[,s])))
    dat <- dcast(melt(dat[,c("Time", "Cavity", s)], id = c("Time", "Cavity")), Time ~ Cavity)
    
    chart_id = paste(s, "chart", sep="-")
    legend_id = paste(s, "legend", sep="-")
    
    p <- dygraph(dat, height = height, group = group, xlab = xlab, ylab = ylab) %>%
      dyHighlight(hideOnMouseOut = TRUE, highlightSeriesBackgroundAlpha = 0.4, highlightSeriesOpts = list(stroke = "2")) %>%
      dyOptions(colors = colors[as.numeric(unique(data$Cavity))], animatedZooms = TRUE) %>%
      dyLegend(show = "always", labelsSeparateLines = TRUE, labelsDiv = legend_id)
    
    plots[[i]] <- div(style = "position: relative; width: 100%;",
                      p,
                      div(id = legend_id, style = "position: absolute; top: 0; left: 980px; min-width = 8em;")
    )
    i <- i + 1
  }
  
  # When a series is highlighted, it's entry in the legend gets a .highlight class.
  legend_hl <- tags$style("
.highlight {
  display: inline;
  background-color: #d0d0d0; 
}")
  
  title_tag <- tags$h2(title)
  if ( !is.null(data_url)) {
    title_tag <- tags$h2(title, tags$a("Data", href = data_url))
  }
  to_render <- tagList(tags$head(legend_hl),
                       title_tag,
                       tags$p("(Double-click to unzoom)"),
                       tags$div(id = "nav"), 
                       plots)
  
  if (is.null(filename)) {
    htmltools::browsable(to_render)
  } else {
    # The save_html process.  Generates the HTML file, and dependency libraries.  This will overwrite the lib directory if it
    # (or a symlink pointing to it) exists.  So we have to remove the symlink, let a local lib dir be created, then remove
    # the local lib dir and replace it with the symlink back to the single centralized lib dir.
    #
    # This is possible point of contention if multiple events for the same zone are triggered back to back.  The an event
    # could be triggered every three seconds and this script runs for much longer than that.  I implemented a simplistic
    # lockfile logic around this code since the odds of collision are small and this code won't be in use for long.  A better
    # solution would be to use a third party lockfile library, but this was quick.
    
    # Figure out where the local lib dir will be created.
    fileparts <- strsplit(filename, "/")[[1]]
    fileparts <- fileparts[1:(length(fileparts)-1)]
    chart_dir <- paste(fileparts, collapse = "/")
    local_libdir <- paste(chart_dir, "lib", sep="/")
    
    # Check for a lockfile.  If it doesn't exist, make one, if it does sleep for a at most 10 seconds before charging on.
    lockfile <- paste(chart_dir, 'lockfile', sep="/")
    i <- 0
    while(file.exists(lockfile) & i < 10) {
      Sys.sleep(1)
      i <- i + 1
    }
    if (file.exists(lockfile)) {
      cat("Warning: Forcibly removing lockfile and continuing.")
      file.remove(lockfile)
    }
    file.create(lockfile)
    
    # If something exists where the local lib dir will be created it is probably a symlink back to the central lib dir.  Remove
    # this so save_html doesn't mess with the central copy.  Don't make this recursive in case this is the symlink to the main
    # dir.
    if ( file.exists(local_libdir) ) {
      file.remove(local_libdir)
    }

    # Save the html files containing the dygraph charts, etc.  This creates a local lib dir with the html's dependencies
    # (mostly JS files)
    save_html(to_render, file = filename)

    # Remove the local lib dir and replace it with a symlink to the main/central lib dir.  This may create the central lib dir
    # if it does not already exist.
    centralize_libdir(main = main_libdir, local = local_libdir)

    # Clear out the lockfile now that we're done    
    if (file.exists(lockfile)) {
      file.remove(lockfile)
    }
    
  }
}

# Print out the current version of this script
print_version <- function() {
  write(paste("wfGrapher.R v", script_version, sep=""), stdout())
}


# Print out a usage message
usage <- function() {
  print_version()
  write("This script is designed to be called by wfGrapher.pl and should not normally be run manually", stdout())
  write("usage: wfGrapher.R <waveform data path> <view directory path> <config dir path>", stdout())
  write("example: wfGrapher.R /usr/opsdata/waveforms/data/rf/2L26/2018_04_21/001724/ /usr/opsdata/waveforms/view/rf/2L26/2018_04_21/ /cs/certified/apps/harvester/2.0/cfg/", stdout())
}


# Copy library dependencies to a centralized location and leave a symlink behind
centralize_libdir <- function(main, local) {
  # Create the main lib dir
  if (!file.exists(main)) {
    dir.create(main)
    file.copy(to = file.path(main, ".."), from = local, recursive = TRUE)
  }
  unlink(local, recursive = TRUE)
  file.symlink(to = local, from = main)
}

# This function creates a series of charts based on 'data' and saves them to a file.  
# Signals not included in 'data' will be excluded from the plots.  The ylabels will 
# either be the raw signal names or the string in the list signal_labels whose list 
# element name matches the signal.
#
# data - a data.frame of the format generated by get_wf_data
#
# graph_list - a list where each element's name is the directory name where the chart file
# will be saved relative to viewpath.  Each list element is a vector of signal names to be
# plotted in that chart.
#
# viewpath - string that is the path to the "date" directory under waveform/view directory
create_graphs <- function(data, graph_list, viewpath, signal_labels = signal_labels, data_url = NULL,
                          main_libdir) {
  for (i in 1:length(graph_list)) {
    
    name <- names(graph_list)[i]
    signals <- graph_list[[i]]
    signals <- signals[signals %in% names(dat)]

    # In case any of the signals haven't been added to the signal_labels config array
    # add them with their label being the signal
    missing_labels <- signals[!(signals %in% names(signal_labels))]
    if (length(missing_labels) > 0) {
      temp <- rbind(data.frame(), missing_labels)
      names(temp) <- missing_labels
      signal_labels <- cbind(signal_labels, temp)
    }

    ylabels <- apply(as.data.frame(signal_labels[, signals]), MARGIN = 2, FUN = as.character)
    names(ylabels) <- NULL

    # Make the signal directory if needed
    chart_dir = file.path(viewpath, name)
    if (!file.exists(chart_dir)) {
      dir.create(chart_dir)
    }
    
    filename <- paste(zone, name, date, time, sep = "-")
    # local_libdir <- file.path(chart_dir, "lib")
    # if ( file.exists(local_libdir) ) {
    #   file.remove(local_libdir)
    # }
    generate_plot_dygraphs(data = dat, signals = signals, title = title, group = "1", data_url = data_url, ylabels = ylabels,
                           filename = file.path(chart_dir, paste(filename, "html", sep=".")), main_libdir = main_libdir)
#    centralize_libdir(main = main_libdir, local = local_libdir)
    
    if ( !file.exists(file.path(chart_dir, "index.php")) ) {
      curr_dir <- getwd()
      setwd(chart_dir)
      file.symlink(to = file.path(chart_dir, "index.php"), from = "../../../../bin/index.php")
      setwd(curr_dir)
    }
  }
}

### MAIN ROUTINE ####

args <- commandArgs(trailingOnly=TRUE)
path <- strsplit(args[1], "/")
# Should be at least four parts long (system, zone, date, time)
path <- path[[1]][nchar(path) > 0]

if ( length(args) == 0 ) {
  print_version()
  quit(save = "no", status = 0, runLast = FALSE)
}

if ( length(args) == 1 && args[1] == "-h" ) {
  usage()
  quit(save = "no", status = 0, runLast = FALSE)
}

if ( length(args) != 3) {
  usage()
  quit(save = "no", status = 10, runLast = FALSE)
}

# Split out arguments
datapath <- args[1]
viewpath <- args[2]
confpath <- args[3]
time  <- path[length(path)]
date    <- path[length(path)-1]
zone    <- path[length(path)-2]
system    <- path[length(path)-3]
main_libdir <- file.path(viewpath, "../../../lib")


title <- paste(zone, 
               gsub(pattern = "_", x = date, "-"),
               gsub("(\\d{2})(?=\\d{2})", "\\1:", time, perl = TRUE),
               sep = "  ")

# Get the waveform data
dat <- get_wf_data(datapath)

# Create the directories for holding images
if (!file.exists(viewpath)) {
  dir.create(viewpath)
}

# By default we make a graph for each individual signal on it  
graph_defs = as.list(names(dat)[!names(dat) %in% c("Time", "Cavity")])
names(graph_defs) = as.list(names(dat)[!names(dat) %in% c("Time", "Cavity")])

config = file.path(confpath,"wfGrapher1.1.cfg")
if (file.exists(config)) {
  source(config)
}

# A customGraphs list can be defined in the config file.  If so, merge it in
# the list of graphs we're going to make.
if (!is.null(customGraphs)) {
  graph_defs <- c(graph_defs, customGraphs)
}
signal_labels <- if(is.null(signal_labels)) {data.frame()} else {signal_labels}

# Create a the link where the waveform data files can be downloaded from.  data_url_base should
# be defined in the config file
data_url <- NULL
if ( !is.null(data_url_base) ) {
  data_url <- paste(data_url_base, system, zone, date, time, sep = "/")
}
create_graphs(data = dat, graph_list = graph_defs, viewpath = viewpath, main_libdir = main_libdir,
              signal_labels = signal_labels, data_url = data_url)
