definition(
    name: "Device Refresher",
    namespace: "larrywho",
    description: "Call refresh() on a sensor",
    category: "My Apps",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png",
    iconX3Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png")


preferences {
    section ("Device(s) to refresh ...") {
        input "sensor", "capability.temperatureMeasurement", title: "Device", required: true, multiple: true
    }
}

def installed() {
    initialize()
}

def updated() {
    unsubscribe()
    unschedule()
    initialize()
}

def initialize() {
   runEvery1Minute("runApp")
}

def runApp() {
   log.info("calling sensor.refresh()")
   sensor.refresh()
}

