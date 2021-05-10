import socket
import json
import sys
import time

HOST = '192.168.43.61'  # The server's hostname or IP address
PORT = 3003        # The port used by the server

goToPos = "1"
goTo = "2"
saveLocation = "3"
speak = "4"
stopPatrol = "5"

def throwJson(command, commandx, a=None, ax=None, b=None, bx=None, c=None, cx=None):
    with open("temi_commands.json") as f:
        data = json.load(f)
    for pos in data[command][commandx]:
        if a != None:
            pos[a] = ax
            if b != None:
                pos[b] = bx
                if c != None:
                    pos[c] = cx
            else:
                break
        else:
            break
    newData = json.dumps(data[command])
    return newData

def send_msg():
    # s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    # s.connect((HOST, PORT))
    while True:
        s = socket.create_connection((HOST,PORT))
        command = input(str("\nSelect Command: \n1) Go To Position\n2) Go To\n3) Save Location\n4) TTS\n5) Stop Patrol Mode\n> "))
        print()
        if command == goToPos:
            x = input(str("Enter X Pos: "))
            y = input(str("Enter Y Pos: "))
            yaw = input(str("Enter Yaw Pos: "))
            message = throwJson(goToPos,"goToPos", "x", x, "y", y, "yaw", yaw)
        elif command == goTo:
            desto = input(str("Enter Destination: "))
            message = throwJson(goTo, "goTo", "desto", desto)
        elif command == saveLocation:
            location = input(str("Enter Location: "))
            message = throwJson(saveLocation, "saveLocation", "location", location)
        elif command == speak:
            say = input(str("Enter speak command: "))
            message = throwJson(speak, "speak", "say", say)
        elif command == stopPatrol:
            message = throwJson(stopPatrol, "stopPatrol")
        s.sendall(bytes(message,encoding="utf-8"))


send_msg()