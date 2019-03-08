/**
 *  IMPORT URL: 
 *
 *  Dome Z-Wave Plus Range Extender DMEX1
 *
 *  1.0.0 (03/08/2019) - Initial Version
 */

metadata {
	definition (name: "Dome Z-Wave Plus Range Extender", namespace: "Botched1", author: "Jason Bottjen") {
		capability "Actuator"
		capability "Configuration"
		capability "Refresh"
		
		// fingerprint mfr:"0063", prod:"4952", model: "3132", ver: "5.20", deviceJoinName: "Jasco Z-Wave Plus Toggle Switch"
	}

 preferences {
	    input name: "paramNLOff", type: "number", title: "Night light OFF Threshold", multiple: false
	    input name: "paramNLOn", type: "number", title: "Night light ON Threshold", multiple: false
		input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: false
    }
}

/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Parse
/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
def parse(String description) {
    def result = null
	if (description != "updated") {
		if (logEnable) log.debug "parse() >> zwave.parse($description)"
		def cmd = zwave.parse(description) //, [0x20: 1, 0x25: 1, 0x56: 1, 0x70: 2, 0x72: 2, 0x85: 2])

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
	
	def encapsulatedCommand = zwave.getCommand(cmd.commandClass, cmd.command, cmd.data, 1)
	if (encapsulatedCommand) {
       zwaveEvent(encapsulatedCommand)
   } else {
       log.warn "Unable to extract CRC16 command from ${cmd}"
   }
}

def zwaveEvent(hubitat.zwave.commands.basicv1.BasicReport cmd) {
    log.debug "---BASIC REPORT V1--- ${device.displayName} sent ${cmd}"
	//sendEvent(name: "switch", value: cmd.value ? "on" : "off", isStateChange: true)
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
        case 1:
            name = "Night light OFF Threshold"
			value = reportValue
            break
        case 2:
			name = "Night light ON Threshold"
            value = reportValue
            break
        default:
            break
    }
	sendEvent([name: name, value: value, displayed: false])
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
def refresh() {
	log.info "refresh() is called"
	
	def cmds = []
    cmds << zwave.configurationV2.configurationGet(parameterNumber: 1).format()
    cmds << zwave.configurationV2.configurationGet(parameterNumber: 2).format()
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

	// Set Night light OFF Threshold param
	if (paramNLOff==null) {
		paramNLOff = 0
	}
	cmds << zwave.configurationV2.configurationSet(scaledConfigurationValue: paramNLOff, parameterNumber: 1, size: 1).format()
	cmds << zwave.configurationV2.configurationGet(parameterNumber: 1).format()

	// Set Night light ON Threshold param
	if (paramNLOn==null) {
		paramNLOn = 0
	}
	cmds << zwave.configurationV2.configurationSet(scaledConfigurationValue: paramNLOn, parameterNumber: 2, size: 1).format()
	cmds << zwave.configurationV2.configurationGet(parameterNumber: 2).format()
	
	//
    delayBetween(cmds, 500)
}

def configure() {
        log.info "configure() called"
		def cmds = []
        cmds << zwave.associationV1.associationSet(groupingIdentifier:1, nodeId:zwaveHubNodeId).format()
        delayBetween(cmds, 500)
}

def logsOff(){
    log.warn "debug logging disabled..."
    device.updateSetting("logEnable",[value:"false",type:"bool"])
}
