definition(
	name: "HVAC - Temperature - Media Room Thermostat",
	namespace: "Botched1",
	author: "Jason Bottjen",
	description: "Adjusts the temperature used to control the Media Room Thermostat",
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
			input "temp1", "capability.temperatureMeasurement", title: "Temperature", description: "Temperature", required: true, multiple: false
			input "temp2", "capability.temperatureMeasurement", title: "Temperature", description: "Temperature", required: true, multiple: false
		}
		
		section() 
		{
			input(name: "staleTempHours", type: "number", defaultValue: "3", title: "Stale Temperature Reading", description: "Hours before temperature reading is considered stale.")
			input "sendPushMessage", "capability.notification", title: "Send a Pushover notification?", multiple: true, required: false, submitOnChange: true
			input(name: "logEnable", type: "bool", defaultValue: "true", title: "Enable Debug Logging", description: "Enable extra logging for debugging.")
		}
	}
}

def installed() 
{
	if (logEnable) log.debug "installed"
	initialize()
}

def updated()
{
	if (logEnable) log.debug "updated"
	initialize()
}

def logsOff(){
    log.warn "debug logging disabled..."
    app.updateSetting("logEnable",[value:"false",type:"bool"])
}
	
def initialize() 
{
	unschedule()
	if (logEnable) log.debug "initialize"
	if (logEnable) log.debug "tStat: $tStat"
	if (logEnable) log.debug "temp1: $temp1"
	if (logEnable) log.debug "temp2: $temp2"
	if (logEnable) {
		log.warn "Debug logging is enabled. Will be automatically turned off in 30 minutes."
		runIn(1800,logsOff)
	}
	state.PushNotSent = true
	//checkDevices()
	runEvery1Minute(checkDevices)
}

def uninstalled() 
{
	if (logEnable) log.debug "uninstalled"
}

def checkDevices() 
{
 	log.info("${app.label} - Ran")
	
	if (logEnable) log.debug "sendPushMessage: $sendPushMessage"
	if (logEnable) log.debug "state.PushNotSent: $state.PushNotSent"
	
	def currentCal = tStat.currentValue("currentSensorCal")
	def currentTSMode = tStat.currentValue("thermostatMode")
	def currentTSOpState = tStat.currentValue("thermostatOperatingState")
	def currentTSheatingSetpoint = tStat.currentValue("heatingSetpoint")
	def currentTScoolingSetpoint = tStat.currentValue("coolingSetpoint")
	def currentTemp1 = temp1.currentValue("temperature")
	def currentTemp2 = temp2.currentValue("temperature")
	def currentTSTemp = tStat.currentValue("temperature")
	def tempDelta
	def newCal
	def tempAvg
	def tempCalcMode
	def staleHoursTemp1
	def staleHoursTemp2
	
	// Check for stale readings
	if (logEnable) log.debug "--- START: Checking for stale readings ---"
	if (logEnable) log.debug "staleTempHours: $staleTempHours"
	// Check for stale temp1 readings
	staleHoursTemp1 = (now() - temp1.lastActivity.getTime())/3600000
	if (logEnable) log.debug "staleHoursTemp1: $staleHoursTemp1"
	if (logEnable) log.debug "staleHoursTemp1 > staleTempHours: " + (staleHoursTemp1 > staleTempHours)
	
	if (staleHoursTemp1 > staleTempHours) {
		log.warn "temp1 reading is stale. Skipping calculations."
		if(sendPushMessage && state.PushNotSent) pushNow(temp1)
		state.PushNotSent = false
		return
	}
		
	// Check for stale temp2 readings
	staleHoursTemp2 = (now() - temp2.lastActivity.getTime())/3600000
	if (logEnable) log.debug "staleHoursTemp2: $staleHoursTemp2"
	if (logEnable) log.debug "staleHoursTemp2 > staleTempHours: " + (staleHoursTemp2 > staleTempHours)
	
	if (staleHoursTemp2 > staleTempHours) {
		log.warn "temp2 reading is stale. Skipping calculations."
		if(sendPushMessage && state.PushNotSent) pushNow(temp2)
		state.PushNotSent = false
		return
	}
	if (logEnable) log.debug "--- END: Checking for stale readings ---"	
	
	if (state.PushNotSent == false) sendPushMessage.deviceNotification("APP: ${app.label} \nTemperatures are no longer stale.")
	state.PushNotSent = true
		
	if (logEnable) log.debug "currentCal: $currentCal"
	if (logEnable) log.debug "currentTSMode: $currentTSMode"
	if (logEnable) log.debug "currentTSOpState: $currentTSOpState"
	if (logEnable) log.debug "currentTSheatingSetpoint: $currentTSheatingSetpoint"
	if (logEnable) log.debug "currentTScoolingSetpoint: $currentTScoolingSetpoint"
	if (logEnable) log.debug "currentTemp1: $currentTemp1"
	if (logEnable) log.debug "currentTemp2: $currentTemp2"
	if (logEnable) log.debug "currentTSTemp: $currentTSTemp"
	if (logEnable) log.debug "state.lastTemp1: $state.lastTemp1"
	if (logEnable) log.debug "state.lastTemp2: $state.lastTemp2"
	if (logEnable) log.debug "state.lastTSTemp: $state.lastTSTemp"
	
	if (state.lastTemp1==currentTemp1 && state.lastTemp2==currentTemp2 && state.lastTSTemp==currentTSTemp) {
		if (logEnable) log.debug "No temps changed. Exiting."
		return
	}

	state.lastTemp1=currentTemp1
	state.lastTemp2=currentTemp2
	state.lastTSTemp=currentTSTemp	
	
	tempCalcMode = ""
	if (thermostatOperatingState == "cooling") {
		tempCalcMode = "cool"
	} else {
		if (thermostatOperatingState == "heating") {
			tempCalcMode = "heat"
		} else {
			tempCalcMode = currentTSMode
		}
	}
	
	if (logEnable) log.debug "tempCalcMode: $tempCalcMode"
	
	switch (tempCalcMode) {
	case "heat":
		if (currentTemp1 <= currentTemp2) {
			tempDelta = currentTemp1 - currentTSTemp
			if (logEnable) log.debug "1 colder than 2 tempDelta: " + tempDelta
			if (logEnable) log.debug "1 colder than 2 tempDelta rounded: " + Math.round(tempDelta)
		} else {
			tempDelta = currentTemp2 - currentTSTemp
			if (logEnable) log.debug "2 colder than 1 tempDelta: " + tempDelta
			if (logEnable) log.debug "2 colder than 1 tempDelta rounded: " + Math.round(tempDelta)
		}
		if (logEnable) log.debug "currentCal: " + currentCal
		def tempTemp = currentCal + Math.round(tempDelta)
		if (logEnable) log.debug "currentCal + tempDelta rounded: " + tempTemp
		newCal = currentCal + Math.round(tempDelta)
		if (newCal < -7) {
			log.warn "NewCal < -7, clamping at -7"
			newCal=-7
		}
		if (newCal > 7) {
			log.warn "newCal > 7, clamping at 7"
			newCal=7
		}
		if (newCal == currentCal) {
			if (logEnable) log.debug "newCal and currentCal are the same. Doing nothing."
		} else {
			if (logEnable) log.debug "New Cal: " + newCal
			tStat.SensorCal(newCal)
		}
		break
	case "cool":
		if (currentTemp1 >= currentTemp2) {
			tempDelta = currentTemp1 - currentTSTemp
			if (logEnable) log.debug "1 hotter than 2 tempDelta: " + tempDelta
			if (logEnable) log.debug "1 hotter than 2 tempDelta rounded: " + Math.round(tempDelta)
		} else {
			tempDelta = currentTemp2 - currentTSTemp
			if (logEnable) log.debug "2 hotter than 1 tempDelta: " + tempDelta
			if (logEnable) log.debug "2 hotter than 1 tempDelta rounded: " + Math.round(tempDelta)
		}
		if (logEnable) log.debug "currentCal: " + currentCal
		def tempTemp = currentCal + Math.round(tempDelta)
		if (logEnable) log.debug "currentCal + tempDelta rounded: " + tempTemp
		newCal = currentCal + Math.round(tempDelta)
		if (newCal < -7) {
			log.warn "NewCal < -7, clamping at -7"
			newCal=-7
		}
		if (newCal > 7) {
			log.warn "newCal > 7, clamping at 7"
			newCal=7
		}
		if (newCal == currentCal) {
			if (logEnable) log.debug "newCal and currentCal are the same. Doing nothing."
		} else {
			if (logEnable) log.debug "New Cal: " + newCal
			tStat.SensorCal(newCal)
		}
		break
	case "auto":
		def boolFarBelow = false
		def boolFarAbove = false
		if (currentTemp1 < (currentTSheatingSetpoint-2) || currentTemp2 < (currentTSheatingSetpoint-2)) {
			boolFarBelow = true
		}
		if (currentTemp1 > (currentTScoolingSetpoint+2) || currentTemp2 > (currentTScoolingSetpoint+2)) {
			boolFarAbove = true
		}
		if (logEnable) log.debug "boolFarBelow: $boolFarBelow"
		if (logEnable) log.debug "boolFarAbove: $boolFarAbove"
		if (boolFarBelow==true && boolFarAbove==false) {
			if (logEnable) log.debug "In AUTO, one temp is too cold!! TS: $currentTScoolingSetpoint, 1: $currentTemp1, 2: $currentTemp2"
			if (currentTemp1 <= currentTemp2) {
				tempDelta = currentTemp1 - currentTSTemp
				if (logEnable) log.debug "1 colder than 2 tempDelta: " + tempDelta
				if (logEnable) log.debug "1 colder than 2 tempDelta rounded: " + Math.round(tempDelta)
			} else {
				tempDelta = currentTemp2 - currentTSTemp
				if (logEnable) log.debug "2 colder than 1 tempDelta: " + tempDelta
				if (logEnable) log.debug "2 colder than 1 tempDelta rounded: " + Math.round(tempDelta)
			}
			if (logEnable) log.debug "currentCal: " + currentCal
			def tempTemp = currentCal + Math.round(tempDelta)
			if (logEnable) log.debug "currentCal + tempDelta rounded: " + tempTemp
			newCal = currentCal + Math.round(tempDelta)
			if (newCal < -7) {
				log.warn "NewCal < -7, clamping at -7"
				newCal=-7
			}
			if (newCal > 7) {
				log.warn "newCal > 7, clamping at 7"
				newCal=7
			}
			if (newCal == currentCal) {
				if (logEnable) log.debug "newCal and currentCal are the same. Doing nothing."
			} else {
				if (logEnable) log.debug "New Cal: " + newCal
				tStat.SensorCal(newCal)
			}
		} else {
			if (boolFarBelow == false && boolFarAbove == true) {
				if (logEnable) log.debug "In AUTO, one temp is too hot!! TS: $currentTScoolingSetpoint, 1: $currentTemp1, 2: $currentTemp2"
				if (currentTemp1 >= currentTemp2) {
					tempDelta = currentTemp1 - currentTSTemp
					if (logEnable) log.debug "1 hotter than 2 tempDelta: " + tempDelta
					if (logEnable) log.debug "1 hotter than 2 tempDelta rounded: " + Math.round(tempDelta)
				} else {
					tempDelta = currentTemp2 - currentTSTemp
					if (logEnable) log.debug "2 hotter than 1 tempDelta: " + tempDelta
					if (logEnable) log.debug "2 hotter than 1 tempDelta rounded: " + Math.round(tempDelta)
				}
				if (logEnable) log.debug "currentCal: " + currentCal
				def tempTemp = currentCal + Math.round(tempDelta)
				if (logEnable) log.debug "currentCal + tempDelta rounded: " + tempTemp
				newCal = currentCal + Math.round(tempDelta)
				if (newCal < -7) {
					log.warn "NewCal < -7, clamping at -7"
					newCal=-7
				}
				if (newCal > 7) {
					log.warn "newCal > 7, clamping at 7"
					newCal=7
				}
				if (newCal == currentCal) {
					if (logEnable) log.debug "newCal and currentCal are the same. Doing nothing."
				} else {
					if (logEnable) log.debug "New Cal: " + newCal
					tStat.SensorCal(newCal)
				}
			} else {
				// Just take the average of the two readings and use that
				tempAvg = (currentTemp1 + currentTemp2) / 2
				if (logEnable) log.debug "Use average temp: $tempAvg"
				tempDelta = tempAvg - currentTSTemp
				def tempTemp = currentCal + Math.round(tempDelta)
				if (logEnable) log.debug "currentCal + tempDelta rounded: " + tempTemp
				newCal = currentCal + Math.round(tempDelta)
				if (newCal < -7) {
					log.warn "NewCal < -7, clamping at -7"
					newCal=-7
				}
				if (newCal > 7) {
					log.warn "newCal > 7, clamping at 7"
					newCal=7
				}
				if (newCal == currentCal) {
					if (logEnable) log.debug "newCal and currentCal are the same. Doing nothing."
				} else {
					if (logEnable) log.debug "New Cal: " + newCal
					tStat.SensorCal(newCal)
				}
			}
		break
		}
	}
}


def pushNow(myDevice){
	if (logEnable) log.debug "In pushNow..."
	def statusMessage
	statusMessage = "APP: ${app.label} \n"
	statusMessage += "DEVICE: ${myDevice} \n"
	statusMessage += "is stale."
	if (logEnable) log.debug "In pushNow...Sending message: ${statusMessage}"
	sendPushMessage.deviceNotification(statusMessage)
}
