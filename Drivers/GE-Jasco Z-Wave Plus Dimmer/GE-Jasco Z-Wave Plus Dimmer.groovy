/**
 *  IMPORT URL: https://raw.githubusercontent.com/Botched1/Hubitat/master/Drivers/GE-Jasco%20Z-Wave%20Plus%20Dimmer/GE-Jasco%20Z-Wave%20Plus%20Dimmer.groovy
 *
 *  GE Z-Wave Plus Dimmer
 *
 *
 *  Original based off of the Dimmer Switch under Templates in the IDE 
 *  Original custom DTH Author: Matt Lebaugh (@mlebaugh)
 *
 *  HUBITAT PORT
 *  1.0.0 (01/29/2019) - Ported to Hubitat by Jason Bottjen. Removed ST specifics, removed Polling and Health Check capabilities.
 *  1.1.0 (01/30/2019) - Fixed missing parenthesis in setLevel, and fixed an issue where switch on events were created every time dimmer level changed.                       
 *  1.2.0 (01/31/2019) - Redid CRC16 section based on Hubitat example to try and fix CRC16 errors
 *  1.3.0 (01/31/2019) - Added multilevelget to refresh(), tweaked on/off refresh on long delay. May help some scenarios
 *  1.4.0 (02/26/2019) - Revamped, moving most commands back to preferences. Removed all on/off steps and duration settings. Removed indicator capability. Removed doubletap commands.
 *  1.5.0 (03/03/2019) - Removed unneeded functions, changed preferences format to be consistent with switch driver
 *  1.6.0 (03/03/2019) - Yet another attempt to get CRC16 encapsulation working correctly
 *  1.6.1 (03/03/2019) - Yet another attempt to get CRC16 encapsulation working correctly
 */

metadata {
	definition (name: "GE Z-Wave Plus Dimmer", namespace: "Botched1", author: "Jason Bottjen") {
		capability "Actuator"
		capability "PushableButton"
		capability "DoubleTapableButton"
		capability "Configuration"
		capability "Refresh"
		capability "Sensor"
		capability "Switch"
		capability "Switch Level"
		capability "Light"
		
        fingerprint mfr:"0063", prod:"4944", model:"3038", ver: "5.26", deviceJoinName: "GE Z-Wave Plus Wall Dimmer"
	    fingerprint mfr:"0063", prod:"4944", model:"3038", ver: "5.27", deviceJoinName: "GE Z-Wave Plus Wall Dimmer"
	    fingerprint mfr:"0063", prod:"4944", model:"3038", ver: "5.28", deviceJoinName: "GE Z-Wave Plus Wall Dimmer"
	    fingerprint mfr:"0063", prod:"4944", model:"3038", ver: "5.29", deviceJoinName: "GE Z-Wave Plus Wall Dimmer"
        fingerprint mfr:"0063", prod:"4944", model:"3039", ver: "5.19", deviceJoinName: "GE Z-Wave Plus 1000W Wall Dimmer"
        fingerprint mfr:"0063", prod:"4944", model:"3130", ver: "5.21", deviceJoinName: "GE Z-Wave Plus Toggle Dimmer"
        fingerprint mfr:"0063", prod:"4944", model:"3135", ver: "5.26", deviceJoinName: "Jasco Z-Wave Plus Wall Dimmer"
        fingerprint mfr:"0063", prod:"4944", model:"3136", ver: "5.21", deviceJoinName: "Jasco Z-Wave Plus 1000W Wall Dimmer"
        fingerprint mfr:"0063", prod:"4944", model:"3137", ver: "5.20", deviceJoinName: "Jasco Z-Wave Plus Toggle Dimmer"
	}

 preferences {

	        input (
            type: "paragraph",
            element: "paragraph",
            title: "Dimmer General Settings",
            description: ""
        )

	    input "paramLED", "enum", title: "LED Behavior", multiple: false, options: ["0" : "LED ON When Switch OFF (default)", "1" : "LED ON When Switch ON", "2" : "LED Always OFF"], required: false, displayDuringSetup: true
	    input "paramInverted", "enum", title: "Dimmer Buttons Direction", multiple: false, options: ["0" : "Normal (default)", "1" : "Inverted"], required: false, displayDuringSetup: true

	        input (
            type: "paragraph",
            element: "paragraph",
            title: "Dimmer Timing Settings. Total dimming time = steps*duration",
            description: ""
        )
	 
	    input "paramZSteps", "number", title: "Z-Wave Dimming Steps", multiple: false, defaultValue: "1", range: "1..99", required: false, displayDuringSetup: true
		input "paramZDuration", "number", title: "Z-Wave Dimming Duration (in 10ms increments)", multiple: false, defaultValue: "3", range: "1..255", required: false, displayDuringSetup: true
	    input "paramPSteps", "number", title: "Physical Dimming Steps", multiple: false, defaultValue: "1", range: "1..99", required: false, displayDuringSetup: true
		input "paramPDuration", "number", title: "Physical Dimming Duration (in 10ms increments)", multiple: false, defaultValue: "3", range: "1..255", required: false, displayDuringSetup: true
	 
        input (
            type: "paragraph",
            element: "paragraph",
            title: "Association Groups",
            description: "Devices in group 2 will turn on/off when the switch is turned on or off.\n\n" +
                         "Devices in group 3 will turn on/off when the switch is double tapped up or down.\n\n" +
                         "Devices are entered as a comma delimited list of IDs in hexadecimal format."
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
			input ( type: "paragraph", element: "paragraph", title: "", description: "Logging")
			input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: true		
    }
}

/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Parse
/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
def parse(String description) {
    def result = null
	if (description != "updated") {
		if (logEnable) log.debug "parse() >> zwave.parse($description)"
		def cmd = zwave.parse(description, [0x20: 1, 0x25: 1, 0x56: 1, 0x70: 2, 0x72: 2, 0x85: 2])

		if (logEnable) log.debug "cmd: $cmd"
		
		if (cmd) {
			result = zwaveEvent(cmd)
        }
	}
    if (!result) { log.warn "Parse returned ${result} for $description" }
    else {if (logEnable) log.debug "Parse returned ${result}"}
	
	return result
}

/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Z-Wave Messages
/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
def zwaveEvent(hubitat.zwave.commands.crc16encapv1.Crc16Encap cmd) {
	log.warn("zwaveEvent(): CRC-16 Encapsulation Command received: ${cmd}")
	
	def newVersion = 1
	
	// SwitchMultilevel = 38 decimal
	// Configuration = 112 decimal
	// Manufacturer Specific = 114 decimal
	// Association = 133 decimal
	if (cmd.commandClass == 38) {newVersion = 3}
	if (cmd.commandClass == 112) {newVersion = 2}
	if (cmd.commandClass == 114) {newVersion = 2}								 
	if (cmd.commandClass == 133) {newVersion = 2}		
	
	def encapsulatedCommand = zwave.getCommand(cmd.commandClass, cmd.command, cmd.data, newVersion)
	if (encapsulatedCommand) {
       zwaveEvent(encapsulatedCommand)
   } else {
       log.warn "Unable to extract CRC16 command from ${cmd}"
   }
}

def zwaveEvent(hubitat.zwave.commands.basicv1.BasicReport cmd) {
    log.debug "---BASIC REPORT V1--- ${device.displayName} sent ${cmd}"
	//createEvent(name: "switch", value: cmd.value ? "on" : "off", isStateChange: true)
}

def zwaveEvent(hubitat.zwave.commands.basicv1.BasicSet cmd) {
	
    if (logEnable) log.debug "---BASIC SET V1--- ${device.displayName} sent ${cmd}"
	def result = []
	
	if (cmd.value == 255) {
		if (logEnable) log.debug "Double Up Triggered"
		result << createEvent([name: "doubleTapped", value: 1, descriptionText: "Doubletap up (button 1) on $device.displayName", isStateChange: true])
    }
	else if (cmd.value == 0) {
		if (logEnable) log.debug "Double Down Triggered"
		result << createEvent([name: "doubleTapped", value: 2, descriptionText: "Doubletap down (button 2) on $device.displayName", isStateChange: true])
    }

    return result
}

def zwaveEvent(hubitat.zwave.commands.associationv2.AssociationReport cmd) {
	if (logEnable) log.debug "---ASSOCIATION REPORT V2--- ${device.displayName} sent groupingIdentifier: ${cmd.groupingIdentifier} maxNodesSupported: ${cmd.maxNodesSupported} nodeId: ${cmd.nodeId} reportsToFollow: ${cmd.reportsToFollow}"
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
	def name = ""
    def value = ""
    def reportValue = cmd.configurationValue[0]
    switch (cmd.parameterNumber) {
        case 3:
            name = "indicatorStatus"
            value = reportValue == 1 ? "when on" : reportValue == 2 ? "never" : "when off"
            break
        case 4:
            name = "inverted"
            value = reportValue == 1 ? "true" : "false"
            break
        default:
            break
    }
	result << createEvent([name: name, value: value, displayed: false])
	return result
}

def zwaveEvent(hubitat.zwave.commands.switchbinaryv1.SwitchBinaryReport cmd) {
    if (logEnable) log.debug "---BINARY SWITCH REPORT V1--- ${device.displayName} sent ${cmd}"
    
	def desc
	
	if (cmd.value == 255) {
		desc = "Switch turned ON"
	}
	else if (cmd.value == 0) {
		desc = "Switch turned OFF"	
	}
	createEvent([name: "switch", value: cmd.value ? "on" : "off", descriptionText: "$desc", isStateChange: true])
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
	log.warn "Hail command received..."
	[name: "hail", value: "hail", descriptionText: "Switch button was pressed", displayed: false]
}

def zwaveEvent(hubitat.zwave.commands.switchmultilevelv3.SwitchMultilevelReport cmd) {
	if (logEnable) log.debug "---SwitchMultilevelReport V3---  ${device.displayName} sent ${cmd}"
	if (cmd.value) {
		sendEvent(name: "level", value: cmd.value, unit: "%")
		if (device.currentValue("switch") == "off") {sendEvent(name: "switch", value: "on", isStateChange: true)}
	} else {
		if (device.currentValue("switch") == "on") {sendEvent(name: "switch", value: "off", isStateChange: true)}
	}
}

def zwaveEvent(hubitat.zwave.commands.switchmultilevelv3.SwitchMultilevelSet cmd) {
	log.warn "SwitchMultilevelSet V3 Called. This doesn't do anything righnt now."
}

def zwaveEvent(hubitat.zwave.Command cmd) {
    log.warn "${device.displayName} received unhandled command: ${cmd}"
}

/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Driver Commands / Functions
/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
def on() {
	if (logEnable) log.debug "Turn device ON"
	def cmds = []
    sendEvent(name: "switch", value: "on", isStateChange: true)
	cmds << zwave.basicV1.basicSet(value: 0xFF).format()
   	cmds << zwave.switchMultilevelV2.switchMultilevelGet().format()
	delayBetween(cmds, 3000)
}

def off() {
	if (logEnable) log.debug "Turn device OFF"
	def cmds = []
	sendEvent(name: "switch", value: "off", isStateChange: true)
    cmds << zwave.basicV1.basicSet(value: 0x00).format()
   	cmds << zwave.switchMultilevelV2.switchMultilevelGet().format()
	delayBetween(cmds, 3000)}

def setLevel(value) {
	def valueaux = value as Integer
	def level = Math.max(Math.min(valueaux, 99), 0)
	def currval = device.currentValue("switch")
	def delay = 0
	state.level = level
	
	if (logEnable) log.debug "SetLevel (value) - currval: $currval"
	
	if (level > 0 && currval == "off") {
		sendEvent(name: "switch", value: "on")
	} else if (level == 0 && currval == "on") {
		sendEvent(name: "switch", value: "off")
		delay += 2000
	}
	sendEvent(name: "level", value: level, unit: "%")
	if (settings.paramZSteps) {
		zsteps = settings.paramZSteps
	} else {
		zsteps = 1
	}
	if (settings.paramZDuration) {
		zdelay = settings.paramZDuration
	} else {
		zdelay = 3
	}
    delay = delay + (zsteps * zdelay * 10 + 1000).toInteger()
	if (logEnable) log.debug "setLevel >> value: $level, delay: $delay"
	delayBetween ([
    	zwave.basicV1.basicSet(value: level).format(),
        zwave.switchMultilevelV1.switchMultilevelGet().format()
    ], delay )
}

def setLevel(value, duration) {
	if (logEnable) log.debug "setLevel($value, $duration)"
	def currval = device.currentValue("switch")
	def getStatusDelay = (duration * 1000 + 1000).toInteger()
	value = Math.max(Math.min(value.toInteger(), 99), 0)
	state.level = value
	if (value > 0 && currval == "off") {
		sendEvent(name: "switch", value: "on")
	} else if (value == 0 && currval == "on") {
		sendEvent(name: "switch", value: "off")
		delay += 2000
	}
	sendEvent(name: "level", value: value, unit: "%")
	if (logEnable) log.debug "setLevel(value, duration) >> value: $value, duration: $duration, delay: $getStatusDelay"
	delayBetween ([zwave.switchMultilevelV2.switchMultilevelSet(value: value, dimmingDuration: duration).format(),
				   zwave.switchMultilevelV1.switchMultilevelGet().format()], getStatusDelay)
}

def refresh() {
	log.info "refresh() is called"
	
	def cmds = []
	cmds << zwave.switchBinaryV1.switchBinaryGet().format()
	cmds << zwave.switchMultilevelV1.switchMultilevelGet().format()
    cmds << zwave.configurationV2.configurationGet(parameterNumber: 3).format()
    cmds << zwave.configurationV2.configurationGet(parameterNumber: 4).format()
    cmds << zwave.configurationV2.configurationGet(parameterNumber: 7).format()
    cmds << zwave.configurationV2.configurationGet(parameterNumber: 8).format()
    cmds << zwave.configurationV2.configurationGet(parameterNumber: 9).format()
    cmds << zwave.configurationV2.configurationGet(parameterNumber: 10).format()
	cmds << zwave.associationV2.associationGet(groupingIdentifier: 3).format()
	if (getDataValue("MSR") == null) {
		cmds << zwave.manufacturerSpecificV1.manufacturerSpecificGet().format()
	}
	delayBetween(cmds,1000)
}

def installed() {
	configure()
}

def updated() {
    log.info "updated..."
    log.warn "debug logging is: ${logEnable == true}"
    log.warn "description logging is: ${txtEnable == true}"
    if (logEnable) runIn(1800,logsOff)

    if (state.lastUpdated && now() <= state.lastUpdated + 3000) return
    state.lastUpdated = now()

	def cmds = []
    cmds << zwave.associationV1.associationSet(groupingIdentifier:1, nodeId:zwaveHubNodeId).format()
	cmds << zwave.associationV1.associationRemove(groupingIdentifier:2, nodeId:zwaveHubNodeId).format()
	cmds << zwave.associationV1.associationSet(groupingIdentifier:3, nodeId:zwaveHubNodeId).format()

	//associations
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
	
	// Set LED param
	cmds << zwave.configurationV2.configurationSet(scaledConfigurationValue: paramLED.toInteger(), parameterNumber: 3, size: 1).format()
	cmds << zwave.configurationV2.configurationGet(parameterNumber: 3).format()
	
	// Set Inverted param
	cmds << zwave.configurationV2.configurationSet(scaledConfigurationValue: paramInverted.toInteger(), parameterNumber: 4, size: 1).format()
	cmds << zwave.configurationV2.configurationGet(parameterNumber: 4).format()
	
	// Set Z Steps
	cmds << zwave.configurationV2.configurationSet(scaledConfigurationValue: paramZSteps.toInteger(), parameterNumber: 7, size: 1).format()
	cmds << zwave.configurationV2.configurationGet(parameterNumber: 7).format()
	
	// Set Z Duration
	cmds << zwave.configurationV2.configurationSet(scaledConfigurationValue: paramZDuration.toInteger(), parameterNumber: 8, size: 2).format()
	cmds << zwave.configurationV2.configurationGet(parameterNumber: 8).format()
	
	// Set P Steps
	cmds << zwave.configurationV2.configurationSet(scaledConfigurationValue: paramPSteps.toInteger(), parameterNumber: 9, size: 1).format()
	cmds << zwave.configurationV2.configurationGet(parameterNumber: 9).format()
	
	// Set P Duration
	cmds << zwave.configurationV2.configurationSet(scaledConfigurationValue: paramPDuration.toInteger(), parameterNumber: 10, size: 2).format()
	cmds << zwave.configurationV2.configurationGet(parameterNumber: 10).format()

    delayBetween(cmds, 500)
}

def configure() {
        log.info "configure triggered"
		def cmds = []
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
                log.warn "Association Group ${group}: Too many members. Greater than ${max}! This one was discarded: ${node}"
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
