/**
 *  LG Connector (v.0.0.1)
 *
 * MIT License
 *
 * Copyright (c) 2018 fison67@nate.com
 *
 * Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without
 * restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following
 * conditions:
 * 
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
 * OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 */
 
import groovy.json.JsonSlurper
import groovy.json.JsonOutput

definition(
    name: "LG Connector",
    namespace: "fison67",
    author: "fison67",
    description: "A Connector between LG and ST",
    category: "My Apps",
    iconUrl: "https://images.techhive.com/images/article/2015/11/lg-logo-100629042-large.jpg",
    iconX2Url: "https://images.techhive.com/images/article/2015/11/lg-logo-100629042-large.jpg",
    iconX3Url: "https://images.techhive.com/images/article/2015/11/lg-logo-100629042-large.jpg",
    oauth: true
)

preferences {
   page(name: "mainPage")
   page(name: "monitorPage")
   page(name: "langPage")
}


def mainPage() {
	def languageList = ["English", "Korean"]
    dynamicPage(name: "mainPage", title: "LG Connector", nextPage: null, uninstall: true, install: true) {
   		section("Request New Devices"){
        	input "address", "string", title: "Server address", required: true
        	input "address2", "string", title: "Port forwarding Server address", required: false
            input(name: "selectedLang", title:"Select a language" , type: "enum", required: true, options: languageList, defaultValue: "English", description:"Language for DTH")
        	href url:"http://${settings.address}", style:"embedded", required:false, title:"Local Management", description:"This makes you easy to setup"
        	href url:"http://${settings.address2}", style:"embedded", required:false, title:"External Management", description:"This makes you easy to setup"
        }
        
       	section() {
            paragraph "View this SmartApp's configuration to use it in other places."
            href url:"${apiServerUrl("/api/smartapps/installations/${app.id}/config?access_token=${state.accessToken}")}", style:"embedded", required:false, title:"Config", description:"Tap, select, copy, then click \"Done\""
       	}
    }
}

def langPage(){
	dynamicPage(name: "langPage", title:"Select a Language") {
    	section ("Select") {
        	input "Korean",  title: "Korean", multiple: false, required: false
        }
    }
}

def installed() {
    log.debug "Installed with settings: ${settings}"
    
    if (!state.accessToken) {
        createAccessToken()
    }
    
    initialize()
}

def updated() {
    log.debug "Updated with settings: ${settings}"

    initialize()
}

/**
* deviceNetworkID : Reference Device. Not Remote Device
*/
def getDeviceToNotifyList(deviceNetworkID){
	def list = []
	state.monitorMap.each{ targetNetworkID, _data -> 
        if(deviceNetworkID == _data.id){
        	def item = [:]
            item['id'] = 'lg-connector-' + targetNetworkID
            item['data'] = _data.data
            list.push(item)
        }
    }
    return list
}

def updateLanguage(){
    log.debug "Languge >> ${settings.selectedLang}"
    def list = getChildDevices()
    list.each { child ->
        try{
        	child.setLanguage(settings.selectedLang)
        }catch(e){
        	log.error "DTH is not supported to select language"
        }
    }
}

def updateExternalNetwork(){
	log.debug "External Network >> ${settings.externalAddress}"
    def list = getChildDevices()
    list.each { child ->
        try{
        	child.setExternalAddress(settings.externalAddress)
        }catch(e){
        	log.error "DTH is not supported to select external address"
        }
    }
}

def initialize() {
	log.debug "initialize"
    
    def options = [
     	"method": "POST",
        "path": "/settings/smartthings",
        "headers": [
        	"HOST": settings.address,
            "Content-Type": "application/json"
        ],
        "body":[
            "app_url":"${apiServerUrl}/api/smartapps/installations/",
            "app_id":app.id,
            "access_token":state.accessToken
        ]
    ]
    
    def myhubAction = new physicalgraph.device.HubAction(options, null, [callback: null])
    sendHubCommand(myhubAction)
    log.debug "server..."
    
    updateLanguage()
    updateExternalNetwork()
}

def dataCallback(physicalgraph.device.HubResponse hubResponse) {
    def msg, json, status
    try {
        msg = parseLanMessage(hubResponse.description)
        status = msg.status
        json = msg.json
        log.debug "${json}"
    } catch (e) {
        logger('warn', "Exception caught while parsing data: "+e);
    }
}

def getDataList(){
    def options = [
     	"method": "GET",
        "path": "/requestDevice",
        "headers": [
        	"HOST": settings.address,
            "Content-Type": "application/json"
        ]
    ]
    def myhubAction = new physicalgraph.device.HubAction(options, null, [callback: dataCallback])
    sendHubCommand(myhubAction)
}

def addDevice(){
    def address = params.address
    def type = params.type
    
    log.debug("Try >> ADD LG Device ip=${address} type=${type}")
	
    def dni = "lg-connector-" + address
    log.debug("DNI >> " + dni)
    def chlid = getChildDevice(dni)
    if(!child){
        def dth = "LG TV";
        if(type == "tv"){
        	dth = "LG TV";
        }
        def name = dth;
        
        def childDevice = addChildDevice("fison67", dth, dni, location.hubs[0].id, [
            "label": dth
        ])    
        childDevice.setInfo(settings.address, address)
        log.debug "Success >> ADD Device DNI=${dni} ${name}"

        try{ childDevice.setLanguage(settings.selectedLang) }catch(e){}

        def resultString = new groovy.json.JsonOutput().toJson("result":"ok")
        render contentType: "application/javascript", data: resultString
    }
}

def updateDevice(){
    def address = params.id
    def dni = "lg-connector-" + address
    log.debug "update dni >> " + dni
    def chlid = getChildDevice(dni)
    if(chlid){
		chlid.setStatus(params)
    }
    def resultString = new groovy.json.JsonOutput().toJson("result":true)
    render contentType: "application/javascript", data: resultString
}

def getDeviceList(){
	log.debug "getDeviceList"
	def list = getChildDevices();
    def resultList = [];
    list.each { child ->
        def dni = child.deviceNetworkId
        log.debug dni
        resultList.push( dni.substring(13, dni.length()) );
    }
    
    def configString = new groovy.json.JsonOutput().toJson("list":resultList)
    render contentType: "application/javascript", data: configString
}

def authError() {
    [error: "Permission denied"]
}

def renderConfig() {
    def configJson = new groovy.json.JsonOutput().toJson([
        description: "LG Connector API",
        platforms: [
            [
                platform: "SmartThings LG Connector",
                name: "LG Connector",
                app_url: apiServerUrl("/api/smartapps/installations/"),
                app_id: app.id,
                access_token:  state.accessToken
            ]
        ],
    ])

    def configString = new groovy.json.JsonOutput().prettyPrint(configJson)
    render contentType: "text/plain", data: configString
}

mappings {
    if (!params.access_token || (params.access_token && params.access_token != state.accessToken)) {
        path("/config")                         { action: [GET: "authError"] }
        path("/list")                         	{ action: [GET: "authError"]  }
        path("/update")                         { action: [POST: "authError"]  }
        path("/add")                         	{ action: [POST: "authError"]  }

    } else {
        path("/config")                         { action: [GET: "renderConfig"]  }
        path("/list")                         	{ action: [GET: "getDeviceList"]  }
        path("/update")                         { action: [POST: "updateDevice"]  }
        path("/add")                         	{ action: [POST: "addDevice"]  }
    }
}