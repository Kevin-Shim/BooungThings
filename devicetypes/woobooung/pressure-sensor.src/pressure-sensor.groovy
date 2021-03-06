/*
 *  Copyright 2019 SmartThings
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not
 *  use this file except in compliance with the License. You may obtain a copy
 *  of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 *  WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 *  License for the specific language governing permissions and limitations
 *  under the License.
 */
import physicalgraph.zigbee.clusters.iaszone.ZoneStatus
import physicalgraph.zigbee.zcl.DataType


// Change Water Sensor to Presence Sensor
metadata {
    definition(name: "Pressure Sensor", namespace: "WooBooung", author: "Booung", runLocally: true, minHubCoreVersion: '000.017.0012', executeCommandsLocally: false, mnmn: "SmartThings", genericHandler: "Zigbee") {
        capability "Configuration"
        capability "Battery"
        capability "Presence Sensor"
        capability "Temperature Measurement"
        capability "Refresh"
        capability "Health Check"
        capability "Sensor"

        command "enrollResponse"

        fingerprint inClusters: "0000,0001,0003,0402,0500,0020,0B05", outClusters: "0019", manufacturer: "CentraLite", model: "3315-S", deviceJoinName: "Pressure Sensor"
        fingerprint inClusters: "0000,0001,0003,0402,0500,0020,0B05", outClusters: "0019", manufacturer: "CentraLite", model: "3315"
        fingerprint inClusters: "0000,0001,0003,0402,0500,0020,0B05", outClusters: "0019", manufacturer: "CentraLite", model: "3315-Seu", deviceJoinName: "Pressure Sensor"
        fingerprint inClusters: "0000,0001,0003,000F,0020,0402,0500", outClusters: "0019", manufacturer: "SmartThings", model: "moisturev4", deviceJoinName: "Pressure Sensor"
        fingerprint inClusters: "0000,0001,0003,0020,0402,0500", outClusters: "0019", manufacturer: "Samjin", model: "water", deviceJoinName: "Pressure Sensor"
        fingerprint inClusters: "0000,0001,0003,0402,0500,0020,0B05", outClusters: "0019", manufacturer: "Visonic", model: "MCT-340 E", deviceJoinName: "Pressure Sensor" //Visonic Door/Window Sensor
    }

    simulator {

    }

    preferences {
        section {
            image(name: 'educationalcontent', multiple: true, images: [
                "http://cdn.device-gse.smartthings.com/Moisture/Moisture1.png",
                "http://cdn.device-gse.smartthings.com/Moisture/Moisture2.png",
                "http://cdn.device-gse.smartthings.com/Moisture/Moisture3.png"
            ])
        }
        section {
            input "presentStayTime", "number", title: "not present interval (in seconds).", description: "default 30 sec", value:30, displayDuringSetup: false
        }
        section {
            input title: "Temperature Offset", description: "This feature allows you to correct any temperature variations by selecting an offset. Ex: If your sensor consistently reports a temp that's 5 degrees too warm, you'd enter '-5'. If 3 degrees too cold, enter '+3'.", displayDuringSetup: false, type: "paragraph", element: "paragraph"
            input "tempOffset", "number", title: "Degrees", description: "Adjust temperature by this many degrees", range: "*..*", displayDuringSetup: false
        }
    }

    tiles(scale: 2) {
        multiAttributeTile(name: "presence", type: "generic", width: 6, height: 4) {
            tileAttribute("device.presence", key: "PRIMARY_CONTROL") {
                attributeState "not present", label: "Disconnected", icon: "st.presence.tile.mobile-not-present", backgroundColor: "#ffffff"
                attributeState "present", label: "Connected", icon: "st.presence.tile.mobile-present", backgroundColor: "#00A0DC"
            }
        }
        valueTile("temperature", "device.temperature", inactiveLabel: false, width: 2, height: 2) {
            state "temperature", label: '${currentValue}°',
                backgroundColors: [
                    [value: 31, color: "#153591"],
                    [value: 44, color: "#1e9cbb"],
                    [value: 59, color: "#90d2a7"],
                    [value: 74, color: "#44b621"],
                    [value: 84, color: "#f1d801"],
                    [value: 95, color: "#d04e00"],
                    [value: 96, color: "#bc2323"]
                ]
        }
        valueTile("battery", "device.battery", decoration: "flat", inactiveLabel: false, width: 2, height: 2) {
            state "battery", label: '${currentValue}% battery', unit: ""
        }
        standardTile("refresh", "device.refresh", inactiveLabel: false, decoration: "flat", width: 2, height: 2) {
            state "default", action: "refresh.refresh", icon: "st.secondary.refresh"
        }

        main(["presence", "temperature"])
        details(["presence", "temperature", "battery", "refresh"])
    }
}

def delayPost(){
    log.debug "------delay Post NotPresent------"
    // sendEvent(name : 'presence', value : 'not present')
}

private List<Map> collectAttributes(Map descMap) {
    List<Map> descMaps = new ArrayList<Map>()

    descMaps.add(descMap)

    if (descMap.additionalAttrs) {
        descMaps.addAll(descMap.additionalAttrs)
    }

    return  descMaps
}

def parse(String description) {
    log.debug "description: $description"

    // getEvent will handle temperature and humidity
    Map map = zigbee.getEvent(description)
    if (!map) {
        if (description?.startsWith('zone status')) {
            map = parseIasMessage(description)
        } else {
            Map descMap = zigbee.parseDescriptionAsMap(description)

            if (descMap?.clusterInt == zigbee.POWER_CONFIGURATION_CLUSTER && descMap.commandInt != 0x07 && descMap.value) {
                List<Map> descMaps = collectAttributes(descMap)

                if (device.getDataValue("manufacturer") == "Samjin") {
                    def battMap = descMaps.find { it.attrInt == 0x0021 }

                    if (battMap) {
                        map = getBatteryPercentageResult(Integer.parseInt(battMap.value, 16))
                    }
                } else {
                    def battMap = descMaps.find { it.attrInt == 0x0020 }

                    if (battMap) {
                        map = getBatteryResult(Integer.parseInt(battMap.value, 16))
                    }
                }
            } else if (descMap?.clusterInt == 0x0500 && descMap.attrInt == 0x0002) {
                def zs = new ZoneStatus(zigbee.convertToInt(descMap.value, 16))
                map = translateZoneStatus(zs)
            } else if (descMap?.clusterInt == zigbee.TEMPERATURE_MEASUREMENT_CLUSTER && descMap.commandInt == 0x07) {
                if (descMap.data[0] == "00") {
                    log.debug "TEMP REPORTING CONFIG RESPONSE: $descMap"
                    sendEvent(name: "checkInterval", value: 60 * 12, displayed: false, data: [protocol: "zigbee", hubHardwareId: device.hub.hardwareID, offlinePingable: "1"])
                } else {
                    log.warn "TEMP REPORTING CONFIG FAILED- error code: ${descMap.data[0]}"
                }
            } else if (descMap?.clusterInt == zigbee.IAS_ZONE_CLUSTER && descMap.attrInt == zigbee.ATTRIBUTE_IAS_ZONE_STATUS && descMap?.value) {
                def zs = new ZoneStatus(zigbee.convertToInt(descMap.value, 16))
                map = getContactResult(zs.isAlarm1Set() ? "open" : "closed")
            }
        }
    } else if (map.name == "temperature") {
        if (tempOffset) {
            map.value = (int) map.value + (int) tempOffset
        }
        map.descriptionText = temperatureScale == 'C' ? '{{ device.displayName }} was {{ value }}°C' : '{{ device.displayName }} was {{ value }}°F'
        map.translatable = true
        map.displayed = true
    }


    def result = map ? sendEvent(map) : [:]

    if (description?.startsWith('enroll request')) {
        List cmds = zigbee.enrollResponse()
        log.debug "enroll response: ${cmds}"
        result = cmds?.collect { new physicalgraph.device.HubAction(it) }
    }

    log.debug "Parse returned $result"

    return result
}

private Map parseIasMessage(String description) {
    ZoneStatus zs = zigbee.parseZoneStatus(description)

    translateZoneStatus(zs)
}

private Map translateZoneStatus(ZoneStatus zs) {
    if (zs.isAlarm1Set())  {  
        log.debug "========runIn=============="
        runIn(30, 'delayPost', [overwrite: true])
    }
    return zs.isAlarm1Set() ? getMoistureResult('present') : getMoistureResult('not present')
}

private Map getBatteryResult(rawValue) {
    log.debug "Battery rawValue = ${rawValue}"
    def linkText = getLinkText(device)

    def result = [:]

    def volts = rawValue / 10

    if (!(rawValue == 0 || rawValue == 255)) {
        result.name = 'battery'
        result.translatable = true
        result.descriptionText = "{{ device.displayName }} battery was {{ value }}%"
        if (device.getDataValue("manufacturer") == "SmartThings") {
            volts = rawValue // For the batteryMap to work the key needs to be an int
            def batteryMap = [28: 100, 27: 100, 26: 100, 25: 90, 24: 90, 23: 70,
                              22: 70, 21: 50, 20: 50, 19: 30, 18: 30, 17: 15, 16: 1, 15: 0]
            def minVolts = 15
            def maxVolts = 28

            if (volts < minVolts)
            volts = minVolts
            else if (volts > maxVolts)
                volts = maxVolts
            def pct = batteryMap[volts]
            result.value = pct
        } else {
            def minVolts = 2.1
            def maxVolts = 3.0
            def pct = (volts - minVolts) / (maxVolts - minVolts)
            def roundedPct = Math.round(pct * 100)
            if (roundedPct <= 0)
            roundedPct = 1
            result.value = Math.min(100, roundedPct)
        }
		result.displayed = true
    }

    return result
}

private Map getBatteryPercentageResult(rawValue) {
    log.debug "Battery Percentage rawValue = ${rawValue} -> ${rawValue / 2}%"
    def result = [:]

    if (0 <= rawValue && rawValue <= 200) {
        result.name = 'battery'
        result.translatable = true
        result.descriptionText = "{{ device.displayName }} battery was {{ value }}%"
        result.value = Math.round(rawValue / 2)
        result.displayed = true
    }

    return result
}

private Map getContactResult(value) {
	log.debug 'Contact Status'
	def linkText = getLinkText(device)
	def descriptionText = "${linkText} was ${value == 'open' ? 'not present' : 'present'}"
	return [
		name           : 'presence',
		value          : value,
		descriptionText: descriptionText,
        displayed      : true
	]
}


private Map getMoistureResult(value) {
    log.debug "${value}"

    def map = [:]

    def descriptionText
    long presentStayTimeMs = settings.presentStayTime * 1000

	return [
        name           : 'presence',
        value          : value,
        descriptionText: "{{ device.displayName }} is ${value}",
        displayed      : true
    ]
}


/**
 * PING is used by Device-Watch in attempt to reach the Device
 * */
def ping() {
    zigbee.readAttribute(zigbee.IAS_ZONE_CLUSTER, zigbee.ATTRIBUTE_IAS_ZONE_STATUS)
}

def refresh() {
    log.debug "Refreshing Values"
    def refreshCmds = []

    if (device.getDataValue("manufacturer") == "Samjin") {
        refreshCmds += zigbee.readAttribute(zigbee.POWER_CONFIGURATION_CLUSTER, 0x0021)
    } else {
        refreshCmds += zigbee.readAttribute(zigbee.POWER_CONFIGURATION_CLUSTER, 0x0020)
    }
    refreshCmds += zigbee.readAttribute(zigbee.TEMPERATURE_MEASUREMENT_CLUSTER, 0x0000) +
        zigbee.readAttribute(zigbee.IAS_ZONE_CLUSTER, zigbee.ATTRIBUTE_IAS_ZONE_STATUS) +
        zigbee.enrollResponse()

    return refreshCmds
}

def configure() {
    // Device-Watch allows 2 check-in misses from device + ping (plus 1 min lag time)
    // enrolls with default periodic reporting until newer 5 min interval is confirmed
    sendEvent(name: "checkInterval", value: 2 * 60 * 60 + 1 * 60, displayed: false, data: [protocol: "zigbee", hubHardwareId: device.hub.hardwareID, offlinePingable: "1"])

    log.debug "Configuring Reporting"
    def configCmds = []

    // temperature minReportTime 30 seconds, maxReportTime 5 min. Reporting interval if no activity
    // battery minReport 30 seconds, maxReportTime 6 hrs by default
    if (device.getDataValue("manufacturer") == "Samjin") {
        configCmds += zigbee.configureReporting(zigbee.POWER_CONFIGURATION_CLUSTER, 0x0021, DataType.UINT8, 30, 21600, 0x10)
    } else {
        configCmds += zigbee.batteryConfig()
    }
    configCmds += zigbee.temperatureConfig(30, 300)

    return refresh() + configCmds + refresh() // send refresh cmds as part of config
}