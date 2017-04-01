import groovy.json.JsonSlurper

metadata {
	definition (name: "Raspberry Pi", author: "larrywho", namespace:"larrywho") {
		capability "Polling"
		capability "Refresh"
		capability "Temperature Measurement"
        capability "Sensor"
        attribute "cpuUsage", "string"
		attribute "spaceUsed", "string"
		attribute "upTime", "string"
		attribute "cpuTemp", "string"
		attribute "freeMem", "string"
	    attribute "date", "string"
        command "RefreshTrigger"
	    command "RebootNow"
		command "ResetTiles"
		command "ClearTiles"
	}

	preferences {
		input("DeviceIP", "string", title:"Device IP Address", description: "Please enter your device's IP Address", required: true, displayDuringSetup: true)
		input("DevicePort", "string", title:"Device Port", description: "Empty assumes port 80.", required: false, displayDuringSetup: true)
		input("DevicePath", "string", title:"URL Path", description: "Rest of the URL, include forward slash.", displayDuringSetup: true)
		section() {
			input("HTTPAuth", "bool", title:"Requires User Auth?", description: "Choose if the HTTP requires basic authentication", defaultValue: false, required: true, displayDuringSetup: true)
			input("HTTPUser", "string", title:"HTTP User", description: "Enter your basic username", required: false, displayDuringSetup: true)
			input("HTTPPassword", "string", title:"HTTP Password", description: "Enter your basic password", required: false, displayDuringSetup: true)
		}
	}
	
	simulator {
	}

	tiles {
		standardTile("RefreshTrigger", "device.refreshswitch", decoration: "flat", canChangeIcon: true) {
			state "default", label:'REFRESH', action: "refresh.refresh", icon: "st.secondary.refresh-icon", backgroundColor:"#53a7c0", nextState: "refreshing"
			state "refreshing", label: 'REFRESHING', action: "ResetTiles", icon: "st.secondary.refresh-icon", backgroundColor: "#FF6600", nextState: "default"
		}

		valueTile("cpuUsage", "device.cpuUsage") {
			state("cpuUsage", label: '${currentValue}%',
				backgroundColors:[
					[value: 0, color: "#00cc33"],
					[value: 10, color: "#99ff33"],
					[value: 30, color: "#ffcc99"],
					[value: 55, color: "#ff6600"],
					[value: 90, color: "#ff0000"]
				]
			)
		}
		valueTile("cpuTemp", "device.cpuTemp") {
			state("cpuTemp", label: 'CPU Temp ${currentValue}',
				backgroundColors:[
					[value: 50, color: "#00cc33"],
					[value: 60, color: "#99ff33"],
					[value: 67, color: "#ff6600"],
					[value: 75, color: "#ff0000"]
				]
			)
		}
		valueTile("spaceUsed", "device.spaceUsed") {
			state("spaceUsed", label: 'Space Used\r\n ${currentValue}%',
				backgroundColors:[
					[value: 50, color: "#00cc33"],
					[value: 75, color: "#ffcc66"],
					[value: 85, color: "#ff6600"],
					[value: 95, color: "#ff0000"]
				]
			)
		}
		valueTile("upTime", "device.upTime", decoration: "flat", canChangeIcon: true) {
			state("upTime", label: '${currentValue}', backgroundColor:"#ffffff")
		}
		valueTile("date", "device.date", decoration: "flat", canChangeIcon: true) {
			state("date", label: '${currentValue}', backgroundColor:"#ffffff")
		}
        valueTile("freeMem", "device.freeMem", decoration: "flat") {
			state("freeMem", label: 'Free Mem\r\n ${currentValue}', backgroundColor:"#ffffff")
		}
		standardTile("clearTiles", "device.clear", decoration: "flat") {
			state "clearTiles", label:'Clear Tiles', action:"ClearTiles", icon:"st.Bath.bath9"
		}

		standardTile("RebootNow", "device.rebootnow", decoration: "flat") {
			state "default", label:'REBOOT' , action: "RebootNow", icon: "st.Seasonal Winter.seasonal-winter-014", backgroundColor:"#ff0000", nextState: "rebooting"
			state "rebooting", label: 'REBOOTING', action: "ResetTiles", icon: "st.Office.office13", backgroundColor: "#FF6600", nextState: "default"
		}
		main "date"
		details(["RefreshTrigger", "cpuUsage", "cpuTemp", "upTime", "date", "spaceUsed", "freeMem", "clearTiles", "RebootNow"])
	}
}

def refresh() {
	def FullCommand = 'Refresh=&UseJSON='

	runCmd(FullCommand)
}
def poll() {
	refresh()
}

def RebootNow() {
	log.debug "Reboot Triggered!!!"
	runCmd('RebootNow=')
}
def ClearTiles() {
	sendEvent(name: "cpuUsage", value: "", unit: "")
	sendEvent(name: "cpuTemp", value: "", unit: "")
	sendEvent(name: "spaceUsed", value: "", unit: "")
	sendEvent(name: "upTime", value: "", unit: "")
	sendEvent(name: "date", value: "", unit: "")
	sendEvent(name: "freeMem", value: "", unit: "")
}
def ResetTiles() {
	//RETURN BUTTONS TO CORRECT STATE

	sendEvent(name: "refreshswitch", value: "default", isStateChange: true)
	sendEvent(name: "rebootnow", value: "default", isStateChange: true)
	log.debug "Resetting tiles."
}

def runCmd(String varCommand) {
	def host = DeviceIP
	def hosthex = convertIPtoHex(host).toUpperCase()
	def LocalDevicePort = ''
	if (DevicePort==null) { LocalDevicePort = "80" } else { LocalDevicePort = DevicePort }
	def porthex = convertPortToHex(LocalDevicePort).toUpperCase()
	device.deviceNetworkId = "$hosthex:$porthex"
	def userpassascii = "${HTTPUser}:${HTTPPassword}"
	def userpass = "Basic " + userpassascii.encodeAsBase64().toString()

	log.debug "The device id configured is: $device.deviceNetworkId"

	def headers = [:] 
	headers.put("HOST", "$host:$LocalDevicePort")
	headers.put("Content-Type", "application/x-www-form-urlencoded")
	if (HTTPAuth) {
		headers.put("Authorization", userpass)
	}
	log.debug "The Header is $headers"

	def path = ''
	def body = ''
	log.debug "Uses which method: $DevicePostGet"
	def method = "POST"

			path = DevicePath
			body = varCommand 
			log.debug "POST body is: $body"


	try {
		def hubAction = new physicalgraph.device.HubAction(
			method: method,
			path: path,
			body: body,
			headers: headers
			)
		hubAction.options = [outputMsgToS3:false]
		log.debug hubAction
		hubAction
	}
	catch (Exception e) {
		log.debug "Hit Exception $e on $hubAction"
	}
}

def parse(String description) {
//	log.debug "Parsing '${description}'"
	def whichTile = ''
	def map = [:]
	def retResult = []
	def descMap = parseDescriptionAsMap(description)
	def jsonlist = [:]
	def bodyReturned = ' '
	def headersReturned = ' '
	if (descMap["body"] && descMap["headers"]) {
		bodyReturned = new String(descMap["body"].decodeBase64())
		headersReturned = new String(descMap["headers"].decodeBase64())
	}
	//log.debug "BODY---" + bodyReturned
	//log.debug "HEADERS---" + headersReturned

	if (descMap["body"]) {
		if (headersReturned.contains("application/json")) {
			def body = new String(descMap["body"].decodeBase64())
			def slurper = new JsonSlurper()
			jsonlist = slurper.parseText(body)
			//log.debug "JSONLIST---" + jsonlist."CPU"
			jsonlist.put ("Date", new Date().format("yyyy-MM-dd h:mm:ss a", location.timeZone))
		} 
	}
	if (descMap["body"] && (headersReturned.contains("application/json") || headersReturned.contains("text/html"))) {
		//putImageInS3(descMap)

		if (jsonlist."Refresh"=="Success") {
			sendEvent(name: "refreshTriggered", value: jsonlist."Date", unit: "")
			whichTile = 'refresh'
		}

		if (jsonlist."CPU") {
			sendEvent(name: "cpuUsage", value: jsonlist."CPU".replace("=","\n").replace("%",""), unit: "")
		}
		if (jsonlist."Space Used") {
			sendEvent(name: "spaceUsed", value: jsonlist."Space Used".replace("=","\n").replace("%",""), unit: "")
		}
		if (jsonlist."UpTime") {
			sendEvent(name: "upTime", value: jsonlist."UpTime".replace("=","\n"), unit: "")
		}
		if (jsonlist."Date") {
			sendEvent(name: "date", value: jsonlist."Date".replace("=","\n"), unit: "")
		}
        if (jsonlist."CPU Temp") {
			sendEvent(name: "cpuTemp", value: jsonlist."CPU Temp".replace("=","\n").replace("\'","°").replace("C ","C="), unit: "")
		}
		if (jsonlist."Free Mem") {
			sendEvent(name: "freeMem", value: jsonlist."Free Mem".replace("=","\n"), unit: "")
		}

		if (jsonlist."RebootNow") {
			whichTile = 'RebootNow'
		}
	}

	log.debug jsonlist

	//RESET THE DEVICE ID TO GENERIC/RANDOM NUMBER. THIS ALLOWS MULTIPLE DEVICES TO USE THE SAME ID/IP
	device.deviceNetworkId = "ID_WILL_BE_CHANGED_AT_RUNTIME_" + (Math.abs(new Random().nextInt()) % 99999 + 1)

	//RETURN BUTTONS TO CORRECT STATE
	log.debug 'whichTile: ' + whichTile
    switch (whichTile) {
        case 'refresh':
			sendEvent(name: "refreshswitch", value: "default", isStateChange: true)
			def result = createEvent(name: "refreshswitch", value: "default", isStateChange: true)
			//log.debug "refreshswitch returned ${result?.descriptionText}"
			return result

        case 'RebootNow':
			sendEvent(name: "rebootnow", value: "default", isStateChange: true)
			def result = createEvent(name: "rebootnow", value: "default", isStateChange: true)
			return result
        default:
			sendEvent(name: "refreshswitch", value: "default", isStateChange: true)
			def result = createEvent(name: "refreshswitch", value: "default", isStateChange: true)
			//log.debug "refreshswitch returned ${result?.descriptionText}"
			return result
    }
}

def parseDescriptionAsMap(description) {
	description.split(",").inject([:]) { map, param ->
	def nameAndValue = param.split(":")
	map += [(nameAndValue[0].trim()):nameAndValue[1].trim()]
	}
}
private String convertIPtoHex(ipAddress) { 
	String hex = ipAddress.tokenize( '.' ).collect {  String.format( '%02x', it.toInteger() ) }.join()
	//log.debug "IP address entered is $ipAddress and the converted hex code is $hex"
	return hex
}
private String convertPortToHex(port) {
	String hexport = port.toString().format( '%04x', port.toInteger() )
	//log.debug hexport
	return hexport
}
private Integer convertHexToInt(hex) {
	Integer.parseInt(hex,16)
}
private String convertHexToIP(hex) {
	//log.debug("Convert hex to ip: $hex") 
	[convertHexToInt(hex[0..1]),convertHexToInt(hex[2..3]),convertHexToInt(hex[4..5]),convertHexToInt(hex[6..7])].join(".")
}
private getHostAddress() {
	def parts = device.deviceNetworkId.split(":")
	//log.debug device.deviceNetworkId
	def ip = convertHexToIP(parts[0])
	def port = convertHexToInt(parts[1])
	return ip + ":" + port
}