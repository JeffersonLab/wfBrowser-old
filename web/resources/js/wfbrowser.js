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

/*Custom time picker*/
jlab.dateTimePickerControl = {
    create: function (tp_inst, obj, unit, val, min, max, step) {
        $('<input class="ui-timepicker-input" value="' + val + '" style="width:50%">')
                .appendTo(obj)
                .spinner({
                    min: min,
                    max: max,
                    step: step,
                    change: function (e, ui) { // key events
                        // don't call if api was used and not key press
                        if (e.originalEvent !== undefined)
                            tp_inst._onTimeChange();
                        tp_inst._onSelectHandler();
                    },
                    spin: function (e, ui) { // spin events
                        tp_inst.control.value(tp_inst, obj, unit, ui.value);
                        tp_inst._onTimeChange();
                        tp_inst._onSelectHandler();
                    }
                });
        return obj;
    },
    options: function (tp_inst, obj, unit, opts, val) {
        if (typeof (opts) === 'string' && val !== undefined)
            return obj.find('.ui-timepicker-input').spinner(opts, val);
        return obj.find('.ui-timepicker-input').spinner(opts);
    },
    value: function (tp_inst, obj, unit, val) {
        if (val !== undefined)
            return obj.find('.ui-timepicker-input').spinner('value', val);
        return obj.find('.ui-timepicker-input').spinner('value');
    }
};
