/**
 *  IMPORT URL: https://raw.githubusercontent.com/Botched1/Hubitat/master/Drivers/GE-Jasco%20Z-Wave%20Plus%20Fan%20Control/GE%20Z-Wave%20Plus%20Fan%20Control.groovy
 *
 *  GE Z-Wave Plus Fan Control
 *
 *
 *  Original based off of the Dimmer Switch under Templates in the IDE 
 *
 *  VERSION HISTORY
 *  1.0.0 (04/06/2019) - Initial Version
 *  1.0.1 (04/07/2019) - Fixed issue where "speed" wasn't calculated when speed changed from physical switch.
 */

metadata {
	definition (name: "GE Z-Wave Plus Fan Control", namespace: "Botched1", author: "Jason Bottjen") {
		capability "Actuator"
		capability "PushableButton"
		capability "DoubleTapableButton"
		capability "Configuration"
		capability "Refresh"
		capability "Switch"
		capability "SwitchLevel"
		capability "FanControl"
		
		attribute "speed", "enum", ["low","medium-low","medium","medium-high","high","on","off","auto"]
	}

 preferences {

	        input (
            type: "paragraph",
            element: "paragraph",
            title: "Fan Control General Settings",
            description: ""
        )

	    input "paramInverted", "enum", title: "Fan Buttons Direction", multiple: false, options: ["0" : "Normal (default)", "1" : "Inverted"], required: false, displayDuringSetup: true
		input "paramLOW", "number", title: "Low Speed Fan %", multiple: false, defaultValue: "20",  range: "1..99", required: false, displayDuringSetup: true
		input "paramMEDLOW", "number", title: "Medium-Low Speed Fan %", multiple: false, defaultValue: "40",  range: "1..99", required: false, displayDuringSetup: true
		input "paramMED", "number", title: "Medium Speed Fan %", multiple: false, defaultValue: "60",  range: "1..99", required: false, displayDuringSetup: true
		input "paramMEDHIGH", "number", title: "Medium-High Speed Fan %", multiple: false, defaultValue: "80",  range: "1..99", required: false, displayDuringSetup: true
		input "paramHIGH", "number", title: "High Speed Fan %", multiple: false, defaultValue: "99",  range: "1..99", required: false, displayDuringSetup: true
			
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
			input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: false	
			input name: "logDesc", type: "bool", title: "Enable descriptionText logging", defaultValue: true	
	 
    }
}

/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Parse
/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
def parse(String description) {
    def result = null
	if (description != "updated") {
		if (logEnable) log.debug "parse() >> zwave.parse($description)"
		def cmd = zwave.parse(description, [0x70: 1])

		if (logEnable) log.debug "cmd: $cmd"
		
		if (cmd) {
			result = zwaveEvent(cmd)
        }
	}
    if (!result) { if (logEnable) log.debug "Parse returned ${result} for $description" }
    else {if (logEnable) log.debug "Parse returned ${result}"}
	
	return result
}

/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Z-Wave Messages
/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
def zwaveEvent(hubitat.zwave.commands.crc16encapv1.Crc16Encap cmd) {
	if (logEnable) log.debug "zwaveEvent(): CRC-16 Encapsulation Command received: ${cmd}"
	
	def newVersion = 1
	
	// Manually set the version on commands, as needed based on the device capabilities
	// SwitchMultilevel = 38 decimal
	// Configuration = 112 decimal
	// Manufacturer Specific = 114 decimal
	// Association = 133 decimal
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
}

def zwaveEvent(hubitat.zwave.commands.basicv1.BasicSet cmd) {
	
    if (logEnable) log.debug "---BASIC SET V1--- ${device.displayName} sent ${cmd}"
	def result = []
	
	if (cmd.value == 255) {
		if (logEnable) log.debug "Double Up Triggered"
		if (logDesc) log.info "$device.displayName had Doubletap up (button 1)"
		result << createEvent([name: "doubleTapped", value: 1, descriptionText: "$device.displayName had Doubletap up (button 1)", isStateChange: true])
    }
	else if (cmd.value == 0) {
		if (logEnable) log.debug "Double Down Triggered"
		if (logDesc) log.info "$device.displayName had Doubletap down (button 2)"
		result << createEvent([name: "doubleTapped", value: 2, descriptionText: "$device.displayName had Doubletap down (button 2)", isStateChange: true])
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


def zwaveEvent(hubitat.zwave.commands.configurationv1.ConfigurationReport cmd) {
	if (logEnable) log.debug "---CONFIGURATION REPORT V1--- ${device.displayName} sent ${cmd}"
    def config = cmd.scaledConfigurationValue.toInteger()
    def result = []
	def name = ""
    def value = ""
    def reportValue = cmd.configurationValue[0]
    switch (cmd.parameterNumber) {
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
	log.warn "Hail command received... This doesn't do anything in this driver right now."
}

def zwaveEvent(hubitat.zwave.commands.switchmultilevelv3.SwitchMultilevelReport cmd) {
	if (logEnable) log.debug "---SwitchMultilevelReport V3---  ${device.displayName} sent ${cmd}"
	//state.level
	
	def currSpeed = device.currentValue("speed")
	
	if (cmd.value) {
		sendEvent(name: "level", value: cmd.value, unit: "%", descriptionText: "$device.displayName is " + cmd.value + "%")
		if (logDesc) log.info "$device.displayName is " + cmd.value + "%"
		//
		if (device.currentValue("switch") == "off") {
			sendEvent(name: "switch", value: "on", isStateChange: true, descriptionText: "$device.displayName is on")
			if (logDesc) log.info "$device.displayName is on"
		}
	} else {
		if (device.currentValue("switch") == "on") {
			sendEvent(name: "switch", value: "off", isStateChange: true, , descriptionText: "$device.displayName is off")
			if (logDesc) log.info "$device.displayName is off"
		}
	}

	if (cmd.value==0) {sendEvent([name: "speed", value: "off", descriptionText: "fan speed set to off"])}
	if (cmd.value>0 && cmd.value<=paramLOW) {sendEvent([name: "speed", value: "low", displayed: true, descriptionText: "fan speed set to low"])}
	if (cmd.value>paramLOW && cmd.value<=paramMEDLOW) {sendEvent([name: "speed", value: "medium-low", displayed: true, descriptionText: "fan speed set to medium-low"])}
	if (cmd.value>paramMEDLOW && cmd.value<=paramMED) {sendEvent([name: "speed", value: "medium", displayed: true, descriptionText: "fan speed set to medium"])}
	if (cmd.value>paramMED && cmd.value<=paramMEDHIGH) {sendEvent([name: "speed", value: "medium-high", displayed: true, descriptionText: "fan speed set to medium-high"])}
	if (cmd.value>paramMEDHIGH && cmd.value<=99) {sendEvent([name: "speed", value: "high", displayed: true, descriptionText: "fan speed set to high"])}
}

def zwaveEvent(hubitat.zwave.commands.switchmultilevelv3.SwitchMultilevelSet cmd) {
	log.warn "SwitchMultilevelSet V3 Called. This doesn't do anything in this driver right now."
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
    def currlevel = device.currentValue("level")
	sendEvent(name: "switch", value: "on", isStateChange: true, descriptionText: "$device.displayName is on")
	if (logDesc) log.info "$device.displayName is on"
	
	if (currlevel == null) {currlevel=20}
	if (paramLOW==null) {paramLOW = 20}	
	if (paramMEDLOW==null) {paramMEDLOW = 40}	
	if (paramMED==null) {paramMED = 60}	
	if (paramMEDHIGH==null) {paramMEDHIGH = 80}	
	if (paramHIGH==null) {paramHIGH = 99}
	
	switch (currlevel) {
    case paramLOW:
		if (logEnable) log.debug "Setting Speed to low"	
		sendEvent([name: "speed", value: "low", displayed: true, descriptionText: "fan speed set to low"])		
		break
    case paramMEDLOW:
		if (logEnable) log.debug "Setting Speed to medium-low"	
		sendEvent([name: "speed", value: "medium-low", displayed: true, descriptionText: "fan speed set to medium-low"])		
		break
    case paramMED:
		if (logEnable) log.debug "Setting Speed to medium"	
		sendEvent([name: "speed", value: "medium", displayed: true, descriptionText: "fan speed set to medium"])		
		break
    case paramMEDHIGH:
		if (logEnable) log.debug "Setting Speed to medium-high"	
		sendEvent([name: "speed", value: "medium-high", displayed: true, descriptionText: "fan speed set to medium-high"])		
		break
    case paramHIGH:
		if (logEnable) log.debug "Setting Speed to high"	
		sendEvent([name: "speed", value: "high", displayed: true, descriptionText: "fan speed set to high"])		
		break
	default:
		sendEvent([name: "speed", value: "on", displayed: true, descriptionText: "fan speed set to on"])		
        break
    }
	
	cmds << zwave.basicV1.basicSet(value: 0xFF).format()
   	cmds << zwave.switchMultilevelV2.switchMultilevelGet().format()
	delayBetween(cmds, 3000)
}

def off() {
	if (logEnable) log.debug "Turn device OFF"
	def cmds = []
	sendEvent(name: "switch", value: "off", isStateChange: true, descriptionText: "$device.displayName is off")
	if (logDesc) log.info "$device.displayName is off"
    cmds << zwave.basicV1.basicSet(value: 0x00).format()
   	cmds << zwave.switchMultilevelV2.switchMultilevelGet().format()
	delayBetween(cmds, 3000)}

def setLevel(value) {
	def valueaux = value as Integer
	def level = Math.max(Math.min(valueaux, 99), 0)
	def currval = device.currentValue("switch")
	state.level = level
	
	if (logEnable) log.debug "SetLevel (value) - currval: $currval"
	
	if (level > 0 && currval == "off") {
		sendEvent(name: "switch", value: "on", descriptionText: "$device.displayName is on")
		if (logDesc) log.info "$device.displayName is on"
	} else if (level == 0 && currval == "on") {
		sendEvent(name: "switch", value: "off", descriptionText: "$device.displayName is off")
		if (logDesc) log.info "$device.displayName is off"
	}
	sendEvent(name: "level", value: level, unit: "%", descriptionText: "$device.displayName is " + level + "%")
	
	if (logEnable) log.debug "setLevel >> value: $level"

	delayBetween ([
    	zwave.basicV1.basicSet(value: level).format(),
        zwave.switchMultilevelV1.switchMultilevelGet().format()
    ], 3000 )
}

def setLevel(value, duration) {
	setLevel(value)
}

def setSpeed(fanspeed) {
	if (logEnable) log.debug "fanspeed is $fanspeed"
	def value
	def result
	
	//speed - ENUM ["low","medium-low","medium","medium-high","high","on","off","auto"]
    switch (fanspeed) {
        case "low":
			if (logEnable) log.debug "fanspeed low detected"	
			sendEvent([name: "speed", value: "low", displayed: true, descriptionText: "fan speed set to $fanspeed"])		
			if (paramLOW==null) {paramLOW = 20}	
			value = paramLOW
            setLevel(value)
			break
		case "medium-low":
			if (logEnable) log.debug "fanspeed medium-low detected"	
			sendEvent([name: "speed", value: "medium-low", displayed: true, descriptionText: "fan speed set to $fanspeed"])		
			if (paramMEDLOW==null) {paramMEDLOW = 40}	
			value = paramMEDLOW
			setLevel(value)
            break
		case "medium":
			if (logEnable) log.debug "fanspeed medium detected"	
			sendEvent([name: "speed", value: "medium", displayed: true, descriptionText: "fan speed set to $fanspeed"])		
			if (paramMED==null) {paramMED = 60}	
			value = paramMED
			setLevel(value)
            break
		case "medium-high":
			if (logEnable) log.debug "fanspeed medium-high detected"	
			sendEvent([name: "speed", value: "medium-high", displayed: true, descriptionText: "fan speed set to $fanspeed"])		
			if (paramMEDHIGH==null) {paramMEDHIGH = 80}	
			value = paramMEDHIGH
			setLevel(value)
            break
		case "high":
			if (logEnable) log.debug "fanspeed high detected"	
			sendEvent([name: "speed", value: "high", displayed: true, descriptionText: "fan speed set to $fanspeed"])		
			if (paramHIGH==null) {paramHIGH = 99}	
			value = paramHIGH
			setLevel(value)
            break
		case "off":
			if (logEnable) log.debug "speed off detected"
			sendEvent([name: "speed", value: "off", displayed: true, descriptionText: "fan speed set to $fanspeed"])		
			off()
			break
		case "on":
			if (logEnable) log.debug "speed on detected"	
			//sendEvent([name: "speed", value: "on", displayed: true, descriptionText: "fan speed set to $fanspeed"])		
			on()
			break
		case "auto":
			//if (logEnable) log.debug "speed auto detected"	
			//sendEvent([name: "speed", value: "on", displayed: true, descriptionText: "fan speed set to $fanspeed"])		
			//on()
			log.warn "Speed AUTO requested. This doesn't do anything in this driver right now."
			break
		default:
            break
    }
}


def refresh() {
	log.info "refresh() is called"
	
	def cmds = []
	cmds << zwave.switchBinaryV1.switchBinaryGet().format()
	cmds << zwave.switchMultilevelV1.switchMultilevelGet().format()
    cmds << zwave.configurationV1.configurationGet(parameterNumber: 4).format()
	cmds << zwave.associationV2.associationGet(groupingIdentifier: 3).format()
	if (getDataValue("MSR") == null) {
		cmds << zwave.manufacturerSpecificV1.manufacturerSpecificGet().format()
	}
	cmds << zwave.versionV1.versionGet().format()
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
	
	// Set Inverted param
	if (paramInverted==null) {
		paramInverted = 0
	}	
	cmds << zwave.configurationV1.configurationSet(scaledConfigurationValue: paramInverted.toInteger(), parameterNumber: 4, size: 1).format()
	cmds << zwave.configurationV1.configurationGet(parameterNumber: 4).format()

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
