/**
 *  ****************  MQTT TEST DRIVER  ****************
 *
 *  Design Usage:
 *  To be used with MQTT TEST APP. Publish Hubitat devices, commands, and attributes to MQTT 
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
 *
 */

import groovy.json.JsonOutput

metadata {
	definition(name: "MQTT TEST DRIVER", namespace: "Botched1", author: "Jason Bottjen", description: "MQTT TEST driver", iconUrl: "", iconX2Url: "", iconX3Url: "") 
	
	{
		capability "Notification"

		preferences {
			input(name: "brokerIp", type: "string", title: "MQTT Broker IP Address", description: "example: 192.168.1.111", required: true, displayDuringSetup: true)
			input(name: "brokerPort", type: "string", title: "MQTT Broker Port", description: "example: 1883", required: true, displayDuringSetup: true)
			input(name: "brokerUser", type: "string", title: "MQTT Broker Username", description: "", required: false, displayDuringSetup: true)
			input(name: "brokerPassword", type: "password", title: "MQTT Broker Password", description: "", required: false, displayDuringSetup: true)
			input(name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: false)
		}

		command "publish", [[name:"topic*",type:"STRING",description:"Topic"],[name:"mqtt",type:"STRING", description:"Payload"]]
		command "subscribe", [[name:"topic*",type:"STRING", description:"Topic"]]
		command "unsubscribe", [[name:"topic*",type:"STRING", description:"Topic"]]
		command "connect"
		command "disconnect"
	}
}

void initialize() {
    atomicState.topicPrefix = "hubitat/${getHubId()}/"
	
	if (logEnable) log.debug "Initialize MQTT Connection"
    
	try {   
        interfaces.mqtt.connect("tcp://${settings?.brokerIp}:${settings?.brokerPort}",
                           "hubitat_${getHubId()}", 
                           settings?.brokerUser, 
                           settings?.brokerPassword, 
                           lastWillTopic: "${atomicState.topicPrefix}LWT",
                           lastWillQos: 0, 
                           lastWillMessage: "offline", 
                           lastWillRetain: true)
       
        // delay for connection
        pauseExecution(1000)
        
    } catch(Exception e) {
        log.error "In initialize: Error initializing."
    }
}

def updated() {
	disconnect()
	initialize()	
}

/////////////////////////////////////////////////////////////////////
// Driver Commands and Functions
/////////////////////////////////////////////////////////////////////

def publish(topic, payload) {
    publishMqtt(topic, payload)
}

def publishMqtt(topic, payload, qos = 0, retained = true) {
    if (!interfaces.mqtt.isConnected()) {
        initialize()
    }
    
    def pubTopic = "${atomicState.topicPrefix}${topic}"

    try {
        interfaces.mqtt.publish("${pubTopic}", payload, qos, retained)
        if (logEnable) log.debug "[publishMqtt] topic: ${pubTopic} payload: ${payload}"
        
    } catch (Exception e) {
        log.error "In publishMqtt: Unable to publish message."
    }
}

def subscribe(topic) {
    if (!interfaces.mqtt.isConnected()) {
        connect()
    }

    if (logEnable) log.debug "Subscribe to: ${atomicState.topicPrefix}${topic}"
    interfaces.mqtt.subscribe("${atomicState.topicPrefix}${topic}")
}

def unsubscribe(topic) {
    if (!interfaces.mqtt.isConnected()) {
        connect()
    }
    
    if (logEnable) log.debug "Unsubscribe from: ${atomicState.topicPrefix}${topic}"
    interfaces.mqtt.unsubscribe("${atomicState.topicPrefix}${topic}")
}

def connect() {
    initialize()
    connected()
}

def connected() {
    log.info "In connected: Connected to broker"
    sendEvent (name: "connectionState", value: "connected")
    publishLwt("online")
	runInMillis(5000, heartbeat)
}

def disconnect() {
    try {
		unschedule(heartbeat)
        interfaces.mqtt.disconnect()
        disconnected()
    } catch(e) {
        log.warn "Disconnection from broker failed."
        if (interfaces.mqtt.isConnected()) connected()
    }
}

def disconnected() {
	log.info "In disconnected: Disconnected from broker"
    sendEvent (name: "connectionState", value: "disconnected")
    publishLwt("offline")
	unschedule(heartbeat)
}

def publishLwt(String status) {
    publishMqtt("LWT", status)
}

def deviceNotification(message) {
    // This does nothing, but is required for notification capability
}


/////////////////////////////////////////////////////////////////////
// Parse
/////////////////////////////////////////////////////////////////////

def parse(String event) {
    def message = interfaces.mqtt.parseMessage(event)  
    def (name, hub, device, type) = message.topic.tokenize( '/' )
    
    if (logEnable) log.debug "In parse, received message: ${message}"
    
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
    return "${hubNameNormalized}".toLowerCase()
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
