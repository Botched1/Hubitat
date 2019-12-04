/**
 *  Author: Jason Bottjen
 *  Date: 2019-12-04
 */

metadata {
	definition (name: "Basic Zigbee Tool", namespace: "botched1", author: "Jason Bottjen") {
        
        command "test"

    }
}
    

def parse(String description) {
    log.debug "Parse description $description"
    def descMap = zigbee.parseDescriptionAsMap(description)
    log.debug "Desc Map: $descMap"
	  def map = [:]
    def result = null
	  if (map) {
		  result = createEvent(map)
	  }
  	log.debug "Parse returned $map"
	  return result
}

def test() {
    log.debug "test"
    def cmds = []
    
    cmds += zigbee.readAttribute(0x001, 0x0020) //Read Battery Voltage
    
    //    cmds += zigbee.configureReporting(0x201, 0x0000, 0x29, 10, 60, 50)   //Attribute ID 0x0000 = local temperature, Data Type: S16BIT
    
    return cmds
}
