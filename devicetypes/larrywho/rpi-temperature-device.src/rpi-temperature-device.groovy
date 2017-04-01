import groovy.json.JsonSlurper

metadata {
	definition (name: "RPi Temperature Device", author: "larrywho", namespace:"larrywho") {
		capability "Temperature Measurement"
//		capability "Polling"
		capability "Refresh"
		command "RefreshTrigger"
//        command "poll"
		attribute "temperature", "string"
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
		valueTile("temperature", "device.temperature", canChangeIcon: true) {
			state("temperature", label:'${currentValue}°', unit:"F",
				backgroundColors:[
					[value: 31, color: "#153591"],
					[value: 44, color: "#1e9cbb"],
					[value: 59, color: "#90d2a7"],
					[value: 74, color: "#44b621"],
					[value: 84, color: "#f1d801"],
					[value: 95, color: "#d04e00"],
					[value: 96, color: "#bc2323"]
				]
			)
		}
        
        standardTile("refresh", "device.refresh", inactiveLabel: false, decoration: "flat") {
 			state "default", action:"refresh.refresh", icon:"st.secondary.refresh"
 		}
        
		main "temperature"
		details(["temperature", "refresh"])
	}
}

def refresh() {
	def FullCommand = 'Refresh=&UseJSON='
	runCmd(FullCommand)
}
//def poll() {
//	refresh()
//}

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

		if (jsonlist."Refresh"=="Success") {
			sendEvent(name: "refreshTriggered", value: jsonlist."Date", unit: "")
			whichTile = 'refresh'
		}

		if (jsonlist."Temperature") {
			sendEvent(name: "temperature", value: jsonlist."Temperature".replace("=","\n").replace("\'","°").replace("C ","C="), unit: "", isStateChange: true)
			//String s = jsonlist."Temperature"
			//for(int i = 0; i < s.length(); i++)	{
			//   int c = s.charAt(i);
			//   log.trace "'${c}'\n"
			//}
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