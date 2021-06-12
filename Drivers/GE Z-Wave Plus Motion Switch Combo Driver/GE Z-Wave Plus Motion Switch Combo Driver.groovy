/*
 *  IMPORT URL: https://raw.githubusercontent.com/Botched1/Hubitat/master/Drivers/GE%20Z-Wave%20Plus%20Motion%20Switch%20Combo%20Driver/GE%20Z-Wave%20Plus%20Motion%20Switch%20Combo%20Driver.groovy
 *
 *  GE Z-Wave Plus Motion Switch Combo Driver
 *  Driver for GE Z-Wave Plus Motion Switch (26931) that can be all-in-one or expose the switch and motion sensor part of a GE Motion Switch device as separate child devices
 *
 *  1.0.0 (06/12/2021) - First version
 */

import groovy.transform.Field

@Field static Map commandClassVersions = 
[
	 0x25: 1  //COMMAND_CLASS_SWITCH_BINARY
	,0x27: 1  //COMMAND_CLASS_SWITCH_ALL (obsoleted)
	,0x2B: 1  //COMMAND_CLASS_SCENE_ACTIVATION
    ,0x2C: 1  //COMMAND_CLASS_SCENE_ACTUATOR_CONF
	,0x56: 1  //COMMAND_CLASS_CRC_16_ENCAP (deprecated)
	,0x59: 1  //COMMAND_CLASS_ASSOCIATION_GRP_INFO
	,0x5A: 1  //COMMAND_CLASS_DEVICE_RESET_LOCALLY
	,0x5E: 2  //COMMAND_CLASS_ZWAVEPLUS_INFO
	,0x60: 4  //COMMAND_CLASS_MULTI_CHANNEL
	,0x70: 1  //COMMAND_CLASS_CONFIGURATION
	,0x71: 4  //COMMAND_CLASS_NOTIFICATION
	,0x72: 2  //COMMAND_CLASS_MANUFACTURER_SPECIFIC
	,0x73: 1  //COMMAND_CLASS_POWERLEVEL
	,0x7A: 2  //COMMAND_CLASS_FIRMWARE_UPDATE_MD
	,0x85: 2  //COMMAND_CLASS_ASSOCIATION
	,0x86: 2  //COMMAND_CLASS_VERSION
	,0x8E: 3  //COMMAND_CLASS_MULTI_CHANNEL_ASSOCIATION
]

metadata {
	definition (name: "GE Z-Wave Plus Motion Switch Combo Driver", namespace: "Botched1", author: "Jason Bottjen") {
		capability "Configuration"
		capability "Refresh"
		capability "Actuator"
		capability "Motion Sensor"
		capability "Sensor"
		capability "Switch"
		capability "Light"		
		
		command "setLightTimeout", [[name:"Light Timeout",type:"ENUM", description:"Time before light turns OFF on no motion - only applies in Occupancy and Vacancy modes.", constraints: ["", "5 seconds", "1 minute", "5 minutes (default)", "15 minutes", "30 minutes", "disabled"]]]
		command "Occupancy"
		command "Vacancy"
		command "Manual"
		command "DebugLogging", [[name:"Debug Logging",type:"ENUM", description:"Turn Debug Logging OFF/ON", constraints:["", "OFF", "30m", "1h", "3h", "6h", "12h", "24h", "ON"]]]        

		attribute "operatingMode", "string"
		attribute "lightTimeout", "string"
	
		fingerprint mfr:"0063", prod:"494D", deviceId:"3032", inClusters:"0x5E,0x72,0x5A,0x73,0x27,0x25,0x2B,0x2C,0x70,0x86,0x71,0x60,0x8E,0x85,0x59,0x7A,0x56", deviceJoinName: "GE-Jasco Motion Switch"
	}

	preferences {
		input "paramInverted", "enum", title: "Switch Buttons Direction", multiple: false, options: ["0" : "Normal", "1" : "Inverted"], required: false, displayDuringSetup: true
		input "paramMotionEnabled", "enum", title: "Motion Sensor", description: "Enable/Disable Motion Sensor.", options: ["0" : "Disable","1" : "Enable"], required: false
		input "paramMotionSensitivity", "enum", title: "Motion Sensitivity", options: ["1" : "High", "2" : "Medium", "3" : "Low"], required: false, displayDuringSetup: true
		input "paramLightSense", "enum", title: "Light Sensing", description: "If enabled, Occupancy mode will only turn light on if it is dark", options: ["0" : "Disabled","1" : "Enabled"], required: false, displayDuringSetup: true
		input "paramMotionResetTimer", "enum", title: "Motion Detection Reset Time", options: ["0" : "Disabled", "1" : "10 sec", "2" : "20 sec", "3" : "30 sec", "4" : "45 sec", "110" : "27 mins"], required: false
		//
		input (
			name: "requestedGroup2",
			title: "Association Group 2 Members (Max of 5):",
			type: "text",
			required: false
			)

		input (
			name: "requestedGroup3",
			title: "Association Group 3 Members (Max of 4):",
			type: "text",
			required: false
			)
		input name: "useChildren", type: "bool", title: "Use Child Devices for Switch and Motion Capabilities", defaultValue: false
		input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: true
		input name: "logDesc", type: "bool", title: "Enable descriptionText logging", defaultValue: true	
	}
}
/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Parse
/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
void parse(String description){
    if (logEnable) log.debug "parse description: ${description}"
    hubitat.zwave.Command cmd = zwave.parse(description,commandClassVersions)
    if (cmd) {
        zwaveEvent(cmd)
    }
}

/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Z-Wave Messages and Methods
/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
String secure(String cmd){
    return zwaveSecureEncap(cmd)
}

String secure(hubitat.zwave.Command cmd){
    return zwaveSecureEncap(cmd)
}

void zwaveEvent(hubitat.zwave.commands.supervisionv1.SupervisionGet cmd){
    hubitat.zwave.Command encapCmd = cmd.encapsulatedCommand(commandClassVersions)
    
    if (encapCmd) {
        zwaveEvent(encapCmd)
    }
    sendHubCommand(new hubitat.device.HubAction(secure(zwave.supervisionV1.supervisionReport(sessionID: cmd.sessionID, reserved: 0, moreStatusUpdates: false, status: 0xFF, duration: 0)), hubitat.device.Protocol.ZWAVE))
}

def zwaveEvent(hubitat.zwave.commands.crc16encapv1.Crc16Encap cmd) {
	//if (logEnable) log.debug "zwaveEvent(): CRC-16 Encapsulation Command received: ${cmd}"
	log.warn "***** CRC-16 Encapsulation Command received: ${cmd} *****"
	log.warn "***** Unused in this driver, please report to driver author *****"
}

def zwaveEvent(hubitat.zwave.commands.basicv1.BasicReport cmd) {
	if (logEnable) log.debug "---BASIC REPORT V1--- ${device.displayName} sent ${cmd}"

	if (useChildren) {
		def cd = fetchChild("Switch")
		String cv = ""

		if (cd) {
			cv = cd.currentValue("switch")
		} else {
			log.warn "In BasicSet no switch child found with fetchChild"
			return
		}

		List<Map> evts = []

		if (state.eventBasicType == "ON") {
			evts.add([name:"switch", value:"on", descriptionText:"${cd.displayName} was turned on", type: "digital"])
		} else {
			evts.add([name:"switch", value:"off", descriptionText:"${cd.displayName} was turned off", type: "digital"])
		}
		
		// Send events to child
		cd.parse(evts)
	} //else {
		if (state.eventBasicType == "ON") {
			if (logDesc) log.info "${device.displayName} was turned on"
			sendEvent(name:"switch", value:"on", descriptionText:"${device.displayName} was turned on", type: "digital")
		} else {
			if (logDesc) log.info "${device.displayName} was turned off"
			sendEvent(name:"switch", value:"off", descriptionText:"${device.displayName} was turned off", type: "digital")
		}
	//}
	
	// Reset Basic report type variable
	state.eventBasicType = ""
}

def zwaveEvent(hubitat.zwave.commands.basicv1.BasicSet cmd) {
	if (logEnable) log.debug "---BASIC SET V1--- ${device.displayName} sent ${cmd}"

	if (useChildren) {
		def cd = fetchChild("Switch")
		String cv = ""

		if (cd) {
			cv = cd.currentValue("switch")
		} else {
			log.warn "In BasicSet no switch child found with fetchChild"
			return
		}

		List<Map> evts = []

		if (cmd.value == 255) {
			evts.add([name:"switch", value:"on", descriptionText:"${cd.displayName} was turned on", type: "physical", isStateChange: true])
		} else if (cmd.value == 0) {
			evts.add([name:"switch", value:"off", descriptionText:"${cd.displayName} was turned off", type: "physical", isStateChange: true])
		}

		// Send events to child
		cd.parse(evts)
	} //else {
		if (cmd.value == 255) {
			if (logDesc) log.info "${device.displayName} was turned on"
			sendEvent(name:"switch", value:"on", descriptionText:"${device.displayName} was turned on", type: "physical", isStateChange: true)
		} else if (cmd.value == 0) {
			if (logDesc) log.info "${device.displayName} was turned off"
			sendEvent(name:"switch", value:"off", descriptionText:"${device.displayName} was turned off", type: "physical", isStateChange: true)
		}
	//}
}

def zwaveEvent(hubitat.zwave.commands.associationv2.AssociationReport cmd) {
	if (logEnable) log.debug "---ASSOCIATION REPORT V2--- ${device.displayName} sent groupingIdentifier: ${cmd.groupingIdentifier} maxNodesSupported: ${cmd.maxNodesSupported} nodeId: ${cmd.nodeId} reportsToFollow: ${cmd.reportsToFollow}"
	if (cmd.groupingIdentifier == 3) {
		if (cmd.nodeId.contains(zwaveHubNodeId)) {
			//sendEvent(name: "numberOfButtons", value: 2, displayed: false)
		} else {
			//sendEvent(name: "numberOfButtons", value: 0, displayed: false)
			List<String> cmds = []
			cmds.add(zwave.associationV2.associationSet(groupingIdentifier: 3, nodeId: zwaveHubNodeId).format())
			cmds.add(zwave.associationV2.associationGet(groupingIdentifier: 3).format())
			// Send Commands
			sendToDevice(cmds)
		}
	}
}

def zwaveEvent(hubitat.zwave.commands.configurationv1.ConfigurationReport cmd) {
	if (logEnable) log.debug "---CONFIGURATION REPORT V1--- ${device.displayName} sent ${cmd}"
	def config = cmd.scaledConfigurationValue.toInteger()
	def result = []
	def name = ""
	def value = ""
	def reportValue = config // cmd.configurationValue[0]
	switch (cmd.parameterNumber) {
		case 1:
			name = "lightTimeout"
			value = reportValue == 0 ? "5 seconds" : reportValue == 1 ? "1 minute" : reportValue == 5 ? "5 minutes" : reportValue == 15 ? "15 minutes" : reportValue == 30 ? "30 minutes" : reportValue == -1 ? "disabled" : "error"
		    break
		case 3:
			name = "operatingMode"
			value = reportValue == 1 ? "Manual" : reportValue == 2 ? "Vacancy" : reportValue == 3 ? "Occupancy": "error"
			break
		case 5:
			name = "Invert Buttons"
			value = reportValue == 0 ? "Disabled" : reportValue == 1 ? "Enabled" : "error"
			break
		case 6:
			name = "Motion Sensor"
			value = reportValue == 0 ? "Disabled" : reportValue == 1 ? "Enabled" : "error"
			break
		case 13:
			name = "Motion Sensitivity"
			value = reportValue == 1 ? "High" : reportValue == 2 ? "Medium" :  reportValue == 3 ? "Low" : "error"
			break
		case 14:
			name = "Light Sensing"
			value = reportValue == 0 ? "Disabled" : reportValue == 1 ? "Enabled" : "error"
			break
		case 15:
			name = "Motion Reset Timer"
			value = reportValue == 0 ? "Disabled" : reportValue == 1 ? "10 seconds" : reportValue == 2 ? "20 seconds" : reportValue == 3 ? "30 seconds" : reportValue == 4 ? "45 seconds" : reportValue == 110 ? "27 minutes" : "error"
			break
		default:
			break
	}
	sendEvent([name: name, value: value])
}

def zwaveEvent(hubitat.zwave.commands.switchbinaryv1.SwitchBinaryReport cmd) {
	if (logEnable) log.debug "---CONFIGURATION REPORT V1--- ${device.displayName} sent ${cmd}"

    if (useChildren) {
		def cd = fetchChild("Switch")
		String cv = ""

		if (cd) {
			cv = cd.currentValue("switch")
		} else {
			log.warn "In BasicSet no switch child found with fetchChild"
			return
		}

		List<Map> evts = []

		if (cmd.value == 255) {
			evts.add([name:"switch", value:"on", descriptionText:"${cd.displayName} was turned on", type: "physical", isStateChange: true])
		} else if (cmd.value == 0) {
			evts.add([name:"switch", value:"off", descriptionText:"${cd.displayName} was turned off", type: "physical", isStateChange: true])
		}
    }
}

def zwaveEvent(hubitat.zwave.commands.manufacturerspecificv2.ManufacturerSpecificReport cmd) {
	log.info "---MANUFACTURER SPECIFIC REPORT V2--- ${device.displayName} sent ${cmd}"
	log.info "manufacturerId:   ${cmd.manufacturerId}"
	log.info "manufacturerName: ${cmd.manufacturerName}"
	state.manufacturer=cmd.manufacturerName
	log.info "productId:        ${cmd.productId}"
	log.info "productTypeId:    ${cmd.productTypeId}"
	def msr = String.format("%04X-%04X-%04X", cmd.manufacturerId, cmd.productTypeId, cmd.productId)
	updateDataValue("MSR", msr)	
	sendEvent(descriptionText: "$device.displayName MSR: $msr", isStateChange: false)
}

def zwaveEvent(hubitat.zwave.commands.versionv2.VersionReport cmd) {
	def fw = "${cmd.applicationVersion}.${cmd.applicationSubVersion}"
	updateDataValue("fw", fw)
	if (logEnable) log.debug "---VERSION REPORT V2--- ${device.displayName} is running firmware version: $fw, Z-Wave version: ${cmd.zWaveProtocolVersion}.${cmd.zWaveProtocolSubVersion}"
}

def zwaveEvent(hubitat.zwave.commands.hailv1.Hail cmd) {
	log.warn "***** Hail command received. *****"
	log.warn "***** Unused in this driver, please report to driver author *****"
}

def zwaveEvent(hubitat.zwave.Command cmd) {
	log.warn "${device.displayName} received unhandled command: ${cmd}"
}
def zwaveEvent(hubitat.zwave.commands.notificationv4.NotificationReport cmd) {
	if (logEnable) log.debug "---NOTIFICATION REPORT V4--- ${device.displayName} sent ${cmd}"

	if (useChildren) {
		def cd = fetchChild("Motion Sensor")

		if (!cd) {
			log.warn "In NotificationReport no Motion Sensor child found with fetchChild"
			return
		}		

		List<Map> evts = []

		if (cmd.notificationType == 0x07) {
			if ((cmd.event == 0x00)) {
				evts.add([name:"motion", value:"inactive", descriptionText:"${cd.displayName} motion inactive", type: "physical"])
				cd.parse(evts)
			} else if (cmd.event == 0x08) {
				evts.add([name:"motion", value:"active", descriptionText:"${cd.displayName} motion active", type: "physical"])
				cd.parse(evts)  
			} 
		}
	} //else {
		if (cmd.notificationType == 0x07) {
			if ((cmd.event == 0x00)) {
				if (logDesc) log.info "${device.displayName} motion inactive"
				sendEvent(name:"motion", value:"inactive", descriptionText:"${device.displayName} motion inactive", type: "physical")
			} else if (cmd.event == 0x08) {
				if (logDesc) log.info "${device.displayName} motion active"
				sendEvent(name:"motion", value:"active", descriptionText:"${device.displayName} motion active", type: "physical")
			}
		}
	//}
}

def zwaveEvent(hubitat.zwave.commands.switchmultilevelv2.SwitchMultilevelSet cmd) {
	log.warn "***** SwitchMultilevelSet Called. *****"
	log.warn "***** Unused in this driver, please report to driver author *****"
}

/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Component Child
/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
void componentRefresh(cd){
	if (logEnable) log.info "received refresh request from ${cd.displayName}"
	refresh()
}

void componentOn(cd){
	if (logEnable) log.info "received on request from ${cd.displayName}"
	//log.warn "componentOn setting state.eventType to -1"
	state.eventLevelType = -1
	on()
}

void componentOff(cd){
	if (logEnable) log.info "received off request from ${cd.displayName}"
	off()
}

def fetchChild(String type){
	String thisId = device.id
	def cd = getChildDevice("${thisId}-${type}")

	if (!cd) {
		log.warn "fetchChild - no child found for ${type}"
	}

	return cd 
}

/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Driver Commands / Functions
/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
void sendToDevice(List<String> cmds, Long delay = 300) {
    sendHubCommand(new hubitat.device.HubMultiAction(commands(cmds, delay), hubitat.device.Protocol.ZWAVE))
}

void sendToDevice(String cmd, Long delay = 300) {
    sendHubCommand(new hubitat.device.HubAction(zwaveSecureEncap(cmd), hubitat.device.Protocol.ZWAVE))
}

List<String> commands(List<String> cmds, Long delay = 300) {
    return delayBetween(cmds.collect { zwaveSecureEncap(it) }, delay)
}

void on() {
	if (logEnable) log.debug "Turn device ON"

	state.eventBasicType = "ON"

	List<String> cmds = []

	cmds.add(zwave.basicV1.basicSet(value: 0xFF).format())
	cmds.add(zwave.basicV1.basicGet().format())
	// Send Commands
	sendToDevice(cmds)	
}

void off() {
	if (logEnable) log.debug "Turn device OFF"

	state.eventBasicType = "OFF"

	List<String> cmds = []

	cmds.add(zwave.basicV1.basicSet(value: 0x00).format())
	cmds.add(zwave.basicV1.basicGet().format())
	// Send Commands
	sendToDevice(cmds)
}

void setLightTimeout(value) {
	if (logEnable) log.debug "Setting light timeout value: ${value}"
	
	List<String> cmds = []
    
	// "5 seconds", "1 minute", "5 minutes", "15 minutes", "30 minutes", "disabled"
	switch (value) {
		case "5 seconds":
			state.lightTimeout = "5 seconds"
			cmds.add(zwave.configurationV1.configurationSet(scaledConfigurationValue: 0 , parameterNumber: 1, size: 1).format())
			break
		case "1 minute":
			state.lightTimeout = "1 minute"
			cmds.add(zwave.configurationV1.configurationSet(scaledConfigurationValue: 1 , parameterNumber: 1, size: 1).format())
			break
		case "5 minutes":
			state.lightTimeout = "5 minutes"
			cmds.add(zwave.configurationV1.configurationSet(scaledConfigurationValue: 5 , parameterNumber: 1, size: 1).format())
			break
		case "15 minutes":
			state.lightTimeout = "15 minutes"
			cmds.add(zwave.configurationV1.configurationSet(scaledConfigurationValue: 15 , parameterNumber: 1, size: 1).format())
			break
		case "30 minutes":
			state.lightTimeout = "30 minutes"
			cmds.add(zwave.configurationV1.configurationSet(scaledConfigurationValue: 30 , parameterNumber: 1, size: 1).format())
			break
		case "disabled":
			state.lightTimeout = "disabled"
			cmds.add(zwave.configurationV1.configurationSet(scaledConfigurationValue: 255 , parameterNumber: 1, size: 1).format())
			break
		default:
			return
	}
	cmds.add(zwave.configurationV1.configurationGet(parameterNumber: 1).format())
	// Send Commands
	sendToDevice(cmds)
}

void Occupancy() {
	state.operatingMode = "Occupancy"
	
	List<String> cmds = []
	
	cmds.add(zwave.configurationV1.configurationSet(configurationValue: [3] , parameterNumber: 3, size: 1).format())
	cmds.add(zwave.configurationV1.configurationGet(parameterNumber: 3).format())
	// Send Commands
	sendToDevice(cmds)
}

void Vacancy() {
	state.operatingMode = "Vacancy"
	
	List<String> cmds = []
	
	cmds.add(zwave.configurationV1.configurationSet(configurationValue: [2] , parameterNumber: 3, size: 1).format())
	cmds.add(zwave.configurationV1.configurationGet(parameterNumber: 3).format())
	// Send Commands
	sendToDevice(cmds)
}

void Manual() {
	state.operatingMode = "Manual"
	
	List<String> cmds = []
	
	cmds.add(zwave.configurationV1.configurationSet(configurationValue: [1] , parameterNumber: 3, size: 1).format())
	cmds.add(zwave.configurationV1.configurationGet(parameterNumber: 3).format())
	// Send Commands
	sendToDevice(cmds)
}

void refresh() {
	log.info "refresh() is called"
	
	List<String> cmds = []
	
	cmds.add(zwave.switchBinaryV1.switchBinaryGet().format())
	cmds.add(zwave.notificationV4.notificationGet(notificationType: 7).format())
	cmds.add(zwave.configurationV1.configurationGet(parameterNumber: 1).format())
	cmds.add(zwave.configurationV1.configurationGet(parameterNumber: 3).format())
	cmds.add(zwave.configurationV1.configurationGet(parameterNumber: 5).format())
	cmds.add(zwave.configurationV1.configurationGet(parameterNumber: 6).format())
	cmds.add(zwave.configurationV1.configurationGet(parameterNumber: 13).format())
	cmds.add(zwave.configurationV1.configurationGet(parameterNumber: 14).format())
	cmds.add(zwave.configurationV1.configurationGet(parameterNumber: 15).format())
	if (getDataValue("MSR") == null) {
		cmds.add(zwave.manufacturerSpecificV1.manufacturerSpecificGet().format())
	}
	
	// Send Commands
	log.info "Sending configuration parameters to the device..."
	sendToDevice(cmds)
}

void installed() {
	device.updateSetting("logEnable", [value: "true", type: "bool"])
	runIn(1800,logsOff)
	configure()
}

void updated() {
	log.info "updated..."
	log.warn "debug logging is: ${logEnable == true}"
	log.warn "description logging is: ${logDesc == true}"
	if (logEnable) runIn(1800,logsOff)

	if (state.lastUpdated && now() <= state.lastUpdated + 3000) return
	state.lastUpdated = now()

	state.clear()
	
	List<String> cmds = []

	if (useChildren) {
		// See if Child Devices are Created, if not then create them
		String thisId = device.id

		def cd = getChildDevice("${thisId}-Switch")
		if (!cd) {
			cd = addChildDevice("hubitat", "Generic Component Switch", "${thisId}-Switch", [name: "${device.displayName} Switch", isComponent: true])
		}

		cd = getChildDevice("${thisId}-Motion Sensor")
		if (!cd) {
			cd = addChildDevice("hubitat", "Generic Component Motion Sensor", "${thisId}-Motion Sensor", [name: "${device.displayName} Motion Sensor", isComponent: true])
		}
	} else {
		// Delete Any children if they exist
		List<com.hubitat.app.ChildDeviceWrapper> myChildren = getChildDevices()
		
		if (myChildren) {
			for (myChild in myChildren) {
				deleteChildDevice(myChild.deviceNetworkId)
			}
		}
	}
	
    // Get light timeout parameter
    cmds.add(zwave.configurationV1.configurationGet(parameterNumber: 1).format())
    
    // Get mode parameter
    cmds.add(zwave.configurationV1.configurationGet(parameterNumber: 3).format())
    
	// Set Inverted param
	if (paramInverted==null) {
		paramInverted = 0
	}
	cmds.add(zwave.configurationV1.configurationSet(scaledConfigurationValue: paramInverted.toInteger(), parameterNumber: 5, size: 1).format())
	cmds.add(zwave.configurationV1.configurationGet(parameterNumber: 5).format())

	// Set Motion Enabled param
	if (paramMotionEnabled==null) {
		paramMotionEnabled = 1
	}
	cmds.add(zwave.configurationV1.configurationSet(scaledConfigurationValue: paramMotionEnabled.toInteger(), parameterNumber: 6, size: 1).format())
	cmds.add(zwave.configurationV1.configurationGet(parameterNumber: 6).format())

	// Set Motion Sensitivity param
	if (paramMotionSensitivity==null) {
		paramMotionSensitivity = 2
	}
	cmds.add(zwave.configurationV1.configurationSet(scaledConfigurationValue: paramMotionSensitivity.toInteger(), parameterNumber: 13, size: 1).format())
	cmds.add(zwave.configurationV1.configurationGet(parameterNumber: 13).format())

	// Set Light Sense param
	if (paramLightSense==null) {
		paramLightSense = 1
	}
	cmds.add(zwave.configurationV1.configurationSet(scaledConfigurationValue: paramLightSense.toInteger(), parameterNumber: 14, size: 1).format())
	cmds.add(zwave.configurationV1.configurationGet(parameterNumber: 14).format())

	// Set Motion Reset Timer param
	if (paramMotionResetTimer==null) {
		paramMotionResetTimer = 2
	}
	cmds.add(zwave.configurationV1.configurationSet(scaledConfigurationValue: paramMotionResetTimer.toInteger(), parameterNumber: 15, size: 2).format())
	cmds.add(zwave.configurationV1.configurationGet(parameterNumber: 15).format())

	// Association groups
	cmds.add(zwave.associationV2.associationSet(groupingIdentifier:1, nodeId:zwaveHubNodeId).format())
	cmds.add(zwave.associationV2.associationRemove(groupingIdentifier:2, nodeId:zwaveHubNodeId).format())
	cmds.add(zwave.associationV2.associationSet(groupingIdentifier:3, nodeId:zwaveHubNodeId).format())
	
	// Add endpoints to groups 2 and 3
	def nodes = []
	if (settings.requestedGroup2 != state.currentGroup2) {
		nodes = parseAssocGroupList(settings.requestedGroup2, 2)
		cmds.add(zwave.associationV2.associationRemove(groupingIdentifier: 2, nodeId: []).format())
		cmds.add(zwave.associationV2.associationSet(groupingIdentifier: 2, nodeId: nodes).format())
		cmds.add(zwave.associationV2.associationGet(groupingIdentifier: 2).format())
		state.currentGroup2 = settings.requestedGroup2
	}
	
	if (settings.requestedGroup3 != state.currentGroup3) {
		nodes = parseAssocGroupList(settings.requestedGroup3, 3)
		cmds.add(zwave.associationV2.associationSetRemove(groupingIdentifier: 3, nodeId: []).format())
		cmds.add(zwave.associationV2.associationSet(groupingIdentifier: 3, nodeId: nodes).format())
		cmds.add(zwave.associationV2.associationGet(groupingIdentifier: 3).format())
		state.currentGroup3 = settings.requestedGroup3
	}    

	// Send Commands
	log.info "Sending configuration parameters to the device..."
	sendToDevice(cmds)
}

void configure() {
	log.info "configure triggered"
	
	state.clear()
	
	List<String> cmds = []
	
	cmds.add(zwave.associationV2.associationSet(groupingIdentifier:1, nodeId:zwaveHubNodeId).format())
	cmds.add(zwave.associationV2.associationRemove(groupingIdentifier:2, nodeId:zwaveHubNodeId).format())
	cmds.add(zwave.associationV2.associationSet(groupingIdentifier:3, nodeId:zwaveHubNodeId).format())
    
	// Send Commands
	log.info "Sending configuration parameters to the device..."
	sendToDevice(cmds)
    
	refresh()
}

private parseAssocGroupList(list, group) {
	def nodes = group == 2 ? [] : [zwaveHubNodeId]
	if (list) {
		def nodeList = list.split(',')
		def max = group == 2 ? 5 : 4
		def count = 0

		nodeList.each { node ->
		    node = node.trim()
		
		    if ( count >= max) {
			    log.warn "Association Group ${group}: Number of members is greater than ${max}! The following member was discarded: ${node}"
		    } else if (node.matches("\\p{XDigit}+")) {
			    def nodeId = Integer.parseInt(node,16)
			    if (nodeId == zwaveHubNodeId) {
				    log.warn "Association Group ${group}: Adding the hub as an association is not allowed (it would break double-tap)."
			    } else if ( (nodeId > 0) & (nodeId < 256) ) {
				    nodes << nodeId
				    count++
			    } else {
				    log.warn "Association Group ${group}: Invalid member: ${node}"
			    }
		    } else {
			    log.warn "Association Group ${group}: Invalid member: ${node}"
		    }
		}
	}
	return nodes
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
