/*
 *  Import URL: https://raw.githubusercontent.com/Botched1/Hubitat/master/Apps/YAMA/YAMA%20APP.groovy 
 *
 *  ****************  YAMA (Yet Another MQTT App) APP  ****************
 *
 *  Design Usage:
 *  Publish Hubitat devices, commands, and attributes to MQTT 
 *
 *  Original code, but inspiration and some ideas taken from the work of Kevin (xAPPO) and MQTT Link (mydevbox, jeubanks, et al)
 *  
 *-------------------------------------------------------------------------------------------------------------------
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 * ------------------------------------------------------------------------------------------------------------------------------
 *
 *  Changes:
 *
 *  V0.0.1 - 01/12/21 - Test release 1
 *  V0.0.2 - 01/13/21 - Test release 2. fixed init after reboot, and a few other code cleanup items
 *  V0.0.3 - 01/13/21 - Added sendAll command support. Made init only do commands and subscriptions, but not re-publish all attributes.
 *  V0.0.4 - 01/13/21 - Added logic to remove mqttDriver from deviceList if it was selected
 *  V0.0.5 - 01/20/21 - Added check for driver connection status on init 
 *  V0.0.6 - 01/23/21 - Added check for driver connection status before sending events to MQTT
 *  V0.0.7 - 01/30/21 - Split sendAll and Initialize methods apart. Delayed initialization code by 1s to prevent issues if the driver happens to send, or app sees, multiple "init" events.
 *  V0.0.8 - 02/06/21 - Changed topic structure, putting attributes in hubitat/hubName/deviceName/attributes/* and commands in hubitat/hubName/deviceName/commands/*
 *  V0.0.9 - 08/08/22 - Removed all ["filterEvents": false] from subscriptions as a test.
 *  V0.1.0 - 09/26/22 - Fixed a number of initialization issues.
 */

import groovy.json.JsonSlurper

definition(
	name: "YAMA (Yet Another MQTT App)",
	namespace: "Botched1",
	author: "Jason Bottjen",
	description: "",
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
		section("Configuration") 
		{
			input "mqttDriver", "capability.notification", title: "MQTT Driver", description: "MQTT Driver", required: true, multiple: false
			input "deviceList", "capability.*", title: "Devices", description: "Devices", required: true, multiple: true
			input(name: "logEnable", type: "bool", defaultValue: "true", title: "Enable Debug Logging", description: "Enable extra logging for debugging.")
		}
	}
}

def installed() 
{
	log.debug "----- in YAMA App installed -----"	
	if (logEnable) log.debug "installed"
	atomicState.initialized = "installed"
	initialize()
}

def updated()
{
	log.debug "----- in YAMA App updated -----"
	atomicState.initialized = "updated"
    initialize()
}

def logsOff(){
    log.warn "debug logging disabled..."
    app.updateSetting("logEnable",[value:"false",type:"bool"])
}

def initialize() 
{
	if (logEnable) {
		log.warn "Debug logging is enabled. Will be automatically turned off in 30 minutes."
		runIn(1800,logsOff)
	}

	subscribe(mqttDriver, "parentComplete", deviceEvent, ["filterEvents": true])
	
	log.warn "YAMA App State: " + atomicState.initialized
	if (atomicState.initialized != "initializing") {
		if (logEnable) log.debug "YAMA App initialize starting"	
		atomicState.initialized = "initializing"
		runInMillis(1000, initializeMqtt);
	}
}

def initializeMqtt() 
{
	log.debug "YAMA app initialization: Beginning"
	
	// Unsubscribe from all events
	unsubscribe()
	
	// Remove the MQTT Driver from the device list, if it was selected for publishing
	if (deviceList.find {it.name == mqttDriver.name}) {
		deviceList.remove(deviceList.findIndexOf {it.name == mqttDriver.name})
	}

	// See if driver is connected to MQTT broker
	if (mqttDriver.currentValue("connectionState") == "disconnected") {
		log.debug "Error: YAMA Driver is not connected to MQTT broker! Will retry Initialize in 10s."
		mqttDriver.connect()
        runInMillis(10000, initializeMqtt);
        return;
	}
	
	// Walk through selected devices to get commands and attributes
	for(item in deviceList){
		// Publish Commands
		commandList = item.getSupportedCommands()

		for(commandItem in commandList){
			mqttDriver.publish("${item}/commands/${commandItem}/set","")
			pauseExecution(100)
		}

		// Publish sendAll command
		mqttDriver.publish("sendAll","")
		pauseExecution(100)
		
		// MQTT Subscribe to all command sets
		mqttDriver.subscribe("+/+/+/set")
		pauseExecution(100)
		
		// Subscribe to sendAll
		mqttDriver.subscribe("sendAll")
	}

	// Subscribe to events form selected devices, and MQTT driver
	subscribe(deviceList, deviceEvent, ["filterEvents": true])
	subscribe(mqttDriver, "mqtt", deviceEvent, ["filterEvents": true])
	subscribe(mqttDriver, "init", deviceEvent, ["filterEvents": true])
	subscribe(mqttDriver, "sendAll", deviceEvent, ["filterEvents": true])
	subscribe(mqttDriver, "parentComplete", deviceEvent, ["filterEvents": true])
	
	log.debug "YAMA app initialization: Complete"
	
	// Set state so new events will get processed in that handler
	atomicState.initialized = "completed"
}

def sendAll() 
{
	if (atomicState.initialized == "completed") {
		if (logEnable) log.debug "sendAll starting"	
		atomicState.initialized = "sendAll"
		runInMillis(100,sendAllMqtt);
	} 
	else {
		log.debug "sendAll aborted since initialized flag != completed"	
	}
}

def sendAllMqtt() 
{
	log.debug "YAMA app initialization: Beginning"
	
	// Set state so events that happen during init are ignored in the event handler
	//atomicState.initialized = "re-sending"
	
	// Unsubscribe from all events
	unsubscribe()
	
	// Remove the MQTT Driver from the device list, if it was selected for publishing
	if (deviceList.find {it.name == mqttDriver.name}) {
		deviceList.remove(deviceList.findIndexOf {it.name == mqttDriver.name})
	}

	// See if driver is connected to MQTT broker
	if (mqttDriver.currentValue("connectionState") == "disconnected") {
		log.debug "Error: Driver could not connect to MQTT broker! Could not Initialize app."
		return;
	}
	
	// Walk through selected devices to get commands and attributes
	for(item in deviceList){
		// Publish Commands
		commandList = item.getSupportedCommands()

		for(commandItem in commandList){
			log.debug "Setting ${item}/commands/${commandItem}/set"
			mqttDriver.publish("${item}/commands/${commandItem}/set","")
			pauseExecution(100)
		}

		// Publish Attributes
		attributeList = item.getSupportedAttributes()
		
		for(attributeItem in attributeList){
			curVal = item.currentValue("${attributeItem}")
			
			if (!curVal) {
				curVal="null"
			} else {
				curVal = curVal.toString()
			}
			mqttDriver.publish("${item}/attributes/${attributeItem}/value",curVal)
			pauseExecution(100)
		}
	}

	// Publish sendAll command
	mqttDriver.publish("sendAll","")
	pauseExecution(100)
		
	// MQTT Subscribe to all command sets
	mqttDriver.subscribe("+/+/+/set")
	pauseExecution(100)
		
	// Subscribe to sendAll
	mqttDriver.subscribe("sendAll")
	pauseExecution(100)
	
	// Subscribe to events form selected devices, and MQTT driver
	subscribe(deviceList, deviceEvent, ["filterEvents": true])
	subscribe(mqttDriver, "mqtt", deviceEvent, ["filterEvents": true])
	subscribe(mqttDriver, "init", deviceEvent, ["filterEvents": true])
	subscribe(mqttDriver, "sendAll", deviceEvent, ["filterEvents": true])
	subscribe(mqttDriver, "parentComplete", deviceEvent, ["filterEvents": true])
	
	log.debug "YAMA app initialization: Complete"
	
	// Set state so new events will get processed in that handler
	atomicState.initialized = "completed"
}

def uninstalled() 
{
	unschedule()
	unsubscribe()
	log.debug "uninstalled"
}

def deviceEvent(evt)
{
	/*
	log.debug "name: " + evt.name
	log.debug "descriptionText: " + evt.descriptionText
	log.debug "source: " + evt.source
	log.debug "value: " + evt.value
	log.debug "unit: " + evt.unit
	log.debug "description: " + evt.description
	log.debug "type: " + evt.type	
	log.debug "locationId: " + evt.locationId
	log.debug "hubId: " + evt.hubId
	log.debug "displayName: " + evt.getDisplayName()
	log.debug "device: " + evt.getDevice()
	log.debug "deviceId: " + evt.getDeviceId()
    */

	log.debug "atomicState.initialized: " + atomicState.initialized
	log.debug "atomicState.initialized != completed: " + (atomicState.initialized != "completed")

    // MQTT driverparentComplete
	if (evt.name == "parentComplete") {
		log.debug "Setting atomicState.initialized = completed"
		atomicState.initialized = "completed"
		return;
	}
	
	if (atomicState.initialized != "completed") {
		log.debug "Aborting since atomicState.initialized != completed"
		return;
	}
	
    // If MQTT driver is init, then re-initialize app
	if (evt.name == "init") {
		//log.debug "Received init from the driver."
		initialize()
		return;
	}

    // MQTT driver re-send all
	if (evt.name == "sendAll") {
		sendAll()
		return;
	}
		
	// Process incoming HUB DEVICE events, and publish to MQTT topics
	if (evt.name != "mqtt") {
		// See if driver is connected to MQTT broker
		if (mqttDriver.currentValue("connectionState") == "disconnected") {
			if (logEnable) log.debug "Error: App could not send MQTT event. Driver is not connected to MQTT broker!"
			return;
		}
		else {
			mqttDriver.publish("${evt.getDevice()}/attributes/${evt.name}/value","${evt.value}")
		}
	}
	// Process incoming MQTT events from subscibed topics
	else
	{
		def jsonSlurper = new JsonSlurper()
		def object = jsonSlurper.parseText(evt.value)
		
		if (logEnable) log.debug "event device: " + object.device
		if (logEnable) log.debug "event type: " + object.type
		if (logEnable) log.debug "event value: " + object.value
		
		eventDevice = deviceList.find {it.getDisplayName() == object.device}
		//log.debug "eventDevice: " + eventDevice
		eventDeviceCommands = eventDevice.getSupportedCommands()
		//log.debug "eventDeviceCommands: " + eventDeviceCommands
		eventDeviceCommand = eventDeviceCommands.find {it.name == object.type}
		//log.debug "eventDeviceCommand: " + eventDeviceCommand
		eventDeviceParams = eventDeviceCommand.getParameters()
		//log.debug "eventDeviceParams: " + eventDeviceParams
		
		// Determine if the updated command accepts parameters or not, and process
		if (eventDeviceParams) {
			if (object.value) {
				newstr = (object.value).split(',')
				
				switch(newstr.size()) {
				case 1:
					//log.debug "size 1";
					param1 = (eventDeviceParams[0].type == "NUMBER" ? newstr[0].toLong() : newstr[0])
					eventDevice."$object.type"(param1)
					break;
				case 2:
					//log.debug "size 2";
					param1 = (eventDeviceParams[0].type == "NUMBER" ? newstr[0].toLong() : newstr[0])
					param2 = (eventDeviceParams[1].type == "NUMBER" ? newstr[1].toLong() : newstr[1])
					eventDevice."$object.type"(param1, param2)
					break;
				case 3:
					//log.debug "size 3";
					param1 = (eventDeviceParams[0].type == "NUMBER" ? newstr[0].toLong() : newstr[0])
					param2 = (eventDeviceParams[1].type == "NUMBER" ? newstr[1].toLong() : newstr[1])
					param3 = (eventDeviceParams[2].type == "NUMBER" ? newstr[2].toLong() : newstr[2])
					eventDevice."$object.type"(param1, param2, param3)
					break;
				case 4:
					//log.debug "size 4";
					param1 = (eventDeviceParams[0].type == "NUMBER" ? newstr[0].toLong() : newstr[0])
					param2 = (eventDeviceParams[1].type == "NUMBER" ? newstr[1].toLong() : newstr[1])
					param3 = (eventDeviceParams[2].type == "NUMBER" ? newstr[2].toLong() : newstr[2])
					param4 = (eventDeviceParams[3].type == "NUMBER" ? newstr[3].toLong() : newstr[3])
					eventDevice."$object.type"(param1, param2, param3, param4)
					break;
				case 5:
					//log.debug "size 5";
					param1 = (eventDeviceParams[0].type == "NUMBER" ? newstr[0].toLong() : newstr[0])
					param2 = (eventDeviceParams[1].type == "NUMBER" ? newstr[1].toLong() : newstr[1])
					param3 = (eventDeviceParams[2].type == "NUMBER" ? newstr[2].toLong() : newstr[2])
					param4 = (eventDeviceParams[3].type == "NUMBER" ? newstr[3].toLong() : newstr[3])
					param5 = (eventDeviceParams[4].type == "NUMBER" ? newstr[4].toLong() : newstr[4])
					eventDevice."$object.type"(param1, param2, param3, param4, param5)
					break;
				} 
			}
			else
			{
				log.debug "Empty value received unexpectedly - abort"
			}
		}
		else
		{
			// No parameters needed for this command, so process it as-is
			eventDevice."$object.type"()
		}
	}
}
