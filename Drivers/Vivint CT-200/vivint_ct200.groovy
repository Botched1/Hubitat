/**
 *  IMPORT URL: https://raw.githubusercontent.com/Botched1/Hubitat/master/Drivers/Vivint%20CT-200/vivint_ct200.groovy
 *
 *  Vivint CT200 Thermostat driver for Hubitat
 *
 *  Copyright 2020 Jason Bottjen
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 *  Version 1.0 - 07/25/2020     Initial Version
 *  Version 1.1 - 07/26/2020     Fixed currentSensorCal not recording correctly
 *  Version 1.2 - 07/27/2020     Added Refresh after 10s when configure button is pressed
 */
metadata {
	definition (name: "Vivint CT200 Thermostat", namespace: "Botched1", author: "Jason Bottjen") {
		capability "Actuator"
		capability "Battery"
		capability "Configuration"
		capability "Refresh"
		capability "Relative Humidity Measurement"
		capability "Sensor"
		capability "Temperature Measurement"
		capability "Thermostat"
		capability "Thermostat Fan Mode"
		   
		command "SensorCal", [[name:"Degrees",type:"ENUM", description:"Number of degrees to add/subtract from thermostat sensor", constraints:["0", "-6", "-5", "-4", "-3", "-2", "-1", "0", "1", "2", "3", "4", "5", "6"]]]
		command "ManualFanTimer", [[name:"Minutes",type:"ENUM", description:"Manually run fan for specified # of minutes", constraints:["0", "15", "30", "60"]]]
		command "DebugLogging", [[name:"Command",type:"ENUM", description:"Turn Debug Logging OFF/ON", constraints:["OFF", "ON"]]]
		
		attribute "thermostatFanState", "string"
		attribute "currentSensorCal", "number"
        
        fingerprint mfr:"0098", prod:"C801", deviceId:"0022", inClusters:"0x5E,0x86,0x5D,0x85,0x59,0x80,0x7A,0x31,0x81,0x87,0x40,0x42,0x44,0x45,0x43,0x70,0x20,0x8E,0x73,0x5A,0x60,0x72", deviceJoinName: "Vivint CT200 Thermostat"
	}
	preferences {
		input "paramTempReportThreshold", "enum", title: "Temperature Delta for Reporting", multiple: false, defaultValue: "2: 1.0 Degree", options: ["0: Disabled", "1: 0.5 Degree", "2: 1.0 Degree", "3: 1.5 Degrees", "4: 2.0 Degrees"], required: false, displayDuringSetup: true																		
		//input "paramHVACSettings", "number", title: "HVAC Configuration Settings", multiple: false, defaultValue: "0", required: false, displayDuringSetup: true																		
		//input "paramUtilityLock", "number", title: "Lock Display", multiple: false, defaultValue: "0", range: "0..1", required: false, displayDuringSetup: true																		
		//input "paramPowerType", "number", title: "Power by C-Wire or Battery", multiple: false, defaultValue: "0", required: false, displayDuringSetup: true																		
		input "paramHumidityReportThreshold", "enum", title: "Humidity Delta for Reporting", multiple: false, defaultValue: "2: 5%", options: ["0: Disabled", "1: 3%", "2: 5%", "3: 10%"], required: false, displayDuringSetup: true																		
		//input "paramAuxHeating", "number", title: "Aux / Emergency Heating", multiple: false, defaultValue: "0", range: "0..1", required: false, displayDuringSetup: true																		
		input "paramSwingTemp", "enum", title: "Swing Temperature (degrees away from setpoint before HVAC turns on)", multiple: false, defaultValue: "4: 2.0 Degrees", options: ["1: 0.5 Degree", "2: 1.0 Degree", "3: 1.5 Degrees", "4: 2.0 Degrees", "5: 2.5 Degrees", "6: 3.0 Degrees", "7: 3.5 Degrees", "8: 4.0 Degrees"], required: false, displayDuringSetup: true																		
		//input "paramDiffTemp", "number", title: "Swing Temperature", multiple: false, defaultValue: "2", range: "1..8", required: false, displayDuringSetup: true																		
		input "paramRecoveryMode", "enum", title: "Recovery Mode", multiple: false, defaultValue: "2: Economy", options: ["1: Fast", "2: Economy"], required: false, displayDuringSetup: true																		
        //input "paramTempReportFilter", "number", title: "Temperature Reporting Filter", multiple: false, defaultValue: "2", range: "1..2", required: false, displayDuringSetup: true																		
		input "paramUIMode", "enum", title: "UI Mode", multiple: false, defaultValue: "0: Normal", options: ["0: Normal", "1: Simple"], required: false, displayDuringSetup: true																		
		input "paramMulticast", "enum", title: "Multicast Messaging", multiple: false, defaultValue: "0: Disabled", options: ["0: Disabled", "1: Enabled"], required: false, displayDuringSetup: true																		
		//input "paramDisplaySelect", "number", title: "Display Temperature or Relative Humidity on Display (0=temp, 1=RH)", multiple: false, defaultValue: "0", range: "0..1", required: false, displayDuringSetup: true																		
		//input "paramSaveEnergyModeType", "number", title: "Save Energy Mode Type", multiple: false, defaultValue: "2", range: "1..255", required: false, displayDuringSetup: true																		
		//input "paramFanTimer", "number", title: "Run fan for XX minutes", multiple: false, defaultValue: "0", range: "0..1", required: false, displayDuringSetup: true																		
		//input "paramHumidityControlActivation", "number", title: "Passthrough for humidity control", multiple: false, defaultValue: "0", range: "0..1", required: false, displayDuringSetup: true																		
		input "paramSensorCal", "enum", title: "Temperature Sensor Calibration/Offset (degrees)", multiple: false, defaultValue: "0", options: ["0", "-6", "-5", "-4", "-3", "-2", "-1", "0", "1", "2", "3", "4", "5", "6"], required: false, displayDuringSetup: true						
		input "paramDisplayUnits", "enum", title: "Display Units", multiple: false, defaultValue: "0: F", options: ["0: F", "1: C"], required: false, displayDuringSetup: true																		
	}
            
}

///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// parse events into attributes
///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
def parse(String description) {
	if (logEnable) log.debug "Parse...START"
	if (logEnable) log.debug "Parsing '${description}'"
	try {
		def map = createEvent(zwaveEvent(zwave.parse(description, [0x20:1, 0x31: 6, 0x40:2, 0x42:2, 0x43:2, 0x44:1, 0x45:1, 0x59:1, 0x5A:1, 0x5D:1, 0x5E:2, 0x60:4, 0x70:1, 0x72:2, 0x73:1, 0x7A:3, 0x80: 1, 0x81: 1, 0x85:2, 0x86:2, 0x87:1, 0x8F:3])))
		if (logEnable) log.debug "In parse, map is $map"
	}
	catch (e) {
		//if (logEnable) log.debug "In parse, error in zwave.parse"
		log.debug "In parse, error in zwave.parse. Description = '${description}'. Error = '${e}'."
	}
	return null
}

///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Handle commands from Thermostat
///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
//
def setHeatingSetpoint(double degrees) {
	if (logEnable) log.debug "setHeatingSetpoint...START"
    
	def locationScale = getTemperatureScale()
    def p = (state.precision == null) ? 0 : state.precision
	
	if (p==0) {
		degrees2 = Math.rint(degrees)
	} else {
        degrees2 = degrees2.round(p)
	}
	
    if (state.scale == null)
    {
        state.scale = 1
    }
    
	if (logEnable) log.debug "stateScale is $state.scale"
    if (logEnable) log.debug "locationScale is $locationScale"
	if (logEnable) log.debug "precision is $p"
    if (logEnable) log.debug "setpoint requested is $degrees"
    if (logEnable) log.debug "setpoint written is $degrees2"
    if (logEnable) log.debug "setHeatingSetpoint...END"

	commands([
		zwave.thermostatSetpointV2.thermostatSetpointSet(setpointType: 1, scale: state.scale, precision: p, scaledValue: degrees2),
        zwave.thermostatSetpointV2.thermostatSetpointGet(setpointType: 1)
	], 1000)       
}

def setCoolingSetpoint(double degrees) {
	if (logEnable) log.debug "setCoolingSetpoint...START"

	def locationScale = getTemperatureScale()
	def p = (state.precision == null) ? 0 : state.precision

	if (p==0) {
		degrees2 = Math.rint(degrees)
	} else {
		degrees2 = (Double)degrees
		degrees2 = degrees2.round(p)
	}
	
    if (state.scale == null)
    {
        state.scale = 1
    }
    
	if (logEnable) log.debug "stateScale is $state.scale"
    if (logEnable) log.debug "locationScale is $locationScale"
	if (logEnable) log.debug "precision is $p"
    if (logEnable) log.debug "setpoint requested is $degrees"
    if (logEnable) log.debug "setpoint written is $degrees2"
    if (logEnable) log.debug "setCoolingSetpoint...END"

    commands([
		zwave.thermostatSetpointV2.thermostatSetpointSet(setpointType: 2, scale: state.scale, precision: p, scaledValue: degrees2),
        zwave.thermostatSetpointV2.thermostatSetpointGet(setpointType: 2)
	], 1000)       
}

def off() {
	if (logEnable) log.debug "Switching to off mode..."
    commands([
		zwave.thermostatModeV2.thermostatModeSet(mode: 0),
        zwave.thermostatModeV2.thermostatModeGet(),
        zwave.thermostatOperatingStateV2.thermostatOperatingStateGet()
	], 1000)
}

def on() {
	if (logEnable) log.debug "Executing 'on'"
	log.warn "ON is too ambiguous for a multi-state thermostat, so it does nothing in this driver. Use setThermostatMode or the AUTO/COOL/HEAT commands."
}

def heat() {
	if (logEnable) log.debug "Switching to heat mode..."
	commands([
		zwave.thermostatModeV2.thermostatModeSet(mode: 1),
		zwave.thermostatModeV2.thermostatModeGet(),
        zwave.thermostatOperatingStateV2.thermostatOperatingStateGet()
	], 1000)   
}

def emergencyHeat() {
	if (logEnable) log.debug "Switching to emergency heat mode..."
    commands([
		zwave.thermostatModeV2.thermostatModeSet(mode: 4),
		zwave.thermostatModeV2.thermostatModeGet(),
        zwave.thermostatOperatingStateV2.thermostatOperatingStateGet()
	], 1000)
}

def cool() {
	if (logEnable) log.debug "Switching to cool mode..."
    commands([
		zwave.thermostatModeV2.thermostatModeSet(mode: 2),
		zwave.thermostatModeV2.thermostatModeGet(),
        zwave.thermostatOperatingStateV2.thermostatOperatingStateGet()
	], 1000)
}

def setThermostatMode(value) {
	if (logEnable) log.debug "Executing 'setThermostatMode'"
	if (logEnable) log.debug "value: " + value
	def cmds = []
	switch (value) {
		case "off":
			if (logEnable) log.debug "Switching to off mode..."
			cmds << zwave.thermostatModeV2.thermostatModeSet(mode: 0)
			break
		case "heat":
			if (logEnable) log.debug "Switching to heat mode..."
			cmds << zwave.thermostatModeV2.thermostatModeSet(mode: 1)
			break
		case "cool":
			if (logEnable) log.debug "Switching to cool mode..."
			cmds << zwave.thermostatModeV2.thermostatModeSet(mode: 2)
			break
		case "auto":
		    if (logEnable) log.debug "Switching to auto mode..."
			cmds << zwave.thermostatModeV2.thermostatModeSet(mode: 3)
			break
		case "emergency heat":
			if (logEnable) log.debug "Switching to emergency heat mode..."
			cmds << zwave.thermostatModeV2.thermostatModeSet(mode: 4)
			break
	}
	cmds << zwave.thermostatModeV2.thermostatModeGet()
    cmds << zwave.thermostatOperatingStateV2.thermostatOperatingStateGet()
	commands(cmds, 1000)
}

def fanOn() {
	if (logEnable) log.debug "Switching fan to on mode..."
    commands([
		zwave.thermostatFanModeV1.thermostatFanModeSet(fanMode: 1),
		zwave.thermostatFanModeV1.thermostatFanModeGet(),
        zwave.thermostatFanStateV1.thermostatFanStateGet()
	], 1000)   
}

def fanAuto() {
	if (logEnable) log.debug "Switching fan to auto mode..."
    commands([
		zwave.thermostatFanModeV1.thermostatFanModeSet(fanMode: 0),
		zwave.thermostatFanModeV1.thermostatFanModeGet(),
        zwave.thermostatFanStateV1.thermostatFanStateGet()
	], 1000)
}

def fanCirculate() {
	if (logEnable) log.debug "Fan circulate mode not supported for this thermostat type..."
	log.warn "Fan circulate mode not supported for this thermostat type..."
}

def setThermostatFanMode(value) {
	if (logEnable) log.debug "Executing 'setThermostatFanMode'"
	if (logEnable) log.debug "value: " + value
	def cmds = []
	switch (value) {
		case "on":
			if (logEnable) log.debug "Switching fan to ON mode..."
			cmds << zwave.thermostatFanModeV1.thermostatFanModeSet(fanMode: 1)
			break
		case "auto":
			if (logEnable) log.debug "Switching fan to AUTO mode..."
			cmds << zwave.thermostatFanModeV1.thermostatFanModeSet(fanMode: 0)
			break
		default:
			log.warn "Fan Mode $value unsupported."
			break
	}
	cmds << zwave.thermostatFanModeV1.thermostatFanModeGet()
    cmds << zwave.thermostatFanStateV1.thermostatFanStateGet()
	commands(cmds, 1000)
}

def auto() {
	if (logEnable) log.debug "Switching to auto mode..."
    commands([
		zwave.thermostatModeV2.thermostatModeSet(mode: 3),
		zwave.thermostatModeV2.thermostatModeGet(),
        zwave.thermostatOperatingStateV2.thermostatOperatingStateGet()
	], 1000)
}

def setSchedule() {
	if (logEnable) log.debug "Executing 'setSchedule'"
	log.warn "setSchedule does not do anything with this driver."
}

def refresh() {
	if (logEnable) log.debug "Executing 'refresh'"
	commands([
        zwave.batteryV1.batteryGet(),
        zwave.thermostatModeV2.thermostatModeGet(),
		zwave.thermostatOperatingStateV2.thermostatOperatingStateGet(),
		zwave.thermostatFanModeV1.thermostatFanModeGet(),
		zwave.thermostatFanStateV1.thermostatFanStateGet(),
		zwave.thermostatSetpointV2.thermostatSetpointGet(setpointType: 1),
		zwave.thermostatSetpointV2.thermostatSetpointGet(setpointType: 2)
	], 1000)   
}

def configure() {
	if (logEnable) log.debug "Executing 'configure'"
	if (logEnable) log.debug "zwaveHubNodeId: " + zwaveHubNodeId
	if (logEnable) log.debug "....done executing 'configure'"
	
	runIn(10,refresh)
	
	commands([
		zwave.thermostatModeV2.thermostatModeSupportedGet(),
		zwave.thermostatFanModeV1.thermostatFanModeSupportedGet(),
		zwave.associationV1.associationSet(groupingIdentifier:1, nodeId:[zwaveHubNodeId])
	], 1000)
}

def ManualFanTimer(value) {
	if (logEnable) log.debug "Executing 'ManualFanTimer'"
    value = value.toInteger()
	if (logEnable) log.debug "FanTimer value: " + value
	
	commands([
		zwave.configurationV1.configurationSet(scaledConfigurationValue: value, parameterNumber: 15, size: 1),
		zwave.configurationV1.configurationGet(parameterNumber: 15),
		zwave.thermostatFanModeV1.thermostatFanModeGet(),
        zwave.thermostatFanStateV1.thermostatFanStateGet()
    ], 500)
}

def SensorCal(value) {
	value = value.toInteger()
	if (logEnable) log.debug "SensorCal value: " + value
	
	commands([
		zwave.configurationV1.configurationSet(scaledConfigurationValue: value, parameterNumber: 17, size: 1),
		zwave.configurationV1.configurationGet(parameterNumber: 17)
		], 500)
}

def DebugLogging(value) {
	if (value=="OFF") {logsoff}
        if (value=="ON") {
		log.debug "debug logging is enabled."
		unschedule(logsOff)
		device.updateSetting("logEnable",[value:"true",type:"bool"])
		runIn(1800,logsOff)
	}	
}

def updated() {
	if (logEnable) log.debug "Executing 'updated'"

	unschedule()
	
    if (logEnable) {
		log.debug "debug logging is enabled."
		runIn(1800,logsOff)
	}

    def cmds = []
    String tempstr    
    int paramValue = -99    
    String[] str;    
    
    /*
    	1 - input "paramTempReportThreshold", "enum", title: "Temperature Delta for Reporting", multiple: false, defaultValue: "2: 1.0 Degree", options: ["0: Disabled", "1: 0.5 Degree", "2: 1.0 Degree", "3: 1.5 Degrees", "4: 2.0 Degrees"], required: false, displayDuringSetup: true																		
		5 - input "paramHumidityReportThreshold", "enum", title: "Humidity Delta for Reporting", multiple: false, defaultValue: "2: 5%", options: ["0: Disabled", "1: 3%", "2: 5%", "3: 10%"], required: false, displayDuringSetup: true																		
        7 - input "paramSwingTemp", "enum", title: "Swing Temperature (degrees away from setpoint before HVAC turns on)", multiple: false, defaultValue: "4: 2.0 Degrees", options: ["1: 0.5 Degree", "2: 1.0 Degree", "3: 1.5 Degrees", "4: 2.0 Degrees", "5: 2.5 Degrees", "6: 3.0 Degrees", "7: 3.5 Degrees", "8: 4.0 Degrees"], required: false, displayDuringSetup: true																		
        9 - input "paramRecoveryMode", "enum", title: "Recovery Mode", multiple: false, defaultValue: "2: Economy", options: ["1: Fast", "2: Economy"], required: false, displayDuringSetup: true																		
		11 - input "paramUIMode", "enum", title: "UI Mode", multiple: false, defaultValue: "0: Normal", options: ["0: Normal", "1: Simple"], required: false, displayDuringSetup: true																		
		12 - input "paramMulticast", "enum", title: "Multicast Messaging", multiple: false, defaultValue: "0: Disabled", options: ["0: Disabled", "1: Enabled"], required: false, displayDuringSetup: true																		
		17 - input "paramSensorCal", "enum", title: "Temperature Sensor Calibration/Offset (degrees)", multiple: false, defaultValue: "0", options: ["0", "-6", "-5", "-4", "-3", "-2", "-1", "0", "1", "2", "3", "4", "5", "6"], required: false, displayDuringSetup: true						
		18 - input "paramDisplayUnits", "enum", title: "Display Units", multiple: false, defau
    */
    
    if (paramTempReportThreshold != null) {
        str = paramTempReportThreshold.split(':');
        tempstr = str[0]
        paramValue = tempstr.toInteger();
        if (paramValue != -99) {
            cmds << zwave.configurationV1.configurationSet(parameterNumber: 1, size: 1, scaledConfigurationValue: paramValue)
            cmds << zwave.configurationV1.configurationGet(parameterNumber: 1)
        }
        str = []
        tempstr=""
        paramValue = -99;
    }
    
    if (paramHumidityReportThreshold != null) {
        str = paramHumidityReportThreshold.split(':');
        tempstr = str[0]
        paramValue = tempstr.toInteger();
        if (paramValue != -99) {
            cmds << zwave.configurationV1.configurationSet(parameterNumber: 5, size: 1, scaledConfigurationValue: paramValue)
            cmds << zwave.configurationV1.configurationGet(parameterNumber: 5)
        }
        str = []
        tempstr=""
        paramValue = -99;
    }

    if (paramSwingTemp != null) {
        str = paramSwingTemp.split(':');
        tempstr = str[0]
        paramValue = tempstr.toInteger();
        if (paramValue != -99) {
            cmds << zwave.configurationV1.configurationSet(parameterNumber: 7, size: 1, scaledConfigurationValue: paramValue)
            cmds << zwave.configurationV1.configurationGet(parameterNumber: 7)
        }
        str = []
        tempstr=""
        paramValue = -99;
    }

    if (paramRecoveryMode != null) {
        str = paramRecoveryMode.split(':');
        tempstr = str[0]
        paramValue = tempstr.toInteger();
        if (paramValue != -99) {
            cmds << zwave.configurationV1.configurationSet(parameterNumber: 9, size: 1, scaledConfigurationValue: paramValue)
            cmds << zwave.configurationV1.configurationGet(parameterNumber: 9)
        }
        str = []
        tempstr=""
        paramValue = -99;
    }

    if (paramUIMode != null) {
        str = paramUIMode.split(':');
        tempstr = str[0]
        paramValue = tempstr.toInteger();
        if (paramValue != -99) {
            cmds << zwave.configurationV1.configurationSet(parameterNumber: 11, size: 1, scaledConfigurationValue: paramValue)
            cmds << zwave.configurationV1.configurationGet(parameterNumber: 11)
        }
        str = []
        tempstr=""
        paramValue = -99;
    }

    if (paramMulticast != null) {
        str = paramMulticast.split(':');
        tempstr = str[0]
        paramValue = tempstr.toInteger();
        if (paramValue != -99) {
            cmds << zwave.configurationV1.configurationSet(parameterNumber: 12, size: 1, scaledConfigurationValue: paramValue)
            cmds << zwave.configurationV1.configurationGet(parameterNumber: 12)
        }
        str = []
        tempstr=""
        paramValue = -99;
    }

    if (paramSensorCal != null) {
        paramValue = paramSensorCal.toInteger();
        if (paramValue != -99) {
            cmds << zwave.configurationV1.configurationSet(parameterNumber: 17, size: 1, scaledConfigurationValue: paramValue)
            cmds << zwave.configurationV1.configurationGet(parameterNumber: 17)
        }
        str = []
        tempstr=""
        paramValue = -99;
    }
    
    if (paramDisplayUnits != null) {
        str = paramDisplayUnits.split(':');
        tempstr = str[0]
        paramValue = tempstr.toInteger();
        if (paramValue != -99) {
            cmds << zwave.configurationV1.configurationSet(parameterNumber: 18, size: 1, scaledConfigurationValue: paramValue)
            cmds << zwave.configurationV1.configurationGet(parameterNumber: 18)
        }
    }
        
	if (logEnable) log.debug "cmds: " + cmds
	commands(cmds, 500)
}

def logsOff() {
    log.warn "debug logging disabled..."
    device.updateSetting("logEnable",[value:"false",type:"bool"])
}

///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Handle updates from thermostat
///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
def zwaveEvent(hubitat.zwave.commands.configurationv1.ConfigurationReport cmd) {
    if (logEnable) log.debug "---CONFIGURATION REPORT V1--- ${device.displayName} sent parameterNumber:${cmd.parameterNumber}, size:${cmd.size}, value:${cmd.scaledConfigurationValue}"
    def config = cmd.scaledConfigurationValue
    def CalValue = config
	
    if (cmd.parameterNumber == 17) { 
        sendEvent([name:"currentSensorCal", value: cmd.scaledConfigurationValue, displayed:true, unit: getTemperatureScale(), isStateChange:true])
	}
    
    if (logEnable) log.debug "Parameter: ${cmd.parameterNumber} value is: ${config}"
}

def zwaveEvent(hubitat.zwave.commands.sensormultilevelv1.SensorMultilevelReport cmd) {
	if (logEnable) log.debug "SensorMultilevelReport V1...START"
	if (logEnable) log.debug "cmd: $cmd"
	def map = [:]
	if (logEnable) log.debug "cmd.sensorType: " + cmd.sensorType
	if (cmd.sensorType.toInteger() == 1) {
        map.value = cmd.scaledSensorValue
		map.unit = getTemperatureScale()
		map.name = "temperature"
	}
	if (cmd.sensorType.toInteger() == 5) {
        map.value = cmd.scaledSensorValue
		map.unit = "%"
		map.name = "humidity"
	}
    
	if (logEnable) log.debug "In SensorMultilevelReport map is $map"
    sendEvent(map)
	if (logEnable) log.debug "SensorMultilevelReport...END"
	return map
}

def zwaveEvent(hubitat.zwave.commands.thermostatsetpointv2.ThermostatSetpointReport cmd)
{
	if (logEnable) log.debug "ThermostatSetpointReport...START"
	def cmdScale = cmd.scale == 1 ? "F" : "C"
    if (logEnable) log.debug "cmdScale is $cmd.scale before (this is the state variable), and $cmdScale after"
    if (logEnable) log.debug "setpoint requested is $cmd.scaledValue and unit is $cmdScale"
	def map = [:]
	map.value = cmd.scaledValue
	map.unit = getTemperatureScale()
	map.displayed = true
    if (logEnable) log.debug "value is $map.value and unit is $map.unit"
	switch (cmd.setpointType) {
		case 1:
			map.name = "heatingSetpoint"
			updateDataValue("heatingSetpoint", map.value.toString())
			break;
		case 2:
			map.name = "coolingSetpoint"
			updateDataValue("coolingSetpoint", map.value.toString())
			break;
		default:
			return [:]
	}
	if (logEnable) log.debug "Setpoint Size is $cmd.size"
	state.size = cmd.size
	if (logEnable) log.debug "Setpoint Scale is $cmd.scale"
	state.scale = cmd.scale
	if (logEnable) log.debug "Setpoint Precision is $cmd.precision"
	state.precision = cmd.precision
    if (logEnable) log.debug "In ThermostatSetpointReport map is $map"
	sendEvent([name: map.name, value: map.value, displayed:true, unit: map.unit, isStateChange:true])

	// Update thermostatSetpoint based on mode and operatingstate
	def tos = getDataValue("thermostatOperatingState")
	def tm = getDataValue("thermostatMode")
	def lrm = getDataValue("lastRunningMode")
	def csp = getDataValue("coolingSetpoint")
	def hsp = getDataValue("heatingSetpoint")

	if (lrm == null) {
		if (tm == "cool") {
			updateDataValue("lastRunningMode", "cool")
			lrm = "cool"
		} else {
			if (tm == "heat") {
				updateDataValue("lastRunningMode", "heat")
				lrm = "heat"
			} else {
				if (tm == "auto") {
					updateDataValue("lastRunningMode", "heat")
					lrm = "heat"
				}
			}	
		}
	}
	
	def map2 = [:]
	map2.value = cmd.scaledValue
	map2.unit = getTemperatureScale()
	map2.displayed = true
	map2.name = "thermostatSetpoint"
	
	if ((tos == "idle") && tm == "auto") {
		if (lrm == "cool") {
			if (map.name == "coolingSetpoint") {
				if (logEnable) log.debug "thermostatSetpoint being set to " + map.value
				updateDataValue("thermostatSetpoint", map.value.toString())
				map2.value = map.value
				
			} else { 
				if (logEnable) log.debug "thermostatSetpoint being set to " + csp
				updateDataValue("thermostatSetpoint", csp)
				map2.value = csp
			}	
		}
		if (getDataValue("lastRunningMode") == "heat") {
			if (map.name == "heatingSetpoint") {
				if (logEnable) log.debug "thermostatSetpoint being set to " + map.value
				updateDataValue("thermostatSetpoint", map.value.toString())
				map2.value = map.value
			} else { 
				if (logEnable) log.debug "thermostatSetpoint being set to " + hsp
				updateDataValue("thermostatSetpoint", hsp)
				map2.value = hsp
			}	
		}
	}
	
	if (tm == "cool") {		
			if (map.name == "coolingSetpoint") {
				if (logEnable) log.debug "thermostatSetpoint being set to " + map.value
				updateDataValue("thermostatSetpoint", map.value.toString())
				map2.value = map.value
			} else { 
				if (logEnable) log.debug "thermostatSetpoint being set to " + csp
				updateDataValue("thermostatSetpoint", csp)
				map2.value = csp
			}	
	}

	if (tm == "heat") {		
			if (map.name == "heatingSetpoint") {
				if (logEnable) log.debug "thermostatSetpoint being set to " + map.value
				updateDataValue("thermostatSetpoint", map.value.toString())
				map2.value = map.value
			} else { 
				if (logEnable) log.debug "thermostatSetpoint being set to " + hsp
				updateDataValue("thermostatSetpoint", hsp)
				map2.value = hsp
			}	
	}

	sendEvent([name: map2.name, value: map2.value, displayed:true, unit: map2.unit, isStateChange:false])

	if (logEnable) log.debug "thermostatSetpoint is " + getDataValue("thermostatSetpoint")
	
	if (logEnable) log.debug "ThermostatSetPointReport...END"
	return map	
}

def zwaveEvent(hubitat.zwave.commands.thermostatoperatingstatev2.ThermostatOperatingStateReport cmd)
{
	if (logEnable) log.debug "thermostatOperatingState...START"
	def map = [:]
	if (logEnable) log.debug "cmd.operatingState: " + cmd.operatingState
	switch (cmd.operatingState) {
		case hubitat.zwave.commands.thermostatoperatingstatev2.ThermostatOperatingStateReport.OPERATING_STATE_IDLE:
			map.value = "idle"
            break
		case hubitat.zwave.commands.thermostatoperatingstatev2.ThermostatOperatingStateReport.OPERATING_STATE_HEATING:
			map.value = "heating"
           	break
		case hubitat.zwave.commands.thermostatoperatingstatev2.ThermostatOperatingStateReport.OPERATING_STATE_COOLING:
			map.value = "cooling"
            break
		case hubitat.zwave.commands.thermostatoperatingstatev2.ThermostatOperatingStateReport.OPERATING_STATE_FAN_ONLY:
			map.value = "fan only"
			break
		case hubitat.zwave.commands.thermostatoperatingstatev2.ThermostatOperatingStateReport.OPERATING_STATE_PENDING_HEAT:
			map.value = "pending heat"
            break
		case hubitat.zwave.commands.thermostatoperatingstatev2.ThermostatOperatingStateReport.OPERATING_STATE_PENDING_COOL:
			map.value = "pending cool"
            break
		case hubitat.zwave.commands.thermostatoperatingstatev2.ThermostatOperatingStateReport.OPERATING_STATE_VENT_ECONOMIZER:
			map.value = "vent economizer"
            break
	}
	map.name = "thermostatOperatingState"
    if (logEnable) log.debug "In ThermostatOperatingState map is $map"
    updateDataValue("thermostatOperatingState", map.value)
	sendEvent(map)
	// Update lastRunningMode variable. Needed for Google Home support.
	if (map.value == "heating") {updateDataValue("lastRunningMode", "heat")}
	if (map.value == "cooling") {updateDataValue("lastRunningMode", "cool")}
    if (logEnable) log.debug "lastRunningMode is: " + getDataValue("lastRunningMode")
    //								
	if (logEnable) log.debug "thermostatOperatingState...END"
	return map
}

def zwaveEvent(hubitat.zwave.commands.thermostatmodev2.ThermostatModeReport cmd) {
	if (logEnable) log.debug "ThermostatModeReport...START"
	if (logEnable) log.debug "cmd: $cmd"
    def map = [name: "thermostatMode", data:[supportedThermostatModes: state.supportedModes]]
	if (logEnable) log.debug "map: $map"
    if (logEnable) log.debug "cmd.mode: $cmd.mode"
    switch (cmd.mode) {
        case 0: //hubitat.zwave.commands.thermostatmodeV2.ThermostatModeReport.MODE_OFF:
			map.value = "off"
			break
		case 1: //hubitat.zwave.commands.thermostatmodeV2.ThermostatModeReport.MODE_HEAT:
			map.value = "heat"
			break
		case 2: //hubitat.zwave.commands.thermostatmodeV2.ThermostatModeReport.MODE_COOL:
			map.value = "cool"
			break
		case 3: //hubitat.zwave.commands.thermostatmodeV2.ThermostatModeReport.MODE_AUTO:
			map.value = "auto"
			break
		case 4: //hubitat.zwave.commands.thermostatmodeV2.ThermostatModeReport.MODE_AUXILIARY_HEAT:
			map.value = "emergency heat"
			break
		case 5: //hubitat.zwave.commands.thermostatmodeV2.ThermostatModeReport.MODE_RESUME:
			map.value = "resume"
			break
		case 6: //hubitat.zwave.commands.thermostatmodeV2.ThermostatModeReport.MODE_FAN_ONLY:
			map.value = "fan only"
			break
		case 7: //hubitat.zwave.commands.thermostatmodeV2.ThermostatModeReport.MODE_FURNACE:
			map.value = "furnace"
			break
		case 8: //hubitat.zwave.commands.thermostatmodeV2.ThermostatModeReport.MODE_DRY_AIR:
			map.value = "dry air"
			break
		case 9: //hubitat.zwave.commands.thermostatmodeV2.ThermostatModeReport.MODE_MOIST_AIR:
			map.value = "moist air"
			break
		case 10: //hubitat.zwave.commands.thermostatmodeV2.ThermostatModeReport.MODE_AUTO_CHANGEOVER:
			map.value = "auto changeover"
			break
		case 11: //hubitat.zwave.commands.thermostatmodeV2.ThermostatModeReport.MODE_ENERGY_SAVE_HEAT:
			map.value = "energy save heat"
			break
		case 12: //hubitat.zwave.commands.thermostatmodeV2.ThermostatModeReport.MODE_ENERGY_SAVE_COOL:
			map.value = "energy save cool"
			break
		case 13: //hubitat.zwave.commands.thermostatmodeV2.ThermostatModeReport.MODE_AWAY:
			map.value = "away"
			break
	}
	
    if (logEnable) log.debug "map.value: $map.value"
    
	// Sync lastRunningMode and thermostatSetpoint if the mode is changed
	if (getDataValue("lastRunningMode") != map.value) {
		if (map.value == "cool") {
			updateDataValue("lastRunningMode", "cool")
			def csp = getDataValue("coolingSetpoint")
			updateDataValue("thermostatSetpoint", csp.toString())
			sendEvent([name: "thermostatSetpoint", value: csp, displayed:true, unit: getTemperatureScale(), isStateChange:true])
		} else {
			if (map.value == "heat") {
				updateDataValue("lastRunningMode", "heat")
				def hsp = getDataValue("heatingSetpoint")
				updateDataValue("thermostatSetpoint", hsp.toString())
				sendEvent([name: "thermostatSetpoint", value: hsp, displayed:true, unit: getTemperatureScale(), isStateChange:true])
			}
		}											  
	}
	
	updateDataValue("thermostatMode", map.value)
	sendEvent(map)

    commands([
		zwave.configurationV1.configurationGet(parameterNumber: 53),
		zwave.configurationV1.configurationGet(parameterNumber: 54)
	], 400)
	if (logEnable) log.debug "ThermostatModeReport...END"
	return map
}

def zwaveEvent(hubitat.zwave.commands.thermostatmodev2.ThermostatModeSupportedReport cmd) {
	if (logEnable) log.debug "V2 ThermostatModeSupportedReport...START"
	if (logEnable) log.debug "cmd: " + cmd

    def supportedModes = ""

    if (cmd.off) { supportedModes += "off " }
	if (cmd.heat) { supportedModes += "heat " }
	if (cmd.auxiliaryemergencyHeat) { supportedModes += "emergencyHeat " }
	if (cmd.cool) { supportedModes += "cool " }
	if (cmd.auto) { supportedModes += "auto " }
    if (cmd.autoChangeover) { supportedModes += "autoChangeover " }
    if (cmd.away) { supportedModes += "away " }
    if (cmd.dryAir) { supportedModes += "dryAir " }
    if (cmd.energySaveCool) { supportedModes += "energySaveCool " }
    if (cmd.energySaveHeat) { supportedModes += "energySaveHeat " }
    if (cmd.fanOnly) { supportedModes += "fanOnly " }
    if (cmd.furnace) { supportedModes += "furnace " }
    if (cmd.moistAir) { supportedModes += "moistAir " }
    if (cmd.resume) { supportedModes += "resume " }

    if (logEnable) log.debug "supportedModes: " + supportedModes

    if (supportedModes) {
		state.supportedModes = supportedModes
	} else {
		supportedModes = "off heat cool auto "
		state.supportedModes = supportedModes
	}
	if (logEnable) log.debug "state.supportedModes: " + state.supportedModes
	if (logEnable) log.debug "V2 ThermostatModeSupportedReport...END"
}

def zwaveEvent(hubitat.zwave.commands.thermostatfanmodev1.ThermostatFanModeSupportedReport cmd) {
	if (logEnable) log.debug "ThermostatFanModeSupportedReport...START"
	if (logEnable) log.debug "cmd: " + cmd
	def supportedFanModes = ""
	
	if (cmd.auto) { supportedFanModes += "auto " }
	if (cmd.autoHigh) { supportedFanModes += "autoHigh " }
	if (cmd.high) { supportedFanModes += "high " }
	if (cmd.low) { supportedFanModes += "low " }
	if (logEnable) log.debug "supportedFanModes: " + supportedFanModes

	if (supportedFanModes) {
		state.supportedFanModes = supportedFanModes
	} else {
		supportedFanModes = "auto on "
		state.supportedFanModes = supportedFanModes
	}
	    
    if (logEnable) log.debug "ThermostatFanModeSupportedReport...END"
}

def zwaveEvent(hubitat.zwave.commands.thermostatfanstatev1.ThermostatFanStateReport cmd) {
	if (logEnable) log.debug "ThermostatFanStateReport...START"
	if (logEnable) log.debug "cmd: " + cmd
	def map = [name: "thermostatFanState", unit: ""]
	switch (cmd.fanOperatingState) {
		case 0:
			map.value = "idle"
            sendEvent(name: "thermostatFanState", value: "idle")
			break
		case 1:
			map.value = "running"
            sendEvent(name: "thermostatFanState", value: "running")
			break
		case 2:
			map.value = "running high"
			sendEvent(name: "thermostatFanState", value: "running high")
			break
	}
	updateDataValue("thermostatFanState", map.value)
    if (logEnable) log.debug "In ThermostatFanStateReport map is $map"
	commands([zwave.configurationV1.configurationGet(parameterNumber: 52)])
    if (logEnable) log.debug "ThermostatFanStateReport...END"
	return map
}

def zwaveEvent(hubitat.zwave.commands.thermostatfanmodev1.ThermostatFanModeReport cmd) {
    if (logEnable) log.debug "ThermostatFanModeReport...START"
	def map = [:]
	switch (cmd.fanMode) {
		case 0: //hubitat.zwave.commands.thermostatfanmodev1.ThermostatFanModeReport.FAN_MODE_AUTO_LOW:
			map.value = "auto"
            break
		case 1: //hubitat.zwave.commands.thermostatfanmodev1.ThermostatFanModeReport.FAN_MODE_LOW:
			map.value = "low"
            break
        case 2: //hubitat.zwave.commands.thermostatfanmodev1.ThermostatFanModeReport.FAN_MODE_AUTO_HIGH:
			map.value = "auto"
            break
		case 3: //hubitat.zwave.commands.thermostatfanmodev1.ThermostatFanModeReport.FAN_MODE_HIGH:
			map.value = "high"
            break
	}
	map.name = "thermostatFanMode"
	map.displayed = false
    if (logEnable) log.debug "In ThermostatFanModeReport map is $map"
	updateDataValue("thermostatFanMode", map.value)
    sendEvent(map)
	if (logEnable) log.debug "ThermostatFanModeReport...END"
	return map
}

def zwaveEvent(hubitat.zwave.commands.basicv1.BasicReport cmd) {
	if (logEnable) log.debug "BasicReport...START"
	if (logEnable) log.debug "Zwave event received: $cmd"
	if (logEnable) log.debug "BasicReport...END"
}

def zwaveEvent(hubitat.zwave.commands.batteryv1.BatteryReport cmd) {
    if (logEnable) log.debug "In BatteryReport"
    //def result = []
    def map = [:]
    map = [ name: "battery", unit: "%" ]
    if (cmd.batteryLevel == 0xFF) {
        map.value = 1
        map.descriptionText = "${device.displayName} battery is low"
    } else {
        map.value = cmd.batteryLevel
    }
    updateDataValue("battery", map.value.toString())
    sendEvent(map)
    return map
}

def zwaveEvent(hubitat.zwave.Command cmd) {
	log.warn "Unexpected zwave command $cmd"
}

private command(hubitat.zwave.Command cmd) {
	if (getDataValue("zwaveSecurePairingComplete") == "true") {
		return zwave.securityV1.securityMessageEncapsulation().encapsulate(cmd).format()
    } else {
		return cmd.format()
    }	
}

private commands(commands, delay=200) {
	delayBetween(commands.collect{ command(it) }, delay)
}
