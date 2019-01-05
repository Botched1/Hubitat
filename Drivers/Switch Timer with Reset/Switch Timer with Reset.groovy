/**
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
 *
 *  
 *
 *
 * Modified from code from @Cobra on Hubitat forums. Added ability to RESET timer if switch triggered again before delay
 * expiration.
 */


metadata {

    definition (name: "Switch Timer with Reset", namespace: "Botched1", author: "Jason Bottjen") {
		capability "Switch"
	}


preferences {
		
		input(name: "delayNum", type: "number", title:"Delay before OFF/ON", required: true, defaultValue: 0)	
    	input(name: "delayType", type: "bool", title: "Default State OFF or ON", required: true, defaultValue: false)
		input(name: "minSec", type: "bool", title: "OFF = Seconds - ON = Minutes", required: true, defaultValue: false)
		input(name: "delayReset", type: "bool", title: "OFF = No Reset - ON = Reset", required: true, defaultValue: false)	
	}
}


def on() {
    checkDelay()
	
    if(delayType == false){
    	log.debug "$version - ON"
		sendEvent(name: "switch", value: "on")
    	log.debug "Turning off in $state.delay1 seconds"
		
		if(delayReset == false){
			runIn(state.delay1, off,[overwrite: false])
		} 
		else {
			runIn(state.delay1, off,[overwrite: true])
		}
    }
    
    if(delayType == true){
    	log.debug "$version - ON"
		sendEvent(name: "switch", value: "on")
		unschedule()
    }
}

def off() {
     if(delayType == false){
		log.debug "$version - OFF"
		sendEvent(name: "switch", value: "off")
		unschedule()
     }
    
    if(delayType == true){
		log.debug "$version - OFF"
		sendEvent(name: "switch", value: "off")
    	
		if(delayReset == false){
			runIn(state.delay1, on,[overwrite: false])
		} 
		else {
			runIn(state.delay1, on,[overwrite: true])
		}
    }
}

def checkDelay(){
    if (minSec == true) {
    	state.delay1 = 60 * delayNum as int            
    }
    else {
    	state.delay1 = delayNum as int   
    }
}

private getVersion() {
	"Switch Timer with Reset Version 1.0"
}
