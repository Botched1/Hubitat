/**
 * Roku TV Lite
 *
 *  IMPORT URL: https://raw.githubusercontent.com/Botched1/Hubitat/master/Drivers/Roku%20TV%20Lite/Roku%20TV%20Lite.groovy
 *
 * Description:
 * This is a device handler designed to manage and control a Roku TV or Player connected to the same network 
 * as the Hubitat hub.
 *
 * Based on the full featured Roku TV driver from Armand Welsh
 * Download: https://github.com/apwelsh/hubitat
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
 *-------------------------------------------------------------------------------------------------------------------
 **/
preferences {
	input "deviceIp", "text", title: "Device IP", required: true
	input "deviceMac", "text", title: "Device MAC Address", required: true, defaultValue: "UNKNOWN"
	input "logEnable", "bool", title: "Enable debug logging", defaultValue: true		
}

metadata {
	definition (name: "Roku TV Lite", namespace: "Botched1", author: "Jason Bottjen") {
		capability "TV"
		capability "AudioVolume"
		capability "Switch"
		capability "Polling"
		capability "Refresh"

		command "hdmi1"
		command "hdmi2"
		command "hdmi3"
		command "hdmi4"
		
		//command "reloadApps"
  }
}

def installed() {
	updated()
}

def updated() {
	log.info "updated..."
    unschedule()
	log.warn "debug logging is: ${logEnable == true}"
    log.warn "description logging is: ${txtEnable == true}"
    if (logEnable) runIn(1800,logsOff)
	poll()
	runEvery1Minute(queryDeviceState)
	//runEvery1Minute(queryCurrentApp)
	//runEvery15Minutes(queryInstalledApps)
}

def parse(String description) {
	def msg = parseLanMessage(description)
	
	if (msg.body) {
		def body = new XmlParser().parseText(msg.body)
		switch (body.name()) {
			case "device-info":
				parseMacAddress body
				parsePowerState body
				parseState body
				break;
			case "apps":
				//parseInstalledApps body
				log.warn "body.name = apps. You shouldn't see this..."
				break;
			case "active-app":
				//parseActiveApp body
				log.warn "body.name = active-app. You shouldn't see this..."
				break;
		}
	}
}

def parseState(Node body) {
	for (def node : body) {
		def key = node.name()
		if (key == null)
			continue
		
		switch (key) {
			case "serial-number":
			case "vendor-name":
			case "device-id":
			case "mode-name":
			case "screen-size":
			case "user-device-name":
				def value = node.value()
				if (value != null) {
					if (value[0] != this.state."${key}") {
						this.state."${key}" = value[0]
						if (logEnable) log.debug "set ${key} = ${value[0]}"
					}
				}
		}
	}
}

def parseMacAddress(Node body) {
	def wifiMac = body."wifi-mac"[0]?.value()
	if (wifiMac != null) {
		def macAddress = wifiMac[0].replaceAll("[^A-f,a-f,0-9]","")
		if (!deviceMac || deviceMac != macAddress) {
			if (logEnable) log.debug "Update config [MAC Address = ${macAddress}]"
			device.updateSetting("deviceMac", [value: macAddress, type:"text"])
		}
	}
}

def parsePowerState(Node body) {
	def powerMode = body."power-mode"[0]?.value()
	if (powerMode != null) {
		def mode = powerMode[0]
		switch (mode) {
			case "PowerOn":
				if (this.state!="on") {
					sendEvent(name: "switch", value: "on")
				} 
				break;
			case "PowerOff":
			case "Headless":
				if (this.state!="off") {
					sendEvent(name: "switch", value: "off")
				}
				break;
		}
	}	
}

def on() {
	sendEvent(name: "switch", value: "on")

	sendHubCommand(new hubitat.device.HubAction (
		"wake on lan ${deviceMac}",
		hubitat.device.Protocol.LAN,
		null,
		[:]
	))

	keypress('Power')
}

def off() {
	sendEvent(name: "switch", value: "off")
	keypress('PowerOff')
}

def channelUp() {
	keypress('ChannelUp')
}

def channelDown() {
	keypress('ChannelDown')
}

def volumeUp() {
	keypress('VolumeUp')
}

def volumeDown() {
	keypress('VolumeDown')
}

def setVolume() {
	
}

def unmute() {
	keypress('VolumeMute')
}

def mute() {
	keypress('VolumeMute')
}

def hdmi1() {
	keypress('InputHDMI1')
}

def hdmi2() {
	keypress('InputHDMI2')
}

def hdmi3() {
	keypress('InputHDMI3')
}

def hdmi4() {
	keypress('InputHDMI4')
}

def poll() {
	if (logEnable) log.debug "Executing 'poll'"
	refresh()
}

def refresh() {
	if (logEnable) log.debug "Executing 'refresh'"
	queryDeviceState()
}

def queryDeviceState() {
	sendHubCommand(new hubitat.device.HubAction(
		method: "GET",
		path: "/query/device-info",
		headers: [ HOST: "${deviceIp}:8060" ]
	))
}

def keypress(key) {
	if (logEnable) log.debug "Executing '${key}'"
	def result = new hubitat.device.HubAction(
		method: "POST",
		path: "/keypress/${key}",
		headers: [ HOST: "${deviceIp}:8060" ],
	)
}

def logsOff(){
    log.warn "debug logging disabled..."
    device.updateSetting("logEnable",[value:"false",type:"bool"])
}
