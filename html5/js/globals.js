// Custom Events
var AUTH_SUCCESS = "Authentication successful";
var AUTH_FAILED = "Authentication failed";
var AUTH_ERROR = "Error while authenticating";
var LOGOUT_REQUESTED = "Logout requested by User"
var LOADING_IN_PROGRESS = "Loading Data in Progress";
var LOADING_DONE = "Loading Data completed";
var LOADING_ERROR = "Error while loading Data";
var JUST_MARRIED = "User just logged in successfully";
var TOGGLE_SPINNER = "Toggle Loading Indicator";
var ENABLE_SPINNER = "Enable Loading Indicator";
var DISABLE_SPINNER = "Disable Loading Indicator";
var START_SINGLE = "Start Single Request Mode"
var STOP_SINGLE = "Stop Single Request Mode"
var START_LOOP = "Start Loop Requests Mode";
var STOP_LOOP = "Stop Loop Requests Mode";
var START_BATCH = "Start Batch Requests Mode";
var STOP_BATCH = "Stop Batch Requests Mode";

// Functions
function setBackground(color) {
    document.documentElement.setAttribute("style", "background-color: " + color)
}

function shakeElement(elem) {
    elem.classList.add("shake");
    window.setTimeout(function () {
        elem.classList.remove("shake");
    }, 1000);
}
