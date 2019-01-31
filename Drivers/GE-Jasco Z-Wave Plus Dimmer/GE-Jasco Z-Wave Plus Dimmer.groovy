/**
 *  GE Z-Wave Plus Dimmer
 *
 *
 *  Original based off of the Dimmer Switch under Templates in the IDE 
 *  Original custom DTH Author: Matt Lebaugh (@mlebaugh)
 *
 *  HUBITAT PORT
 *  1.0.0 (01/29/2019) - Ported to Hubitat by Jason Bottjen. Removed ST specifics, removed Polling and Health Check capabilities.
 *  1.1.0 (01/30/2019) - Fixed missing parenthesis in setLevel, and fixed an issue where switch on events were created every time dimmer level changed.                       
 */

metadata {
	definition (name: "GE Z-Wave Plus Dimmer", namespace: "Botched1", author: "Jason Bottjen") {
		capability "Actuator"
		capability "PushableButton"
		capability "DoubleTapableButton"
		capability "Configuration"
		capability "Indicator"
		capability "Refresh"
		capability "Sensor"
		capability "Switch"
		capability "Switch Level"
		capability "Light"
		
	    command "doubletapUp"
        command "doubletapDown"
        command "inverted"
        command "notInverted"

		attribute "inverted", "enum", ["inverted", "not inverted"]
        attribute "zwaveSteps", "number"
        attribute "zwaveDelay", "number"
        attribute "manualSteps", "number"
        attribute "manualDelay", "number"
        attribute "allSteps", "number"
        attribute "allDelay", "number"
        command "setZwaveSteps", ["number"]
        command "setZwaveDelay", ["number"]
        command "setManualSteps", ["number"]
        command "setManualDelay", ["number"]
        command "setAllSteps", ["number"]
        command "setAllDelay", ["number"]
    
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

def zwaveEvent(hubitat.zwave.commands.crc16encapv1.Crc16Encap cmd) {
	log.warn("zwaveEvent(): CRC-16 Encapsulation Command received: ${cmd}")
	def encapsulatedCommand = zwave.commandClass(cmd.commandClass)?.command(cmd.command)?.parse(cmd.data)
	if (!encapsulatedCommand) {
		log.warn("zwaveEvent(): Could not extract command from ${cmd}")
	} else {
		return zwaveEvent(encapsulatedCommand)
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

def zwaveEvent(hubitat.zwave.Command cmd) {
    log.warn "${device.displayName} received unhandled command: ${cmd}"
}

def on() {
	if (logEnable) log.debug "Turn device ON"
	def cmds = []
	def zsteps
	def zdelay
    cmds << zwave.basicV1.basicSet(value: 0xFF).format()
   	cmds << zwave.switchMultilevelV2.switchMultilevelGet().format()
	if (device.currentValue("zwaveSteps")) {
		zsteps = device.currentValue("zwaveSteps")
	} else {
		zsteps = 1
	}
	if (device.currentValue("zwaveDelay")) {
		zdelay = device.currentValue("zwaveDelay")
	} else {
		zdelay = 3
	}
	
    def delay = (zsteps * zdelay * 10 + 3000).toInteger()
	if (logEnable) log.debug "delay: $delay"
	delayBetween(cmds, delay)
	
}

def off() {
	if (logEnable) log.debug "Turn device OFF"
	def cmds = []
    cmds << zwave.basicV1.basicSet(value: 0x00).format()
   	cmds << zwave.switchMultilevelV2.switchMultilevelGet().format()
		if (device.currentValue("zwaveSteps")) {
		zsteps = device.currentValue("zwaveSteps")
	} else {
		zsteps = 1
	}
	if (device.currentValue("zwaveDelay")) {
		zdelay = device.currentValue("zwaveDelay")
	} else {
		zdelay = 3
	}
    def delay = (zsteps * zdelay * 10 + 3000).toInteger()
	if (logEnable) log.debug "delay: $delay"
	delayBetween(cmds, delay)}

def setLevel(value) {
	def valueaux = value as Integer
	def level = Math.max(Math.min(valueaux, 99), 0)
	def currval = device.currentValue("switch")
	state.level = level
	
	if (logEnable) log.debug "SetLevel (value) - currval: $currval"
	
	if (level > 0 && currval == "off") {
		sendEvent(name: "switch", value: "on")
	} else if (level == 0 && currval == "on") {
		sendEvent(name: "switch", value: "off")
	}
	sendEvent(name: "level", value: level, unit: "%")
	if (device.currentValue("zwaveSteps")) {
		zsteps = device.currentValue("zwaveSteps")
	} else {
		zsteps = 1
	}
	if (device.currentValue("zwaveDelay")) {
		zdelay = device.currentValue("zwaveDelay")
	} else {
		zdelay = 3
	}
    def delay = (zsteps * zdelay * 10 + 3000).toInteger()
	if (logEnable) log.debug "setLevel >> value: $level, delay: $delay"
	delayBetween ([
    	zwave.basicV1.basicSet(value: level).format(),
        zwave.switchMultilevelV1.switchMultilevelGet().format()
    ], delay )
}

def setLevel(value, duration) {
	def valueaux = value as Integer
	def level = Math.max(Math.min(valueaux, 99), 0)
	state.level = level
	def dimmingDuration = (duration < 128 ? duration : 128 + Math.round(duration / 60)).toInteger()
	def getStatusDelay = (duration < 128 ? (duration*1000)+2000 : (Math.round(duration / 60)*60*1000)+3000).toInteger()    
	if (logEnable) log.debug "setLevel(value, duration) >> value: $level, duration: $duration, delay: $getStatusDelay"
	delayBetween ([zwave.switchMultilevelV2.switchMultilevelSet(value: level, dimmingDuration: dimmingDuration).format(),
				   zwave.switchMultilevelV1.switchMultilevelGet().format()], getStatusDelay)
}

def zwaveEvent(hubitat.zwave.commands.switchmultilevelv3.SwitchMultilevelReport cmd) {
	if (logEnable) log.debug "SwitchMultilevelReport"
	dimmerEvents(cmd)
}

def zwaveEvent(hubitat.zwave.commands.switchmultilevelv3.SwitchMultilevelSet cmd) {
	if (logEnable) log.debug "SwitchMultilevelSet"
	dimmerEvents(cmd)
}

private dimmerEvents(hubitat.zwave.Command cmd) {
	if (logEnable) log.debug "dimmerEvents"
	if (logEnable) log.debug "device.currentValue(switch): " + device.currentValue("switch")
	def currvalue = device.currentValue("switch")
	def value = (cmd.value ? "on" : "off")
	def result = []
	if (currvalue != value) {
		result << createEvent(name: "switch", value: value, isStateChange: true)
	}
	if (cmd.value && cmd.value <= 100) {
		result << createEvent(name: "level", value: cmd.value, unit: "%")
	}
	return result
}

def indicatorWhenOn() {
	if (logEnable) log.debug "indicatorWhenOn"
	sendEvent(name: "indicatorStatus", value: "when on", display: false)
	zwave.configurationV2.configurationSet(configurationValue: [1], parameterNumber: 3, size: 1).format()
}

def indicatorWhenOff() {
	if (logEnable) log.debug "indicatorWhenOff"
	sendEvent(name: "indicatorStatus", value: "when off", display: false)
	zwave.configurationV2.configurationSet(configurationValue: [0], parameterNumber: 3, size: 1).format()
}

def indicatorNever() {
	if (logEnable) log.debug "indicatorNever"
	sendEvent(name: "indicatorStatus", value: "never", display: false)
	zwave.configurationV2.configurationSet(configurationValue: [2], parameterNumber: 3, size: 1).format()
}

def refresh() {
	log.info "refresh() is called"
	
	def cmds = []
	cmds << zwave.switchBinaryV1.switchBinaryGet().format()
    cmds << zwave.configurationV2.configurationGet(parameterNumber: 3).format()
    cmds << zwave.configurationV2.configurationGet(parameterNumber: 4).format()
    cmds << zwave.associationV2.associationGet(groupingIdentifier: 3).format()
	if (getDataValue("MSR") == null) {
		cmds << zwave.manufacturerSpecificV1.manufacturerSpecificGet().format()
	}
	delayBetween(cmds,1000)
}

def installed() {
	configure()
}

def doubletapUp() {
	if (logEnable) log.debug "Doubletap Up Triggered"
	sendEvent(name: "doubleTapped", value: 1, descriptionText: "Doubletap up (button 1) on $device.displayName", isStateChange: true)
}

def doubletapDown() {
	if (logEnable) log.debug "Doubletap Down Triggered"
	sendEvent(name: "doubleTapped", value: 2, descriptionText: "Doubletap down (button 2) on $device.displayName", isStateChange: true)
}

def inverted() {
	if (logEnable) log.debug "Inverted Triggered"
	sendEvent(name: "inverted", value: "inverted", display: false)
	zwave.configurationV2.configurationSet(configurationValue: [1], parameterNumber: 4, size: 1).format()
}

def notInverted() {
	if (logEnable) log.debug "Not Inverted Triggered"
	sendEvent(name: "inverted", value: "not inverted", display: false)
	zwave.configurationV2.configurationSet(configurationValue: [0], parameterNumber: 4, size: 1).format()
}

def setZwaveSteps(steps) {
	steps = Math.max(Math.min(steps.toInteger(), 99), 1)
	sendEvent(name: "zwaveSteps", value: steps, displayed: false)	
	if (logEnable) log.debug "setZwaveSteps steps: $steps"	
	zwave.configurationV2.configurationSet(configurationValue: [steps], parameterNumber: 7, size: 1).format()
}

def setZwaveDelay(delay) {
	delay = Math.max(Math.min(delay.toInteger(), 255), 1)
	sendEvent(name: "zwaveDelay", value: delay, displayed: false)
	if (logEnable) log.debug "setZwaveDelay delay: $delay"
	zwave.configurationV2.configurationSet(configurationValue: [0,delay], parameterNumber: 8, size: 2).format()
}

def setManualSteps(steps) {
	steps = Math.max(Math.min(steps.toInteger(), 99), 1)
	sendEvent(name: "manualSteps", value: steps, displayed: false)	
	if (logEnable) log.debug "setManualSteps steps: $steps"	
	zwave.configurationV2.configurationSet(configurationValue: [steps], parameterNumber: 9, size: 1).format()
}

def setManualDelay(delay) {
	delay = Math.max(Math.min(delay.toInteger(), 255), 1)
	sendEvent(name: "manualDelay", value: delay, displayed: false)
	if (logEnable) log.debug "setManualDelay delay: $delay"
	zwave.configurationV2.configurationSet(configurationValue: [0,delay], parameterNumber: 10, size: 2).format()
}

def setAllSteps(steps) {
	steps = Math.max(Math.min(steps.toInteger(), 99), 1)
	sendEvent(name: "allSteps", value: steps, displayed: false)
	if (logEnable) log.debug "setAllSteps steps: $steps"	
	zwave.configurationV2.configurationSet(configurationValue: [steps], parameterNumber: 11, size: 1).format()
}

def setAllDelay(delay) {
	delay = Math.max(Math.min(delay.toInteger(), 255), 1)
	sendEvent(name: "allDelay", value: delay, displayed: false)
	if (logEnable) log.debug "setAllDelay delay: $delay"
	zwave.configurationV2.configurationSet(configurationValue: [0,delay], parameterNumber: 12, size: 2).format()
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
