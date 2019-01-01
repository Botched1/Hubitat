/**
 *  ****************  Dimmer Sync Parent App  ****************
 *
 *  Design Usage:
 *  Keep dimmers in sync - ON/OFF and dimmer level
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
    name:"Dimmer Sync",
    namespace: "Botched1",
    author: "Jason Bottjen",
    description: "Keep dimmers in sync - ON/OFF and level",
    category: "Convenience",
    iconUrl: "",
    iconX2Url: "",
    iconX3Url: ""
    )

preferences {
     page name: "mainPage", title: "", install: true, uninstall: true
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
    log.info "There are ${childApps.size()} child apps"
    childApps.each {child ->
    log.info "Child app: ${child.label}"
    }
}

def mainPage() {
    dynamicPage(name: "mainPage") {
    	installCheck()
		if(state.appInstalled == 'COMPLETE'){
			section(getFormat("title", "${app.label}")) {
				paragraph "<div style='color:#00CED1'>Keep dimmers in sync - ON/OFF and level</div>"
				paragraph getFormat("line")
			}
			section("Instructions:", hideable: true, hidden: true) {
				paragraph "<b>Notes:</b>"
				paragraph "- Add master and slave dimmmers to keep in sync.<br>"
			}
  			section(getFormat("header-darkcyan", " Child Apps")) {
				app(name: "anyOpenApp", appName: "Dimmer Sync Child", namespace: "Botched1", title: "<b>Add a new 'Dimmer Sync' child</b>", multiple: true)
  			}
 			section(getFormat("header-darkcyan", " General")) {
       				label title: "Enter a name for parent app (optional)", required: false
 			}
			display()
		}
	}
}

def installCheck(){         
	state.appInstalled = app.getInstallationState() 
	if(state.appInstalled != 'COMPLETE'){
		section{paragraph "Please hit 'Done' to install '${app.label}' parent app "}
  	}
  	else{
    	log.info "Parent Installed OK"
  	}
}

def getFormat(type, myText=""){
	if(type == "header-green") return "<div style='color:#ffffff;font-weight: bold;background-color:#81BC00;border: 1px solid;box-shadow: 2px 3px #A9A9A9'>${myText}</div>"
	if(type == "header-darkcyan") return "<div style='color:#ffffff;font-weight: bold;background-color:#008B8B;border: 1px solid;box-shadow: 2px 3px #A9A9A9'>${myText}</div>"
	if(type == "line") return "\n<hr style='background-color:#00CED1; height: 1px; border: 0;'></hr>"
	if(type == "title") return "<h2 style='color:#00CED1;font-weight: bold;font-style: italic'>${myText}</h2>"
}

def display(){
	section() {
		paragraph getFormat("line")
		paragraph "<div style='color:#00CED1;text-align:center'>Dimmer Sync - App Version: 1.0.0</div>"
	}       
}          
