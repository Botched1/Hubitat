/*
    Konke Temperature and Humidity Sensor

    Based off of:
        1. The example "environment sensor" driver by Mike Maxwell, published on Hubitat github
        2. The Hubitat "Konke ZigBee Temperature Humidity Sensor" drver by Muxa

    0.1.0 - 12/08/2019 - Initial test version. Not for production use, will definitiely change before final version.
*/

definition (name: "Konke Temperature and Humidity TEST", namespace: "Botched1", author: "Jason Bottjen") {
    capability "Configuration"
    capability "Refresh"
    capability "Battery"
    capability "TemperatureMeasurement"
    capability "RelativeHumidityMeasurement"
    capability "Sensor"

    fingerprint profileId: "0104", inClusters: "0000,0001,0003,0402,0405", outClusters: "0003", manufacturer: "Konke", model: "3AFE140103020000"

}
        
preferences {
    //standard logging options
    input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: true
    input name: "txtEnable", type: "bool", title: "Enable descriptionText logging", defaultValue: true        
}

def logsOff(){
    log.warn "debug logging disabled..."
    device.updateSetting("logEnable",[value:"false",type:"bool"])
}

def parse(String description) {
    if (logEnable) log.debug "description is ${description}"

    if (description.startsWith("catchall")) {
        return
    }
    def descMap = zigbee.parseDescriptionAsMap(description)
    if (logEnable) log.debug "descMap:${descMap}"
	
	  def cluster = descMap.cluster
	  def hexValue = descMap.value
	  def attrId = descMap.attrId
	
	  switch (cluster){
        case "0001" :   //battery
            if (attrId == "0020") {
		            getBatteryResult(hexValue)
            }
            break
        case "0402" :	//temp
            if (hexValue == "F8CD") {
                if (logEnable) log.debug "Reset button was short-pressed"
            } else {
                getTemperatureResult(hexValue)
            }
			  break
		    case "0405" :	//humidity
			      getHumidityResult(hexValue)
			  break
		    default :
			      log.warn "skipped cluster: ${cluster}, descMap:${descMap}"
			  break
	  }
	  return
}

//event methods
private getTemperatureResult(hex){
    def valueRaw = hexStrToSignedInt(hex)
    valueRaw = valueRaw / 100
    def value = convertTemperatureIfNeeded(valueRaw.toFloat(),"c",1)
    def name = "temperature"
    def unit = "°${location.temperatureScale}"
    def descriptionText = "${device.displayName} ${name} is ${value}${unit}"
    if (txtEnable) log.info "${descriptionText}"
    sendEvent(name: name,value: value,descriptionText: descriptionText,unit: unit)
}

private getHumidityResult(hex){
    def valueRaw = hexStrToUnsignedInt(hex)
    def value = valueRaw / 100
    def name = "humidity"
    def unit = "%"
    def descriptionText = "${device.displayName} ${name} is ${value}${unit}"
    if (txtEnable) log.info "${descriptionText}"
    sendEvent(name: name,value: value,descriptionText: descriptionText,unit: unit)
}

private getBatteryResult(hex){
	def valueRaw = hexStrToUnsignedInt(hex)
	def value = valueRaw / 10
	def voltsMin = 2.5
	def voltsMax = 3.0
	def pct = (value - voltsMin) / (voltsMax - voltsMin)
	def roundedPct = Math.min(100, Math.round(pct * 100))
    value = Math.max(0, roundedPct)
    def name = "battery"
    def unit = "%"
    def descriptionText = "${device.displayName} ${name} is ${value}${unit}"
    if (txtEnable) log.info "${descriptionText}"
    sendEvent(name: name,value: value,descriptionText: descriptionText,unit: unit, isStateChange: true)
}

def refresh() {
    log.debug "Refresh"
    
	//readAttribute(cluster,attribute,mfg code,optional delay ms)
    def cmds = zigbee.readAttribute(0x0402,0x0000,[:],200) + //temp
    zigbee.readAttribute(0x0405,0x0000,[:],200) + 			 //humidity
    zigbee.readAttribute(0x0001,0x0020,[:],200)  			 //battery
    return cmds
}

def configure() {
    log.debug "Configuring Reporting and Bindings."
    runIn(1800,logsOff)
    
    //List cmds = zigbee.batteryConfig()                                                        //battery      
    List cmds = zigbee.configureReporting(0x0001, 0x0020, DataType.UINT8, 14400, 86400, 1)      //battery - 0.1v delta
    //cmds = cmds + zigbee.temperatureConfig(60, 3600)									        //temp
    cmds = cmds + zigbee.configureReporting(0x0402, 0x0000, DataType.INT16, 30, 14400, 18)	    //temp - 0.324 DegF delta
    cmds = cmds + zigbee.configureReporting(0x0405, 0x0000, DataType.UINT16, 30, 14400, 100)	//humidity - 1% delta
        
    cmds = cmds + refresh()
    log.info "cmds:${cmds}"
    return cmds
}

def updated() {
    log.trace "Updated()"
    log.warn "debug logging is: ${logEnable == true}"
    log.warn "description logging is: ${txtEnable == true}"
    if (logEnable) runIn(1800,logsOff)    
}
