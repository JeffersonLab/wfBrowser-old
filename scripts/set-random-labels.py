#!/usr/bin/env python3

import requests
import random
import json

cavity_labels = ["1", "2", "3", "4", "5", "6", "7", "8", "multiple", "junk"]
fault_labels = ["Single Cavity Turn Off", "Multi Cavity Turn Off", "Microphonics", "Quench", "E_Quench"]

response = requests.get('http://localhost:8080/wfbrowser/ajax/event');
print("GET status =", response.status_code)

if response.status_code == 200:
    resp_json = response.json()
    for event in resp_json["events"]:
        if event["system"] != "rf":
           continue 
        if event['labels'] is not None:
            print(ascii(event))
        if event["labels"] is None:
            cav = cavity_labels[random.randrange(0, len(cavity_labels))];
            conf = random.random()
            cav_data = {
                            'id': event["id"],
                            'label': '{"model-name":"my_random_labeler","name":"cavity","value":"'+cav+'","confidence":'+str(conf)+'}'
                       }
            fault = fault_labels[random.randrange(0, len(fault_labels))];
            conf = random.random()
            fault_data = {
                            'id': event["id"],
                            'label': '{"model-name":"my_random_labeler","name":"fault-type","value":"'+fault+'","confidence":'+str(conf)+'}'
                       }
            resp = requests.post('http://localhost:8080/wfbrowser/ajax/event-label', data = cav_data)
            print("cav POST status =", resp.status_code)
            print("cav POST content =", resp.content)
            resp = requests.post('http://localhost:8080/wfbrowser/ajax/event-label', data = fault_data)
            print("fault POST status =", resp.status_code)
            print("fault POST content =", resp.content)
else:
    print("we've got a problem")
    print(response.content)
