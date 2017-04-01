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
        input "meter", "capability.powerMeter", title: "Meter", multiple: false, required: true
    }
}

def installed() {

    initialize()
}

def updated() {

    unsubscribe()
    initialize()
}

def initialize() {

    // initialize outlet first time through
    def currentTime = now()

    atomicState.refreshTime = currentTime
    atomicState.eventLock = false
    atomicState.deadlockCounter = 0
	
    // subscribe to events
    subscribe(meter, "energy", meterHandler)
}



def meterHandler(evt) {

    if (atomicState.eventLock == false)
    {
        atomicState.eventLock = true
        atomicState.deadlockCounter = 0

        try
        {
            def currentTime = now()


            if ((currentTime - atomicState.refreshTime) >= 60000)
            {
               log.info("calling sensor.refresh()")
               atomicState.refreshTime = currentTime
               sensor.refresh()
            }

        }
        catch (e)
        {
            atomicState.eventLock = false
            log.info("caught an exception: $e")
        }
        
        atomicState.eventLock = false
    }
    else
    {
        atomicState.deadlockCounter = atomicState.deadlockCounter + 1
        log.info("event lock not available")
    }
    
    if (atomicState.deadlockCounter >= 5)
    {
        atomicState.eventLock = false
        atomicState.deadlockCounter = 0
        log.info("event lock reset")
    }
}
