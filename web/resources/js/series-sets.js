var jlab = jlab || {};
jlab.wfb = jlab.wfb || {};

// Override some parameters from the smoothness template
jlab.editableRowTable.entity = 'SeriesSet';
//jlab.editableRowTable.dialog.width = 10;
//jlab.editableRowTable.dialog.height = 40;


jlab.wfb.deleteRow = function () {
    var $selectedRow = $(".editable-row-table tbody tr.selected-row");

    if ($selectedRow.length < 1) {
        return;
    }

    if (jlab.isRequest()) {
        window.console && console.log("Ajax already in progress");
        return;
    }

    var seriesSetName = $selectedRow.find("td:nth-child(1)").text();

    if (!confirm("Are you sure you want to delete the series " + seriesSetName + "?")) {
        return;
    }

    var seriesSetId = $selectedRow.data("series-set-id");
    var url = jlab.contextPath + "/ajax/series-set-delete";
    var data = {'id': seriesSetId};
    $dialog = null;

    jlab.doAjaxJsonPostRequest(url, data, $dialog, true);
};

jlab.wfb.initDialogs = function () {
    $("#table-row-dialog").dialog({
        autoOpen: false,
        width: 700,
        height: 300,
        modal: true
    });
};

jlab.wfb.validateRowForm = function () {
    if ($("#row-name").val() === '') {
        alert("Please select a name");
        return false;
    }
    if ($("#row-description").val() === '') {
        alert("Please enter a description");
        return false;
    }
    if ($("#row-set").val() === '') {
        alert("Please enter the comma separated set of series names");
        return false;
    }

    return true;
};

jlab.wfb.editRow = function () {
    if (!jlab.wfb.validateRowForm()) {
        return;
    }

    var seriesSetId = $(".editable-row-table tbody tr.selected-row").data("series-set-id");
    var name = $("#row-name").val();
    var description = $("#row-description").val();
    var set = $("#row-set").val();
    var url = jlab.contextPath + "/ajax/series-set-update";
    var data = {"id": seriesSetId, "name": name, "description": description,  "system": "rf", "set": set};
    var $dialog = $("#table-row-dialog");
    jlab.doAjaxJsonPostRequest(url, data, $dialog, true);
};

jlab.wfb.addRow = function () {
    if (!jlab.wfb.validateRowForm()) {
        return;
    }
    var name = $("#row-name").val();
    var description = $("#row-description").val();
    var set = $("#row-set").val();
    var url = jlab.contextPath + "/ajax/series-sets";
    var data = {"name": name, "description": description, "set": set, "system": "rf"};
    var $dialog = $("table-row-dialog");

    jlab.doAjaxJsonPostRequest(url, data, $dialog, true);
};


$(document).on("click", "#remove-row-button", function () {
    jlab.wfb.deleteRow();
});

$(document).on("click", "#open-edit-row-dialog-button", function () {
    var $selectedRow = $(".editable-row-table tbody tr.selected-row");

    if ($selectedRow.length < 1) {
        return;
    }

    var name = $selectedRow.find("td:nth-child(1)").text();
    var description = $selectedRow.find("td:nth-child(2)").text();
    var set = $selectedRow.find("td:nth-child(3)").text();

    $("#row-name").val(name);
    $("#row-set").val(set);
    $("#row-description").val(description);
});

$(document).on("table-row-add", function () {
    jlab.wfb.addRow();
});

$(document).on("table-row-edit", function () {
    jlab.wfb.editRow();
});

$(function () {
    jlab.wfb.initDialogs();
});