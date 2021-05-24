/**
 *  IMPORT URL: https://raw.githubusercontent.com/Botched1/Hubitat/master/Drivers/GE-Jasco%20Z-Wave%20Plus%20Motion%20Switch/GE-Jasco%20Z-Wave%20Plus%20Motion%20Switch.groovy
 *
 *  GE-Jasco Z-Wave Plus Motion Switch
 *
 *  Original custom DTH Author: Matt Lebaugh (@mlebaugh)
 *
 *  HUBITAT PORT
 *  1.0.0 (02/28/2019) - Original version.
 *  1.0.1 (03/03/2019) - Fixed small association setting bug.
 *  1.1.0 (03/03/2019) - Update to fix some CRC16 encapsulation issues. Added command class version  map.
 *  1.1.1 (03/03/2019) - Cleaned up some errant wanring messages that should have been debug.
 *  2.0.0 (02/01/2020) - Added occupancy/vacancy/manual commands, added association settings to preferences
 *  2.1.0 (02/01/2020) - Added setLightTimeout and DebugLogging commands, added description logging, added state variables for operating mode and light timeout
 *  2.1.1 (02/01/2020) - Added digital/physical indication to event types
 *  2.2.0 (12/29/2020) - Unschedule logsOff if maually turning off debug logging
 *  2.3.0 (01/30/2021) - Fixed paramMotionResetTimer. Thanks for the PR @kleung1
 *  2.4.0 (02/17/2021) - Removed erroneous duplicate event recording. Added new preference "Wait for device report before updating status.", added blank selection option to commands to reduce confusion. Granular debug command time options.
 *  2.4.1 (02/18/2021) - Fixed some reporting errors, and synced the code with the component switch driver
 *  2.4.2 (02/18/2021) - Fixed repeated physical events not making events.
 *  2.4.3 (05/24/2021) - Fixed error when setting light timeout to disabled
*/

metadata {
	definition (name: "GE Z-Wave Plus Motion Switch", namespace: "Botched1", author: "Jason Bottjen") {
		capability "Actuator"
		capability "Motion Sensor"
		capability "PushableButton"
		capability "Configuration"
		capability "Refresh"
		capability "Sensor"
		capability "Switch"		
		capability "Light"
		command "setLightTimeout", [[name:"Light Timeout",type:"ENUM", description:"Time before light turns OFF on no motion - only applies in Occupancy and Vacancy modes.", constraints: ["", "5 seconds", "1 minute", "5 minutes (default)", "15 minutes", "30 minutes", "disabled"]]]
		command "Occupancy"
        command "Vacancy"
        command "Manual"        
		command "DebugLogging", [[name:"Debug Logging",type:"ENUM", description:"Turn Debug Logging OFF/ON", constraints:["", "OFF", "30m", "1h", "3h", "6h", "12h", "24h", "ON"]]]        
	}

 preferences {
	 //input "paramLightTimer", "enum", title: "Light Timeout", description: "Length of time after no motion for the light to shut off in Occupancy/Vacancy modes", options: ["0" : "5 seconds", "1" : "1 minute", "5" : "5 minutes (default)", "15" : "15 minutes", "30" : "30 minutes", "255" : "disabled"], required: false, displayDuringSetup: true
	 //input "paramOperationMode", "enum", title: "Operating Mode", description: "Occupancy: Automatically turn on and off the light with motion\nVacancy: Manually turn on, automatically turn off light with no motion.", options: ["1" : "Manual", "2" : "Vacancy", "3" : "Occupancy (default)"], required: false, displayDuringSetup: true
	 input "paramInverted", "enum", title: "Switch Buttons Direction", multiple: false, options: ["0" : "Normal (default)", "1" : "Inverted"], required: false, displayDuringSetup: true
	 input "paramMotionEnabled", "enum", title: "Motion Sensor", description: "Enable/Disable Motion Sensor.",options: ["0" : "Disable","1" : "Enable (default)"], required: false
	 input "paramMotionSensitivity", "enum", title: "Motion Sensitivity", options: ["1" : "High", "2" : "Medium (default)", "3" : "Low"], required: false, displayDuringSetup: true
	 input "paramLightSense", "enum", title: "Light Sensing", description: "If enabled, Occupancy mode will only turn light on if it is dark", options: ["0" : "Disabled","1" : "Enabled (default)"], required: false, displayDuringSetup: true
	 input "paramMotionResetTimer", "enum", title: "Motion Detection Reset Time", options: ["0" : "Disabled", "1" : "10 sec", "2" : "20 sec (default)", "3" : "30 sec", "4" : "45 sec", "110" : "27 mins"], required: false
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
	 input name: "paramWait4Report", type: "bool", title: "Wait for device report before updating status. Can help prevent status sync issues, but will make hub initiated command status slightly slower.", defaultValue: false
	 input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: true
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
	
	// Configuration = 112 decimal
	// Notification = 113 decimal
	// Manufacturer Specific = 114 decimal
	// Association = 133 decimal
	if (cmd.commandClass == 112) {newVersion = 2}
	if (cmd.commandClass == 113) {newVersion = 3}								 
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
}

def zwaveEvent(hubitat.zwave.commands.associationv2.AssociationReport cmd) {
	if (logEnable) log.debug "---ASSOCIATION REPORT V2--- ${device.displayName} sent groupingIdentifier: ${cmd.groupingIdentifier} maxNodesSupported: ${cmd.maxNodesSupported} nodeId: ${cmd.nodeId} reportsToFollow: ${cmd.reportsToFollow}"
    if (cmd.groupingIdentifier == 3) {
    	if (cmd.nodeId.contains(zwaveHubNodeId)) {
        	sendEvent(name: "numberOfButtons", value: 2, displayed: false)
        }
        else {
        	sendEvent(name: "numberOfButtons", value: 0, displayed: false)
			delayBetween([zwave.associationV2.associationSet(groupingIdentifier: 3, nodeId: zwaveHubNodeId).format(),
			zwave.associationV2.associationGet(groupingIdentifier: 3).format()],500)
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
			name = "lightTimeout"
			value = reportValue == 0 ? "5 seconds" : reportValue == 1 ? "1 minute" : reportValue == 5 ? "5 minutes (default)" : reportValue == 15 ? "15 minutes" : reportValue == 30 ? "30 minutes" : reportValue == -1 ? "disabled" : "error"
			break
		case 3:
			name = "operatingMode"
			value = reportValue == 1 ? "Manual" : reportValue == 2 ? "Vacancy" : reportValue == 3 ? "Occupancy (default)": "error"
			break
		case 5:
			name = "Invert Buttons"
			value = reportValue == 0 ? "Disabled (default)" : reportValue == 1 ? "Enabled" : "error"
			break
		case 6:
			name = "Motion Sensor"
			value = reportValue == 0 ? "Disabled" : reportValue == 1 ? "Enabled (default)" : "error"
			break
		case 13:
			name = "Motion Sensitivity"
			value = reportValue == 1 ? "High" : reportValue == 2 ? "Medium (default)" :  reportValue == 3 ? "Low" : "error"
			break
		case 14:
			name = "Light Sensing"
			value = reportValue == 0 ? "Disabled" : reportValue == 1 ? "Enabled (default)" : "error"
			break
		case 15:
			name = "Motion Reset Timer"
			value = reportValue == 0 ? "Disabled" : reportValue == 1 ? "10 seconds" : reportValue == 2 ? "20 seconds (default)" : reportValue == 3 ? "30 seconds" : reportValue == 4 ? "45 seconds" : reportValue == 110 ? "27 minutes" : "error"
			break
		default:
			break
    }
	sendEvent([name: name, value: value])
}

def zwaveEvent(hubitat.zwave.commands.switchbinaryv1.SwitchBinaryReport cmd) {
	if (logEnable) log.debug "---SWITCH BINARY REPORT V1--- ${device.displayName} sent ${cmd}"
    
	def desc
	
	if (cmd.value == 255) {
		desc = "$device.displayName was turned on"
        if (logDesc) log.info desc
	}
	else if (cmd.value == 0) {
		desc = "$device.displayName was turned off"
        if (logDesc) log.info desc
	}
    
	sendEvent([name: "switch", value: cmd.value ? "on" : "off", descriptionText: "$desc", type: state.eventType ? "digital" : "physical", isStateChange: state.eventType ? false : true])
	
	// Reset type state
	state.eventType = 0
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
}

def zwaveEvent(hubitat.zwave.Command cmd) {
    log.warn "${device.displayName} received unhandled command: ${cmd}"
}

def zwaveEvent(hubitat.zwave.commands.notificationv3.NotificationReport cmd) {
	if (logEnable) log.debug "---NOTIFICATION REPORT V3--- ${device.displayName} sent ${cmd}"
	def result = []
	if (cmd.notificationType == 0x07) {
		if ((cmd.event == 0x00)) { 
			if (logDesc) log.info "$device.displayName motion has stopped"
            result << createEvent(name: "motion", value: "inactive", descriptionText: "$device.displayName motion has stopped", type: "physical", isStateChange: true)
		} else if (cmd.event == 0x08) {
			if (logDesc) log.info "$device.displayName detected motion"
            result << createEvent(name: "motion", value: "active", descriptionText: "$device.displayName detected motion", type: "physical", isStateChange: true)
		} 
	} 
	result
}
/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Driver Commands / Functions
/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
def on() {
	if (logEnable) log.debug "Turn device ON"

	def cmds = []
	cmds << zwave.switchBinaryV1.switchBinarySet(switchValue: 0xFF).format()

	if (!paramWait4Report) {
		state.eventType = 0
		sendEvent([name: "switch", value: "on", descriptionText: "$device.displayName was turned on", type: "digital"])
		if (logDesc) log.info "$device.displayName was turned on"
	}
	else {
		state.eventType = -1
		cmds << zwave.switchBinaryV1.switchBinaryGet().format()
	}

	sendHubCommand(new hubitat.device.HubMultiAction(delayBetween(cmds, 500), hubitat.device.Protocol.ZWAVE))
}

def off() {
	if (logEnable) log.debug "Turn device OFF"

	def cmds = []
	cmds << zwave.switchBinaryV1.switchBinarySet(switchValue: 0x00).format()
	
	if (!paramWait4Report) {
		state.eventType = 0
		sendEvent([name: "switch", value: "off", descriptionText: "$device.displayName was turned off", type: "digital"])
		if (logDesc) log.info "$device.displayName was turned off"
	}
	else {
		state.eventType = -1
		cmds << zwave.switchBinaryV1.switchBinaryGet().format()
	}

	sendHubCommand(new hubitat.device.HubMultiAction(delayBetween(cmds, 500), hubitat.device.Protocol.ZWAVE))	
}

def setLightTimeout(value) {
    if (logEnable) log.debug "Setting light timeout value: ${value}"
    def cmds = []        
    // "5 seconds", "1 minute", "5 minutes (default)", "15 minutes", "30 minutes", "disabled"
    switch (value) {
        case "5 seconds":
            state.lightTimeout = "5 seconds"
            if (!paramWait4Report) {			
				sendEvent([name:"lightTimeout", value: "5 seconds"])
			}
			cmds << zwave.configurationV2.configurationSet(scaledConfigurationValue: 0 , parameterNumber: 1, size: 1).format()
            break
        case "1 minute":
            state.lightTimeout = "1 minute"
			if (!paramWait4Report) {			
				sendEvent([name:"lightTimeout", value: "1 minute"])
			}
			cmds << zwave.configurationV2.configurationSet(scaledConfigurationValue: 1 , parameterNumber: 1, size: 1).format()
            break
        case "5 minutes (default)":
            state.lightTimeout = "5 minutes (default)"
            cmds << zwave.configurationV2.configurationSet(scaledConfigurationValue: 5 , parameterNumber: 1, size: 1).format()
            break
        case "15 minutes":
            state.lightTimeout = "15 minutes"
			if (!paramWait4Report) {			
				sendEvent([name:"lightTimeout", value: "5 minutes (default)"])
			}
			cmds << zwave.configurationV2.configurationSet(scaledConfigurationValue: 15 , parameterNumber: 1, size: 1).format()
            break
        case "30 minutes":
            state.lightTimeout = "30 minutes"
			if (!paramWait4Report) {			
				sendEvent([name:"lightTimeout", value: "30 minutes"])
			}            
			cmds << zwave.configurationV2.configurationSet(scaledConfigurationValue: 30 , parameterNumber: 1, size: 1).format()
            break
        case "disabled":
            state.lightTimeout = "disabled"
			if (!paramWait4Report) {			
				sendEvent([name:"lightTimeout", value: "disabled"])
			}
			cmds << zwave.configurationV2.configurationSet(scaledConfigurationValue: 255 , parameterNumber: 1, size: 1).format()
            break
        default:
            return
    }
  	cmds << zwave.configurationV2.configurationGet(parameterNumber: 1).format()
    delayBetween(cmds, 500)
}

def Occupancy() {
	state.operatingMode = "Occupancy (default)"
	if (!paramWait4Report) {
		sendEvent([name:"operatingMode", value: "Occupancy (default)"])
	}    
	def cmds = []
    cmds << zwave.configurationV2.configurationSet(configurationValue: [3] , parameterNumber: 3, size: 1).format()
    cmds << zwave.configurationV2.configurationGet(parameterNumber: 3).format()
    delayBetween(cmds, 500)
}

def Vacancy() {
	state.operatingMode = "Vacancy"
	if (!paramWait4Report) {			
		sendEvent([name:"operatingMode", value: "Vacancy"])
	}
	def cmds = []
    cmds << zwave.configurationV2.configurationSet(configurationValue: [2] , parameterNumber: 3, size: 1).format()
    cmds << zwave.configurationV2.configurationGet(parameterNumber: 3).format()
    delayBetween(cmds, 500)
}

def Manual() {
	state.operatingMode = "Manual"
	if (!paramWait4Report) {
		sendEvent([name:"operatingMode", value: "Manual"])
	}						
	def cmds = []
    cmds << zwave.configurationV2.configurationSet(configurationValue: [1] , parameterNumber: 3, size: 1).format()
    cmds << zwave.configurationV2.configurationGet(parameterNumber: 3).format()
    delayBetween(cmds, 500)
}

def refresh() {
	log.info "refresh() is called"
	
	def cmds = []
	cmds << zwave.switchBinaryV1.switchBinaryGet().format()
	cmds << zwave.notificationV3.notificationGet(notificationType: 7).format()
    cmds << zwave.configurationV2.configurationGet(parameterNumber: 1).format()
    cmds << zwave.configurationV2.configurationGet(parameterNumber: 3).format()
    cmds << zwave.configurationV2.configurationGet(parameterNumber: 5).format()
    cmds << zwave.configurationV2.configurationGet(parameterNumber: 6).format()
    cmds << zwave.configurationV2.configurationGet(parameterNumber: 13).format()
    cmds << zwave.configurationV2.configurationGet(parameterNumber: 14).format()
    cmds << zwave.configurationV2.configurationGet(parameterNumber: 15).format()
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

	// Set Wait preference
	if (paramWait4Report==null) {
		paramWait4Report = false
	}
	
	def cmds = []

	// Set Light Timer param
	//if (paramLightTimer==null) {
	//	paramLightTimer = 5
	//}
	//cmds << zwave.configurationV2.configurationSet(scaledConfigurationValue: paramLightTimer.toInteger(), parameterNumber: 1, size: 1).format()
	//cmds << zwave.configurationV2.configurationGet(parameterNumber: 1).format()
	
	
	// Set Operation Mode param
	//if (paramOperationMode==null) {
	//	paramOperationMode = 3
	//}
	//cmds << zwave.configurationV2.configurationSet(scaledConfigurationValue: paramOperationMode.toInteger(), parameterNumber: 3, size: 1).format()
	//cmds << zwave.configurationV2.configurationGet(parameterNumber: 3).format()
	
	// Set Inverted param
	if (paramInverted==null) {
		paramInverted = 0
	}
	cmds << zwave.configurationV2.configurationSet(scaledConfigurationValue: paramInverted.toInteger(), parameterNumber: 5, size: 1).format()
	cmds << zwave.configurationV2.configurationGet(parameterNumber: 5).format()

	// Set Motion Enabled param
	if (paramMotionEnabled==null) {
		paramMotionEnabled = 1
	}
	cmds << zwave.configurationV2.configurationSet(scaledConfigurationValue: paramMotionEnabled.toInteger(), parameterNumber: 6, size: 1).format()
	cmds << zwave.configurationV2.configurationGet(parameterNumber: 6).format()

	// Set Motion Sensitivity param
	if (paramMotionSensitivity==null) {
		paramMotionSensitivity = 2
	}
	cmds << zwave.configurationV2.configurationSet(scaledConfigurationValue: paramMotionSensitivity.toInteger(), parameterNumber: 13, size: 1).format()
	cmds << zwave.configurationV2.configurationGet(parameterNumber: 13).format()

	// Set Light Sense param
	if (paramLightSense==null) {
		paramLightSense = 1
	}
	cmds << zwave.configurationV2.configurationSet(scaledConfigurationValue: paramLightSense.toInteger(), parameterNumber: 14, size: 1).format()
	cmds << zwave.configurationV2.configurationGet(parameterNumber: 14).format()

	// Set Motion Reset Timer param
	if (paramMotionResetTimer==null) {
		paramMotionResetTimer = 2
	}
	cmds << zwave.configurationV2.configurationSet(scaledConfigurationValue: paramMotionResetTimer.toInteger(), parameterNumber: 15, size: 2).format()
	cmds << zwave.configurationV2.configurationGet(parameterNumber: 15).format()

    // Association groups
    cmds << zwave.associationV2.associationSet(groupingIdentifier:1, nodeId:zwaveHubNodeId).format()
    cmds << zwave.associationV2.associationRemove(groupingIdentifier:2, nodeId:zwaveHubNodeId).format()
    cmds << zwave.associationV2.associationSet(groupingIdentifier:3, nodeId:zwaveHubNodeId).format()
	// Add endpoints to groups 2 and 3
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
        cmds << zwave.associationV2.associationSetRemove(groupingIdentifier: 3, nodeId: []).format()
        cmds << zwave.associationV2.associationSet(groupingIdentifier: 3, nodeId: nodes).format()
        cmds << zwave.associationV2.associationGet(groupingIdentifier: 3).format()
        state.currentGroup3 = settings.requestedGroup3
    }    
	if (logEnable) log.debug "cmds: $cmds"
	delayBetween(cmds, 500)
}

def configure() {
    log.info "configure() called"
    def cmds = []
    cmds << zwave.associationV2.associationSet(groupingIdentifier:1, nodeId:zwaveHubNodeId).format()
	cmds << zwave.associationV2.associationRemove(groupingIdentifier:2, nodeId:zwaveHubNodeId).format()
    cmds << zwave.associationV2.associationSet(groupingIdentifier:3, nodeId:zwaveHubNodeId).format()
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
