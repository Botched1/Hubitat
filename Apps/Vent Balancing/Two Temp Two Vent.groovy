definition(
	name: "Vent Balancing - Boys Room",
	namespace: "Botched1",
	author: "Jason Bottjen",
	description: "Balance temperature in two rooms by manipulating two vents",
	iconUrl: "",
	iconX2Url: "",
	iconX3Url: "")

preferences 
{
	page(name: "pageConfig")
}

def pageConfig() 
{
	dynamicPage(name: "", title: "", install: true, uninstall: true, refreshInterval:0) 
	{
    
		section("Common Items") 
		{
			input "tStat", "capability.thermostat", title: "Thermostat", description: "Thermostat", required: true, multiple: false
			input(name: "deltaTmin", type: "number", defaultValue: "2", range: "0..100", title: "Minimum Delta T before moving a vent", description: "Min Delta T", required: false)
			input(name: "minOpen", type: "number", defaultValue: "10", range: "0..100", title: "Minimum Vent Open %", description: "Min Open %", required: true)
			input(name: "moveSize", type: "number", defaultValue: "15", range: "0..100", title: "Vent Move Size %", description: "Move Size %", required: true)
			input(name: "deadBand", type: "number", defaultValue: "2", range: "0..100", title: "Vent Position Deadband %", description: "Dead Band %", required: false)
		}

		section("Room 1 Devices") 
		{
			input "vent1", "capability.switchLevel", title: "Vent", description: "Vent", required: true, multiple: false
			input "temp1", "capability.temperatureMeasurement", title: "Temperature", description: "Temperature", required: true, multiple: false
		}

		section("Room 2 Devices") 
		{
			input "vent2", "capability.switchLevel", title: "Vent", description: "Vent", required: true, multiple: false
			input "temp2", "capability.temperatureMeasurement", title: "Temperature", description: "Temperature", required: true, multiple: false
		}
		
		section("Debug")
		{
			input "debugMode", "bool", title: "Enable debug logging", required: true, defaultValue: false
		}
	}
}

def installed() 
{
	log.debug "installed"
	initialize()
}

def updated() 
{
	log.debug "updated"
	initialize()
}

def initialize() 
{
	unschedule()
	log.debug "initialize"
	log.debug "tStat: " + tStat
	log.debug "vent1: " + vent1
	log.debug "temp1: " + temp1
	log.debug "vent2: " + vent2
	log.debug "temp2: " + temp2
	log.debug "deltaTmin: " + deltaTmin 
	log.debug "minOpen: " + minOpen
	log.debug "moveSize: " + moveSize
	log.debug "deadBand: " + deadBand
	//checkDevices()
	runEvery5Minutes(checkDevices)
}

def uninstalled() 
{
	log.debug "uninstalled"
}

def checkDevices() 
{
 	def currentTSMode = tStat.currentValue("thermostatOperatingState")
	def currentTemp1 = temp1.currentValue("temperature")
	def currentTemp2 = temp2.currentValue("temperature")
	def currentDeltaT = currentTemp1 - currentTemp2
	
	log.info("${app.label} - Ran")
	
	if (debugMode==true)
	{
		log.debug "checkDevices"
		log.debug "Thermostat State is: " + currentTSMode
		log.debug "currentDeltaT is " + currentDeltaT
	}
	
	if (currentTSMode == "cooling")
	{
		if (debugMode==true) {log.debug "Cooling"}
		
		if (currentDeltaT.abs() > deltaTmin)
		{
			if (debugMode==true) {log.debug "Big Delta T"}
			
			def currentVent1 = vent1.currentValue("level")
			def currentVent2 = vent2.currentValue("level")
					
			if (currentTemp1 > currentTemp2)
			{
				// Move vent 1 if not at 100 +/- deadband
				
				if ((currentVent1 - 100.0).abs() > deadBand)
				{
					vent1.setLevel(100.0)
					if (debugMode==true) {log.debug "New vent1: " + 100.0}
				} else
				{
					if (debugMode==true) {log.debug "New vent1: No move since within deadband"}
				}
			
				// Move vent 2 by moveSize or clamp at minOpen, and check for deadband
				if ((currentVent2 - moveSize) < minOpen)
				{
					if ((currentVent2 - minOpen).abs() > deadBand)
					{
						vent2.setLevel(minOpen)
						if (debugMode==true) {log.debug "New vent2: " + minOpen}
					} else
					{
						if (debugMode==true) {log.debug "New vent2: No move since within deadband"}
					}
				} else
				{
					vent2.setLevel(currentVent2 - moveSize)
					if (debugMode==true) {log.debug "New vent2: " + (currentVent2 - moveSize)}
				}
				
			} else
			{
				// Move vent 1 by moveSize or clamp at minOpen, and check for deadband
				
				if ((currentVent1 - moveSize) < minOpen)
				{
					if ((currentVent1 - minOpen).abs() > deadBand)
					{
						vent1.setLevel(minOpen)
						if (debugMode==true) {log.debug "New vent1: " + minOpen}
					} else
					{
						if (debugMode==true) {log.debug "New vent1: No move since within deadband"}
					}
				} else
				{
					vent1.setLevel(currentVent1 - moveSize)
					if (debugMode==true) {log.debug "New vent1: " + (currentVent1 - moveSize)}
				}
				
				// Move vent 2 if not at 100 +/- deadband
				if ((currentVent2 - 100.0).abs() > deadBand)
				{
					vent2.setLevel(100.0)
					if (debugMode==true) {log.debug "New vent2: " + 100.0}
				} else
				{
					if (debugMode==true) {log.debug "New vent2: No move since within deadband"}
				}
				
			}
		}
	} else if (currentTSMode == "heating")
	{
		if (debugMode==true) {log.debug "Heating"}
		
		if (currentDeltaT.abs() > deltaTmin)
		{
			if (debugMode==true) {log.debug "Big Delta T"}
			
			def currentVent1 = vent1.currentValue("level")
			def currentVent2 = vent2.currentValue("level")
					
			if (currentTemp1 < currentTemp2)
			{
				// Move vent 1 if not at 100 +/- deadband
				
				if ((currentVent1 - 100.0).abs() > deadBand)
				{
					vent1.setLevel(100.0)
					if (debugMode==true) {log.debug "New vent1: " + 100.0}
				} else
				{
					if (debugMode==true) {log.debug "New vent1: No move since within deadband"}
				}
			
				// Move vent 2 by moveSize or clamp at minOpen, and check for deadband
				if ((currentVent2 - moveSize) < minOpen)
				{
					if ((currentVent2 - minOpen).abs() > deadBand)
					{
						vent2.setLevel(minOpen)
						if (debugMode==true) {log.debug "New vent2: " + minOpen}
					} else
					{
						if (debugMode==true) {log.debug "New vent2: No move since within deadband"}
					}
				} else
				{
					vent2.setLevel(currentVent2 - moveSize)
					if (debugMode==true) {log.debug "New vent2: " + (currentVent2 - moveSize)}
				}
				
			} else
			{
				// Move vent 1 by moveSize or clamp at minOpen, and check for deadband
				
				if ((currentVent1 - moveSize) < minOpen)
				{
					if ((currentVent1 - minOpen).abs() > deadBand)
					{
						vent1.setLevel(minOpen)
						if (debugMode==true) {log.debug "New vent1: " + minOpen}
					} else
					{
						if (debugMode==true) {log.debug "New vent1: No move since within deadband"}
					}
				} else
				{
					vent1.setLevel(currentVent1 - moveSize)
					if (debugMode==true) {log.debug "New vent1: " + (currentVent1 - moveSize)}
				}
				
				// Move vent 2 if not at 100 +/- deadband
				if ((currentVent2 - 100.0).abs() > deadBand)
				{
					vent2.setLevel(100.0)
					if (debugMode==true) {log.debug "New vent2: " + 100.0}
				} else
				{
					if (debugMode==true) {log.debug "New vent2: No move since within deadband"}
				}
				
			}
		}
	}
}
