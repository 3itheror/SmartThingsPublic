/**
 *  Copyright 2015 SmartThings
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
 *  Presence Change Push
 *
 *  Author: SmartThings
 */
definition(
    name: "Motion Push",
    namespace: "pocket",
    author: "eitheror",
    description: "Get a push notification when motion detected.",
    category: "Safety & Security",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/Meta/text_presence.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Meta/text_presence@2x.png"
)

preferences {
	section("When motion is detected...") {
    	input "motion", "capability.motionSensor", title: "Where?"
    }
}

preferences {
	section("When I am out..") {
		input "presence", "capability.presenceSensor", title: "What device?"
	}
	section("Send this message (optional, sends standard status message if not specified)"){
		input "messageText", "text", title: "Message Text", required: false
	}
	section("Via a push notification and/or an SMS message"){
		input("recipients", "contact", title: "Send notifications to") {
			input "phone", "phone", title: "Enter a phone number to get SMS", required: false
			paragraph "If outside the US please make sure to enter the proper country code"
			input "pushAndPhone", "enum", title: "Notify me via Push Notification", required: false, options: ["Yes", "No"]
		}
	}
	section("Minimum time between messages (optional, defaults to every message)") {
		input "frequency", "decimal", title: "Minutes", required: false
	}
}

def installed() {
    log.debug "Installed with settings: ${settings}"
    subscribe(motion, "motion.active", motionHandler)
}

def updated() {
    log.debug "Updated with settings: ${settings}"
	unsubscribe()
    subscribe(motion, "motion.active", motionActiveHandler)
}

def motionActiveHandler(evt) {
    log.debug "Notify got evt ${evt}"
 
   if (frequency) {
       def lastTime = state[evt.deviceId]
       if (lastTime == null || now() - lastTime >= frequency * 60000) {
           sendmessage(evt)
       }
   }
   else {
       sendMessage(evt)
   }
}

private sendMessage(evt) {
    String msg = messageText
    Map opetions = [:]

    if (!messageText) {
        msg = defaultText(evt)
        options = [translatable: true, triggerEvent: evt]
    }
    log.debug "$evt.name:$evt.value, pushAndPhone:$pushAndPhone, '$msg'"

    if (location.contactBookEnabled) {
        sendNotificationToContacts(msg, recipients, options)
    } else {
        if (phone) {
            options.phone = phone
            if (pushAndPhone != 'No') {
                log.debug 'Sending push and SMS'
                options.method = 'both'
            } else {
                log.debug 'Sending SMS'
                options.method = 'phone'
            }
        } else if (pushAndPhone != 'No') {
            log.debug 'Sending push'
            options.method = 'push'
        } else {
            log.debug 'Sending nothing'
            options.method = 'none'
        }
        sendNotification(msg, options)
    }
    if (frequency) {
        state[evt.deviceId] = now()
    }
}

private defaultText(evt) {
	if (evt.name == 'presence') {
		if (evt.value == 'present') {
			if (includeArticle) {
				'{{ triggerEvent.linkText }} has arrived at the {{ location.name }}'
			}
			else {
				'{{ triggerEvent.linkText }} has arrived at {{ location.name }}'
			}
		} else {
			if (includeArticle) {
				'{{ triggerEvent.linkText }} has left the {{ location.name }}'
			}
			else {
				'{{ triggerEvent.linkText }} has left {{ location.name }}'
			}
		}
	} else {
		'{{ triggerEvent.descriptionText }}'
	}
}

private getIncludeArticle() {
	def name = location.name.toLowerCase()
	def segs = name.split(" ")
	!(["work","home"].contains(name) || (segs.size() > 1 && (["the","my","a","an"].contains(segs[0]) || segs[0].endsWith("'s"))))
}