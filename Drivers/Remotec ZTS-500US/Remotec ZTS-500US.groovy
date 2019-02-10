/**
 *  Remotec ZTS-500 driver for Hubitat
 *
 *  Copyright 2019 Jason Bottjen
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
 *  Version 1.0 - 01/27/2019     Initial Version
 *  Version 1.1 - 02/10/2019     Added Filter Reset command. Added Swing, deadband, and filter hours prefrences.
 *  Version 1.2 - 02/10/2019     Fixed many parameter issues. Eliminated MANY redundant Set/Get commands, greatly improving responsiveness. 
 *                               Put filter param,eters in MONTHS instead of HOURS, per current vendor firmware version.
 */
metadata {
	definition (name: "Remotec ZTS-500", namespace: "Botched1", author: "Jason Bottjen") {
		capability "Refresh"
		capability "Actuator"
		capability "Temperature Measurement"
		capability "Relative Humidity Measurement"
        capability "Thermostat"
		capability "Thermostat Fan Mode"
		capability "Configuration"
		capability "Sensor"
        capability "Switch"
       
		command "SensorCal", [[name:"calibration",type:"ENUM", description:"Number of degrees to add/substract from thermostat sensor", constraints:["-10", "-9", "-8", "-7", "-6", "-5", "-4", "-3", "-2", "-1", "0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "10"]]]
		command "resetFilter"
		
		attribute "thermostatFanState", "string"
		attribute "currentSensorCal", "number"
	}
		preferences {
/*        	input "autoTempDiff", "enum", title: "Temperature change reporting threshold", description: "Set the temperature change reporting threshold", multiple: false, defaultValue: "4.0°F", options: ["Disabled","1.0°F","2.0°F","3.0°F","4.0°F","5.0°F","6.0°F","7.0°F","8.0°F"], required: false, displayDuringSetup: true
*/
        	input "swingDegrees", "enum", title: "Temperature Swing", description: "Number of degrees above/below setpoint before unit turns on", multiple: false, defaultValue: "2", options: ["1","2","3","4"], required: false, displayDuringSetup: true
			input "deadbandDegrees", "enum", title: "Deadband", description: "Minimum number of degrees between the heating and cooling setpoints", multiple: false, defaultValue: "4", options: ["3","4","5","6"], required: false, displayDuringSetup: true
			input "filterMonths", "enum", title: "Filter months", description: "Number of months between filter replacement notifications", multiple: false, defaultValue: "6", options: ["3","4","5","6","7","8","9","10","11","12"], required: false, displayDuringSetup: true
			input "logEnable", "bool", title: "Enable debug logging", defaultValue: false
		}
            
}

// parse events into attributes
def parse(String description) {
	if (logEnable) log.debug "Parse...START"
	if (logEnable) log.debug "Parsing '${description}'"
	def map = createEvent(zwaveEvent(zwave.parse(description, [0x20:1, 0x31:5, 0x40:3, 0x42:2, 0x43:2, 0x44:4, 0x45:2, 0x59:2, 0x5A:1, 0x5E:2, 0x70:1, 0x72:2, 0x73:1, 0x7A:3, 0x80:1, 0x85:2, 0x86:2, 0x98:1])))
    // if (logEnable) log.debug "In parse, map is $map"
	return null
}

//
// Handle commands from Thermostat
//
def setHeatingSetpoint(double degrees) {
	if (logEnable) log.debug "setHeatingSetpoint...START"
    def locationScale = getTemperatureScale()
    if (logEnable) log.debug "stateScale is $state.scale"
    if (logEnable) log.debug "locationScale is $locationScale"
	def p = (state.precision == null) ? 1 : state.precision
	if (logEnable) log.debug "precision is $p"
    if (logEnable) log.debug "setpoint requested is $degrees"
    if (logEnable) log.debug "setHeatingSetpoint...END"
	return zwave.thermostatSetpointV1.thermostatSetpointSet(setpointType: 1, scale: state.scale, precision: p, scaledValue: degrees).format()
}

def setCoolingSetpoint(double degrees) {
	if (logEnable) log.debug "setCoolingSetpoint...START"
    def locationScale = getTemperatureScale()
    if (logEnable) log.debug "stateScale is $state.scale"
    if (logEnable) log.debug "locationScale is $locationScale"
	def p = (state.precision == null) ? 1 : state.precision
	if (logEnable) log.debug "precision is $p"
    if (logEnable) log.debug "setpoint requested is $degrees"
    if (logEnable) log.debug "setCoolingSetpoint...END"
	return zwave.thermostatSetpointV1.thermostatSetpointSet(setpointType: 2, scale: state.scale, precision: p, scaledValue: degrees).format()
}

def off() {
	if (logEnable) log.debug "Switching to off mode..."
    return delayBetween([
		zwave.thermostatModeV2.thermostatModeSet(mode: 0).format(),
        zwave.thermostatOperatingStateV1.thermostatOperatingStateGet().format()
	], 1000)
}

def on() {
	if (logEnable) log.debug "Executing 'on'"
	log.warn "ON is too ambiguous for a multi-state thermostat, so it does nothing in this driver. Use setThermostatMode or the AUTO/COOL/HEAT commands."
}

def heat() {
	if (logEnable) log.debug "Switching to heat mode..."
	return delayBetween([
		zwave.thermostatModeV2.thermostatModeSet(mode: 1).format(),
		zwave.thermostatOperatingStateV1.thermostatOperatingStateGet().format()
	], 1000)   
}

def emergencyHeat() {
	if (logEnable) log.debug "Switching to emergency heat mode..."
    return delayBetween([
		zwave.thermostatModeV2.thermostatModeSet(mode: 4).format(),
		zwave.thermostatOperatingStateV1.thermostatOperatingStateGet().format()
	], 1000)
}

def cool() {
	if (logEnable) log.debug "Switching to cool mode..."
    return delayBetween([
		zwave.thermostatModeV2.thermostatModeSet(mode: 2).format(),
		zwave.thermostatOperatingStateV1.thermostatOperatingStateGet().format()
	], 1000)
}

def setThermostatMode(value) {
	if (logEnable) log.debug "Executing 'setThermostatMode'"
	if (logEnable) log.debug "value: " + value
	def cmds = []
	switch (value) {
		case "auto":
		    if (logEnable) log.debug "Switching to auto mode..."
			cmds << zwave.thermostatModeV2.thermostatModeSet(mode: 3).format()
			break
		case "off":
			if (logEnable) log.debug "Switching to off mode..."
			cmds << zwave.thermostatModeV2.thermostatModeSet(mode: 0).format()
			break
		case "heat":
			if (logEnable) log.debug "Switching to heat mode..."
			cmds << zwave.thermostatModeV2.thermostatModeSet(mode: 1).format()
			break
		case "cool":
			if (logEnable) log.debug "Switching to cool mode..."
			cmds << zwave.thermostatModeV2.thermostatModeSet(mode: 2).format()
			break
		case "emergency heat":
			if (logEnable) log.debug "Switching to emergency heat mode..."
			cmds << zwave.thermostatModeV2.thermostatModeSet(mode: 4).format()
			break
	}
	cmds << zwave.thermostatOperatingStateV1.thermostatOperatingStateGet().format()
	return delayBetween(cmds, 1000)
}

def fanOn() {
	if (logEnable) log.debug "Switching fan to on mode..."
    return delayBetween([
		zwave.thermostatFanModeV3.thermostatFanModeSet(fanMode: 1).format(),
		zwave.thermostatFanStateV1.thermostatFanStateGet().format()
	], 1000)   
}

def fanAuto() {
	if (logEnable) log.debug "Switching fan to auto mode..."
    return delayBetween([
		zwave.thermostatFanModeV3.thermostatFanModeSet(fanMode: 0).format(),
		zwave.thermostatFanStateV1.thermostatFanStateGet().format()
	], 1000)
}

def fanCirculate() {
	if (logEnable) log.debug "Fan circulate mode not supported for this thermostat type..."
	log.warn "Fan circulate mode not supported for this thermostat type..."
    /*
	sendEvent(name: "currentfanMode", value: "Fan Cycle Mode" as String)
	delayBetween([
		zwave.thermostatFanModeV3.thermostatFanModeSet(fanMode: 6).format(),
		zwave.thermostatFanModeV3.thermostatFanModeGet().format(),
        zwave.thermostatFanStateV1.thermostatFanStateGet().format()
	], 3000)
	*/
}

def setThermostatFanMode(value) {
	if (logEnable) log.debug "Executing 'setThermostatFanMode'"
	if (logEnable) log.debug "value: " + value
	def cmds = []
	switch (value) {
		case "on":
			if (logEnable) log.debug "Switching fan to ON mode..."
			cmds << zwave.thermostatFanModeV3.thermostatFanModeSet(fanMode: 1).format()
			break
		case "auto":
			if (logEnable) log.debug "Switching fan to AUTO mode..."
			cmds << zwave.thermostatFanModeV3.thermostatFanModeSet(fanMode: 0).format()
			break
		default:
			log.warn "Fan Mode $value unsupported."
			break
	}
	cmds << zwave.thermostatFanStateV1.thermostatFanStateGet().format()
	return delayBetween(cmds, 1000)
}

def auto() {
	if (logEnable) log.debug "Switching to auto mode..."
    return delayBetween([
		zwave.thermostatModeV2.thermostatModeSet(mode: 3).format(),
		zwave.thermostatOperatingStateV1.thermostatOperatingStateGet().format()
	], 1000)
}

def setSchedule() {
	if (logEnable) log.debug "Executing 'setSchedule'"
	log.warn "setSchedule does not do anything with this driver."
}

def refresh() {
	if (logEnable) log.debug "Executing 'refresh'"
	if (logEnable) log.debug "....done executing refresh"
	return delayBetween([
		zwave.thermostatModeV2.thermostatModeGet().format(),
		zwave.thermostatOperatingStateV2.thermostatOperatingStateGet().format(),
		zwave.thermostatFanModeV3.thermostatFanModeGet().format(),
		zwave.thermostatFanStateV1.thermostatFanStateGet().format(),
		zwave.thermostatSetpointV1.thermostatSetpointGet(setpointType: 1).format(),
		zwave.thermostatSetpointV1.thermostatSetpointGet(setpointType: 2).format()
	], 1000)
    
}

def configure() {
	if (logEnable) log.debug "Executing 'configure'"
	if (logEnable) log.debug "zwaveHubNodeId: " + zwaveHubNodeId
    if (logEnable) log.debug "....done executing 'configure'"
	
	return delayBetween([
		zwave.thermostatModeV2.thermostatModeSupportedGet().format(),
		zwave.thermostatFanModeV3.thermostatFanModeSupportedGet().format(),
		zwave.associationV1.associationSet(groupingIdentifier:1, nodeId:[zwaveHubNodeId]).format()
	], 1000)
}

def SensorCal(value) {
	value = value.toInteger()
	if (logEnable) log.debug "SensorCal value: " + value
	def SetCalValue
	if (value < 0) {
		SetCalValue = value == -1 ? 255 : value == -2 ? 254 : value == -3 ? 253 : value == -4 ? 252 : value == -5 ? 251 : value == -6 ? 250 : value == -7 ? 249 : value == -8 ? 248 : value == -9 ? 247 : value == -10 ? 246 : "unknown"
		if (logEnable) log.debug "SetCalValue: " + SetCalValue
	} else {
		SetCalValue = value.toInteger()
		if (logEnable) log.debug "SetCalValue: " + SetCalValue
	}
	return delayBetween([
		zwave.configurationV1.configurationSet(configurationValue: [0, SetCalValue] , parameterNumber: 10, size: 2).format(),
		zwave.configurationV1.configurationGet(parameterNumber: 10).format()
		], 1000)
}

def updated() {
	if (logEnable) log.debug "Executing 'updated'"

    if (logEnable) {
		log.debug "debug logging is enabled."
		runIn(1800,logsOff)
	}

	def paramSwing, paramDeadband, paramFilter
	
	if (settings.swingDegrees) {
	    paramSwing = settings.swingDegrees.toInteger()
    } else {
        paramSwing = 2
    }
	if (logEnable) log.debug "paramSwing: " + paramSwing
	
	if (settings.deadbandDegrees) {
	    paramDeadband = settings.deadbandDegrees.toInteger()
    } else {
        paramDeadband = 4
    }
	if (logEnable) log.debug "paramDeadband: " + paramDeadband
	
	if (settings.filterMonths) {
	    paramFilter = settings.filterMonths.toInteger()
    } else {
        paramFilter = 6
    }
	if (logEnable) log.debug "paramFilter: " + paramFilter

	if (logEnable) log.debug "....done executing 'updated'"
	
	return delayBetween([
		zwave.configurationV1.configurationSet(parameterNumber: 2, size: 2, configurationValue: [0,paramSwing]).format(),
		zwave.configurationV1.configurationGet(parameterNumber: 2).format(),
		zwave.configurationV1.configurationSet(parameterNumber: 4, size: 2, configurationValue: [0,paramDeadband]).format(),
		zwave.configurationV1.configurationGet(parameterNumber: 4).format(),
		zwave.configurationV1.configurationSet(parameterNumber: 8, size: 2, configurationValue: [0,paramFilter]).format(),
		zwave.configurationV1.configurationGet(parameterNumber: 8).format()
		], 1000)
}

def logsOff() {
    log.warn "debug logging disabled..."
    device.updateSetting("logEnable",[value:"false",type:"bool"])
}

// Reset filter counter on thermostat
def resetFilter() {
    if (logEnable) log.debug "Executing 'resetFilter'"
	def paramValue

    if (settings.filterMonths) {
	    paramValue = settings.filterMonths.toInteger()
    } else {
        paramValue = 6
    }

	if (logEnable) {log.debug "Resetting filter counter to $paramValue months"}

	if (logEnable) log.debug "....done executing 'resetFilter'"
	
	return delayBetween([
		zwave.configurationV1.configurationSet(parameterNumber: 8, size: 2, configurationValue: [0,paramValue]).format(),
		zwave.configurationV1.configurationGet(parameterNumber: 8).format(),
		zwave.configurationV1.configurationSet(parameterNumber: 7, size: 1, configurationValue: [0]).format()
		], 1000)
}

//
// Handle updates from thermostat
//
def zwaveEvent(hubitat.zwave.commands.configurationv1.ConfigurationReport cmd) {
	if (logEnable) log.debug "---CONFIGURATION REPORT V1--- ${device.displayName} sent parameterNumber:${cmd.parameterNumber}, size:${cmd.size}, value:${cmd.scaledConfigurationValue}"
    def config = cmd.scaledConfigurationValue
	def CalValue
    if (cmd.parameterNumber == 10) {
		if (logEnable) log.debug "cmd.scaledConfigurationValue: " + config
		if (cmd.parameterNumber == 10) {
			if (config.toInteger() > 10) {
				CalValue = config == 255 ? "-1" : config == 254 ? "-2" : config == 253 ? "-3" : config == 252 ? "-4" : config == 251 ? "-5" : config == 250 ? "-6" : config == 249 ? "-7" : config == 248 ? "-8" : config == 247 ? "-9" : config == 246 ? "-10" : "unknown"
				if (logEnable) log.debug "CalValue: " + CalValue
				sendEvent([name:"currentSensorCal", value: CalValue, displayed:true, unit: getTemperatureScale(), isStateChange:true])
			} else {
				CalValue = config
				if (logEnable) log.debug "CalValue: " + CalValue
				sendEvent([name:"currentSensorCal", value: CalValue, displayed:true, unit: getTemperatureScale(), isStateChange:true])
			}
    	}
	}
	if (logEnable) log.debug "Parameter: ${cmd.parameterNumber} value is: ${config}"
}

def zwaveEvent(hubitat.zwave.commands.sensormultilevelv5.SensorMultilevelReport cmd) {
	if (logEnable) log.debug "SensorMultilevelReport...START"
	if (logEnable) log.debug "cmd: $cmd"
	def map = [:]
	if (logEnable) log.debug "cmd.sensorType: " + cmd.sensorType
	if (cmd.sensorType.toInteger() == 1) {
        map.value = cmd.scaledSensorValue
		map.unit = getTemperatureScale()
		map.name = "temperature"
	}
	if (logEnable) log.debug "In SensorMultilevelReport map is $map"
    sendEvent(map)
	if (logEnable) log.debug "SensorMultilevelReport...END"
	
}

def zwaveEvent(hubitat.zwave.commands.thermostatsetpointv2.ThermostatSetpointReport cmd)
{
	if (logEnable) log.debug "ThermostatSetPointReport...START"
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
			break;
		case 2:
			map.name = "coolingSetpoint"
			break;
		default:
			return [:]
	}
	state.size = cmd.size
	state.scale = cmd.scale
	state.precision = cmd.precision
    if (logEnable) log.debug "In ThermostatSetpointReport map is $map"
    
	sendEvent([name: map.name, value: map.value, displayed:true, unit: map.unit, isStateChange:true])

	if (logEnable) log.debug "ThermostatSetPointReport...END"
	return map
	
}

def zwaveEvent(hubitat.zwave.commands.thermostatoperatingstatev1.ThermostatOperatingStateReport cmd)
{
	log.debug "thermostatOperatingStateV1...START"
	log.warn "thermostatOperatingStateV1 called. This currently has no fuction in this driver."
	log.debug "thermostatOperatingStateV1...START"
}


def zwaveEvent(hubitat.zwave.commands.thermostatoperatingstatev2.ThermostatOperatingStateReport cmd)
{
	if (logEnable) log.debug "ThermostatOperatingStateReport...START"
	def map = [:]
	switch (cmd.operatingState) {
		case hubitat.zwave.commands.thermostatoperatingstatev1.ThermostatOperatingStateReport.OPERATING_STATE_IDLE:
			map.value = "idle"
            break
		case hubitat.zwave.commands.thermostatoperatingstatev1.ThermostatOperatingStateReport.OPERATING_STATE_HEATING:
			map.value = "heating"
           	break
		case hubitat.zwave.commands.thermostatoperatingstatev1.ThermostatOperatingStateReport.OPERATING_STATE_COOLING:
			map.value = "cooling"
            break
		case hubitat.zwave.commands.thermostatoperatingstatev1.ThermostatOperatingStateReport.OPERATING_STATE_FAN_ONLY:
			map.value = "fan only"
			break
		case hubitat.zwave.commands.thermostatoperatingstatev1.ThermostatOperatingStateReport.OPERATING_STATE_PENDING_HEAT:
			map.value = "pending heat"
            break
		case hubitat.zwave.commands.thermostatoperatingstatev1.ThermostatOperatingStateReport.OPERATING_STATE_PENDING_COOL:
			map.value = "pending cool"
            break
		case hubitat.zwave.commands.thermostatoperatingstatev1.ThermostatOperatingStateReport.OPERATING_STATE_VENT_ECONOMIZER:
			map.value = "vent economizer"
            break
	}
	map.name = "thermostatOperatingState"
    if (logEnable) log.debug "In ThermostatOperatingStateReport map is $map"
    sendEvent(map)
	if (logEnable) log.debug "ThermostatOperatingStateReport...END"
}

def zwaveEvent(hubitat.zwave.commands.thermostatmodev2.ThermostatModeReport cmd) {
	if (logEnable) log.debug "ThermostatModeReport...START"
	if (logEnable) log.debug "cmd: $cmd"
	def map = [name: "thermostatMode", data:[supportedThermostatModes: state.supportedModes]]
	switch (cmd.mode) {
		case hubitat.zwave.commands.thermostatmodev2.ThermostatModeReport.MODE_OFF:
			map.value = "off"
			break
		case hubitat.zwave.commands.thermostatmodev2.ThermostatModeReport.MODE_HEAT:
			map.value = "heat"
			break
		case hubitat.zwave.commands.thermostatmodev2.ThermostatModeReport.MODE_AUXILIARY_HEAT:
			map.value = "emergency heat"
			break
		case hubitat.zwave.commands.thermostatmodev2.ThermostatModeReport.MODE_COOL:
			map.value = "cool"
			break
		case hubitat.zwave.commands.thermostatmodev2.ThermostatModeReport.MODE_AUTO:
			map.value = "auto"
			break
	}
	sendEvent(map)
	if (logEnable) log.debug "ThermostatModeReport...END"
}

def zwaveEvent(hubitat.zwave.commands.thermostatmodev2.ThermostatModeSupportedReport cmd) {
	if (logEnable) log.debug "ThermostatModeSupportedReport...START"
	def supportedModes = ""
	if(cmd.off) { supportedModes += "off " }
	if(cmd.heat) { supportedModes += "heat " }
	if(cmd.auxiliaryemergencyHeat) { supportedModes += "emergencyHeat " }
	if(cmd.cool) { supportedModes += "cool " }
	if(cmd.auto) { supportedModes += "auto " }
	state.supportedModes = supportedModes
	if (logEnable) log.debug "ThermostatModeSupportedReport...END"
}

def zwaveEvent(hubitat.zwave.commands.thermostatfanmodev3.ThermostatFanModeSupportedReport cmd) {
	if (logEnable) log.debug "ThermostatFanModeSupportedReport...START"
	def supportedFanModes = "fanAuto fanOn fanCirculate "
	state.supportedFanModes = supportedFanModes
	if (logEnable) log.debug "ThermostatFanModeSupportedReport...END"
}

def zwaveEvent(hubitat.zwave.commands.thermostatfanstatev1.ThermostatFanStateReport cmd) {
	if (logEnable) log.debug "ThermostatFanStateReport...START"
	def map = [name: "thermostatFanState", unit: ""]
	switch (cmd.fanOperatingState) {
		case 0:
			map.value = "idle"
            sendEvent(name: "thermostatFanState", value: "not running")
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
    if (logEnable) log.debug "In ThermostatFanStateReport map is $map"
    if (logEnable) log.debug "ThermostatFanStateReport...END"
}

def zwaveEvent(hubitat.zwave.commands.thermostatfanmodev3.ThermostatFanModeReport cmd) {
    if (logEnable) log.debug "ThermostatFanModeReport...START"
	def map = [:]
	switch (cmd.fanMode) {
		case hubitat.zwave.commands.thermostatfanmodev3.ThermostatFanModeReport.FAN_MODE_AUTO_LOW:
			map.value = "fanAuto"
            break
		case hubitat.zwave.commands.thermostatfanmodev3.ThermostatFanModeReport.FAN_MODE_LOW:
			map.value = "fanOn"
            break
		case hubitat.zwave.commands.thermostatfanmodev3.ThermostatFanModeReport.FAN_MODE_CIRCULATION:
			map.value = "fanCirculate"
            break
	}
	map.name = "thermostatFanMode"
	map.displayed = false
    if (logEnable) log.debug "In ThermostatFanModeReport map is $map"
    sendEvent(map)
	if (logEnable) log.debug "ThermostatFanModeReport...END"
}

def updateState(String name, String value) {
	if (logEnable) log.debug "updateState...START"
	state[name] = value
	device.updateDataValue(name, value)
	if (logEnable) log.debug "updateState...END"
}

def zwaveEvent(hubitat.zwave.commands.basicv1.BasicReport cmd) {
	if (logEnable) log.debug "BasicReport...START"
	if (logEnable) log.debug "Zwave event received: $cmd"
	if (logEnable) log.debug "BasicReport...END"
}

def zwaveEvent(hubitat.zwave.Command cmd) {
	log.warn "Unexpected zwave command $cmd"
}
