/**
 *  IMPORT URL: https://raw.githubusercontent.com/Botched1/Hubitat/master/Drivers/Haozee%20Zwave%20700%20Multisensor/Haozee%20Zwave%20700%20Multisensor.groovy
 *
 *  Haozee / NEO Coolcam Zwave 700 Multisensor
 *
 *  1.0.0 (05/22/2021) - Initial Version
 */

import groovy.transform.Field

@Field static Map commandClassVersions = 
[
	 0x30: 2  //Sensor Binary
	,0x31: 11 //Sensor Multilevel
	,0x59: 3  //Association Group Info
	,0x5A: 1  //Device Reset Locally
	,0x6C: 1  //Supervision
	,0x70: 4  //Configuration
	,0x71: 8  //Notification
	,0x72: 2  //Manufacturer Specific
	,0x73: 1  //Powerlevel
	,0x7A: 5  //Firmware Update MD
	,0x80: 1  //Battery
	,0x84: 2  //Wakeup
	,0x85: 2  //association
	,0x86: 3  //Version
	,0x87: 3  //Indicator
	,0x8E: 3  //Multi channel association
]

metadata {
	definition (name: "Haozee Zwave 700 Multisensor", namespace: "Botched1", author: "Jason Bottjen") {
		capability "Actuator"
		capability "Battery"
		//capability "Configuration"
		capability "Illuminance Measurement"
		capability "Motion Sensor"
		//capability "Initialize"
		//capability "Refresh"
		capability "Relative Humidity Measurement"
		capability "Sensor"
		capability "TamperAlert"
		capability "Temperature Measurement"

		fingerprint mfr:"0258", prod:"0020", deviceId:"0718", inClusters:"0x5E,0x98,0x9F,0x6C,0x55", deviceJoinName: "Haozee Zwave 700 Multisensor"
		
		//fingerprint mfr:"0086", prod:"0002"
    	//fingerprint deviceId: "100", inClusters: "0x5E, 0x86, 0x72, 0x59, 0x85, 0x73, 0x71, 0x84, 0x80, 0x30, 0x31, 0x70, 0x7A, 0x5A"
    	//fingerprint deviceId: "100", inClusters: "0x5E, 0x98, 0x84, 0x5A"	
	}

	preferences {
		input name: "paramLED", type: "enum", title: "LED Indicator Behavior", multiple: false, options: ["0" : "No LED when Motion Detected", "1" : "LED ON when Motion Detected"], required: false, displayDuringSetup: true
		input name: "paramMOTION", type: "enum", title: "Motion Sensor Enabled", multiple: false, options: ["0" : "Motion Sensor Disabled", "1" : "Motion Sensor Enabled"], required: false, displayDuringSetup: true
		input name: "paramMOTIONONCE", type: "enum", title: "Motion Sensor Report Once", multiple: false, options: ["0" : "Motion Sensor Reported Every Time", "1" : "Motion Sensor Reports 1x Until Inactive"], required: false, displayDuringSetup: true
		// Param 4 = Enable minimum LUX for Motion reporting 
		// Param 5 = Binary Sensor reports on motion
		input name: "paramMOTIONSENSITIVITY", type: "number", title: "Motion Sensor Sensitivity 0=Most Sensitive, 15=Least Sensitive", multiple: false, defaultValue: "2", range: "0..15", required: false, displayDuringSetup: true
		input name: "paramTEMPOFFSET", type: "number", title: "Temperature Offset (degrees)", multiple: false, defaultValue: "0", range: "-12..12", required: false, displayDuringSetup: true
		input name: "paramHUMIDITYOFFSET", type: "number", title: "Humidity Offset (%)", multiple: false, defaultValue: "0", range: "-12..12", required: false, displayDuringSetup: true
		input name: "paramTEMPREPORT", type: "number", title: "Temperature Report Delta (degrees)", multiple: false, defaultValue: "1", range: "0..10", required: false, displayDuringSetup: true
		input name: "paramHUMIDITYREPORT", type: "number", title: "Humidity Report Delta (%)", multiple: false, defaultValue: "2", range: "0..10", required: false, displayDuringSetup: true
		input name: "paramILLUMINANCEREPORT", type: "number", title: "Illuminance Report Delta (lux)", multiple: false, defaultValue: "50", range: "0..120", required: false, displayDuringSetup: true
		// Param 12 = Basic Set Level
		input name: "paramMOTIONBLIND", type: "number", title: "Motion Blind Time (s)", multiple: false, defaultValue: "8", range: "1..8", required: false, displayDuringSetup: true
		// Param 14 = Basic Set Off Time
		input name: "paramMOTIONCLEAR", type: "number", title: "Motion Clear Time (s)", multiple: false, defaultValue: "30", range: "1..30000", required: false, displayDuringSetup: true
		// Param 16 = Luminance Threshold for Associated
		input name: "paramSENSORREPORT", type: "number", title: "Sensor Report Interval Time (s)", multiple: false, defaultValue: "180", range: "0..30000", required: false, displayDuringSetup: true
		// Param 18 is Lux calibration
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

def zwaveEvent(hubitat.zwave.commands.sensorbinaryv2.SensorBinaryReport cmd) {
	if (logEnable) log.debug "---SensorBinaryReport--- ${device.displayName} sent ${cmd}"

	/*
	if (cmd.sensorValue) {
		sendEvent(name: "motion", value: "active", descriptionText: "$device.displayName detected motion", type: "physical")
	} else {
		sendEvent(name: "motion", value: "inactive", descriptionText: "$device.displayName motion has stopped", type: "physical")
	}
    */
}


def zwaveEvent(hubitat.zwave.commands.notificationv3.NotificationReport cmd)
{
	if (logEnable) log.debug "---NOTIFICATION REPORT V3--- ${device.displayName} sent ${cmd}"

	if (cmd.notificationType == 0x07) {
		if ((cmd.event == 0x00)) {
            if (logDesc) log.info "$device.displayName motion has stopped"
			sendEvent(name: "tamper", value: "clear", descriptionText: "$device.displayName tamper cleared", type: "physical")
			sendEvent(name: "motion", value: "inactive", descriptionText: "$device.displayName motion has stopped", type: "physical")
		} else if (cmd.event == 0x03) {
            if (logDesc) log.info "$device.displayName tamper detected"
			sendEvent(name: "tamper", value: "detected", descriptionText: "$device.displayName detected tamper", type: "physical")
		} else if (cmd.event == 0x08) {
            if (logDesc) log.info "$device.displayName detected motion"
			sendEvent(name: "motion", value: "active", descriptionText: "$device.displayName detected motion", type: "physical")
		} 
	} 
}

def zwaveEvent(hubitat.zwave.commands.sensormultilevelv11.SensorMultilevelReport cmd) {
	if (logEnable) log.debug "---SensorMultilevelReport REPORT V11--- ${device.displayName} sent ${cmd}"
	def result = []

	switch (cmd.sensorType) {
		case 1:
			def val = convertTemperatureIfNeeded(cmd.scaledSensorValue, cmd.scale == 1 ? "f" : "c", cmd.precision)
			if (logDesc) log.info "$device.displayName temperature is " + val + "\u00b0" + getTemperatureScale()
			sendEvent(name: "temperature", value: val, descriptionText: "$device.displayName temperature is " + val + "\u00b0" + getTemperatureScale(), type: "physical")
    	break
		case 3:
			if (logDesc) log.info "$device.displayName illuminance is " + cmd.scaledSensorValue + " lux"
			sendEvent(name: "illuminance", value: cmd.scaledSensorValue, descriptionText: "$device.displayName illuminance is " + cmd.scaledSensorValue + " lux", type: "physical")
    	break
		case 5:
			int humidityInt = Math.round(cmd.scaledSensorValue)
			if (logDesc) log.info "$device.displayName humidity is " + humidityInt + "%"
			sendEvent(name: "humidity", value: humidityInt, descriptionText: "$device.displayName humidity is " + humidityInt + "%", type: "physical")
	    break
	}
}

def zwaveEvent(hubitat.zwave.commands.batteryv1.BatteryReport cmd) {
	if (logEnable) log.debug "---BatteryReport REPORT V1--- ${device.displayName} sent ${cmd}"
	def result = []

	if (cmd.batteryLevel == 0xFF) {
		if (logDesc) log.info "$device.displayName humidity is " + humidityInt + "%"	
		sendEvent(name: "battery", value: 1, descriptionText: "$device.displayName has Low Battery", type: "physical", isStateChange: true)
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

def zwaveEvent(hubitat.zwave.commands.versionv3.VersionReport cmd) {
	def fw = "${cmd.applicationVersion}.${cmd.applicationSubVersion}"
	updateDataValue("fw", fw)
	if (logEnable) log.debug "---VERSION REPORT V3--- ${device.displayName} is running firmware version: $fw, Z-Wave version: ${cmd.zWaveProtocolVersion}.${cmd.zWaveProtocolSubVersion}"
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

void zwaveEvent(hubitat.zwave.commands.wakeupv2.WakeUpNotification cmd) {
    if (logEnable) log.debug "---WakeUpNotification: ${cmd}---"

	if (state.queuedConfig) {
		state.queuedConfig = false
		updateConfig()
	} else {
		sendToDevice(zwave.batteryV1.batteryGet().format())
	}
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

	// Wakeup Interval
	cmds.add(zwave.wakeUpV1.wakeUpIntervalSet(seconds: 43200, nodeid:zwaveHubNodeId).format())
	
	// Associations    
	cmds.add(zwave.associationV2.associationSet(groupingIdentifier:1, nodeId:zwaveHubNodeId).format())
	cmds.add(zwave.associationV2.associationRemove(groupingIdentifier: 2, nodeId: []).format())
	cmds.add(zwave.associationV2.associationRemove(groupingIdentifier: 3, nodeId: []).format())
	
	// Set LED param
	if (paramLED==null) {
		paramLED = 1
	}
	cmds.add(zwave.configurationV1.configurationSet(scaledConfigurationValue: paramLED.toInteger(), parameterNumber: 1, size: 1).format())
	cmds.add(zwave.configurationV2.configurationGet(parameterNumber: 1).format())

	// Set MOTION param
	if (paramMOTION==null) {
		paramMOTION = 1
	}	
	cmds.add(zwave.configurationV2.configurationSet(scaledConfigurationValue: paramMOTION.toInteger(), parameterNumber: 2, size: 1).format())
	cmds.add(zwave.configurationV2.configurationGet(parameterNumber: 2).format())
	
	// Set MOTIONONCE param
	if (paramMOTIONONCE==null) {
		paramMOTIONONCE = 1
	}	
	cmds.add(zwave.configurationV2.configurationSet(scaledConfigurationValue: paramMOTIONONCE.toInteger(), parameterNumber: 3, size: 1).format())
	cmds.add(zwave.configurationV2.configurationGet(parameterNumber: 3).format())
	
	// Param 4 = Enable minimum LUX for Motion reporting 
	
	// Param 5 = Binary Sensor reports on motion
	cmds.add(zwave.configurationV2.configurationSet(scaledConfigurationValue: 0, parameterNumber: 5, size: 1).format())
	cmds.add(zwave.configurationV2.configurationGet(parameterNumber: 5).format())
	
	// Set MOTIONSENSITIVITY param
	if (paramMOTIONSENSITIVITY==null) {
		paramMOTIONSENSITIVITY = 2
	}	
	cmds.add(zwave.configurationV2.configurationSet(scaledConfigurationValue: paramMOTIONSENSITIVITY.toInteger(), parameterNumber: 6, size: 1).format())
	cmds.add(zwave.configurationV2.configurationGet(parameterNumber: 6).format())
	
	// Set TEMPOFFSET param
	if (paramTEMPOFFSET==null) {
		paramTEMPOFFSET = 0
	}
	// Parameter units are 0.1 deg, so adjust the user value
	paramTEMPOFFSET = paramTEMPOFFSET * 10
	cmds.add(zwave.configurationV2.configurationSet(scaledConfigurationValue: paramTEMPOFFSET.toInteger(), parameterNumber: 7, size: 1).format())
	cmds.add(zwave.configurationV2.configurationGet(parameterNumber: 7).format())

	// Set HUMIDITYOFFSET param
	if (paramHUMIDITYOFFSET==null) {
		paramHUMIDITYOFFSET = 0
	}
	// Parameter units are 0.1 %, so adjust the user value
	paramHUMIDITYOFFSET = paramHUMIDITYOFFSET * 10
	cmds.add(zwave.configurationV2.configurationSet(scaledConfigurationValue: paramHUMIDITYOFFSET.toInteger(), parameterNumber: 8, size: 1).format())
	cmds.add(zwave.configurationV2.configurationGet(parameterNumber: 8).format())
	
	// Set TEMPREPORT param
	if (paramTEMPREPORT==null) {
		paramTEMPREPORT = 10
	}
	// Parameter units are 0.1 deg, so adjust the user value
	paramTEMPREPORT = paramTEMPREPORT * 10
	cmds.add(zwave.configurationV2.configurationSet(scaledConfigurationValue: paramTEMPREPORT.toInteger(), parameterNumber: 9, size: 1).format())
	cmds.add(zwave.configurationV2.configurationGet(parameterNumber: 9).format())
	
	// Set HUMIDITYREPORT param
	if (paramHUMIDITYREPORT==null) {
		paramHUMIDITYREPORT = 20
	}
	// Parameter units are 0.1 %, so adjust the user value
	paramHUMIDITYREPORT = paramHUMIDITYREPORT * 10
	cmds.add(zwave.configurationV2.configurationSet(scaledConfigurationValue: paramHUMIDITYREPORT.toInteger(), parameterNumber: 10, size: 1).format())
	cmds.add(zwave.configurationV2.configurationGet(parameterNumber: 10).format())

	// Set ILLUMINANCEREPORT param
	if (paramILLUMINANCEREPORT==null) {
		paramILLUMINANCEREPORT = 50
	}	
	cmds.add(zwave.configurationV2.configurationSet(scaledConfigurationValue: paramILLUMINANCEREPORT.toInteger(), parameterNumber: 11, size: 1).format())
	cmds.add(zwave.configurationV2.configurationGet(parameterNumber: 11).format())

	// Param 12 = Basic Set Level
	
	// Set MOTIONBLIND param
	if (paramMOTIONBLIND==null) {
		paramMOTIONBLIND = 8
	}	
	cmds.add(zwave.configurationV2.configurationSet(scaledConfigurationValue: paramMOTIONBLIND.toInteger(), parameterNumber: 13, size: 1).format())
	cmds.add(zwave.configurationV2.configurationGet(parameterNumber: 13).format())

	// Param 14 = Basic Set Off Time

	// Set MOTIONCLEAR param
	if (paramMOTIONCLEAR==null) {
		paramMOTIONCLEAR = 30
	}	
	cmds.add(zwave.configurationV2.configurationSet(scaledConfigurationValue: paramMOTIONCLEAR.toInteger(), parameterNumber: 15, size: 2).format())
	cmds.add(zwave.configurationV2.configurationGet(parameterNumber: 15).format())

	// Param 16 = Luminance Threshold for Associated
	
	// Set SENSORREPORT param
	if (paramSENSORREPORT==null) {
		paramSENSORREPORT = 180
	}	
	cmds.add(zwave.configurationV2.configurationSet(scaledConfigurationValue: paramSENSORREPORT.toInteger(), parameterNumber: 17, size: 2).format())
	cmds.add(zwave.configurationV2.configurationGet(parameterNumber: 17).format())

	// Param 18 is Lux calibration

	// If on battery
	if (getDataValue("inClusters").contains("0x80") || getDataValue("secureInClusters").contains("0x80")) {
		cmds.add(zwave.batteryV1.batteryGet().format())
		cmds.add(zwave.wakeUpV2.wakeUpNoMoreInformation().format())
	}
	
	// Send Commands
	sendToDevice(cmds)
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

def logsOff(){
    log.warn "debug logging disabled..."
    device.updateSetting("logEnable",[value:"false",type:"bool"])
}
