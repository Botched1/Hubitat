/*
 * Import URL: https://raw.githubusercontent.com/Botched1/Hubitat/master/Apps/YAMA/YAMA%20DRIVER.groovy 
 *
 *  ****************  YAMA (Yet Another MQTT App) DRIVER  ****************
 *
 *  Design Usage:
 *  To be used with YAMA APP. Publish Hubitat devices, commands, and attributes to MQTT 
 *
 *  Mostly original code, but inspiration and helper functions used from MQTT Link Driver (mydevbox, jeubanks, et al)
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
 *  V0.0.2 - 01/13/21 - Test release 2. Fixed init after reboot, and a few other code cleanup items
 *  V0.0.3 - 01/13/21 - Added sendAll command
 *  V0.0.4 - 01/18/21 - REDACTED
 *  V0.0.5 - 01/18/21 - Removed atomicState as it was doing odd things on some hubs
 *  V0.0.6 - 01/20/21 - Fixed looping issue when broker disconnected from calling PublishLWT. Added subscribe to +/+/set and 
 *                      SendAll on connected status. Added periodic reconnect setting.
 *
 */

import groovy.json.JsonOutput

metadata {
	definition(name: "YAMA (Yet Another MQTT App) DRIVER", namespace: "Botched1", author: "Jason Bottjen", description: "YAMA driver", iconUrl: "", iconX2Url: "", iconX3Url: "") 
	
	{
		capability "Initialize"
		capability "Notification"

		preferences {
			input(name: "brokerIp", type: "string", title: "MQTT Broker IP Address", description: "example: 192.168.1.111", required: true, displayDuringSetup: true)
			input(name: "brokerPort", type: "string", title: "MQTT Broker Port", description: "example: 1883", required: true, displayDuringSetup: true)
			input(name: "brokerUser", type: "string", title: "MQTT Broker Username", description: "", required: false, displayDuringSetup: true)
			input(name: "brokerPassword", type: "password", title: "MQTT Broker Password", description: "", required: false, displayDuringSetup: true)
			input(name: "periodicConnectionRetry", type: "bool", title: "Periodically attempt re-connection if disconnected", defaultValue: true)
			input(name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: false)		
		}

		command "publish", [[name:"topic*",type:"STRING",description:"Topic"],[name:"mqtt",type:"STRING", description:"Payload"]]
		command "subscribe", [[name:"topic*",type:"STRING", description:"Topic"]]
		command "unsubscribe", [[name:"topic*",type:"STRING", description:"Topic"]]
		command "connect"
		command "disconnect"
		command "sendAll"
		
		attribute "connectionState", "string"
	}
}

def installed() {
	//log.debug "----- IN INSTALLED -----"
}

def initialize() {
	runInMillis(5000, heartbeat)
	mqttConnectionAttempt()
	sendEvent(name: "init", value: true, displayed: false)
}

def mqttConnectionAttempt() {
	if (logEnable) log.debug "MQTT Connection Attempt"
 
	if (!interfaces.mqtt.isConnected()) {
		try {   
			interfaces.mqtt.connect("tcp://${settings?.brokerIp}:${settings?.brokerPort}",
							   "hubitat_${getHubId()}", 
							   settings?.brokerUser, 
							   settings?.brokerPassword, 
							   lastWillTopic: "hubitat/${getHubId()}/LWT",
							   lastWillQos: 0, 
							   lastWillMessage: "offline", 
							   lastWillRetain: true)

			// delay for connection
			pauseExecution(1000)

		} catch(Exception e) {
			log.error "In mqttConnectionAttempt: Error initializing."
			if (!interfaces.mqtt.isConnected()) disconnected()
		}
	}

	if (interfaces.mqtt.isConnected()) {
		unschedule(connect)
		runInMillis(5000, heartbeat)
		connected()
	}
}

def updated() {
	disconnect()
	pauseExecution(1000)
	mqttConnectionAttempt()	
}

/////////////////////////////////////////////////////////////////////
// Driver Commands and Functions
/////////////////////////////////////////////////////////////////////

def publish(topic, payload) {
    publishMqtt(topic, payload)
}

def publishMqtt(topic, payload, qos = 0, retained = true) {
    if (!interfaces.mqtt.isConnected()) {
        mqttConnectionAttempt()
    }

    try {
        interfaces.mqtt.publish("hubitat/${getHubId()}/${topic}", payload, qos, retained)
        if (logEnable) log.debug "[publishMqtt] topic: hubitat/${getHubId()}/${topic} payload: ${payload}"
    } catch (Exception e) {
        log.error "In publishMqtt: Unable to publish message."
    }
}

def subscribe(topic) {
    if (!interfaces.mqtt.isConnected()) {
        connect()
    }

    if (logEnable) log.debug "Subscribe to: hubitat/${getHubId()}/${topic}"
    interfaces.mqtt.subscribe("hubitat/${getHubId()}/${topic}")
}

def unsubscribe(topic) {
    if (!interfaces.mqtt.isConnected()) {
        connect()
    }
    
    if (logEnable) log.debug "Unsubscribe from: hubitat/${getHubId()}/${topic}"
    interfaces.mqtt.unsubscribe("hubitat/${getHubId()}/${topic}")
}

def connect() {
    mqttConnectionAttempt()
}

def connected() {
    log.info "In connected: Connected to broker"
    sendEvent (name: "connectionState", value: "connected")
    publishLwt("online")
	subscribe("+/+/set")
	subscribe("sendAll")
	runInMillis(5000, heartbeat)
}

def disconnect() {
	unschedule(heartbeat)

	if (interfaces.mqtt.isConnected()) {
		publishLwt("offline")
		pauseExecution(1000)
		try {
			interfaces.mqtt.disconnect()
			pauseExecution(500)
			disconnected()
		} catch(e) {
			log.warn "Disconnection from broker failed."
			if (interfaces.mqtt.isConnected()) {
				connected()
			}
			else {
				disconnected()
			}
			return;
		}
	} 
	else {
		disconnected()
	}
}

def disconnected() {
	log.info "In disconnected: Disconnected from broker"
    sendEvent (name: "connectionState", value: "disconnected")
	
	if (periodicConnectionRetry) runIn(60, connect)
}

def publishLwt(String status) {
    publishMqtt("LWT", status)
}

def deviceNotification(message) {
    // This does nothing, but is required for notification capability
}

def sendAll() {
	if (logEnable) log.debug "In sendAll"
	sendEvent(name: "sendAll", value: true, displayed: false)	
}
	
/////////////////////////////////////////////////////////////////////
// Parse
/////////////////////////////////////////////////////////////////////

def parse(String event) {
    def message = interfaces.mqtt.parseMessage(event)  
    def (name, hub, device, type) = message.topic.tokenize( '/' )
    
    if (logEnable) log.debug "In parse, received message: ${message}"
    
	if  (device == "sendAll") {
		if (message.payload) {
			sendAll();
		}
		return;
	}
	
    def json = new groovy.json.JsonOutput().toJson([
        device: device,
        type: type,
        value: message.payload
	])
    
    return createEvent(name: "mqtt", value: json, displayed: false)
}


/////////////////////////////////////////////////////////////////////
// Helper Functions
/////////////////////////////////////////////////////////////////////

def normalize(name) {
    return name.replaceAll("[^a-zA-Z0-9]+","-").toLowerCase()
}

def getHubId() {
    def hub = location.hub
    def hubNameNormalized = normalize(hub.name)
    hubNameNormalized = hubNameNormalized.toLowerCase()
    return hubNameNormalized
}

def heartbeat() {
	if (interfaces.mqtt.isConnected()) {
		publishMqtt("heartbeat", now().toString())
		runInMillis(5000, heartbeat)
	}				
}

def mqttClientStatus(status) {
     if (logEnable) log.debug "In mqttClientStatus: ${status}"
}
