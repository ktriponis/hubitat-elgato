/**
 *  Elgato Key Light Device Handler
 *
 *  Copyright © 2021 KTriponis
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
 * Hubitat is the Trademark and Intellectual Property of Hubitat, Inc.
 * Elgato is the Trademark and Intellectual Property of Corsair Gaming, Inc.
 *
 * -------------------------------------------------------------------------------------------------------------------
 *
 *  Changes:
 *  1.0.0 - Initial release
 *
 */

import groovy.json.JsonOutput

metadata {
    definition(
        name: "Elgato Key Light",
        namespace: "elgato",
        author: "KTriponis",
        importUrl: "https://raw.githubusercontent.com/ktriponis/Hubitat-Elgato-Key-Light/main/Elgato-Key-Light.groovy"
    ) {
        capability "Light"
        capability "Switch"
        capability "Polling"
        capability "Refresh"
        capability "SwitchLevel"
        capability "ColorTemperature"

        attribute "numberOfLights", "number"
    }
    preferences {
    	input name: "pollIntervalSec", 
    		type: "enum", 
    		title: "Polling Interval (seconds)", 
          	options: ["5", "15", "30", "60", "Disabled"], 
          	defaultValue: "Disabled", 
          	required: false
        input name: "ip",
            type: "string",
            title: "Key Light IP Address:",
            required: true,
            defaultValue: ""
    }
}

def updated() {
    unschedule()

    startPolling()
    
    refresh()
    getAccessoryInfo()
}

def on() {
    updateLightOptions(switchValue: "on")
}

def off() {
    updateLightOptions(switchValue: "off")
}

def setLevel(level, duration = 0) {
    updateLightOptions(level: level, duration: duration)
}

def setColorTemperature(colortemperature, level = null, transitionTime = 0) {
    updateLightOptions(colortemperature: colortemperature, level: level, transitionTime: transitionTime)
}

def poll() {
    refresh()
}

def startPolling() {
    if (pollIntervalSec && pollIntervalSec != "Disabled") {
        Integer interval = pollIntervalSec.toInteger()
        log.debug "Scheduling refresh every ${interval} seconds"
        runIn(interval, scheduledRefresh)
    } else {
        log.info "Polling disabled"
    }
}

def scheduledRefresh() {
    refresh()
    startPolling() 
}

def refresh() {
    try {
        httpGet("http://${ip}:9123/elgato/lights") { resp ->
            options = resp.data
            state.numberOfLights = options.numberOfLights
            state.switch = toSwitchValue(options.lights[0].on)
            state.level = options.lights[0].brightness
            state.colorTemperature = toTemperatureValue(options.lights[0].temperature)

            sendEvent name: "numberOfLights", value: state.numberOfLights
            sendEvent name: "switch", value: state.switch
            sendEvent name: "level", value: state.level
            sendEvent name: "colorTemperature", value: state.colorTemperature
        }
    } catch (e) {
        log.error "Error retrieving light info: $e"
    }
}

private def getAccessoryInfo() {
    try {
        httpGet("http://${ip}:9123/elgato/accessory-info") { resp ->
            info = resp.data
            state.productName = info.productName
            state.hardwareBoardType = info.hardwareBoardType
            state.firmwareBuildNumber = info.firmwareBuildNumber
            state.firmwareVersion = info.firmwareVersion
            state.serialNumber = info.serialNumber
            state.displayName = info.displayName
        }
    } catch (e) {
        log.error "Error retrieving accessory info: $e"
    }
}

private def updateLightOptions(opts) {
    try {
        httpPut("http://${ip}:9123/elgato/lights", lightOptions(opts)) {
            refresh()
        }
    } catch (e) {
        log.error "Error updating light options: $e"
    }
}

private def lightOptions(opts) {
    JsonOutput.toJson([
        numberOfLights: state.numberOfLights,
        lights        : [
            [
                on         : fromSwitchValue(opts.switchValue ?: state.switch),
                brightness : opts.level ?: state.level,
                temperature: fromTemperatureValue(opts.colortemperature ?: state.colorTemperature)
            ]
        ]
    ])
}

private static toSwitchValue(lightOn) {
    lightOn ? "on" : "off"
}

private static fromSwitchValue(switchValue) {
    switchValue == "on" ? 1 : 0
}

private static toTemperatureValue(temperature) {
    def temp = 999249.2902569509 * temperature**-0.9998360126977142
    temp = Math.round(temp + (50 - (temp % 50 ?: 50)))
    if (temp < 2900) return 2900
    if (temp > 7000) return 7000
    return temp
}

private static fromTemperatureValue(temperatureValue) {
    def val = Math.round(1068894.4204689409 * temperatureValue**-1.0074488102192827)
    if (val < 143) return 143
    if (val > 344) return 344
    return val
}
