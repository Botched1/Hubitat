/**
 *  ****************  Dimmer Sync Child App  ****************
 *
 *  Design Usage:
 *  Keep dimmers in sync - ON/OFF and level
 *
 *  Code and ideas used from @Cobra and @BPTWorld on Hubitat forums.
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
 *  V1.0.0 - 12/31/18 - Initial release.
 *
 */

definition(
    name:"Dimmer Sync Child",
    namespace: "Botched1",
    author: "Jason Bottjen",
    description: "Keep dimmers in sync - ON/OFF and level",
    category: "",

	parent: "Botched1:Dimmer Sync",
    
    iconUrl: "",
    iconX2Url: "",
    iconX3Url: "",
)

preferences {
    page(name: "pageConfig")
}

def installed() {
    log.debug "Installed with settings: ${settings}"
    initialize()
}

def updated() {
    log.debug "Updated with settings: ${settings}"
    unsubscribe()
    initialize()
}

def initialize() {
	setDefaults()
	if(pause1==false){subscribeNow()}
}

def pageConfig() {
    dynamicPage(name: "pageConfig", title: "<h2 style='color:#00CED1;font-weight: bold'>Dimmer Sync</h2>", nextPage: null, install: true, uninstall: true, refreshInterval:0) {	
	display()
    
	section("Instructions:", hideable: true, hidden: true) {
		paragraph "<b>Notes:</b>"
		paragraph "- Select master and slave dimmers you want to keep in sync<br>- The slave(s) will follow the master."
	}
		
	section(getFormat("header-darkcyan", " Select Master Dimmer Device")) {
		input "masterDimmer", "capability.switchLevel", title: "Select Master Dimmer Device", submitOnChange: true, hideWhenEmpty: true, required: true, multiple: false
	}
	section(getFormat("header-darkcyan", " Select Slave Dimmer Device(s)")) {
		input "slaveDimmer", "capability.switchLevel", title: "Select Slave Dimmer Device(s)", submitOnChange: true, hideWhenEmpty: true, required: true, multiple: true
	}
	section(getFormat("header-darkcyan", " General")) {label title: "Enter a name for this child app", required: false}
	section() {
		input(name: "logEnable", type: "bool", defaultValue: "true", title: "Enable Debug Logging", description: "Enable extra logging for debugging.")
   	}
	display2()
	}
}

def display() {
	section() {
		paragraph getFormat("line")
		input "pause1", "bool", title: "Pause This App", required: true, submitOnChange: true, defaultValue: false
	}
}

def display2() {
	section() {
		paragraph getFormat("line")
		paragraph "<div style='color:#00CED1;text-align:center'>Dimmer Sync - App Version: 1.0.0</div>"
	}
}

def getFormat(type, myText=""){
	if(type == "header-green") return "<div style='color:#ffffff;font-weight: bold;background-color:#81BC00;border: 1px solid;box-shadow: 2px 3px #A9A9A9'>${myText}</div>"
	if(type == "header-darkcyan") return "<div style='color:#ffffff;font-weight: bold;background-color:#008B8B;border: 1px solid;box-shadow: 2px 3px #A9A9A9'>${myText}</div>"
    if(type == "line") return "\n<hr style='background-color:#00CED1; height: 1px; border: 0;'></hr>"
	if(type == "title") return "<div style='color:#00CED1;font-weight: bold; font-style: italic'>${myText}</div>"
}

def LOGDEBUG(txt){
    try {
		if (settings.logEnable) { log.debug("${app.label} - ${txt}") }
    } catch(ex) {
    	log.error("${app.label} - LOGDEBUG unable to output requested data!")
    }
}

def pauseOrNot(){
	LOGDEBUG("In pauseOrNot...")
    state.pauseNow = pause1
        if(state.pauseNow == true){
            state.pauseApp = true
            if(app.label){
            if(app.label.contains('red')){
                log.warn "Paused"}
            else{app.updateLabel(app.label + ("<font color = 'red'> (Paused) </font>" ))
              LOGDEBUG("App Paused - state.pauseApp = $state.pauseApp ")   
            }
            }
        }
     if(state.pauseNow == false){
         state.pauseApp = false
         if(app.label){
     if(app.label.contains('red')){ app.updateLabel(app.label.minus("<font color = 'red'> (Paused) </font>" ))
     	LOGDEBUG("App Released - state.pauseApp = $state.pauseApp ")                          
        }
     }
  }    
}

def setDefaults(){
    pauseOrNot()
    if(pause1 == null){pause1 = false}
    if(state.pauseApp == null){state.pauseApp = false}
	if(logEnable == null){logEnable = false}
}

def subscribeNow() {
	unsubscribe()
	subscribe(masterDimmer, "switch", masterONOFFHandler) 
	subscribe(masterDimmer, "level", masterLEVELHandler) 
}

def masterONOFFHandler(evt){
	LOGDEBUG("Event Value: " + evt.value)
	if(evt.value == "on"){
		LOGDEBUG("ON Check True")
		def NewLevel = masterDimmer.currentValue("level")
		slaveDimmer.each{
			LOGDEBUG("Turning ON " + it)
			it.on()
			it.setLevel(NewLevel)
		}
	}
	if(evt.value == "off"){
		LOGDEBUG("OFF Check True")
		slaveDimmer.each{
			LOGDEBUG("Turning OFF " + it)
			it.off()
		}
	}
}

def masterLEVELHandler(evt){
	LOGDEBUG("Event Level Value: " + evt.value)
	LOGDEBUG("slaveDimmer: " + slaveDimmer)
	def NewLevel = evt.value.toInteger()
	slaveDimmer.each{
		LOGDEBUG("Dimming " + it + " to " + NewLevel)
		it.setLevel(NewLevel)
	}
}
