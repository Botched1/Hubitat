/**
 *  IMPORT URL: https://raw.githubusercontent.com/Botched1/Hubitat/master/Drivers/Zooz%20ZSE42%20WATER%20LEAK%20XS%20SENSOR/Zooz%20ZSE42%20WATER%20LEAK%20XS%20SENSOR.groovy
 *
 *  Zooz ZSE42 WATER LEAK XS SENSOR
 *
 *  1.0.0 (08/16/2021) - Initial Version
 *  1.0.1 (08/21/2021) - Removed redundant battery report on wakeup
 *  1.1.0 (09/07/2021) - Added debug logging command and firmware version retrieval on parameter update
 *  1.1.1 (09/16/2021) - Fixed bug that would throw an error if LED parameter wasn't set in preferences.
 *  1.2.0 (09/21/2021) - Moved wakeup interval setting to the end of updates, and changed delay from 300 to 500ms to try and made updating the config more reliable. 
 */

import groovy.transform.Field

@Field static Map commandClassVersions = 
[
	 0x30: 2  //Sensor Binary
	,0x55: 2  //Transport Service
	,0x59: 3  //Association Group Info
	,0x5A: 1  //Device Reset Locally
	,0x5E: 2  //Z-Wave Plus Info	
	,0x6C: 1  //Supervision
	,0x70: 4  //Configuration
	,0x71: 8  //Notification
	,0x72: 2  //Manufacturer Specific
	,0x73: 1  //Powerlevel
	,0x7A: 5  //Firmware Update MD
	,0x80: 1  //Battery
	,0x84: 2  //Wakeup
	,0x85: 3  //Association
	,0x86: 3  //Version
	,0x87: 3  //Indicator
	,0x8E: 4  //Multi Channel Association
	,0x9F: 1  //Security
]

metadata {
	definition (name: "Zooz ZSE42 WATER LEAK XS SENSOR", namespace: "Botched1", author: "Jason Bottjen") {
		capability "Actuator"
		capability "Battery"
		capability "Sensor"
		capability "TamperAlert"
        capability "WaterSensor"

        command "DebugLogging", [[name:"Debug Logging",type:"ENUM", description:"Turn Debug Logging OFF/ON", constraints:["", "OFF", "30m", "1h", "3h", "6h", "12h", "24h", "ON"]]]        

		fingerprint  mfr:"027A", prod:"7000", deviceId:"E002", inClusters:"0x5E,0x85,0x8E,0x59,0x55,0x86,0x72,0x5A,0x73,0x80,0x9F,0x71,0x87,0x30,0x70,0x84,0x6C,0x7A" 
	}

	preferences {
		input name: "paramLEDIndicatorMode", type: "enum", title: "LED Indicator Mode", multiple: false, options: ["0" : "No LED When Water Detected", "1" : "LED Blinks When Water Detected"], required: false, displayDuringSetup: true
		input name: "paramAlarmClearDelay", type: "number", title: "Delay before reporting dry after having been wet (in seconds)", multiple: false, defaultValue: "0", range: "0..3600", required: false, displayDuringSetup: true
		input name: "paramBatteryReportingThreshold", type: "number", title: "Battery Change Reporting Threshold (in %)", multiple: false, defaultValue: "10", range: "1..50", required: false, displayDuringSetup: true
		input name: "paramLowBatteryAlertThreshold", type: "number", title: "Low Battery Alert Threshold (in %)", multiple: false, defaultValue: "20", range: "10..50", required: false, displayDuringSetup: true
		input name: "paramGroup2Behavior", type: "enum", title: "Association Group 2 Behavior", multiple: false, options: ["0" : "Do not send commands to Group 2", "1" : "Sends 0xFF (ON) when wet and 0x00 (OFF) when dry", "2" : "Sends 0xFF (ON) when dry and 0x00 (OFF) when wet"], required: false, displayDuringSetup: true
		input name: "requestedGroup2", title: "Association Group 2 Members (Max of 5):", type: "text", required: false
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

def zwaveEvent(hubitat.zwave.commands.associationv3.AssociationReport cmd) {
	log.info "---ASSOCIATION REPORT V3--- ${device.displayName} sent groupingIdentifier: ${cmd.groupingIdentifier} maxNodesSupported: ${cmd.maxNodesSupported} nodeId: ${cmd.nodeId} reportsToFollow: ${cmd.reportsToFollow}"
}

def zwaveEvent(hubitat.zwave.commands.sensorbinaryv2.SensorBinaryReport cmd) {
	if (logEnable) log.debug "---SensorBinaryReport--- ${device.displayName} sent ${cmd}"
}

def zwaveEvent(hubitat.zwave.commands.notificationv8.NotificationReport cmd)
{
	if (logEnable) log.debug "---NOTIFICATION REPORT V8--- ${device.displayName} sent ${cmd}"

	if (cmd.notificationType == 0x05) {
		if ((cmd.event == 0x00)) {
            if (logDesc) log.info "$device.displayName is dry"
			sendEvent(name: "water", value: "dry", descriptionText: "$device.displayName is dry", type: "physical")
		} else if (cmd.event == 0x02) {
            if (logDesc) log.info "$device.displayName is wet"
			sendEvent(name: "water", value: "wet", descriptionText: "$device.displayName is wet", type: "physical")
		} 
    } else {
        log.debug "Unhandled notification: Type ${cmd.notificationType}, Event ${cmd.event}"
    }
}

def zwaveEvent(hubitat.zwave.commands.batteryv1.BatteryReport cmd) {
	if (logEnable) log.debug "---BatteryReport REPORT V1--- ${device.displayName} sent ${cmd}"
	def result = []

	if (cmd.batteryLevel == 0xFF) {
		if (logDesc) log.info "$device.displayName battery is low"	
		sendEvent(name: "battery", value: 1, descriptionText: "$device.displayName battery is low", type: "physical", isStateChange: true)
	} else {
		if (logDesc) log.info "$device.displayName battery is ${cmd.batteryLevel}%"
		sendEvent(name: "battery", value: cmd.batteryLevel, descriptionText: "$device.displayName battery is ${cmd.batteryLevel}%",type: "physical", isStateChange: true)
	}
}

def zwaveEvent(hubitat.zwave.commands.deviceresetlocallyv1.DeviceResetLocallyNotification cmd) {
	if (logEnable) log.debug "---DeviceResetLocallyNotification--- ${device.displayName} sent ${cmd}"
	log.info ("$device.displayName has reset.")
}

def zwaveEvent(hubitat.zwave.commands.configurationv2.ConfigurationReport cmd) {
	if (logEnable) log.debug "---CONFIGURATION REPORT V2--- ${device.displayName} sent ${cmd}"
}

def zwaveEvent(hubitat.zwave.commands.manufacturerspecificv2.DeviceSpecificReport cmd) {
    if (logEnable) log.debug "---Device Specific Report: ${cmd}---"
    switch (cmd.deviceIdType) {
        case 1:
            // serial number
            def serialNumber=""
            if (cmd.deviceIdDataFormat==1) {
                cmd.deviceIdData.each { serialNumber += hubitat.helper.HexUtils.integerToHexString(it & 0xff,1).padLeft(2, '0')}
            } else {
                cmd.deviceIdData.each { serialNumber += (char) it }
            }
            device.updateDataValue("serialNumber", serialNumber)
            break
    }
}

void zwaveEvent(hubitat.zwave.commands.wakeupv2.WakeUpIntervalReport cmd) {
    if (logEnable) log.debug "---WakeUpIntervalReport: ${cmd}---"
}

void zwaveEvent(hubitat.zwave.commands.wakeupv2.WakeUpIntervalCapabilitiesReport cmd) {
    if (logEnable) log.debug "---WakeUpIntervalCapabilitiesReport: ${cmd}---"
}

void zwaveEvent(hubitat.zwave.commands.wakeupv2.WakeUpNotification cmd) {
    if (logEnable) log.debug "---WakeUpNotification: ${cmd}---"

	if (state.queuedConfig) {
		state.queuedConfig = false
		updateConfig()
	} else {
		//sendToDevice(zwave.batteryV1.batteryGet().format())
	}
}

void zwaveEvent(hubitat.zwave.commands.versionv3.VersionReport cmd) {
    if (logEnable) log.debug "---Version Report: ${cmd}"
    device.updateDataValue("firmwareVersion", "${cmd.firmware0Version}.${cmd.firmware0SubVersion}")
    device.updateDataValue("protocolVersion", "${cmd.zWaveProtocolVersion}.${cmd.zWaveProtocolSubVersion}")
    device.updateDataValue("hardwareVersion", "${cmd.hardwareVersion}")
}

def zwaveEvent(hubitat.zwave.Command cmd) {
    log.warn "${device.displayName} received unhandled command: ${cmd}"
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

def installed() {
	state.queuedConfig = false
	updated()
}

def updateConfig() {
    log.info "Updating Configuration..."

	List<String> cmds = []
	
	// Associations    
	// Group1
	cmds.add(zwave.associationV3.associationSet(groupingIdentifier:1, nodeId:zwaveHubNodeId).format())
	
	// Group2
	def nodes = []
    if (settings.requestedGroup2 != state.currentGroup2) {
        nodes = parseAssocGroupList(settings.requestedGroup2, 2)
       	cmds.add(zwave.associationV3.associationRemove(groupingIdentifier: 2, nodeId: []).format())
       	cmds.add(zwave.associationV3.associationSet(groupingIdentifier: 2, nodeId: nodes).format())
       	cmds.add(zwave.associationV3.associationGet(groupingIdentifier: 2).format())
       	state.currentGroup2 = settings.requestedGroup2
    }
	
	// Set LED param
	if (paramLEDIndicatorMode==null) {
		paramLEDIndicatorMode = 1
	}
	cmds.add(zwave.configurationV2.configurationSet(scaledConfigurationValue: paramLEDIndicatorMode.toInteger(), parameterNumber: 1, size: 1).format())
	cmds.add(zwave.configurationV2.configurationGet(parameterNumber: 1).format())

	// Set Alarm Clear Delay param
	if (paramAlarmClearDelay==null) {
		paramAlarmClearDelay = 0
	}	
	cmds.add(zwave.configurationV2.configurationSet(scaledConfigurationValue: paramAlarmClearDelay.toInteger(), parameterNumber: 2, size: 4).format())
	cmds.add(zwave.configurationV2.configurationGet(parameterNumber: 2).format())
	
	// Set Battery Reporting Threshold param
	if (paramBatteryReportingThreshold==null) {
		paramBatteryReportingThreshold = 10
	}	
	cmds.add(zwave.configurationV2.configurationSet(scaledConfigurationValue: paramBatteryReportingThreshold.toInteger(), parameterNumber: 3, size: 1).format())
	cmds.add(zwave.configurationV2.configurationGet(parameterNumber: 3).format())
	
	// Set Low Battery Alert Threshold param
	if (paramLowBatteryAlertThreshold==null) {
		paramLowBatteryAlertThreshold = 20
	}	
	cmds.add(zwave.configurationV2.configurationSet(scaledConfigurationValue: paramLowBatteryAlertThreshold.toInteger(), parameterNumber: 4, size: 1).format())
	cmds.add(zwave.configurationV2.configurationGet(parameterNumber: 4).format())

	// Set Group 2 Reporting Behavior
	if (paramGroup2Behavior==null) {
		paramGroup2Behavior = 1
	}	
	cmds.add(zwave.configurationV2.configurationSet(scaledConfigurationValue: paramGroup2Behavior.toInteger(), parameterNumber: 5, size: 1).format())
	cmds.add(zwave.configurationV2.configurationGet(parameterNumber: 5).format())
	
	// Get current sensor state
	cmds.add(zwave.notificationV8.notificationGet(notificationType: 5, event: 0, v1AlarmType: 0).format())

	// Get a battery Update
	cmds.add(zwave.batteryV1.batteryGet().format())

	// Get version info
	cmds.add(new hubitat.zwave.commands.versionv3.VersionGet().format())
	
	// Wakeup Interval
	cmds.add(zwave.wakeUpV2.wakeUpIntervalSet(seconds: 43200, nodeid:zwaveHubNodeId).format())
    
	// Send No More Information
	cmds.add(zwave.wakeUpV2.wakeUpNoMoreInformation().format())
	
	// Send Commands
	log.info "Sending configuration parameters to the device..."
	sendToDevice(cmds,500)
}

def updated() {
    log.info "updated..."
    log.warn "debug logging is: ${logEnable == true}"
    log.warn "description logging is: ${txtEnable == true}"
    if (logEnable) runIn(1800,logsOff)

    if (state.lastUpdated && now() <= state.lastUpdated + 3000) return
    state.lastUpdated = now()

	if (getDataValue("inClusters").contains("0x80") || getDataValue("secureInClusters").contains("0x80")) {
		state.queuedConfig = true
	} else {
		state.queuedConfig = false
		updateConfig()
	}
}

private parseAssocGroupList(list, group) {
    def nodes = group == 2 ? [] : [zwaveHubNodeId]
    if (list) {
        def nodeList = list.split(',')
        def max = 5
        def count = 0

        nodeList.each { node ->
            node = node.trim()
            if ( count >= max) {
                log.warn "Association Group ${group}: Too many members. Greater than ${max}! This one was discarded: ${node}"
            }
            else if (node.matches("\\p{XDigit}+")) {
                def nodeId = Integer.parseInt(node,16)
                if (nodeId <= 5) {
                	log.warn "Association Group ${group}: Adding the hub id as an association is not allowed."
                }
                else if ( (nodeId > 5) & (nodeId < 256) ) {
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

def logsOff(){
    log.warn "debug logging disabled..."
    device.updateSetting("logEnable",[value:"false",type:"bool"])
}
