/**
 *  GE Motion Switch 26931
 *
 *
 *  Original based off of the Dimmer Switch under Templates in the IDE 
 *  Original custom DTH Author: Matt Lebaugh (@mlebaugh)
 *
 *  HUBITAT PORT
 *  1.2.0 (01/24/2019) - Ported to Hubitat by Jason Bottjen. Removed ST specifics, removed Polling and Health Check capabilities.
 *  1.3.0 (02/08/2019) - Redid CRC16 code to support newer firmware devices                       
 */

metadata {
	definition (name: "GE Motion Switch 26931", namespace: "Botched1", author: "Jason Bottjen") {
		capability "Motion Sensor"
        capability "Actuator"
 		capability "Switch"
		capability "Refresh"
		capability "Sensor"
		capability "Light"
        capability "PushableButton"

		command "toggleMode"
        command "Occupancy"
        command "Vacancy"
        command "Manual"
		command "LightSenseOn"
        command "LightSenseOff"

        attribute "operatingMode", "enum", ["Manual", "Vacancy", "Occupancy"]

		fingerprint mfr:"0063", prod:"494D", model: "3032", deviceJoinName: "GE Z-Wave Plus Motion Wall Switch"
	}

	preferences {
        	input title: "", description: "Select your prefrences here, they will be sent to the device once updated.\n\nTo verify the current settings of the device, they will be shown in the 'recently' page once any setting is updated", displayDuringSetup: false, type: "paragraph", element: "paragraph"
			input (
                name: "operationmode",
                title: "Operating Mode",
                description: "Occupancy: Automatically turn on and off the light with motion\nVacancy: Manually turn on, automatically turn off light with no motion.",
                type: "enum",
                options: [
                    "1" : "Manual",
                    "2" : "Vacancy (auto-off)",
                    "3" : "Occupancy (auto-on/off)",
                ],
                required: false
            )
            input (
                name: "timeoutduration",
                title: "Timeout Duration",
                description: "Length of time after no motion for the light to shut off in Occupancy/Vacancy modes",
                type: "enum",
                options: [
                    "0" : "5 seconds",
                    "1" : "1 minute",
                    "5" : "5 minutes (default)",
                    "15" : "15 minutes",
                    "30" : "30 minutes",
                    "255" : "disabled"
                ],
                required: false
            )
			input (
                name: "motionsensitivity",
                title: "Motion Sensitivity",
                description: "Motion Sensitivity",
                type: "enum",
                options: [
                    "1" : "High",
                    "2" : "Medium (default)",
                    "3" : "Low"
                ],
                required: false
            )
			input (
                name: "lightsense",
                title: "Light Sensing",
                description: "If enabled, Occupancy mode will only turn light on if it is dark",
                type: "enum",
                options: [
                    "0" : "Disabled",
                    "1" : "Enabled",
                ],
                required: false
            )
			
			input (
                name: "motion",
                title: "Motion Sensor",
                description: "Enable/Disable Motion Sensor.",
                type: "enum",
                options: [
                    "0" : "Disable",
                    "1" : "Enable",
                ],
                required: false
            )
            input (
                name: "invertSwitch",
                title: "Switch Orientation",
                type: "enum",
                options: [
                    "0" : "Normal",
                    "1" : "Inverted",
                ],
                required: false
            )
            input (
                name: "resetcycle",
                title: "Motion Detection Reset Time",
                type: "enum",
                options: [
                    "0" : "Disabled",
                    "1" : "10 sec",
                    "2" : "20 sec (default)",
                    "3" : "30 sec",
                    "4" : "45 sec",
                    "110" : "27 mins",
                ],
                required: false
            )

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
			input ( type: "paragraph", element: "paragraph", title: "", description: "**MISC**")
			input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: true		
    }
}


def parse(String description) {
    def result = null
	if (description != "updated") {
		if (logEnable) log.debug "parse() >> zwave.parse($description)"
		def cmd = zwave.parse(description, [0x20: 1, 0x25: 1, 0x56: 1, 0x70: 2, 0x72: 2, 0x85: 2, 0x71: 3])
		if (cmd) {
			result = zwaveEvent(cmd)
        }
	}
    if (!result) { log.warn "Parse returned ${result} for $description" }
    else {if (logEnable) log.debug "Parse returned ${result}"}
	return result
	if (result?.name == 'hail' && hubFirmwareLessThan("000.011.00602")) {
		result = [result, response(zwave.basicV1.basicGet().format)]
		if (logEnable) log.debug "Was hailed: requesting state update"
	}
	return result
}

def zwaveEvent(hubitat.zwave.commands.crc16encapv1.Crc16Encap cmd) {
    def ver = cmd.commandClass == 0x71 ? 2 : 1
    def encapsulatedCommand = zwave.getCommand(cmd.commandClass, cmd.command, cmd.data, ver)
    if (encapsulatedCommand) {
        zwaveEvent(encapsulatedCommand)
    } else {
        log.warn "Unable to extract CRC16 command from ${cmd}"
    }
}

def zwaveEvent(hubitat.zwave.commands.basicv1.BasicReport cmd) {
	[name: "switch", value: cmd.value ? "on" : "off", type: "physical"]
}

def zwaveEvent(hubitat.zwave.commands.basicv1.BasicSet cmd) {
    if (logEnable) log.debug "---BASIC SET V1--- ${device.displayName} sent ${cmd}"
	def result = []
    result << createEvent([name: "switch", value: cmd.value ? "on" : "off", type: "physical"])
    if (cmd.value == 255) {
    	result << createEvent([name: "pushed", value: "1", descriptionText: "On/Up on (button 1) $device.displayName was pushed", isStateChange: true, type: "physical"])
    }
	else if (cmd.value == 0) {
    	result << createEvent([name: "pushed", value: "2", descriptionText: "Off/Down (button 2) on $device.displayName was pushed", isStateChange: true, type: "physical"])
    }
    return result
}

def zwaveEvent(hubitat.zwave.commands.associationv2.AssociationReport cmd) {
	if (logEnable) log.debug "---ASSOCIATION REPORT V2--- ${device.displayName} sent groupingIdentifier: ${cmd.groupingIdentifier} maxNodesSupported: ${cmd.maxNodesSupported} nodeId: ${cmd.nodeId} reportsToFollow: ${cmd.reportsToFollow}"
    state.group3 = "1,2"
    if (cmd.groupingIdentifier == 3) {
    	if (cmd.nodeId.contains(zwaveHubNodeId)) {
        	sendEvent(name: "numberOfButtons", value: 2, displayed: false)
        }
        else {
        	sendEvent(name: "numberOfButtons", value: 0, displayed: false)
			zwave.associationV2.associationSet(groupingIdentifier: 3, nodeId: zwaveHubNodeId).format()
			zwave.associationV2.associationGet(groupingIdentifier: 3).format()
        }
    }
}

def zwaveEvent(hubitat.zwave.commands.configurationv2.ConfigurationReport cmd) {
	if (logEnable) log.debug "---CONFIGURATION REPORT V2--- ${device.displayName} sent ${cmd}"
    def config = cmd.scaledConfigurationValue.toInteger()
    def result = []
    if (cmd.parameterNumber == 1) {
		def value = config == 0 ? "Test 5s" : config == 1 ? "1 minute" : config == 5 ? "5 minute" : config == 15 ? "15 minute" : config == 30 ? "30 minute" : "255 minute" 
    	result << createEvent([name:"TimeoutDuration", value: value, displayed:true, isStateChange:true])
    } else if (cmd.parameterNumber == 13) {
		def value = config == 1 ? "High" : config == 2 ? "Medium" : "Low"
    	result << createEvent([name:"MotionSensitivity", value: value, displayed:true, isStateChange:true])
	} else if (cmd.parameterNumber == 14) {
		def value = config == 0 ? "Disabled" : "Enabled"
    	result << createEvent([name:"LightSense", value: value, displayed:true, isStateChange:true])
    } else if (cmd.parameterNumber == 15) {
    	def value = config == 0 ? "Disabled" : config == 1 ? "10 sec" : config == 2 ? "20 sec" : config == 3 ? "30 sec" : config == 4 ? "45 sec" : "27 minute" 
    	result << createEvent([name:"ResetCycle", value: value, displayed:true, isStateChange:true])
    } else if (cmd.parameterNumber == 3) {
    	if (config == 1 ) {
        	result << createEvent([name:"operatingMode", value: "Manual", displayed:true, isStateChange:true])
         } else if (config == 2 ) {
        	result << createEvent([name:"operatingMode", value: "Vacancy", displayed:true, isStateChange:true])
        } else if (config == 3 ) {
        	result << createEvent([name:"operatingMode", value: "Occupancy", displayed:true, isStateChange:true])
        }
    } else if (cmd.parameterNumber == 6) {
    	def value = config == 0 ? "Disabled" : "Enabled"
    	result << createEvent([name:"MotionSensor", value: value, displayed:true, isStateChange:true])
    } else if (cmd.parameterNumber == 5) {
		def value = config == 0 ? "Normal" : "Inverted"
    	result << createEvent([name:"SwitchOrientation", value: value, displayed:true, isStateChange:true])
    } 
   return result
}

def zwaveEvent(hubitat.zwave.commands.switchbinaryv1.SwitchBinaryReport cmd) {
    if (logEnable) log.debug "---BINARY SWITCH REPORT V1--- ${device.displayName} sent ${cmd}"
    createEvent([name: "switch", value: cmd.value ? "on" : "off", type: "digital"])
}

def zwaveEvent(hubitat.zwave.commands.manufacturerspecificv2.ManufacturerSpecificReport cmd) {
    log.debug "---MANUFACTURER SPECIFIC REPORT V2--- ${device.displayName} sent ${cmd}"
	log.debug "manufacturerId:   ${cmd.manufacturerId}"
	log.debug "manufacturerName: ${cmd.manufacturerName}"
    state.manufacturer=cmd.manufacturerName
	log.debug "productId:        ${cmd.productId}"
	log.debug "productTypeId:    ${cmd.productTypeId}"
	def msr = String.format("%04X-%04X-%04X", cmd.manufacturerId, cmd.productTypeId, cmd.productId)
	updateDataValue("MSR", msr)	
    sendEvent([descriptionText: "$device.displayName MSR: $msr", isStateChange: false])
}

def zwaveEvent(hubitat.zwave.commands.versionv1.VersionReport cmd) {
	def fw = "${cmd.applicationVersion}.${cmd.applicationSubVersion}"
	updateDataValue("fw", fw)
	if (logEnable) log.debug "---VERSION REPORT V1--- ${device.displayName} is running firmware version: $fw, Z-Wave version: ${cmd.zWaveProtocolVersion}.${cmd.zWaveProtocolSubVersion}"
}

def zwaveEvent(hubitat.zwave.commands.hailv1.Hail cmd) {
	[name: "hail", value: "hail", descriptionText: "Switch button was pressed", displayed: false]
}


def zwaveEvent(hubitat.zwave.commands.notificationv3.NotificationReport cmd)
{
	if (logEnable) log.debug "---NOTIFICATION REPORT V3--- ${device.displayName} sent ${cmd}"
	def result = []
	if (cmd.notificationType == 0x07) {
		if ((cmd.event == 0x00)) { 
			result << createEvent(name: "motion", value: "inactive", descriptionText: "$device.displayName motion has stopped", isStateChange: true)
		} else if (cmd.event == 0x08) {
			result << createEvent(name: "motion", value: "active", descriptionText: "$device.displayName detected motion", isStateChange: true)
		} 
	} 
	result
}

def zwaveEvent(hubitat.zwave.Command cmd) {
    log.warn "${device.displayName} received unhandled command: ${cmd}"
}

def on() {
	//sendEvent(name: "switch", value: "on")
	delayBetween([
		zwave.basicV1.basicSet(value: 0xFF).format(),
		zwave.switchBinaryV1.switchBinaryGet().format()
	],100)
}

def off() {
	//sendEvent(name: "switch", value: "off")
	delayBetween([
		zwave.basicV1.basicSet(value: 0x00).format(),
		zwave.switchBinaryV1.switchBinaryGet().format()
	],100)
}

def poll() {
	delayBetween([
		zwave.switchBinaryV1.switchBinaryGet().format(),
		zwave.manufacturerSpecificV1.manufacturerSpecificGet().format()
	],100)
}

/**
  * PING is used by Device-Watch in attempt to reach the Device
**/
def ping() {
		refresh()
}

def refresh() {
	log.debug "refresh() is called"
    delayBetween([
        zwave.switchBinaryV1.switchBinaryGet().format(),
		zwave.notificationV3.notificationGet(notificationType: 7).format()    
	],100)
}

def toggleMode() {
	if (logEnable) log.debug "Toggling Mode" 
    def cmds = []
    if (device.currentValue("operatingMode") == "Manual") { 
    	cmds << zwave.configurationV1.configurationSet(configurationValue: [2] , parameterNumber: 3, size: 1).format()
    }
    else if (device.currentValue("operatingMode") == "Vacancy") {
    	cmds << zwave.configurationV1.configurationSet(configurationValue: [3], parameterNumber: 3, size: 1).format()
    }
    else if (device.currentValue("operatingMode") == "Occupancy") {
    	cmds << zwave.configurationV1.configurationSet(configurationValue: [1], parameterNumber: 3, size: 1).format()
    }
    cmds << zwave.configurationV1.configurationGet(parameterNumber: 3).format()
        delayBetween(cmds, 1000)

}

def SetModeNumber(value) {
	if (logEnable) log.debug "Setting mode by number: ${value}"
    def cmds = []
    	cmds << zwave.configurationV1.configurationSet(configurationValue: [value] , parameterNumber: 3, size: 1).format()
  		cmds << zwave.configurationV1.configurationGet(parameterNumber: 3).format()
    delayBetween(cmds, 1000)

}

def Occupancy() {
def cmds = []
    cmds << zwave.configurationV1.configurationSet(configurationValue: [3] , parameterNumber: 3, size: 1).format()
    cmds << zwave.configurationV1.configurationGet(parameterNumber: 3).format()
    delayBetween(cmds, 1000)

}

def Vacancy() {
def cmds = []
    cmds << zwave.configurationV1.configurationSet(configurationValue: [2] , parameterNumber: 3, size: 1).format()
    cmds << zwave.configurationV1.configurationGet(parameterNumber: 3).format()
        delayBetween(cmds, 1000)

}

def Manual() {
def cmds = []
    cmds << zwave.configurationV1.configurationSet(configurationValue: [1] , parameterNumber: 3, size: 1).format()
    cmds << zwave.configurationV1.configurationGet(parameterNumber: 3).format()
        delayBetween(cmds, 1000)

}

def LightSenseOn() {
	if (logEnable) log.debug "Setting Light Sense On"
    def cmds = []
    	cmds << zwave.configurationV1.configurationSet(configurationValue: [1], parameterNumber: 14, size: 1).format()
    delayBetween(cmds, 1000)
}

def LightSenseOff() {
	if (logEnable) log.debug "Setting Light Sense Off"
    def cmds = []
    	cmds << zwave.configurationV1.configurationSet(configurationValue: [0], parameterNumber: 14, size: 1).format()
    delayBetween(cmds, 1000)
}

def installed() {
}

def updated() {
    log.info "updated..."
    log.warn "debug logging is: ${logEnable == true}"
    log.warn "description logging is: ${txtEnable == true}"
    if (logEnable) runIn(1800,logsOff)

    if (state.lastUpdated && now() <= state.lastUpdated + 3000) return
    state.lastUpdated = now()

	def cmds = []
	//switch and dimmer settings
        if (settings.timeoutduration) {cmds << zwave.configurationV1.configurationSet(configurationValue: [settings.timeoutduration.toInteger()], parameterNumber: 1, size: 1).format()}
        cmds << zwave.configurationV1.configurationGet(parameterNumber: 1).format()
        if (settings.motionsensitivity) {cmds << zwave.configurationV1.configurationSet(configurationValue: [settings.motionsensitivity.toInteger()], parameterNumber: 13, size: 1).format()}
        cmds << zwave.configurationV1.configurationGet(parameterNumber: 13).format()
        if (settings.lightsense) {cmds << zwave.configurationV1.configurationSet(configurationValue: [settings.lightsense.toInteger()], parameterNumber: 14, size: 1).format()}
        cmds << zwave.configurationV1.configurationGet(parameterNumber: 14).format()
        if (settings.resetcycle) {cmds << zwave.configurationV1.configurationSet(configurationValue: [settings.resetcycle.toInteger()], parameterNumber: 15, size: 1).format()}
        cmds << zwave.configurationV1.configurationGet(parameterNumber: 15).format()
        if (settings.operationmode) {cmds << zwave.configurationV1.configurationSet(configurationValue: [settings.operationmode.toInteger()], parameterNumber: 3, size: 1).format()}
        cmds << zwave.configurationV1.configurationGet(parameterNumber: 3).format()
        if (settings.motion) {cmds << zwave.configurationV1.configurationSet(configurationValue: [settings.motion.toInteger()], parameterNumber: 6, size: 1).format()}
        cmds << zwave.configurationV1.configurationGet(parameterNumber: 6).format()
        if (settings.invertSwitch) {cmds << zwave.configurationV1.configurationSet(configurationValue: [settings.invertSwitch.toInteger()], parameterNumber: 5, size: 1).format()}
        cmds << zwave.configurationV1.configurationGet(parameterNumber: 5).format()
		
        cmds << zwave.associationV1.associationSet(groupingIdentifier:0, nodeId:zwaveHubNodeId).format()
        cmds << zwave.associationV1.associationSet(groupingIdentifier:1, nodeId:zwaveHubNodeId).format()
		cmds << zwave.associationV1.associationRemove(groupingIdentifier:2, nodeId:zwaveHubNodeId).format()
		cmds << zwave.associationV1.associationSet(groupingIdentifier:3, nodeId:zwaveHubNodeId).format()
        
        //association groups
		def nodes = []
		if (settings.requestedGroup2 != state.currentGroup2) {
    	    nodes = parseAssocGroupList(settings.requestedGroup2, 2)
        	cmds << zwave.associationV2.associationRemove(groupingIdentifier: 2, nodeId: []).format()
        	cmds << zwave.associationV2.associationSet(groupingIdentifier: 2, nodeId: nodes).format()
        	cmds << zwave.associationV2.associationGet(groupingIdentifier: 2).format()
        	state.currentGroup2 = settings.requestedGroup2
    	}

    	if (settings.requestedGroup3 != state.currentGroup3) {
        	nodes = parseAssocGroupList(settings.requestedGroup3, 3)
        	cmds << zwave.associationV2.associationRemove(groupingIdentifier: 3, nodeId: []).format()
        	cmds << zwave.associationV2.associationSet(groupingIdentifier: 3, nodeId: nodes).format()
        	cmds << zwave.associationV2.associationGet(groupingIdentifier: 3).format()
        	state.currentGroup3 = settings.requestedGroup3
    	}
        delayBetween(cmds, 500)
}

def configure() {
        def cmds = []
		cmds << zwave.associationV1.associationSet(groupingIdentifier:0, nodeId:zwaveHubNodeId).format()
        cmds << zwave.associationV1.associationSet(groupingIdentifier:1, nodeId:zwaveHubNodeId).format()
		cmds << zwave.associationV1.associationRemove(groupingIdentifier:2, nodeId:zwaveHubNodeId).format()
		cmds << zwave.associationV1.associationSet(groupingIdentifier:3, nodeId:zwaveHubNodeId).format()
        delayBetween(cmds, 500)
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
            }
            else if (node.matches("\\p{XDigit}+")) {
                def nodeId = Integer.parseInt(node,16)
                if (nodeId == zwaveHubNodeId) {
                	log.warn "Association Group ${group}: Adding the hub as an association is not allowed (it would break double-tap)."
                }
                else if ( (nodeId > 0) & (nodeId < 256) ) {
                    nodes << nodeId
                    count++
                }
                else {
                    log.warn "Association Group ${group}: Invalid member: ${node}"
                }
            }
            else {
                log.warn "Association Group ${group}: Invalid member: ${node}"
            }
        }
    }  
    return nodes
}
def logsOff(){
    log.warn "debug logging disabled..."
    device.updateSetting("logEnable",[value:"false",type:"bool"])
}
