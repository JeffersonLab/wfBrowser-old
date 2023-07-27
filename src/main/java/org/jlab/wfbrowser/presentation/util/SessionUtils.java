package org.jlab.wfbrowser.presentation.util;

import org.jlab.wfbrowser.business.util.TimeUtil;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

public class SessionUtils {
    private static final Object LOCK = new Object(); // A global lock used for creating per-session mutexes.

    private SessionUtils() {}

    public static Pair<String, Instant> getGraphBegin(HttpServletRequest request, String beginString, Instant now) {
        Instant begin;
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

        synchronized (getSessionLock(request, null)) {
            HttpSession session = request.getSession();
            if (beginString != null && !beginString.isEmpty()) {
                TimeUtil.validateDateTimeString(beginString);
                begin = TimeUtil.getInstantFromDateTimeString(beginString);
                session.setAttribute("graphBegin", begin);
            } else if (session.getAttribute("graphBegin") != null) {
                begin = (Instant) session.getAttribute("graphBegin");
                beginString = dtf.format(begin);
            } else {
                begin = now.plus(-2, ChronoUnit.DAYS);
                session.setAttribute("graphBegin", begin);
                beginString = dtf.format(begin);
            }
        }

        return new Pair<>(beginString, begin);
    }

    public static Pair<String, Instant> getGraphEnd(HttpServletRequest request, String endString, Instant now) {
        Instant end;
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

        synchronized(getSessionLock(request, null)) {
            HttpSession session = request.getSession();
            if (endString != null && !endString.isEmpty()) {
                TimeUtil.validateDateTimeString(endString);
                end = TimeUtil.getInstantFromDateTimeString(endString);
                session.setAttribute("graphEnd", end);
            } else if (session.getAttribute("graphEnd") != null) {
                end = (Instant) session.getAttribute("graphEnd");
                endString = dtf.format(end);
            } else {
                end = now;
                session.setAttribute("graphEnd", end);
                endString = dtf.format(end);
            }
        }

        return new Pair<>(endString, end);
    }

    public static Object getSessionLock(HttpServletRequest request, String lockName) {
        if (lockName == null) { lockName = "SESSION_LOCK"; }
        Object result = request.getSession().getAttribute(lockName);
        if (result == null) {
            // No per-session lock found.  Time to make it.  Lock this section so we only make one with the given name.
            synchronized (LOCK) {
                result = request.getSession().getAttribute(lockName);
                // If multiple requests came at the same time, the first one through will find it's still null.  The
                // rest will see it has been created and have nothing to do.
                if (result == null) {
                    result = new Object();
                    request.getSession().setAttribute(lockName, result);
                }
            }
        }
        return result;
    }
}
