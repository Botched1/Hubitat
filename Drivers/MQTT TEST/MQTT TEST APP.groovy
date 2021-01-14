/**
 *  ****************  MQTT TEST APP  ****************
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
 *
 */

import groovy.json.JsonSlurper

definition(
	name: "MQTT TEST APP",
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
			input "deviceList", "capability.*", title: "Devices", description: "Devices", required: true, multiple: true
			input "mqttDriver", "capability.notification", title: "MQTT Driver", description: "MQTT Driver", required: true, multiple: false
			input(name: "logEnable", type: "bool", defaultValue: "true", title: "Enable Debug Logging", description: "Enable extra logging for debugging.")
		}
	}
}

def installed() 
{
	//log.debug "----- in installed -----"	
	if (logEnable) log.debug "installed"
	initialize()
}

def updated()
{
	//log.debug "----- in updated -----"
	if (logEnable) log.debug "updated"
	initialize()
}

def logsOff(){
    log.warn "debug logging disabled..."
    app.updateSetting("logEnable",[value:"false",type:"bool"])
}

def initialize() 
{
	log.debug "initialize starting"
	
	// Set state so events that happen during init are ignored in the event handler
	atomicState.initialized = false
	
	// Unsubscribe from all events
	unsubscribe()
	
	// Subscribe to events form selected devices, and MQTT driver
	subscribe(deviceList, deviceEvent, ["filterEvents": false])
	subscribe(mqttDriver, "mqtt", deviceEvent, ["filterEvents": false])
	subscribe(mqttDriver, "init", deviceEvent, ["filterEvents": false])
	subscribe(mqttDriver, "sendAll", deviceEvent, ["filterEvents": false])

	if (logEnable) {
		log.warn "Debug logging is enabled. Will be automatically turned off in 30 minutes."
		runIn(1800,logsOff)
	}
	
	// Walk through selected devices to get commands and attributes
	for(item in deviceList){
		// Publish Commands
		commandList = item.getSupportedCommands()

		for(commandItem in commandList){
			mqttDriver.publish("${item}/${commandItem}/set","")
			pauseExecution(100)
		}

		// Publish sendAll command
		mqttDriver.publish("sendAll","")
		pauseExecution(100)
		
		// MQTT Subscribe to all command sets
		mqttDriver.subscribe("+/+/set")
		pauseExecution(100)
		
		// Subscribe to sendAll
		mqttDriver.subscribe("sendAll")
	}

	log.debug "initialize complete"
	
	// Set state so new events will get processed in that handler
	atomicState.initialized = true
}

def sendAll() 
{
	log.debug "sendAll starting"
	
	// Set state so events that happen during init are ignored in the event handler
	atomicState.initialized = false
	
	// Unsubscribe from all events
	unsubscribe()
	
	// Subscribe to events form selected devices, and MQTT driver
	subscribe(deviceList, deviceEvent, ["filterEvents": false])
	subscribe(mqttDriver, "mqtt", deviceEvent, ["filterEvents": false])
	subscribe(mqttDriver, "init", deviceEvent, ["filterEvents": false])
	subscribe(mqttDriver, "sendAll", deviceEvent, ["filterEvents": false])
		
	// Walk through selected devices to get commands and attributes
	for(item in deviceList){
		// Publish Commands
		commandList = item.getSupportedCommands()

		for(commandItem in commandList){
			mqttDriver.publish("${item}/${commandItem}/set","")
			pauseExecution(100)
		}

		// Publish sendAll command
		mqttDriver.publish("sendAll","")
		pauseExecution(100)
		
		// MQTT Subscribe to all command sets
		mqttDriver.subscribe("+/+/set")
		pauseExecution(100)
		
		// Subscribe to sendAll
		mqttDriver.subscribe("sendAll")
		pauseExecution(100)
		
		// Publish Attributes
		attributeList = item.getSupportedAttributes()
		
		for(attributeItem in attributeList){
			curVal = item.currentValue("${attributeItem}")
			
			if (!curVal) {
				curVal="null"
			} else {
				curVal = curVal.toString()
			}
			mqttDriver.publish("${item}/${attributeItem}/value",curVal)
			pauseExecution(100)
		}
	}

	log.debug "sendAll complete"
	
	// Set state so new events will get processed in that handler
	atomicState.initialized = true
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

	if (!atomicState.initialized) {
		return;
	}
	
    // If MQTT driver is init, then re-initialize app
	if (evt.name == "init") {
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
		mqttDriver.publish("${evt.getDevice()}/${evt.name}/value","${evt.value}")
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
