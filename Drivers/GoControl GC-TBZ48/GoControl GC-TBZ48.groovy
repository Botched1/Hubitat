/**
 *  IMPORT URL: https://raw.githubusercontent.com/Botched1/Hubitat/master/Drivers/GoControl%20GC-TBZ48/GoControl%20GC-TBZ48.groovy
 *
 * Enhanced GoControl GC-TBZ48 driver for Hubitat
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
 *  Version 1.0 - 02/24/2019     Initial Version
 *  Version 1.1 - 02/27/2019     Removed unused code leftover from other drivers. Added param 23 to config to ensure reporting from thermostat works correctly.
 *  Version 1.2 - 04/03/2019     Added thermostatSetpoint, lastRunningMode, allowing for Hubitat Google Home integration support
 *  Version 1.3 - 07/14/2019     Added BatteryReport
 *  Version 1.3.1 - 07/15/2019   Fixed typo in BatteryReport code
 *  Version 1.4 - 12/04/2019     Added Try/Catch around parse in attempt to catch intermittent errors
 *  Version 1.5 - 12/19/2019     Fixed an initial initialization error where scale was unknown until the first thermostat report was received from the device
 *  Version 1.5.1 - 12/21/2019   Tweaked supportedFanMode code, removing fanCirculate as a valid mode from the state variable.
 *  Version 1.6.0b - 01/16/2020   Added Mechanical and SCP report values to state variables
 *  Version 1.7.0 - 01/16/2020   Added thermostatOperatingState to idle when SCP report=0 to fix some out of sync issues with
 *                               that variable. Added in mechanical status and scp status state variables, when parameters
 *                               are saved the driver now sets the thermostat report autosend bits (param 23) to -1 
 *				                 which = send all reports
 *  Version 1.7.1 - 01/16/2020   Removed setting report bits in the parameter save, it was already in the CONFIGURE section. Oops. :) 
 *  Version 1.8.0 - 01/27/2020   Added Battery Capability
 *  bcopeland     - 02/01/2020   Added filter capability and clock syncronization and security ecnapsulation 
 */
metadata {
	definition (name: "Enhanced GoControl GC-TBZ48", namespace: "Botched1", author: "Jason Bottjen") {
		
		capability "Actuator"
		capability "Battery"
		capability "Configuration"
		capability "Refresh"
		capability "Relative Humidity Measurement"
		capability "Sensor"
		capability "Temperature Measurement"
		capability "Thermostat"
		capability "Thermostat Fan Mode"
		capability "FilterStatus"
       
		command "SensorCal", [[name:"calibration",type:"ENUM", description:"Number of degrees to add/subtract from thermostat sensor", constraints:["0", "-7", "-6", "-5", "-4", "-3", "-2", "-1", "0", "1", "2", "3", "4", "5", "6", "7"]]]
		command "DebugLogging", [[name:"Debug Logging",type:"ENUM", description:"Turn Debug Logging OFF/ON", constraints:["OFF", "ON"]]]
		command "SyncClock"
		command "filterReset"
		
		attribute "thermostatFanState", "string"
		attribute "currentSensorCal", "number"
	}
		preferences {
			input "paramSystemType", "enum", title: "HVAC System Type", multiple: false, defaultValue: "0-Standard", options: ["0-Standard","1-Heat Pump"], required: false, displayDuringSetup: true
			input "paramFanType", "enum", title: "Air Handler Fan Type", multiple: false, defaultValue: "0-Gas [no fan signal w/heat]", options: ["0-Gas [no fan signal w/heat]","1-Electric [fan signal w/heat]"], required: false, displayDuringSetup: true
			input "paramCOType", "enum", title: "Change Over Type", multiple: false, defaultValue: "0-CO w/cool", options: ["0-CO w/cool","1-CO w/heat"], required: false, displayDuringSetup: true
			input "param2StageHeatEnable", "enum", title: "2nd Stage Heat Enable", multiple: false, defaultValue: "0-Disabled", options: ["0-Disabled","1-Enabled"], required: false, displayDuringSetup: true
			input "paramAuxHeatEnable", "enum", title: "Auxiliary Heat Enable", multiple: false, defaultValue: "1-Enabled", options: ["0-Disabled","1-Enabled"], required: false, displayDuringSetup: true
			input "param2StageCoolEnable", "enum", title: "2nd Stage Cool Enable", multiple: false, defaultValue: "0-Disabled", options: ["0-Disabled","1-Enabled"], required: false, displayDuringSetup: true			
			input "paramTemperatureUnits", "enum", title: "Temperature Units", multiple: false, defaultValue: "1-Fahrenheit", options: ["0-Centigrade","1-Fahrenheit"], required: false, displayDuringSetup: true						
			input "paramMOT", "number", title: "Minimum Off Time (minutes)", multiple: false, defaultValue: "5",  range: "5..9", required: false, displayDuringSetup: true						
			input "paramMRT", "number", title: "Minimum Run Time (minutes)", multiple: false, defaultValue: "3",  range: "3..9", required: false, displayDuringSetup: true
			input "paramSPMinDelta", "number", title: "Minimum Delta Between Heat and Cool Setpoint (degrees)", multiple: false, defaultValue: "3",  range: "3..15", required: false, displayDuringSetup: true						
			input "paramStage1HDeltaON", "number", title: "Delta from setpoint that stage 1 heating turns ON (degrees)", multiple: false, defaultValue: "1",  range: "1..6", required: false, displayDuringSetup: true						
			input "paramStage1HDeltaOFF", "number", title: "Delta from setpoint that stage 1 heating turns OFF (degrees)", multiple: false, defaultValue: "0",  range: "0..5", required: false, displayDuringSetup: true									
			input "paramStage2HDeltaON", "number", title: "Delta from setpoint that stage 2 heating turns ON (degrees)", multiple: false, defaultValue: "2",  range: "2..7", required: false, displayDuringSetup: true						
			input "paramStage2HDeltaOFF", "number", title: "Delta from setpoint that stage 2 heating turns OFF (degrees)", multiple: false, defaultValue: "0",  range: "0..6", required: false, displayDuringSetup: true									
			input "paramAuxHDeltaON", "number", title: "Delta from setpoint that Aux heating turns ON (degrees)", multiple: false, defaultValue: "3",  range: "3..8", required: false, displayDuringSetup: true						
			input "paramAuxHDeltaOFF", "number", title: "Delta from setpoint that Aux heating turns OFF (degrees)", multiple: false, defaultValue: "0",  range: "0..7", required: false, displayDuringSetup: true									
			input "paramStage1CDeltaON", "number", title: "Delta from setpoint that stage 1 cooling turns ON (degrees)", multiple: false, defaultValue: "1",  range: "1..6", required: false, displayDuringSetup: true						
			input "paramStage1CDeltaOFF", "number", title: "Delta from setpoint that stage 1 cooling turns OFF (degrees)", multiple: false, defaultValue: "0",  range: "0..5", required: false, displayDuringSetup: true									
			input "paramStage2CDeltaON", "number", title: "Delta from setpoint that stage 2 cooling turns ON (degrees)", multiple: false, defaultValue: "2",  range: "2..7", required: false, displayDuringSetup: true						
			input "paramStage2CDeltaOFF", "number", title: "Delta from setpoint that stage 2 cooling turns OFF (degrees)", multiple: false, defaultValue: "0",  range: "0..6", required: false, displayDuringSetup: true									
            // Autosend Enable Bits 0-65536
			input "paramDisplayLock", "enum", title: "Display Lock", multiple: false, defaultValue: "0-Unlocked", options: ["0-Unlocked","1-Locked"], required: false, displayDuringSetup: true
			input "paramBacklightTimer", "number", title: "Display Backlight Turn OFF Timer (seconds)", multiple: false, defaultValue: "20",  range: "10..30", required: false, displayDuringSetup: true									
			input "paramMaxHeatSP", "number", title: "Maximum Heat Setpoint (degrees)", multiple: false, defaultValue: "90",  range: "30..109", required: false, displayDuringSetup: true									
			input "paramMinCoolSP", "number", title: "Minimum Cool Setpoint (degrees)", multiple: false, defaultValue: "60",  range: "33..112", required: false, displayDuringSetup: true									
			input "paramScheduleEnable", "enum", title: "Enables or disables the use of schedules", multiple: false, defaultValue: "0-Disabled", options: ["0-Disabled","1-Enabled"], required: false, displayDuringSetup: true			
			input "paramRunHold", "enum", title: "Hold/Run. Hold locks the current temperature indefinitely. Run resumes the preprogrammed schedule.", multiple: false, defaultValue: "0-Hold", options: ["0-Hold","1-Run"], required: false, displayDuringSetup: true			
			input "paramSetback", "enum", title: "Presence status, used for allowing temperature setpoints to relax when away", multiple: false, defaultValue: "0-Occupied", options: ["0-Occupied","2-Unoccupied"], required: false, displayDuringSetup: true			
			input "paramUnOccupiedHSP", "number", title: "Heating setpoint when unoccupied (degrees)", multiple: false, defaultValue: "62", range: "30..109", required: false, displayDuringSetup: true			
			input "paramUnOccupiedCSP", "number", title: "Cooling setpoint when unoccupied (degrees)", multiple: false, defaultValue: "80", range: "33..112", required: false, displayDuringSetup: true						
			input "paramSensorCal", "number", title: "Temperature Sensor Calibration/Offset (degrees)", multiple: false, defaultValue: 0, range: "-7..7", required: false, displayDuringSetup: true						
			//input "paramFilterTimer", "number", title: "Filter Timer (hours)", multiple: false, defaultValue: "0", range: "0..4000", required: false, displayDuringSetup: true
			input "paramFilterTimerMax", "number", title: "Filter Timer Max (hours)", multiple: false, defaultValue: 300, range: "0..4000", required: false, displayDuringSetup: true
			//input "paramHeatTimer", "number", title: "Heat Timer (hours)", multiple: false, defaultValue: "0", range: "0..4000", required: false, displayDuringSetup: true												
			//input "paramCoolTimer", "number", title: "Cool Timer (hours)", multiple: false, defaultValue: "0", range: "0..4000", required: false, displayDuringSetup: true												
			input "paramFanPurgeHeat", "number", title: "Fan purge after heat cycle (seconds)", multiple: false, defaultValue: "0", range: "0..90", required: false, displayDuringSetup: true												
			input "paramFanPurgeCool", "number", title: "Fan purge after cool cycle (seconds)", multiple: false, defaultValue: "0", range: "0..90", required: false, displayDuringSetup: true															
			input "paramTempSendDeltaTemp", "number", title: "Temperature Delta before Autosending Data to Hub (degrees)", multiple: false, defaultValue: "2", range: "1..5", required: false, displayDuringSetup: true																		
			input "paramTempSendDeltaTime", "number", title: "Temperature Time Delta before Autosending Data to Hub (minutes, 0=Disabled)", multiple: false, defaultValue: "0", range: "0..120", required: false, displayDuringSetup: true																					
			input "logEnable", "bool", title: "Enable debug logging", defaultValue: false
		}
            
}


///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// parse events into attributes
///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
def parse(String description) {
	if (logEnable) log.debug "Parse...START"
	if (logEnable) log.debug "Parsing '${description}'"
	try {
		def map = createEvent(zwaveEvent(zwave.parse(description, [0x20:1, 0x2B: 1, 0x2C: 1, 0x31:1, 0x40:1, 0x42:1, 0x43:2, 0x44:1, 0x45:1, 0x59:1, 0x5A:1, 0x5E:1, 0x70:1, 0x72:1, 0x73:1, 0x7A:2, 0x80: 1, 0x81: 1, 0x85:2, 0x86:2, 0x8F:1])))
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
	return zwave.thermostatSetpointV1.thermostatSetpointSet(setpointType: 1, scale: state.scale, precision: p, scaledValue: degrees2).format()
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
	return zwave.thermostatSetpointV1.thermostatSetpointSet(setpointType: 2, scale: state.scale, precision: p, scaledValue: degrees2).format()
}

def off() {
	if (logEnable) log.debug "Switching to off mode..."
    commands([
		zwave.thermostatModeV1.thermostatModeSet(mode: 0),
        zwave.thermostatModeV1.thermostatModeGet()
	], 1000)
}

def on() {
	if (logEnable) log.debug "Executing 'on'"
	log.warn "ON is too ambiguous for a multi-state thermostat, so it does nothing in this driver. Use setThermostatMode or the AUTO/COOL/HEAT commands."
}

def heat() {
	if (logEnable) log.debug "Switching to heat mode..."
	commands([
		zwave.thermostatModeV1.thermostatModeSet(mode: 1),
		zwave.thermostatModeV1.thermostatModeGet()
	], 1000)   
}

def emergencyHeat() {
	if (logEnable) log.debug "Switching to emergency heat mode..."
    commands([
		zwave.thermostatModeV1.thermostatModeSet(mode: 4),
		zwave.thermostatModeV1.thermostatModeGet()
	], 1000)
}

def cool() {
	if (logEnable) log.debug "Switching to cool mode..."
    comands([
		zwave.thermostatModeV1.thermostatModeSet(mode: 2),
		zwave.thermostatModeV1.thermostatModeGet()
	], 1000)
}

def setThermostatMode(value) {
	if (logEnable) log.debug "Executing 'setThermostatMode'"
	if (logEnable) log.debug "value: " + value
	def cmds = []
	switch (value) {
		case "auto":
		    if (logEnable) log.debug "Switching to auto mode..."
			cmds << zwave.thermostatModeV1.thermostatModeSet(mode: 3)
			break
		case "off":
			if (logEnable) log.debug "Switching to off mode..."
			cmds << zwave.thermostatModeV1.thermostatModeSet(mode: 0)
			break
		case "heat":
			if (logEnable) log.debug "Switching to heat mode..."
			cmds << zwave.thermostatModeV1.thermostatModeSet(mode: 1)
			break
		case "cool":
			if (logEnable) log.debug "Switching to cool mode..."
			cmds << zwave.thermostatModeV1.thermostatModeSet(mode: 2)
			break
		case "emergency heat":
			if (logEnable) log.debug "Switching to emergency heat mode..."
			cmds << zwave.thermostatModeV1.thermostatModeSet(mode: 4)
			break
	}
	cmds << zwave.thermostatModeV1.thermostatModeGet()
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
    /*
	sendEvent(name: "currentfanMode", value: "Fan Cycle Mode" as String)
	delayBetween([
		zwave.thermostatFanModeV1.thermostatFanModeSet(fanMode: 6).format(),
		zwave.thermostatFanModeV1.thermostatFanModeGet().format(),
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
	commands(cmds, 1000)
}

def auto() {
	if (logEnable) log.debug "Switching to auto mode..."
    commands([
		zwave.thermostatModeV1.thermostatModeSet(mode: 3),
		zwave.thermostatModeV1.thermostatModeGet()
	], 1000)
}

def setSchedule() {
	if (logEnable) log.debug "Executing 'setSchedule'"
	log.warn "setSchedule does not do anything with this driver."
}

def filterReset() {
	commands([
		zwave.configurationV1.configurationSet(parameterNumber: 52, size: 2, scaledConfigurationValue: 0),
		zwave.configurationV1.configurationGet(parameterNumber: 52)
	], 1000)
}

def coolTimerReset() {
	commands([
		zwave.configurationV1.configurationSet(parameterNumber: 55, size: 2, scaledConfigurationValue: 0),
		zwave.configurationV1.configurationGet(parameterNumber: 55)
	], 1000)
}

def heatTimerReset() {
	commands([
		zwave.configurationV1.configurationSet(parameterNumber: 54, size: 2, scaledConfigurationValue: 0),
		zwave.configurationV1.configurationGet(parameterNumber: 54)
	], 1000)
}

def refresh() {
	if (logEnable) log.debug "Executing 'refresh'"
	if (logEnable) log.debug "....done executing refresh"
	commands([
		zwave.thermostatModeV1.thermostatModeGet(),
		zwave.thermostatOperatingStateV1.thermostatOperatingStateGet(),
		zwave.thermostatFanModeV1.thermostatFanModeGet(),
		zwave.thermostatFanStateV1.thermostatFanStateGet(),
		zwave.thermostatSetpointV1.thermostatSetpointGet(setpointType: 1),
		zwave.thermostatSetpointV1.thermostatSetpointGet(setpointType: 2),
		zwave.configurationV1.configurationGet(parameterNumber: 52),
		zwave.configurationV1.configurationGet(parameterNumber: 54),
		zwave.configurationV1.configurationGet(parameterNumber: 53),
		zwave.configurationV1.configurationGet(parameterNumber: 55)
	], 1000)   
}

def configure() {
	if (logEnable) log.debug "Executing 'configure'"
	if (logEnable) log.debug "zwaveHubNodeId: " + zwaveHubNodeId
	if (logEnable) log.debug "....done executing 'configure'"
	device.removeSetting("paramFilterTimer")
	device.removeSetting("paramCoolTimer") 
	device.removeSetting("paramHeatTimer") 
	
	commands([
		zwave.thermostatModeV1.thermostatModeSupportedGet(),
		zwave.thermostatFanModeV1.thermostatFanModeSupportedGet(),
		zwave.configurationV1.configurationSet(parameterNumber: 23, size: 2, scaledConfigurationValue: -1),
		zwave.configurationV1.configurationGet(parameterNumber: 23),
		zwave.associationV1.associationSet(groupingIdentifier:1, nodeId:[zwaveHubNodeId])
	], 1000)
}

def ParamToInteger(value) {
	long newParam
	if (value instanceof CharSequence) {
		if (logEnable) log.debug "$value is a CharSequence"
		def newString = value.split("-")
		newParam = newString[0].toInteger()
	} else {
		if (value instanceof Boolean) {
			newParam = value ? 1 : 0
		} else {
			newParam = value
		}
	}
	return newParam
}

def SyncClock() {
	Calendar currentDate = Calendar.getInstance()
	def cmds = [
		zwave.clockV1.clockSet(hour: currentDate.get(Calendar.HOUR_OF_DAY), minute: currentDate.get(Calendar.MINUTE), weekday: currentDate.get(Calendar.DAY_OF_WEEK))
	]
	if (logEnable) log.debug cmd
	commands(cmds, 400)
}

def SensorCal(value) {
	value = value.toInteger()
	if (logEnable) log.debug "SensorCal value: " + value
	def SetCalValue

	if (value < 0) {
		SetCalValue = 256 + value // == -1 ? 255 : value == -2 ? 254 : value == -3 ? 253 : value == -4 ? 252 : value == -5 ? 251 : value == -6 ? 250 : value == -7 ? 249 : value == -8 ? 248 : value == -9 ? 247 : value == -10 ? 246 : "unknown"
		if (logEnable) log.debug "SetCalValue: " + SetCalValue
	} else {
		SetCalValue = value.toInteger()
		if (logEnable) log.debug "SetCalValue: " + SetCalValue
	}

	if (logEnable) log.debug "SetCalValue: " + SetCalValue
	
	commands([
		zwave.configurationV1.configurationSet(scaledConfigurationValue: SetCalValue, parameterNumber: 48, size: 1),
		zwave.configurationV1.configurationGet(parameterNumber: 48)
		], 500)
}

def DebugLogging(value) {
	if (value=="OFF") {logsoff}
    if (value=="ON") {
		log.debug "debug logging is enabled."
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
		
	def ParamNum = [:], ParamSize = [:]
    //def paramAutoSendEnableBits = -1
    
	// Build Parameter Number Map
	ParamNum['paramSystemType'] = 1
	ParamNum['paramFanType'] = 2
	ParamNum['paramCOType'] = 3
	ParamNum['param2StageHeatEnable'] = 4
	ParamNum['paramAuxHeatEnable'] = 5
	ParamNum['param2StageCoolEnable'] = 6
	ParamNum['paramTemperatureUnits'] = 7
	ParamNum['paramMOT'] = 8
	ParamNum['paramMRT'] = 9
	ParamNum['paramSPMinDelta'] = 10
	ParamNum['paramStage1HDeltaON'] = 11
	ParamNum['paramStage1HDeltaOFF'] = 12
	ParamNum['paramStage2HDeltaON'] = 13
	ParamNum['paramStage2HDeltaOFF'] = 14
	ParamNum['paramAuxHDeltaON'] = 15
	ParamNum['paramAuxHDeltaOFF'] = 16
	ParamNum['paramStage1CDeltaON'] = 17
	ParamNum['paramStage1CDeltaOFF'] = 18
	ParamNum['paramStage2CDeltaON'] = 19
	ParamNum['paramStage2CDeltaOFF'] = 20
    //ParamNum['paramAutoSendEnableBits'] = 23
	ParamNum['paramDisplayLock'] = 24
	ParamNum['paramBacklightTimer'] = 26
	ParamNum['paramMaxHeatSP'] = 33
	ParamNum['paramMinCoolSP'] = 34
	ParamNum['paramScheduleEnable'] = 38
	ParamNum['paramRunHold'] = 39
	ParamNum['paramSetback'] = 40
	ParamNum['paramUnOccupiedHSP'] = 41
	ParamNum['paramUnOccupiedCSP'] = 42
	ParamNum['paramSensorCal'] = 48
	ParamNum['paramFilterTimer'] = 52
	ParamNum['paramFilterTimerMax'] = 53
	ParamNum['paramHeatTimer'] = 54
	ParamNum['paramCoolTimer'] = 55
	ParamNum['paramFanPurgeHeat'] = 61
	ParamNum['paramFanPurgeCool'] = 62
	ParamNum['paramTempSendDeltaTemp'] = 186
	ParamNum['paramTempSendDeltaTime'] = 187		

	// Build Parameter Size Map
	ParamSize['paramSystemType'] = 1
	ParamSize['paramFanType'] = 1
	ParamSize['paramCOType'] = 1
	ParamSize['param2StageHeatEnable'] = 1
	ParamSize['paramAuxHeatEnable'] = 1
	ParamSize['param2StageCoolEnable'] = 1
	ParamSize['paramTemperatureUnits'] = 1
	ParamSize['paramMOT'] = 1
	ParamSize['paramMRT'] = 1
	ParamSize['paramSPMinDelta'] = 1
	ParamSize['paramStage1HDeltaON'] = 1
	ParamSize['paramStage1HDeltaOFF'] = 1
	ParamSize['paramStage2HDeltaON'] = 1
	ParamSize['paramStage2HDeltaOFF'] = 1
    //ParamSize['paramAutoSendEnableBits'] = 2
	ParamSize['paramAuxHDeltaON'] = 1
	ParamSize['paramAuxHDeltaOFF'] = 1
	ParamSize['paramStage1CDeltaON'] = 1
	ParamSize['paramStage1CDeltaOFF'] = 1
	ParamSize['paramStage2CDeltaON'] = 1
	ParamSize['paramStage2CDeltaOFF'] = 1
	ParamSize['paramDisplayLock'] = 1
	ParamSize['paramBacklightTimer'] = 1
	ParamSize['paramMaxHeatSP'] = 1
	ParamSize['paramMinCoolSP'] = 1
	ParamSize['paramScheduleEnable'] = 1
	ParamSize['paramRunHold'] = 1
	ParamSize['paramSetback'] = 1
	ParamSize['paramUnOccupiedHSP'] = 1
	ParamSize['paramUnOccupiedCSP'] = 1
	ParamSize['paramSensorCal'] = 1
	ParamSize['paramFilterTimer'] = 2
	ParamSize['paramFilterTimerMax'] = 2
	ParamSize['paramHeatTimer'] = 2
	ParamSize['paramCoolTimer'] = 2
	ParamSize['paramFanPurgeHeat'] = 1
	ParamSize['paramFanPurgeCool'] = 1
	ParamSize['paramTempSendDeltaTemp'] = 1
	ParamSize['paramTempSendDeltaTime'] = 1
	
	def cmds = []
	
	settings.each {
		if (it.key.substring(0,5) == "param") {
			long newNumber, newSize, newValue
			newNumber = ParamNum[it.key]
			newSize = ParamSize[it.key]
			newValue = ParamToInteger(it.value)
			if (logEnable) log.debug "key: ${it.key}, parameterNumber: ${newNumber}, value: ${it.value}, newvalue: ${newValue}"
			if (newNumber == 48) {
				long SetCalValue
				if (newValue < 0) {
					SetCalValue = 256 + newValue // == -1 ? 255 : newValue == -2 ? 254 : newValue == -3 ? 253 : newValue == -4 ? 252 : newValue == -5 ? 251 : newValue == -6 ? 250 : newValue == -7 ? 249 : newValue == -8 ? 248 : newValue == -9 ? 247 : newValue == -10 ? 246 : "unknown"
					if (logEnable) log.debug "SetCalValue: " + SetCalValue
					newValue = SetCalValue	
				} else {
					if (logEnable) log.debug "SetCalValue: " + SetCalValue
				}
			}
			
			//if (newSize==2 && newValue<=255) {
				//if (logEnable) log.debug "zwave.configurationV1.configurationSet(parameterNumber: $newNumber, size: $newSize, configurationValue: [0, $newValue]).format(),"
				//cmds << zwave.configurationV1.configurationSet(parameterNumber: newNumber, size: newSize, configurationValue: [0, newValue])
				//cmds << zwave.configurationV1.configurationGet(parameterNumber: newNumber)
			//} else {
		 //if (logEnable) log.debug "zwave.configurationV1.configurationSet(parameterNumber: $newNumber, size: $newSize, scaledConfigurationValue: $newValue).format(),"
				cmds << zwave.configurationV1.configurationSet(parameterNumber: newNumber, size: newSize, scaledConfigurationValue: newValue)
				cmds << zwave.configurationV1.configurationGet(parameterNumber: newNumber)
			//}
		}	
	}
	    
	if (logEnable) log.debug "cmds: " + cmds
	commands(cmds, 500)
	//return delayBetween(cmds, 500)
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
    def CalValue
    if (cmd.parameterNumber == 48) {
        if (logEnable) log.debug "cmd.scaledConfigurationValue: " + config
	
		if (config.toInteger() > 7) {
	    	CalValue = config.toInteger() - 256 // == 255 ? "-1" : config == 254 ? "-2" : config == 253 ? "-3" : config == 252 ? "-4" : config == 251 ? "-5" : config == 250 ? "-6" : config == 249 ? "-7" : config == 248 ? "-8" : config == 247 ? "-9" : config == 246 ? "-10" : "unknown"
	    	if (logEnable) log.debug "CalValue: " + CalValue
	    	sendEvent([name:"currentSensorCal", value: CalValue, displayed:true, unit: getTemperatureScale(), isStateChange:true])
		} else {
			CalValue = config
			if (logEnable) log.debug "CalValue: " + CalValue
			sendEvent([name:"currentSensorCal", value: CalValue, displayed:true, unit: getTemperatureScale(), isStateChange:true])
		}
    	
    }
	if (cmd.parameterNumber == 53) { 
		state.filterTimerMax = cmd.scaledConfigurationValue
		device.updateSetting("paramFilterTimerMax", cmd.scaledConfigurationValue)
	}
	if (cmd.parameterNumber == 52) {
		state.filterTimer = cmd.scaledConfigurationValue
		if (cmd.scaledConfigurationValue >= state.filterTimerMax) {
			if (state.filterStatus != "replace") {
				sendEvent([name:"filterStatus", value: "replace", displayed: true, isStatusChange:true])
			}	
		} else {
			if (state.filterStatus != "normal") { 
				sendEvent([name: "filterStatus", value: "normal", displayed: true, isStateChange:true])
			}
		}
	}
	if (cmd.parameterNumber == 54) { 
		state.heatTimer = cmd.scaledConfigurationValue
	}
	if (cmd.parameterNumber == 55) {
		state.coolTimer = cmd.scaledConfigurationValue
	}
    if (cmd.parameterNumber == 21) {
    if (!config) {
        state.mechanicalStatus = ""
        return
    }
    def mechStatus = ""
	if ((config & (1L << 0)) != 0) {mechStatus += "MECH_H1,"}
	if ((config & (1L << 1)) != 0) {mechStatus += "MECH_H2,"}
	if ((config & (1L << 2)) != 0) {mechStatus += "MECH_H3,"}
	if ((config & (1L << 3)) != 0) {mechStatus += "MECH_C1,"}
	if ((config & (1L << 4)) != 0) {mechStatus += "MECH_C2,"}
	if ((config & (1L << 5)) != 0) {mechStatus += "PHANTOM_F,"}
	if ((config & (1L << 6)) != 0) {mechStatus += "MECH_F,"}
	if ((config & (1L << 7)) != 0) {mechStatus += "MANUAL_F,"}
	if ((config & (1L << 8)) != 0) {mechStatus += "reserved,"}
	mechStatus = mechStatus[0..-2]
	state.mechanicalStatus = mechStatus
    if (logEnable) log.debug "mechanicalStatus = $mechStatus"
    }

    if (cmd.parameterNumber == 22) {
    if (!config) {
        state.scpStatus = ""
        def mapCR = [:]
        mapCR.name = "thermostatOperatingState" 
        mapCR.value = "idle"
	    updateDataValue("thermostatOperatingState", mapCR.value)
        sendEvent(mapCR)
        return
    }
	def scp = ""
	if ((config & (1L << 0)) != 0) {scp += "STATE_HEAT,"}
	if ((config & (1L << 1)) != 0) {scp += "STATE_COOL,"}
	if ((config & (1L << 2)) != 0) {scp += "STATE_2ND,"}
	if ((config & (1L << 3)) != 0) {scp += "STATE_3RD,"}
	if ((config & (1L << 4)) != 0) {scp += "STATE_FAN,"}
	if ((config & (1L << 5)) != 0) {scp += "STATE_LAST,"}
	if ((config & (1L << 6)) != 0) {scp += "STATE_MOT,"}
	if ((config & (1L << 7)) != 0) {scp += "STATE_MRT,"}
	scp = scp[0..-2]
	state.scpStatus = scp
    if (logEnable) log.debug "scpStatus = $scp"
    
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
	if (logEnable) log.debug "In SensorMultilevelReport map is $map"
    sendEvent(map)
	if (logEnable) log.debug "SensorMultilevelReport...END"
	return map
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

def zwaveEvent(hubitat.zwave.commands.thermostatoperatingstatev1.ThermostatOperatingStateReport cmd)
{
	if (logEnable) log.debug "thermostatOperatingStateV1...START"
	def map = [:]
	if (logEnable) log.debug "cmd.operatingState: " + cmd.operatingState
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
    if (logEnable) log.debug "In ThermostatOperatingState map is $map"
    updateDataValue("thermostatOperatingState", map.value)
	sendEvent(map)
	// Update lastRunningMode variable. Needed for Google Home support.
	if (map.value == "heating") {updateDataValue("lastRunningMode", "heat")}
	if (map.value == "cooling") {updateDataValue("lastRunningMode", "cool")}
    if (logEnable) log.debug "lastRunningMode is: " + getDataValue("lastRunningMode")
    //								
	if (logEnable) log.debug "thermostatOperatingStateV1...END"
	return map
}

def zwaveEvent(hubitat.zwave.commands.thermostatmodev1.ThermostatModeReport cmd) {
	if (logEnable) log.debug "ThermostatModeReport...START"
	if (logEnable) log.debug "cmd: $cmd"
	def map = [name: "thermostatMode", data:[supportedThermostatModes: state.supportedModes]]
	switch (cmd.mode) {
		case hubitat.zwave.commands.thermostatmodev1.ThermostatModeReport.MODE_OFF:
			map.value = "off"
			break
		case hubitat.zwave.commands.thermostatmodev1.ThermostatModeReport.MODE_HEAT:
			map.value = "heat"
			break
		case hubitat.zwave.commands.thermostatmodev1.ThermostatModeReport.MODE_AUXILIARY_HEAT:
			map.value = "emergency heat"
			break
		case hubitat.zwave.commands.thermostatmodev1.ThermostatModeReport.MODE_COOL:
			map.value = "cool"
			break
		case hubitat.zwave.commands.thermostatmodev1.ThermostatModeReport.MODE_AUTO:
			map.value = "auto"
			break
	}
	
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

def zwaveEvent(hubitat.zwave.commands.thermostatmodev1.ThermostatModeSupportedReport cmd) {
	if (logEnable) log.debug "V1 ThermostatModeSupportedReport...START"
	if (logEnable) log.debug "cmd: " + cmd
	def supportedModes = ""
	if (logEnable) log.debug "supportedModes: " + supportedModes
	if (cmd.off) { supportedModes += "off " }
	if (logEnable) log.debug "supportedModes: " + supportedModes
	if(cmd.heat) { supportedModes += "heat " }
	if (logEnable) log.debug "supportedModes: " + supportedModes
	if(cmd.auxiliaryemergencyHeat) { supportedModes += "emergencyHeat " }
	if (logEnable) log.debug "supportedModes: " + supportedModes
	if(cmd.cool) { supportedModes += "cool " }
	if (logEnable) log.debug "supportedModes: " + supportedModes
	if(cmd.auto) { supportedModes += "auto " }
	if (logEnable) log.debug "supportedModes: " + supportedModes
	if (supportedModes) {
		state.supportedModes = supportedModes
	} else {
		supportedModes = "off heat cool auto "
		state.supportedModes = supportedModes
	}
	
	if (logEnable) log.debug "state.supportedModes: " + state.supportedModes
	if (logEnable) log.debug "V1 ThermostatModeSupportedReport...END"
}


def zwaveEvent(hubitat.zwave.commands.thermostatmodev2.ThermostatModeSupportedReport cmd) {
	if (logEnable) log.debug "V2 ThermostatModeSupportedReport...START"
	if (logEnable) log.debug "cmd: " + cmd
	def supportedModes = ""
	if (logEnable) log.debug "supportedModes: " + supportedModes
	if (cmd.off) { supportedModes += "off " }
	if (logEnable) log.debug "supportedModes: " + supportedModes
	if(cmd.heat) { supportedModes += "heat " }
	if (logEnable) log.debug "supportedModes: " + supportedModes
	if(cmd.auxiliaryemergencyHeat) { supportedModes += "emergencyHeat " }
	if (logEnable) log.debug "supportedModes: " + supportedModes
	if(cmd.cool) { supportedModes += "cool " }
	if (logEnable) log.debug "supportedModes: " + supportedModes
	if(cmd.auto) { supportedModes += "auto " }
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
	
	/* NOTE - Not currently implemented in the zwave stack as of Hubitat 2.0.6

	if (cmd.auto) { supportedFanModes += "auto " }
	if (logEnable) log.debug "supportedFanModes: " + supportedFanModes
	if (cmd.autoHigh) { supportedFanModes += "autoHigh " }
	if (logEnable) log.debug "supportedFanModes: " + supportedFanModes
	if (cmd.autoMedium) { supportedFanModes += "autoMedium " }
	if (logEnable) log.debug "supportedFanModes: " + supportedFanModes
	if (cmd.circulation) { supportedFanModes += "circulation " }
	if (logEnable) log.debug "supportedFanModes: " + supportedFanModes
	if (cmd.high) { supportedFanModes += "high " }
	if (logEnable) log.debug "supportedFanModes: " + supportedFanModes
	if (cmd.humidityCirculation) { supportedFanModes += "humidityCirculation " }
	if (logEnable) log.debug "supportedFanModes: " + supportedFanModes
	if (cmd.low) { supportedFanModes += "low " }
	if (logEnable) log.debug "supportedFanModes: " + supportedFanModes
	if (cmd.medium) { supportedFanModes += "medium " }
	if (logEnable) log.debug "supportedFanModes: " + supportedFanModes
	

	if (supportedFanModes) {
		state.supportedFanModes = supportedFanModes
	} else {
		supportedFanModes = "auto on "
		state.supportedFanModes = supportedFanModes
	}
    */
	
    // Since report not supported, just force it for now. If ever supported, uncomment the above block and remove this:
    supportedFanModes = "auto on "
	state.supportedFanModes = supportedFanModes
    
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
		case hubitat.zwave.commands.thermostatfanmodev1.ThermostatFanModeReport.FAN_MODE_AUTO_LOW:
			map.value = "Auto"
            break
		case hubitat.zwave.commands.thermostatfanmodev1.ThermostatFanModeReport.FAN_MODE_LOW:
			map.value = "On"
            break
		case hubitat.zwave.commands.thermostatfanmodev1.ThermostatFanModeReport.FAN_MODE_CIRCULATION:
			map.value = "Circulate"
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
    updateDataValue("battery", map.value)
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

