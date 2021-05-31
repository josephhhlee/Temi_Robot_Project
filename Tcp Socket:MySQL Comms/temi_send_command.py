import socket
import json
import sys
import time

HOST = ''  # The server's hostname or IP address
PORT = 0        # The port used by the server

goToPos = "1"
goTo = "2"
saveLocation = "3"
speak = "4"

def throwJson(command, a=None, ax=None, b=None, bx=None, c=None, cx=None):
    if a != None:
        data = {a : ax}
        if b != None:
            data[b] = bx
            if c != None:
                data[c] = cx
    else:
        data = None
    newData = json.dumps({command:data})
    return newData

def send_msg():
    while True:
        s = socket.create_connection((HOST,PORT))
        command = input(str("\nSelect Command: \n1) Go To Position\n2) Go To\n3) Save Location\n4) TTS\n> "))
        print()
        if command == goToPos:
            x = input(str("Enter X Pos: "))
            y = input(str("Enter Y Pos: "))
            yaw = input(str("Enter Yaw Pos: "))
            message = throwJson("goToPos", "x", float(x), "y", float(y), "yaw", float(yaw))
        elif command == goTo:
            desto = input(str("Enter Destination: "))
            message = throwJson("goTo", "desto", desto)
        elif command == saveLocation:
            location = input(str("Enter Location: "))
            message = throwJson("saveLocation", "location", location)
        elif command == speak:
            say = input(str("Enter speak command: "))
            message = throwJson("speak", "say", say)
        s.send(bytes(message,encoding="utf-8"))


send_msg()