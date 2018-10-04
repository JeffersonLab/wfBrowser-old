/* 
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

var jlab = jlab || {};

jlab.dateToDateString = function (x) {
    var year = x.getFullYear(),
            month = x.getMonth() + 1,
            day = x.getDate(),
            hour = x.getHours(),
            minute = x.getMinutes();

    return year + '-' + jlab.pad(month, 2) + '-' + jlab.pad(day, 2);
};

jlab.dateToDateTimeString = function (x) {
    var year = x.getFullYear();
    var month = x.getMonth() + 1;
    var day = x.getDate();
    var hour = x.getHours();
    var minute = x.getMinutes();
    var second = x.getSeconds();
    var fracs = Math.floor(x.getMilliseconds() / 100);

    return year + '-' + jlab.pad(month, 2) + '-' + jlab.pad(day, 2)
            + " " + jlab.pad(hour, 2) + ":" + jlab.pad(minute, 2) + ":" + jlab.pad(second, 2) + "." + fracs;
};