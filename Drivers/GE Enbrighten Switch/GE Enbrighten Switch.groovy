/**
 *  IMPORT URL: https://raw.githubusercontent.com/Botched1/Hubitat/master/Drivers/GE%20Enbrighten%20Switch/GE%20Enbrighten%20Switch.groovy
 *
 *  GE Enbrighten Z-Wave Plus Switch
 *
 *  1.0.0 (07/16/2019) - Initial Version
 *  1.1.0 (07/17/2019) - Removed DoubleTap from BasicSet, added DoubleTap UP/DOWN and TripleTap UP/DOWN as standard buttons 1-4
 *  1.2.0 (02/07/2020) - Added pushed, held, and released capability. Required renumbering the buttons. Now 1/2=Up/Down, 3/4=Double Up/Down, 5/6=Triple Up/Down
 *  1.2.1 (02/07/2020) - Added doubleTapped events and added doubleTap capability. Now users can use button 3/4 for double tap or the system "doubleTapped" events.
 *  1.3.0 (05/17/2020) - Added associations and inverted paddle options
 *  2.0.0e (08/13/2020) - Added S2 capability for Hubitat 2.2.3 and newer
*/

import groovy.transform.Field

@Field static Map commandClassVersions = 
    [
         0x25: 1    //Switch Binary
        ,0x70: 2    //configuration
        ,0x72: 2    //Manufacturer Specific
        ,0x85: 2    //association
]

metadata {
	definition (name: "GE Enbrighten Z-Wave Plus Switch", namespace: "Botched1", author: "Jason Bottjen") {
		capability "Actuator"
        capability "DoubleTapableButton"
        capability "HoldableButton"
		capability "PushableButton"
        capability "ReleasableButton"
		capability "Configuration"
		capability "Refresh"
		capability "Sensor"
		capability "Switch"
		capability "Light"
	}

 preferences {
        input name: "paramLED", type: "enum", title: "LED Indicator Behavior", multiple: false, options: ["0" : "LED ON When Switch OFF (default)", "1" : "LED ON When Switch ON", "2" : "LED Always OFF", "3" : "LED Always ON"], required: false, displayDuringSetup: true
        input name: "paramInverted", type: "enum", title: "Switch Buttons Direction", multiple: false, options: ["0" : "Normal (default)", "1" : "Inverted"], required: false, displayDuringSetup: true
        input name: "requestedGroup2", type: "text", title: "Association Group 2 Members (Max of 5):", required: false
        input name: "requestedGroup3", type: "text", title: "Association Group 3 Members (Max of 4):", required: false
        input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: false	
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
// Z-Wave Messages
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

def zwaveEvent(hubitat.zwave.commands.basicv1.BasicReport cmd) {
    if (logEnable) log.debug "---BASIC REPORT V1--- ${device.displayName} sent ${cmd}"
	if (logEnable) log.debug "This report does nothing in this driver, and shouldn't have been called..."
}

def zwaveEvent(hubitat.zwave.commands.basicv1.BasicSet cmd) {
    if (logEnable) log.debug "---BASIC SET V1--- ${device.displayName} sent ${cmd}"
    if (logEnable) log.debug "This report does nothing in this driver, and can be ignored..."
}

def zwaveEvent(hubitat.zwave.commands.associationv2.AssociationReport cmd) {
	if (logEnable) log.debug "---ASSOCIATION REPORT V2--- ${device.displayName} sent groupingIdentifier: ${cmd.groupingIdentifier} maxNodesSupported: ${cmd.maxNodesSupported} nodeId: ${cmd.nodeId} reportsToFollow: ${cmd.reportsToFollow}"
    if (cmd.groupingIdentifier == 3) {
    	if (cmd.nodeId.contains(zwaveHubNodeId)) {
        	sendEvent(name: "numberOfButtons", value: 6, displayed: false)
        }
        else {
        	sendEvent(name: "numberOfButtons", value: 0, displayed: false)
			delayBetween([
                secure(zwave.associationV2.associationSet(groupingIdentifier: 3, nodeId: zwaveHubNodeId).format())
			    ,secure(zwave.associationV2.associationGet(groupingIdentifier: 3).format())
                ] ,250)
        }
    }
}


def zwaveEvent(hubitat.zwave.commands.configurationv2.ConfigurationReport cmd) {
	if (logEnable) log.debug "---CONFIGURATION REPORT V2--- ${device.displayName} sent ${cmd}"
}

def zwaveEvent(hubitat.zwave.commands.switchbinaryv1.SwitchBinaryReport cmd) {
    if (logEnable) log.debug "---BINARY SWITCH REPORT V1--- ${device.displayName} sent ${cmd}"
    
	def desc, newValue, curValue, newType
	
	// check state.bin variable to see if event is digital or physical
	if (state.bin)
	{
		newType = "digital"
	}
	else
	{
		newType = "physical"
	}
	
	// Reset state.bin variable
	state.bin = 0
	
	curValue = device.currentValue("switch")

	if (cmd.value) { // == 255) {
		desc = "$device.displayName was turned on [$newType]"
		//if (logDesc) log.info "$device.displayName is on"
		newValue = "on"
	} else {
		desc = "$device.displayName was turned off [$newType]"
		//if (logDesc) log.info "$device.displayName is off"
		newValue = "off"
	}

	if (curValue != newValue) {
		if (logDesc) log.info "$device.displayName is " + (cmd.value ? "on" : "off")
		sendEvent([name: "switch", value: cmd.value ? "on" : "off", descriptionText: "$desc", type: "$newType", isStateChange: true])
	}
}

def zwaveEvent(hubitat.zwave.commands.versionv1.VersionReport cmd) {
	def fw = "${cmd.applicationVersion}.${cmd.applicationSubVersion}"
	updateDataValue("fw", fw)
	if (logEnable) log.debug "---VERSION REPORT V1--- ${device.displayName} is running firmware version: $fw, Z-Wave version: ${cmd.zWaveProtocolVersion}.${cmd.zWaveProtocolSubVersion}"
}

def zwaveEvent(hubitat.zwave.commands.hailv1.Hail cmd) {
	if (logEnable) log.debug "Hail command received..."
	if (logEnable) log.debug "This does nothing in this driver, and shouldn't have been called..."
}

def zwaveEvent(hubitat.zwave.commands.centralscenev1.CentralSceneNotification cmd) {
	if (logEnable) log.debug "CentralSceneNotification V1 Called."
    
    def result = []
    
    // Single Tap Up
    if ((cmd.keyAttributes == 0) && (cmd.sceneNumber == 1)) {
		if (logEnable) log.debug "Physical ON Triggered"
		if (logDesc) log.info "$device.displayName turned on [physical]"
        result << sendEvent([name: "pushed", value: 1, descriptionText: "$device.displayName had Up Pushed (button 1) [physical]", type: "physical", isStateChange: true])
    }
    // Single Tap Down
    if ((cmd.keyAttributes == 0) && (cmd.sceneNumber == 2)) {
		if (logEnable) log.debug "Physical OFF Triggered"
		if (logDesc) log.info "$device.displayName turned off [physical]"
        result << sendEvent([name: "pushed", value: 2, descriptionText: "$device.displayName had Down Pushed (button 2) [physical]", type: "physical", isStateChange: true])
    }
    // Released Up
    if ((cmd.keyAttributes == 1) && (cmd.sceneNumber == 1)) {
		if (logEnable) log.debug "Up Released Triggered"
        if (logDesc) log.info "$device.displayName had Up Released (button 1) [physical]"
        result << sendEvent([name: "released", value: 1, descriptionText: "$device.displayName had Up Released (button 1) [physical]", type: "physical", isStateChange: true])
    }
    // Released Down
    if ((cmd.keyAttributes == 1) && (cmd.sceneNumber == 2)) {
		if (logEnable) log.debug "Down Released Triggered"
        if (logDesc) log.info "$device.displayName had Down Released (button 2) [physical]"
        result << sendEvent([name: "released", value: 2, descriptionText: "$device.displayName had Down Released (button 2) [physical]", type: "physical", isStateChange: true])
    }
    // Held Up
    if ((cmd.keyAttributes == 2) && (cmd.sceneNumber == 1)) {
		if (logEnable) log.debug "Up Held Triggered"
		if (logDesc) log.info "$device.displayName had Up Held (button 1) [physical]"
		result << sendEvent([name: "held", value: 1, descriptionText: "$device.displayName had Up held (button 1) [physical]", type: "physical", isStateChange: true])
    }
    // Held Down
    if ((cmd.keyAttributes == 2) && (cmd.sceneNumber == 2)) {
		if (logEnable) log.debug "Down Held Triggered"
		if (logDesc) log.info "$device.displayName had Down Held (button 2) [physical]"
		result << sendEvent([name: "held", value: 2, descriptionText: "$device.displayName had Up held (button 2) [physical]", type: "physical", isStateChange: true])
    }        
    // Double Tap Up
    if ((cmd.keyAttributes == 3) && (cmd.sceneNumber == 1)) {
		if (logEnable) log.debug "Double Tap Up Triggered"
		if (logDesc) log.info "$device.displayName had Doubletap up (button 3) [physical]"
		result << sendEvent([name: "pushed", value: 3, descriptionText: "$device.displayName had Doubletap up (button 3) [physical]", type: "physical", isStateChange: true])
        result << sendEvent([name: "doubleTapped", value: 1, descriptionText: "$device.displayName had Doubletap up (doubleTapped 1) [physical]", type: "physical", isStateChange: true])
    }
    // Double Tap Down
    if ((cmd.keyAttributes == 3) && (cmd.sceneNumber == 2)) {
		if (logEnable) log.debug "Double Tap Down Triggered"
		if (logDesc) log.info "$device.displayName had Doubletap down (button 4) [physical]"
		result << sendEvent([name: "pushed", value: 4, descriptionText: "$device.displayName had Doubletap down (button 4) [physical]", type: "physical", isStateChange: true])
        result << sendEvent([name: "doubleTapped", value: 2, descriptionText: "$device.displayName had Doubletap down (doubleTapped 2) [physical]", type: "physical", isStateChange: true])
    }
    // Triple Tap Up
    if ((cmd.keyAttributes == 4) && (cmd.sceneNumber == 1)) {
		if (logEnable) log.debug "Triple Tap Up Triggered"
		if (logDesc) log.info "$device.displayName had Tripletap up (button 5) [physical]"
		result << sendEvent([name: "pushed", value: 5, descriptionText: "$device.displayName had Tripletap up (button 5) [physical]", type: "physical", isStateChange: true])
    }
    // Triple Tap Down
    if ((cmd.keyAttributes == 4) && (cmd.sceneNumber == 2)) {
		if (logEnable) log.debug "Triple Tap Down Triggered"
		if (logDesc) log.info "$device.displayName had Tripletap down (button 6) [physical]"
		result << sendEvent([name: "pushed", value: 6, descriptionText: "$device.displayName had Tripletap down (button 6) [physical]", type: "physical", isStateChange: true])
    }

    return result
}

def zwaveEvent(hubitat.zwave.Command cmd) {
    log.warn "${device.displayName} received unhandled command: ${cmd}"
}

/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Driver Commands / Functions
/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
def on() {
	if (logEnable) log.debug "Turn device ON"
	state.bin = -1

    return delayBetween([
		secure(zwave.switchBinaryV1.switchBinarySet(switchValue: 0xFF).format()),
		secure(zwave.switchBinaryV1.switchBinaryGet().format())
	],250)
}

def off() {
	if (logEnable) log.debug "Turn device OFF"
	state.bin = -1

    return delayBetween([
		secure(zwave.switchBinaryV1.switchBinarySet(switchValue: 0x00).format()),
		secure(zwave.switchBinaryV1.switchBinaryGet().format())
	],250)
}

def refresh() {
	log.info "refresh() is called"
	
    return delayBetween([
        secure(zwave.switchBinaryV1.switchBinaryGet().format())
        ,secure(zwave.configurationV2.configurationGet(parameterNumber: 3).format())
        ,secure(zwave.configurationV2.configurationGet(parameterNumber: 6).format())
        ,secure(zwave.configurationV2.configurationGet(parameterNumber: 16).format())
        ,secure(zwave.configurationV2.configurationGet(parameterNumber: 30).format())
        ,secure(zwave.configurationV2.configurationGet(parameterNumber: 31).format())
        ,secure(zwave.configurationV2.configurationGet(parameterNumber: 32).format())
        ,secure(zwave.associationV2.associationGet(groupingIdentifier: 3).format())
	] , 250)
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

	// Associations    
    cmds << secure(zwave.associationV2.associationSet(groupingIdentifier:1, nodeId:zwaveHubNodeId).format())

	def nodes = []
	nodes = parseAssocGroupList(settings.requestedGroup2, 2)
    cmds << secure(zwave.associationV2.associationRemove(groupingIdentifier: 2, nodeId: []).format())
    cmds << secure(zwave.associationV2.associationSet(groupingIdentifier: 2, nodeId: nodes).format())
    cmds << secure(zwave.associationV2.associationGet(groupingIdentifier: 2).format())
    state.currentGroup2 = settings.requestedGroup2
	
   	nodes = parseAssocGroupList(settings.requestedGroup3, 3)
    cmds << secure(zwave.associationV2.associationRemove(groupingIdentifier: 3, nodeId: []).format())
    cmds << secure(zwave.associationV2.associationSet(groupingIdentifier: 3, nodeId: nodes).format())
    cmds << secure(zwave.associationV2.associationGet(groupingIdentifier: 3).format())
    state.currentGroup3 = settings.requestedGroup3
   	
	// Set LED param
	if (paramLED==null) {
		paramLED = 0
	}	
	cmds << secure(zwave.configurationV2.configurationSet(scaledConfigurationValue: paramLED.toInteger(), parameterNumber: 3, size: 1).format())
	cmds << secure(zwave.configurationV2.configurationGet(parameterNumber: 3).format())
	
	// Set Inverted param
	if (paramInverted==null) {
		paramInverted = 0
	}
	cmds << secure(zwave.configurationV2.configurationSet(scaledConfigurationValue: paramInverted.toInteger(), parameterNumber: 4, size: 1).format())
	cmds << secure(zwave.configurationV2.configurationGet(parameterNumber: 4).format())
	
    delayBetween(cmds, 250)
}

def configure() {
    log.info "configure triggered"
	state.bin = -1
	def cmds = []
	
	// Associations    
    cmds << secure(zwave.associationV2.associationSet(groupingIdentifier:1, nodeId:zwaveHubNodeId).format())

	def nodes = []
	nodes = parseAssocGroupList(settings.requestedGroup2, 2)
    cmds << secure(zwave.associationV2.associationRemove(groupingIdentifier: 2, nodeId: []).format())
    cmds << secure(zwave.associationV2.associationSet(groupingIdentifier: 2, nodeId: nodes).format())
    cmds << secure(zwave.associationV2.associationGet(groupingIdentifier: 2).format())
    state.currentGroup2 = settings.requestedGroup2
	
   	nodes = parseAssocGroupList(settings.requestedGroup3, 3)
    cmds << secure(zwave.associationV2.associationRemove(groupingIdentifier: 3, nodeId: []).format())
    cmds << secure(zwave.associationV2.associationSet(groupingIdentifier: 3, nodeId: nodes).format())
    cmds << secure(zwave.associationV2.associationGet(groupingIdentifier: 3).format())
    state.currentGroup3 = settings.requestedGroup3
	
	delayBetween(cmds, 250)
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
                	log.warn "Association Group ${group}: Adding the hub ID as an association is not allowed."
                }
                else 
				if ( (nodeId > 0) & (nodeId < 256) ) {
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
	if (logEnable) log.debug "Nodes is $nodes"
    return nodes
}

def logsOff(){
    log.warn "debug logging disabled..."
    device.updateSetting("logEnable",[value:"false",type:"bool"])
}
