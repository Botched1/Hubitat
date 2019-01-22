/**
 *  GE Motion Dimmer Switch (Model 26933) DTH
 *
 *  Copyright © 2018 Michael Struck
 *  Original Author: Matt Lebaugh (@mlebaugh)
 *
 *  Original based off of the Dimmer Switch under Templates in the IDE 
 *  This based on DTH from Michael Struck - Version 1.0.5 12/4/18 
 *
 *  HUBITAT PORT
 *  1.1.0 (01/21/2019) - Ported to Hubitat by Jason Bottjen. Removed ST specifics, removed Polling and Health Check capabilities.
 *                       Removed triple tap capability. Increased delays on status update after on/off/setlevel.
 *                       Original author info left in namespace as no major functionality added/removed.
 */
metadata {
	definition (name: "GE Motion Dimmer Switch 26933", namespace: "Botched1", author: "Jason Bottjen", vid: "generic-dimmer") {
	capability "Motion Sensor"
    capability "Actuator"
 	capability "Switch"
    capability "Switch Level"
	capability "Refresh"
	capability "Sensor"
	capability "Light"
    capability "PushableButton"
        
	command "toggleMode"
    command "occupancy"
    command "vacancy"
    command "manual"
    command "setDefaultLevel", ["number"]
    command "setMotionSenseLow"
    command "setMotionSenseMed"
    command "setMotionSenseHigh"
    command "setMotionSenseOff"
    command "lightSenseOn"
	command "lightSenseOff"
    command "setTimeout5Seconds"
    command "setTimeout1Minute"
    command "setTimeout5Minutes"
    command "setTimeout15Minutes"
    command "setTimeout30Minutes"
    command "switchModeOn"
    command "switchModeOff"
	command "Configure"
        
    attribute "operatingMode", "enum", ["Manual", "Vacancy", "Occupancy"]
    attribute "defaultLevel", "number"
        
    fingerprint mfr:"0063", prod:"494D", model: "3034", deviceJoinName: "GE Z-Wave Plus Motion Wall Dimmer"
	}
	
	preferences {
        input title: "", description: "Preferences", displayDuringSetup: false, type: "paragraph", element: "paragraph"
	    //param 3
		input ("operationmode","enum",title: "Operating Mode",
			description: "Select the mode of the dimmer (no entry will keep the current mode)",
			options: [
				"1" : "Manual (no auto-on/no auto-off)",
				"2" : "Vacancy (no auto-on/auto-off)",
				"3" : "Occupancy (auto-on/auto-off)"
			],
			required: false
		)
		//param 1
        input ("timeoutduration","enum", title: "Timeout Duration (Occupancy/Vacancy)",
			description: "Time after no motion for light to turn off",
			options: [
				"0" : "5 seconds",
				"1" : "1 minute",
				"5" : "5 minutes (Default)",
				"15" : "15 minutes",
				"30" : "30 minutes"
			],
			required: false
		)
		//param 6
		input "motion", "bool", title: "Enable Motion Sensor", defaultValue:true
		//param 13
		input ("motionsensitivity","enum", title: "Motion Sensitivity (When Motion Sensor Enabled)",
			description: "Motion Sensitivity",
			options: [
				"1" : "High",
				"2" : "Medium (Default)",
				"3" : "Low"
			]
		)
		//param 14
		input "lightsense", "bool", title: "Enable Light Sensing (Occupancy)", defaultValue:true
		//param 5
		input "invertSwitch", "bool", title: "Invert Remote Switch Orientation", defaultValue:false
		//param 15
		input ("resetcycle","enum",title: "Motion Reset Cycle", description: "Time to stop reporting motion once motion has stopped",
			options: [
				"0" : "Disabled",
				"1" : "10 sec",
				"2" : "20 sec (Default)",
				"3" : "30 sec",
				"4" : "45 sec",
				"110" : "27 mins"
			]
		)           
		//dimmersettings           
		//description
		input title: "", description: "**Z-Wave Dimmer Ramp Rate Settings**\nDefaults: Step: 1, Duration: 3", type: "paragraph", element: "paragraph"
		//param 9
		input "stepSize", "number", title: "Z-Wave Dimmer Steps (#)", range: "1..99", defaultValue: 1
		//param 10
		input "stepDuration", "number", title: "Z-Wave Dimmer Step Interval (10ms)", range: "1..255", defaultValue: 3
		
		//description
		input title: "", description: "**Single Tap Dimmer Ramp Rate Settings**", displayDuringSetup: false, type: "paragraph", element: "paragraph"
		//param 18
		input "dimrate", "bool", title: "Enable Slow Dim Rate (OFF=Quick,ON=Slow)", defaultValue:false
		//param 17
		input "switchlevel","number", title: "Default Dim Level When Turned On", description:"Default 0=Return to last state, 1-99=Dimmer Level (%)", range: "0..99", defaultValue: 0
	
		//descrip
		input title: "", description: "**Manual Ramp Rate Settings**\nFor push and hold\nDefaults: Step: 1, Duration: 3", type: "paragraph", element: "paragraph"
		//param 7
		input "manualStepSize", "number", title: "Manual Dimmer Steps (#)", range: "1..99", defaultValue: 1
		//param 8
		input "manualStepDuration", "number", title: "Manual Dimmer Step Interval (10ms)", range: "1..255", defaultValue: 3
		//param 16
		input "switchmode", "bool", title: "Enable Switch Mode", defaultValue:false                             
		//association groups
		input ( type: "paragraph", element: "paragraph",
		title: "", description: "**Configure Association Groups**\nDevices in association groups 2 & 3 will receive Basic Set commands directly from the switch when it is turned on or off (physically or locally through the motion detector). Use this to control other devices as if they were connected to this switch.\n\n" +
					 "Devices are entered as a comma delimited list of the Device Network IDs in hexadecimal format."
		)			           
		input ( name: "requestedGroup2", title: "Association Group 2 Members (Max of 5):", description: "Use the 'Device Network ID' for each device", type: "text", required: false )
		input ( name: "requestedGroup3", title: "Association Group 3 Members (Max of 4):", description: "Use the 'Device Network ID' for each device", type: "text", required: false )            
    }
}
def parse(String description) {
    def result = null
    if (description != "updated") {
		//log.debug "parse() >> zwave.parse($description)"
		def cmd = zwave.parse(description, [0x20: 1, 0x25: 1, 0x56: 1, 0x70: 2, 0x72: 2, 0x85: 2, 0x71: 3, 0x56: 1])
		if (cmd) {
			result = zwaveEvent(cmd)
		}
	}
	if (!result) { log.warn "Parse returned ${result} for $description" }
	//else {log.debug "Parse returned ${result}"}
    return result
}
def zwaveEvent(hubitat.zwave.commands.crc16encapv1.Crc16Encap cmd) {
	log.debug("zwaveEvent(): CRC-16 Encapsulation Command received: ${cmd}")
	def encapsulatedCommand = zwave.commandClass(cmd.commandClass)?.command(cmd.command)?.parse(cmd.data)
	if (!encapsulatedCommand) {
		log.debug("zwaveEvent(): Could not extract command from ${cmd}")
	} 
	else {
		return zwaveEvent(encapsulatedCommand)
	}
}
def zwaveEvent(hubitat.zwave.commands.basicv1.BasicReport cmd) {
	dimmerEvents(cmd)
}
def zwaveEvent(hubitat.zwave.commands.basicv1.BasicSet cmd) {
	def result = []
    if (cmd.value == 255) {
    	result << createEvent([name: "button", value: "pushed", data: [buttonNumber: "1"], descriptionText: "On/Up on (button 1) $device.displayName was pushed", isStateChange: true, type: "physical"])
    }
	else if (cmd.value == 0) {
    	result << createEvent([name: "button", value: "pushed", data: [buttonNumber: "2"], descriptionText: "Off/Down (button 2) on $device.displayName was pushed", isStateChange: true, type: "physical"])
    }
    return result
}
def zwaveEvent(hubitat.zwave.commands.associationv2.AssociationReport cmd) {
	log.debug "---ASSOCIATION REPORT V2--- ${device.displayName} sent groupingIdentifier: ${cmd.groupingIdentifier} maxNodesSupported: ${cmd.maxNodesSupported} nodeId: ${cmd.nodeId} reportsToFollow: ${cmd.reportsToFollow}"
    state.group3 = "1,2"
    if (cmd.groupingIdentifier == 3) {
    	if (cmd.nodeId.contains(zwaveHubNodeId)) {
        	sendEvent(name: "numberOfButtons", value: 2, displayed: false)
        }
        else {
        	sendEvent(name: "numberOfButtons", value: 0, displayed: false)
			def cmds=[]
			cmds << zwave.associationV2.associationSet(groupingIdentifier: 3, nodeId: zwaveHubNodeId).format()
			cmds << zwave.associationV2.associationGet(groupingIdentifier: 3).format()
			delayBetween(cmds, 1000)
        }
    }
}
def zwaveEvent(hubitat.zwave.commands.configurationv2.ConfigurationReport cmd) {
	log.debug "---CONFIGURATION REPORT V2--- ${device.displayName} sent ${cmd}"
    def config = cmd.scaledConfigurationValue
    def result = []
    if (cmd.parameterNumber == 1) {
		def value = config == 0 ? "5 seconds" : config == 1 ? "1 minute" : config == 5 ? "5 minutes" : config == 15 ? "15 minutes" : "30 minutes"
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
    //dimmer settings
    	else if (cmd.parameterNumber == 7) {
    	result << createEvent([name:"StepSize", value: config, displayed:true, isStateChange:true])
    } else if (cmd.parameterNumber == 8) {
    	result << createEvent([name:"StepDuration", value: config, displayed:true, isStateChange:true])
    } else if (cmd.parameterNumber == 9) {
    	result << createEvent([name:"ManualStepSize", value: config, displayed:true, isStateChange:true])
    } else if (cmd.parameterNumber == 10) {
    	result << createEvent([name:"ManualStepDuration", value: config, displayed:true, isStateChange:true])
    } else if (cmd.parameterNumber == 16) {
    	result << createEvent([name:"SwitchMode", value: config, displayed:true, isStateChange:true])
    } else if (cmd.parameterNumber == 17) {
    	result << createEvent([name:"defaultLevel", value: config, displayed:true, isStateChange:true])
    } else if (cmd.parameterNumber == 18) {
    	result << createEvent([name:"DimRate", value: config, displayed:true, isStateChange:true])
    } else {
    log.warn "Parameter  ${cmd.parameterNumber} ${config}"}
    //
   return result
}
def zwaveEvent(hubitat.zwave.commands.switchbinaryv1.SwitchBinaryReport cmd) {
    log.debug "---BINARY SWITCH REPORT V1--- ${device.displayName} sent ${cmd}"
    sendEvent(name: "switch", value: cmd.value ? "on" : "off", type: "digital")
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
	log.debug "---VERSION REPORT V1--- ${device.displayName} is running firmware version: $fw, Z-Wave version: ${cmd.zWaveProtocolVersion}.${cmd.zWaveProtocolSubVersion}"
}
def zwaveEvent(hubitat.zwave.commands.hailv1.Hail cmd) {
	createEvent([name: "hail", value: "hail", descriptionText: "Switch button was pressed", displayed: false])
}
def zwaveEvent(hubitat.zwave.commands.notificationv3.NotificationReport cmd){
	log.debug "---NOTIFICATION REPORT V3--- ${device.displayName} sent ${cmd}"
	def result = []
    def cmds = []
	if (cmd.notificationType == 0x07) {
		if ((cmd.event == 0x00)) { 
           	result << createEvent(name: "motion", value: "inactive", descriptionText: "$device.displayName motion has stopped")
         } else if (cmd.event == 0x08) {
            result << createEvent(name: "motion", value: "active", descriptionText: "$device.displayName detected motion")	  
        }
    }
	result  
}
def zwaveEvent(hubitat.zwave.Command cmd) {
    log.warn "${device.displayName} received unhandled command: ${cmd}"
}
def zwaveEvent(hubitat.zwave.commands.switchmultilevelv3.SwitchMultilevelReport cmd) {
	log.debug "---SWITCH MULTILEVEL REPORT V3--- ${device.displayName} sent ${cmd}"
	dimmerEvents(cmd)
}
def zwaveEvent(hubitat.zwave.commands.switchmultilevelv3.SwitchMultilevelSet cmd) {
	log.debug "---SWITCH MULTILEVEL SET V3--- ${device.displayName} sent ${cmd}"
	dimmerEvents(cmd)
}
def zwaveEvent(hubitat.zwave.commands.switchmultilevelv3.SwitchMultilevelStopLevelChange cmd) {
	log.debug "---SWITCH MULTILEVEL Stop Level Change--- ${device.displayName} sent ${cmd}"
	dimmerEvents(cmd)
}
private dimmerEvents(hubitat.zwave.Command cmd) {
    def value = (cmd.value ? "on" : "off")
    def result = [createEvent(name: "switch", value: value)]
    if (cmd.value && cmd.value <= 100) {
		result << createEvent(name: "level", value: cmd.value, unit: "%")
    }
    return result
}
def on() {
    delayBetween([zwave.basicV1.basicSet(value: 0xFF).format(), zwave.switchMultilevelV3.switchMultilevelGet().format()], 5000) 
}
def off() {
	delayBetween ([zwave.basicV1.basicSet(value: 0x00).format(), zwave.switchMultilevelV3.switchMultilevelGet().format()], 5000)
}
def setLevel(value) {
    def valueaux = value as Integer
    def level = Math.min(valueaux, 99)
    def cmds = []
   	log.debug "setlevel activates light"
   	sendEvent(name: "level", value: level, unit: "%")
	delayBetween ([zwave.basicV1.basicSet(value: level).format(), zwave.switchMultilevelV3.switchMultilevelGet().format()], 5000)
    }
def setLevel(value, duration) {
    def valueaux = value as Integer
    def level = Math.min(valueaux, 99)
    def dimmingDuration = duration < 128 ? duration : 128 + Math.round(duration / 60)
    sendEvent(name: "level", value: level, unit: "%")
    zwave.switchMultilevelV2.switchMultilevelSet(value: level, dimmingDuration: dimmingDuration).format()
}
def refresh() {
    log.debug "refresh() is called"
    delayBetween([
    	zwave.notificationV3.notificationGet(notificationType: 7).format(),
        zwave.switchMultilevelV3.switchMultilevelGet().format()
	],500)
    showVersion()
}
def toggleMode() {
	log.debug("Toggling Mode") 
    def cmds = []
    if (device.currentValue("operatingMode") == "Manual")  vacancy()
    else if (device.currentValue("operatingMode") == "Vacancy") occupancy()
    else if (device.currentValue("operatingMode") == "Occupancy") manual()
}
def setTimeout5Seconds() { setTimeoutMinutes(0) }
def setTimeout1Minute() { setTimeoutMinutes(1) }
def setTimeout5Minutes() { setTimeoutMinutes(5) }
def setTimeout15Minutes() { setTimeoutMinutes(15) }
def setTimeout30Minutes() { setTimeoutMinutes(30) }
def setTimeoutMinutes(value){
    def cmds = [], newTimeOut
    if (value==0) newTimeOut="5 seconds"
    else if (value==1) newTimeOut="1 minute"
    else if (value==5) newTimeOut="5 minutes"
    else if (value==15) newTimeOut="15 minutes"
    else if (value==30) newTimeOut="30 minutes"
	cmds << zwave.configurationV1.configurationSet(configurationValue: [value], parameterNumber: 1, size: 1)
    cmds << zwave.configurationV1.configurationGet(parameterNumber: 1)
    delayBetween(cmds, 1000)
    log.debug "Setting timeout duration to: ${newTimeOut}"
}
def occupancy() {
	log.debug "Setting operating mode to: Occupancy"
    def cmds = []
    cmds << zwave.configurationV1.configurationSet(configurationValue: [3] , parameterNumber: 3, size: 1)
    cmds << zwave.configurationV1.configurationGet(parameterNumber: 3)
    delayBetween(cmds, 1000)
}
def vacancy() {
	log.debug "Setting operating mode to: Vacancy"
	def cmds = []
    cmds << zwave.configurationV1.configurationSet(configurationValue: [2] , parameterNumber: 3, size: 1)
    cmds << zwave.configurationV1.configurationGet(parameterNumber: 3)
	delayBetween(cmds, 1000)
}
def manual() {
	log.debug "Setting operating mode to: Manual"
	def cmds = []
    cmds << zwave.configurationV1.configurationSet(configurationValue: [1] , parameterNumber: 3, size: 1)
    cmds << zwave.configurationV1.configurationGet(parameterNumber: 3)
	delayBetween(cmds, 1000)
}
def setDefaultLevel(value) {
    log.debug("Setting default level: ${value}%") 
    def cmds = []
    cmds << zwave.configurationV1.configurationSet(configurationValue: [value.toInteger()] , parameterNumber: 17, size: 1)
  	cmds << zwave.configurationV1.configurationGet(parameterNumber: 17)
    delayBetween(cmds, 1000)
}
def setMotionSenseLow(){ setMotionSensitivity(3) }
def setMotionSenseMed(){ setMotionSensitivity(2) }
def setMotionSenseHigh(){ setMotionSensitivity(1) }
def setMotionSenseOff(){ setMotionSensitivity(0) }
def setMotionSensitivity(value) {
      def cmds = []
      if (value>0){
      	def mode=value==1 ? "High" : value==2 ? "Medium" : "Low"
      	log.debug("Setting motion sensitivity to: ${mode}")
        cmds << zwave.configurationV1.configurationSet(configurationValue: [1], parameterNumber: 6, size: 1)
        cmds << zwave.configurationV1.configurationGet(parameterNumber: 6)
      	cmds << zwave.configurationV1.configurationSet(configurationValue: [value] , parameterNumber: 13, size: 1)
        cmds << zwave.configurationV1.configurationGet(parameterNumber: 13)        
      }
      else if (value==0){
      	log.debug("Turning off motion sensor")
        cmds << zwave.configurationV1.configurationSet(configurationValue: [0], parameterNumber: 6, size: 1)
        cmds << zwave.configurationV1.configurationGet(parameterNumber: 6)
     }
     delayBetween(cmds, 1000)
}
def lightSenseOn() {
	log.debug("Setting Light Sense ON") 
    def cmds = []
    cmds << zwave.configurationV1.configurationSet(configurationValue: [1], parameterNumber: 14, size: 1)
    cmds << zwave.configurationV1.configurationGet(parameterNumber: 14)
    delayBetween(cmds, 1000)
}
def lightSenseOff() {
	log.debug("Setting Light Sense OFF") 
    def cmds = []
    cmds << zwave.configurationV1.configurationSet(configurationValue: [0], parameterNumber: 14, size: 1)
    cmds << zwave.configurationV1.configurationGet(parameterNumber: 14)
    delayBetween(cmds, 1000)
}
def switchModeOn() {
	log.debug ("Enabling Switch Mode")
	def cmds = []
    cmds << zwave.configurationV1.configurationSet(configurationValue: [1], parameterNumber: 16, size: 1)
    cmds << zwave.configurationV1.configurationGet(parameterNumber: 16)
    delayBetween(cmds, 1000)
}
def switchModeOff(){
	log.debug ("Disabling Switch Mode (Entering Dimmer Mode)")
	def cmds = []
    cmds << zwave.configurationV1.configurationSet(configurationValue: [0], parameterNumber: 16, size: 1)
    cmds << zwave.configurationV1.configurationGet(parameterNumber: 16)
    delayBetween(cmds, 1000)
}
def installed() {
	updated()
}
def Configure() {
	updated()
}
def updated() {
    sendEvent(name: "checkInterval", value: 2 * 15 * 60 + 2 * 60, displayed: false, data: [protocol: "zwave", hubHardwareId: device.hub.hardwareID])
    if (!state.settingVar) state.settingVar=[]
    if (state.lastUpdated && now() <= state.lastUpdated + 3000) return
    state.lastUpdated = now()
	def cmds = [], timeDelay, motionSensor, lightSensor, dimLevel, switchMode
    
    //switch and dimmer settings
    //param 1 timeout duration
    if (settings.timeoutduration) {
       	cmds << zwave.configurationV1.configurationSet(configurationValue: [settings.timeoutduration.toInteger()], parameterNumber: 1, size: 1)
       	cmds << zwave.configurationV1.configurationGet(parameterNumber: 1)
    }
    else{
       	cmds << zwave.configurationV1.configurationSet(configurationValue: [5], parameterNumber: 1, size: 1)
       	cmds << zwave.configurationV1.configurationGet(parameterNumber: 1)
    }
    //param 13 motion sensitivity
    if (settings.motionsensitivity) {
        cmds << zwave.configurationV1.configurationSet(configurationValue: [settings.motionsensitivity.toInteger()], parameterNumber: 13, size: 1)
        cmds << zwave.configurationV1.configurationGet(parameterNumber: 13)
    }
    else {
        cmds << zwave.configurationV1.configurationSet(configurationValue: [2], parameterNumber: 13, size: 1)
        cmds << zwave.configurationV1.configurationGet(parameterNumber: 13)
    }
    //param 14 lightsense
    lightSensor = lightsense ? 1 : 0
    cmds << zwave.configurationV1.configurationSet(configurationValue: [lightSensor], parameterNumber: 14, size: 1)
    cmds << zwave.configurationV1.configurationGet(parameterNumber: 14)
    //param 15 reset cycle
    if (settings.resetcycle) {
	cmds << zwave.configurationV1.configurationSet(configurationValue: [settings.resetcycle.toInteger()], parameterNumber: 15, size: 1)
	cmds << zwave.configurationV1.configurationGet(parameterNumber: 15)
    }
    else {
       	cmds << zwave.configurationV1.configurationSet(configurationValue: [2], parameterNumber: 15, size: 1)
       	cmds << zwave.configurationV1.configurationGet(parameterNumber: 15)
    }
    //param 3 operating mode (no default...no entry=no change)
    if (settings.operationmode) cmds << zwave.configurationV1.configurationSet(configurationValue: [settings.operationmode.toInteger()], parameterNumber: 3, size: 1)
    cmds << zwave.configurationV1.configurationGet(parameterNumber: 3)
    //param 6 motion
    motionSensor = !motion ? 0 : motionSensor
    cmds << zwave.configurationV1.configurationSet(configurationValue: [motion ? 1 : 0], parameterNumber: 6, size: 1)
    cmds << zwave.configurationV1.configurationGet(parameterNumber: 6)
    //param 5 invert switch
    cmds << zwave.configurationV1.configurationSet(configurationValue: [invertSwitch ? 1 : 0 ], parameterNumber: 5, size: 1)
    cmds << zwave.configurationV1.configurationGet(parameterNumber: 5)
	//Add hub ID to associations
    cmds << zwave.associationV1.associationSet(groupingIdentifier:0, nodeId:zwaveHubNodeId)
    cmds << zwave.associationV1.associationSet(groupingIdentifier:1, nodeId:zwaveHubNodeId)
    cmds << zwave.associationV1.associationSet(groupingIdentifier:2, nodeId:zwaveHubNodeId)
    cmds << zwave.associationV1.associationSet(groupingIdentifier:3, nodeId:zwaveHubNodeId)
        
    def nodes = []
    if (settings.requestedGroup2 != state.currentGroup2) {
        nodes = parseAssocGroupList(settings.requestedGroup2, 2)
       	cmds << zwave.associationV2.associationRemove(groupingIdentifier: 2, nodeId: [])
       	cmds << zwave.associationV2.associationSet(groupingIdentifier: 2, nodeId: nodes)
       	cmds << zwave.associationV2.associationGet(groupingIdentifier: 2)
       	state.currentGroup2 = settings.requestedGroup2
    }
    if (settings.requestedGroup3 != state.currentGroup3) {
       	nodes = parseAssocGroupList(settings.requestedGroup3, 3)
       	cmds << zwave.associationV2.associationRemove(groupingIdentifier: 3, nodeId: [])
       	cmds << zwave.associationV2.associationSet(groupingIdentifier: 3, nodeId: nodes)
       	cmds << zwave.associationV2.associationGet(groupingIdentifier: 3)
       	state.currentGroup3 = settings.requestedGroup3
    }
    // end switch and dimmer settings
		        
    // dimmer specific settings
    //param 7 zwave step
    if (settings.stepSize) {
       	cmds << zwave.configurationV1.configurationSet(configurationValue: [settings.stepSize.toInteger()], parameterNumber: 7, size: 1)    
       	cmds << zwave.configurationV1.configurationGet(parameterNumber: 7)
    }
    //param 8 zwave duration
    if (settings.stepDuration) {
       	cmds << zwave.configurationV1.configurationSet(configurationValue: [0,settings.stepDuration.toInteger()], parameterNumber: 8, size: 2)
       	cmds << zwave.configurationV1.configurationGet(parameterNumber: 8)
    }
    //param 9 manual step
    if (settings.manualStepSize) {
       	cmds << zwave.configurationV1.configurationSet(configurationValue: [settings.manualStepSize.toInteger()], parameterNumber: 9, size: 1)
       	cmds << zwave.configurationV1.configurationGet(parameterNumber: 9)
    }
    //param 10 manual duration
    if (settings.manualStepDuration) {
      	cmds << zwave.configurationV1.configurationSet(configurationValue: [0,settings.manualStepDuration.toInteger()], parameterNumber: 10, size: 2)
      	cmds << zwave.configurationV1.configurationGet(parameterNumber: 10)  
    }
    //switch mode param 16
    switchMode = switchmode ? 1 : 0
    cmds << zwave.configurationV1.configurationSet(configurationValue: [switchMode], parameterNumber: 16, size: 1)
    cmds << zwave.configurationV1.configurationGet(parameterNumber: 16)
    //switch level param 17
    if (settings.switchlevel == "0" || !switchlevel) {
       	cmds << zwave.configurationV1.configurationSet(configurationValue: [0], parameterNumber: 17, size: 1)
    } 
    else if (settings.switchlevel) {
       	cmds << zwave.configurationV1.configurationSet(configurationValue: [settings.switchlevel.toInteger()], parameterNumber: 17, size: 1)
    	cmds << zwave.configurationV1.configurationGet(parameterNumber: 17)
    }
    //dim rate param 18
    cmds << zwave.configurationV1.configurationSet(configurationValue: [dimrate ? 1 : 0], parameterNumber: 18, size: 1)
    cmds << zwave.configurationV1.configurationGet(parameterNumber: 18)
    //end of dimmer specific params   
    delayBetween(cmds, 500)
    showVersion()
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
                	log.warn "Association Group ${group}: Adding the hub as an association is not allowed."
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
def showVersion() { sendEvent (name: "about", value:"Version 1.1.0 (01/21/2019)") }
