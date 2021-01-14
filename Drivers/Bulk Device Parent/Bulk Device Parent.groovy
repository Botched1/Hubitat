/**
 *  Driver used to create bulk switch and dimm er devices easily. Handy for hub load testing.
 * 
 *  Enter # of devices to create and click button. Enter zero to delete all child devices of that type.
 *
 *  1.0.0 (01/14/2021) - Inititial Version. Supports switches and dimmers as child devices.
 *
 */

metadata {
	definition (name: "Bulk Device Parent", namespace: "Botched1", author: "Jason Bottjen") {		
		command "SwitchCreate", [[name:"How Many?",type:"NUMBER", description:"How Many Switch Child Devices to Make"]]
		command "CreateDimmer", [[name:"How Many?",type:"NUMBER", description:"How Many Dimmer Child Devices to Make"]]
		command "SwitchOn"
		command "SwitchOff"
		command "DimmerOn"
		command "DimmerOff"
		command "DimmerSetLevel", [[name:"Level*",type:"NUMBER", description:"Level to set (0 to 100)"],[name:"Duration",type:"NUMBER", description:"Transition duration in seconds"]]
		
		command "DebugLogging", [[name:"Debug Logging",type:"ENUM", description:"Turn Debug Logging OFF/ON", constraints:["OFF", "30m", "1h", "3h", "6h", "12h", "24h", "ON"]]]        
	}

	preferences {
		input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: true
	}
}
////////////////////////////////////////////////////////////////////////////////////////////////////
// Parse
////////////////////////////////////////////////////////////////////////////////////////////////////
def parse(String description) {
	// parse
	log.debug "PARSE CALLED!!!!! Weird as it shouldn't..."
}

////////////////////////////////////////////////////////////////////////////////////////////////////
// Component Children Methods
////////////////////////////////////////////////////////////////////////////////////////////////////
void componentRefresh(cd){
	if (logEnable) log.info "received refresh request from ${cd.displayName}"
	refresh()
}

void componentOn(cd){
	if (logEnable) log.info "received on request from ${cd.displayName}"
	on(cd)
}

void componentOff(cd){
	if (logEnable) log.info "received off request from ${cd.displayName}"
	off(cd)
}

void componentSetLevel(cd,cdLevel){
	if (logEnable) log.info "received setLevel request from ${cd.displayName}"
	setLevel(cd,cdLevel,0)
}

void componentSetLevel(cd,cdLevel,cdRamp){
	if (logEnable) log.info "received setLevel request from ${cd.displayName}"
	setLevel(cd,cdLevel,cdRamp)
}

////////////////////////////////////////////////////////////////////////////////////////////////////
// Parent Commands
////////////////////////////////////////////////////////////////////////////////////////////////////

void DeviceOn(deviceType) {
	String thisId = device.id

	// Get list of current child devices
	def cdList = getChildDevices()

	// Turn on devices of this type
	cdList.each {
		if (it.typeName == "Generic Component ${deviceType}") {
			on(it)
		}
	}
}

void DeviceOff(deviceType) {
	String thisId = device.id

	// Get list of current child devices
	def cdList = getChildDevices()

	// Turn off devices of this type
	cdList.each {
		if (it.typeName == "Generic Component ${deviceType}") {
			off(it)
		}
	}
}

void DimmerOn() {
	if (logEnable) log.debug "Turn all switch devices ON"

	DeviceOn("Dimmer")
}

void DimmerOff() {
	if (logEnable) log.debug "Turn all switch devices OFF"

	DeviceOff("Dimmer")
}

void DimmerSetLevel(level) {
	DimmerSetLevel(level,0)
}

void DimmerSetLevel(level,duration) {
	String thisId = device.id

	// Get list of current child devices
	def cdList = getChildDevices()

	// Remove any existing devices of this type
	cdList.each {
		if (it.typeName == "Generic Component Dimmer") {
			setLevel(it,level,duration)
		}
	}
}

void SwitchOn() {
	if (logEnable) log.debug "Turn all switch devices ON"

	DeviceOn("Switch")
}

void SwitchOff() {
	if (logEnable) log.debug "Turn all switch devices OFF"

	DeviceOff("Switch")
}


void off() {
	if (logEnable) log.debug "PARENT Turn device OFF"

	// Turn OFF all child devices
	String thisId = device.id
	
	for (int loopVar=1;loopVar<=20;loopVar++) {
		def cd = getChildDevice("${thisId}-Dimmer${loopVar}")
		off(cd)
	}
}

void setLevel(level) {
	if (logEnable) log.debug "PARENT setLevel"

	setLevel(level,0)
}

void setLevel(level,ramp) {
	if (logEnable) log.debug "PARENT setLevel with ramp"

	// setLevel on all child devices
	String thisId = device.id
	
	for (int loopVar=1;loopVar<=20;loopVar++) {
		def cd = getChildDevice("${thisId}-Dimmer${loopVar}")
		setLevel(cd,level,ramp)
	}
}

void refresh() {
	// log.info "refresh() is called"
}

void installed() {
	device.updateSetting("logEnable", [value: "true", type: "bool"])
	runIn(1800,logsOff)
}

void updated() {
	// log.info "updated..."
}

void DeviceCreate(deviceType, value) {
	String thisId = device.id

	// Get list of current child devices
	def cdList = getChildDevices()

	// Remove any existing devices of this type
	cdList.each {
		if (it.typeName == "Generic Component ${deviceType}") {
			deleteChildDevice(it.deviceNetworkId)
		}
	}

	// Create new devices
	if (value) {
		if (value > 99) value=99
		for (int loopVar=1;loopVar<=value;loopVar++) {
			def loopString = loopVar.toString().padLeft(2,"0")
			
			def cd = getChildDevice("${thisId}-${deviceType}${loopString}")
			if (!cd) {
				cd = addChildDevice("hubitat", "Generic Component ${deviceType}", "${thisId}-${deviceType}${loopString}", [name: "${device.displayName} ${deviceType}${loopString}", isComponent: true])
			}
		}
	}
}

void DimmerCreate(value) {
	DeviceCreate("Dimmer", value)
}

void SwitchCreate(value) {
	DeviceCreate("Switch", value)
}

void configure() {
	log.warn "debug logging is: ${logEnable == true}"
	if (logEnable) runIn(1800,logsOff)
}

void DebugLogging(value) {
	if (value=="OFF") {
		unschedule(logsOff)
		logsOff()
	} else

	if (value=="30m") {
		unschedule(logsOff)
		log.debug "debug logging is enabled."
		device.updateSetting("logEnable",[value:"true",type:"bool"])
		runIn(1800,logsOff)		
	} else

	if (value=="1h") {
		unschedule(logsOff)
		log.debug "debug logging is enabled."
		device.updateSetting("logEnable",[value:"true",type:"bool"])
		runIn(3600,logsOff)		
	} else

	if (value=="3h") {
		unschedule(logsOff)
		log.debug "debug logging is enabled."
		device.updateSetting("logEnable",[value:"true",type:"bool"])
		runIn(10800,logsOff)		
	} else

	if (value=="6h") {
		unschedule(logsOff)
		log.debug "debug logging is enabled."
		device.updateSetting("logEnable",[value:"true",type:"bool"])
		runIn(21699,logsOff)		
	} else

	if (value=="12h") {
		unschedule(logsOff)
		log.debug "debug logging is enabled."
		device.updateSetting("logEnable",[value:"true",type:"bool"])
		runIn(43200,logsOff)		
	} else

	if (value=="24h") {
		unschedule(logsOff)
		log.debug "debug logging is enabled."
		device.updateSetting("logEnable",[value:"true",type:"bool"])
		runIn(86400,logsOff)		
	} else
		
	if (value=="ON") {
		unschedule(logsOff)
		log.debug "debug logging is enabled."
		device.updateSetting("logEnable",[value:"true",type:"bool"])
	}
}

void logsOff(){
	log.warn "debug logging disabled..."
	device.updateSetting("logEnable",[value:"false",type:"bool"])
}

////////////////////////////////////////////////////////////////////////////////////////////////////
// Commands to execute on Child Device
////////////////////////////////////////////////////////////////////////////////////////////////////
void on(cd) {
	if (logEnable) log.debug "Turn device ON"

	// Make child events
	List<Map> evts = []
	evts.add([name:"switch", value:"on", descriptionText:"${cd.displayName} was turned on", type: "digital"])
	
	// Send events to child
	getChildDevice(cd.deviceNetworkId).parse(evts)
}

void off(cd) {
	if (logEnable) log.debug "Turn device OFF"
	
	// Make child events
	List<Map> evts = []
	evts.add([name:"switch", value:"off", descriptionText:"${cd.displayName} was turned off", type: "digital"])
	
	// Send events to child
	getChildDevice(cd.deviceNetworkId).parse(evts)
}

void setLevel(cd,cdLevel,cdRamp) {
	if (logEnable) log.debug "Set Level"
	
	// Make child events
	List<Map> evts = []
	evts.add([name:"level", value:"${cdLevel}", descriptionText:"${cd.displayName} was set to ${cdLevel}%", type: "digital"])
	
	// Send events to child
	getChildDevice(cd.deviceNetworkId).parse(evts)
}
    
