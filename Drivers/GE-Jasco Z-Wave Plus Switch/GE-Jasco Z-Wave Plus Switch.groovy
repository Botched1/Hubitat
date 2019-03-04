/**
 *  IMPORT URL: https://raw.githubusercontent.com/Botched1/Hubitat/master/Drivers/GE-Jasco%20Z-Wave%20Plus%20Switch/GE-Jasco%20Z-Wave%20Plus%20Switch.groovy
 *
 *  GE Z-Wave Plus Switch
 *
 *
 *  Original based off of the Dimmer Switch under Templates in the IDE 
 *  Original custom DTH Author: Matt Lebaugh (@mlebaugh)
 *
 *  HUBITAT PORT
 *  1.3.0 (01/29/2019) - Ported to Hubitat by Jason Bottjen. Removed ST specifics, removed Polling and Health Check capabilities.
 *  1.4.0 (01/29/2019) - Changed doubletap events to type doubleTapped
 *  1.5.0 (01/29/2019) - Added control of indicator light
 *  1.6.0 (01/31/2019) - Reded CRC16 section based on Hubitat example to try and fix CRC16 errors
 *  1.7.0 (02/26/2019) - Synchronized with dimmer driver. Moved some commands to preferences. Removed doubletap command buttons (but not doubletap events). Removed indicator capability.
 *  1.8.0 (02/28/2019) - Modified preference code, removed unneeded ParamToInt function, improved wording in configuration report
 *  1.9.0 (03/03/2019) - Update to fix some CRC16 encapsulation issues
 *  1.9.1 (03/03/2019) - Update to fix some CRC16 encapsulation issues. Added command class version  map.
 *  1.9.2 (03/03/2019) - Cleaned up some errant warning messages that should have been debug.
 *  2.0.0 (03/03/2019) - Added descriptionText logging
 */

metadata {
	definition (name: "GE Z-Wave Plus Switch", namespace: "Botched1", author: "Jason Bottjen") {
		capability "Actuator"
		capability "PushableButton"
		capability "DoubleTapableButton"
		capability "Configuration"
		capability "Refresh"
		capability "Sensor"
		capability "Switch"		
		capability "Light"
		
		fingerprint mfr:"0063", prod:"4952", model: "3036", ver: "5.20", deviceJoinName: "GE Z-Wave Plus Wall Switch"
		fingerprint mfr:"0063", prod:"4952", model: "3037", ver: "5.20", deviceJoinName: "GE Z-Wave Plus Toggle Switch"
		fingerprint mfr:"0063", prod:"4952", model: "3038", ver: "5.20", deviceJoinName: "GE Z-Wave Plus Toggle Switch"
		fingerprint mfr:"0063", prod:"4952", model: "3130", ver: "5.20", deviceJoinName: "Jasco Z-Wave Plus Wall Switch"
		fingerprint mfr:"0063", prod:"4952", model: "3131", ver: "5.20", deviceJoinName: "Jasco Z-Wave Plus Toggle Switch"
		fingerprint mfr:"0063", prod:"4952", model: "3132", ver: "5.20", deviceJoinName: "Jasco Z-Wave Plus Toggle Switch"
	}

 preferences {
     input (
            type: "paragraph",
            element: "paragraph",
            title: "Switch General Settings",
            description: ""
        )

	    input "paramLED", "enum", title: "LED Behavior", multiple: false, options: ["0" : "LED ON When Switch OFF (default)", "1" : "LED ON When Switch ON", "2" : "LED Always OFF"], required: false, displayDuringSetup: true
	    input "paramInverted", "enum", title: "Switch Buttons Direction", multiple: false, options: ["0" : "Normal (default)", "1" : "Inverted"], required: false, displayDuringSetup: true

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
		def cmd = zwave.parse(description, [0x20: 1, 0x25: 1, 0x56: 1, 0x70: 2, 0x72: 2, 0x85: 2])

		if (logEnable) log.debug "cmd: $cmd"
		
		if (cmd) {
			result = zwaveEvent(cmd)
        }
	}
    if (!result) {if (logEnable) log.debug "Parse returned ${result} for $description" }
    else {if (logEnable) log.debug "Parse returned ${result}"}
	
	return result
}

/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Z-Wave Messages
/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
def zwaveEvent(hubitat.zwave.commands.crc16encapv1.Crc16Encap cmd) {
	if (logEnable) log.debug "zwaveEvent(): CRC-16 Encapsulation Command received: ${cmd}"
	
	def newVersion = 1
	
	// Configuration = 112 decimal
	// Manufacturer Specific = 114 decimal
	// Association = 133 decimal
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

def zwaveEvent(hubitat.zwave.commands.configurationv2.ConfigurationReport cmd) {
	if (logEnable) log.debug "---CONFIGURATION REPORT V2--- ${device.displayName} sent ${cmd}"
    def config = cmd.scaledConfigurationValue.toInteger()
    def result = []
	def name = ""
    def value = ""
    def reportValue = config // cmd.configurationValue[0]
    switch (cmd.parameterNumber) {
        case 3:
            name = "LED Behavior"
			value = reportValue == 0 ? "LED ON When Switch OFF (default)" : reportValue == 1 ? "LED ON When Switch ON" : reportValue == 2 ? "LED Always OFF" : "error"
            break
        case 4:
			name = "Invert Buttons"
            value = reportValue == 0 ? "Disabled (default)" : reportValue == 1 ? "Enabled" : "error"
            break
        default:
            break
    }
	result << createEvent([name: name, value: value, displayed: false])
	return result
}

def zwaveEvent(hubitat.zwave.commands.switchbinaryv1.SwitchBinaryReport cmd) {
    if (logEnable) log.debug "---BINARY SWITCH REPORT V1--- ${device.displayName} sent ${cmd}"
    
	def desc, newValue, curValue
	
	curValue = device.currentValue("switch")

	if (cmd.value) { // == 255) {
		desc = "$device.displayName is on"
		//if (logDesc) log.info "$device.displayName is on"
		newValue = "on"
	} else {
		desc = "$device.displayName is off"
		//if (logDesc) log.info "$device.displayName is off"
		newValue = "off"
	}

	if (curValue != newValue) {
		if (logDesc) log.info "$device.displayName is " + (cmd.value ? "on" : "off")
		createEvent([name: "switch", value: cmd.value ? "on" : "off", descriptionText: "$desc", isStateChange: true])
	}
	
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

/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Driver Commands / Functions
/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
def on() {
	if (logEnable) log.debug "Turn device ON"
	delayBetween([
		zwave.switchBinaryV1.switchBinarySet(switchValue: 0xFF).format(),
		zwave.switchBinaryV1.switchBinaryGet().format()
	],500)
}

def off() {
	if (logEnable) log.debug "Turn device OFF"
	delayBetween([
		zwave.switchBinaryV1.switchBinarySet(switchValue: 0x00).format(),
		zwave.switchBinaryV1.switchBinaryGet().format()
	],500)
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
	if (paramLED==null) {
		paramLED = 0
	}
	cmds << zwave.configurationV2.configurationSet(scaledConfigurationValue: paramLED, parameterNumber: 3, size: 1).format()
	cmds << zwave.configurationV2.configurationGet(parameterNumber: 3).format()

	// Set Inverted param
	if (paramInverted==null) {
		paramInverted = 0
	}
	cmds << zwave.configurationV2.configurationSet(scaledConfigurationValue: paramInverted, parameterNumber: 4, size: 1).format()
	cmds << zwave.configurationV2.configurationGet(parameterNumber: 4).format()
	
	//
    delayBetween(cmds, 500)
}

def configure() {
        log.info "configure() called"
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
