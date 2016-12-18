
 preferences {
    input("ip", "text", title: "IP Address", description: "ip", required: true)
    input("port", "number", title: "Port", description: "port", default: 9060, required: true)
    input("mac", "text", title: "MAC Addr", description: "mac")
}
 
 metadata {
    definition (name: "ESP8266 Contact Sensor", namespace: "cschwer", author: "Charles Schwer") {
        capability "Refresh"
        capability "Sensor"
        capability "Temperature Measurement"
        capability "Contact Sensor"
    }
 
    // simulator metadata
    simulator {}
 
    // UI tile definitions
    tiles {
        standardTile("contact", "device.contact", width: 2, height: 2) {
            state("open", label:'${name}', icon:"st.contact.contact.open", backgroundColor:"#ffa81e")
            state("closed", label:'${name}', icon:"st.contact.contact.closed", backgroundColor:"#79b821")
       }
        standardTile("refresh", "device.backdoor", inactiveLabel: false, decoration: "flat") {
           state("default", label:'', action:"refresh.refresh", icon:"st.secondary.refresh")
        }
        
       
        
                
        main "contact"
        details (["contact", "refresh", "temperature"])
        valueTile("temperature", "device.temperature", width: 2, height: 2) {
			state("temperature", label:'${currentValue}°', unit:"F",
				backgroundColors:[
					[value: 31, color: "#153591"],
					[value: 44, color: "#1e9cbb"],
					[value: 59, color: "#90d2a7"],
					[value: 74, color: "#44b621"],
					[value: 84, color: "#f1d801"],
					[value: 95, color: "#d04e00"],
					[value: 96, color: "#bc2323"]
				]   
    	)
    }
 
        
 //main "temperature"
	//	details("temperature","refresh")
 
 }
}
def updated() {
    log.debug("Updated with settings: $settings")
    state.dni = ""
    updateDNI()
    updateSettings()
}
 
// parse events into attributes
def parse(String description) {
    def msg = parseLanMessage(description)
    if (!state.mac || state.mac != msg.mac) {
        log.debug "Setting deviceNetworkId to MAC address ${msg.mac}"
        state.mac = msg.mac
    }
    def result = []
    def bodyString = msg.body
    def value = ""
    if (bodyString) {
        def json = msg.json;
        if( json?.name == "contact") {
            value = json.status == 1 ? "open" : "closed"
            log.debug "contact status ${value}"
            result << createEvent(name: "contact", value: value)
        }
        else if( json?.name == "temperature") {
            if(getTemperatureScale() == "F"){
				value = (celsiusToFahrenheit(json.value) as Float).round(0) as Integer
			} else {
				value = json.value
			}
			log.debug "temperature value ${value}"
			result << createEvent(name: "temperature", value: value)
    }
    }
    
    result
}
 
private getHostAddress() {
    def ip = settings.ip
    def port = settings.port
 
    //log.debug "Using ip: ${ip} and port: ${port} for device: ${device.id}"
    return ip + ":" + port
}
 
private def updateDNI() {
    if (!state.dni || state.dni != device.deviceNetworkId || (state.mac && state.mac != device.deviceNetworkId)) {
        device.setDeviceNetworkId(createNetworkId(settings.ip, settings.port))
        state.dni = device.deviceNetworkId
    }
}
 
private String createNetworkId(ipaddr, port) {
    if (state.mac) {
        return state.mac
    }
    def hexIp = ipaddr.tokenize('.').collect {
        String.format('%02X', it.toInteger())
    }.join()
    def hexPort = String.format('%04X', port.toInteger())
    return "${hexIp}:${hexPort}"
}
 
def updateSettings() {
    def headers = [:]
    headers.put("HOST", getHostAddress())
    headers.put("Content-Type", "application/json")
    groovy.json.JsonBuilder json = new groovy.json.JsonBuilder ()
    def map = json {
        hubIp device.hub.getDataValue("localIP")
        hubPort device.hub.getDataValue("localSrvPortTCP").toInteger()
        deviceName device.name
    }    
    return new physicalgraph.device.HubAction(
        method: "POST",
        path: "/updateSettings",
        body: json.toString(),
        headers: headers
    )
}
 
def refresh() {
    log.debug "Executing 'refresh' ${getHostAddress()}"
    updateDNI()
    return new physicalgraph.device.HubAction(
        method: "GET",
        path: "/getTemp",
        headers: [
                HOST: "${getHostAddress()}"
        ]
    )
}
  