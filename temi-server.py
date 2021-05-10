#!/user/bin/python3

import select
import socket,os
import time
import datetime;
import json
import mysql.connector
import threading

serverHost = '192.168.50.82'
serverPort = 3838

getBattPercent = 'battery_strength'
getBattCharging = 'battery_charging'
getId = 'id'

status = {
        1: ['offline', 0.0],
        2: ['offline', 0.0],
        3: ['offline', 0.0],
        4: ['offline', 0.0],
        5: ['offline', 0.0]
    }

# ct stores current time
def Time():
    global timenow, now
    while True:
        ct = datetime.datetime.now()
        timenow = "Time@ " + str(ct)
        now = time.time()

def connectSQL():
    global mydb, mycursor, connectedSQL
    starting = True
    while True:
        try:
            mydb = mysql.connector.connect(
                host="192.168.48.210",
                user="lance",
                password="1234",
                database="batterystatus")
            mycursor = mydb.cursor()
            connectedSQL = True
            if starting:
                print('['+timenow+'] >> '+"Database Connected\n"+str(mydb))
                starting = False
                time.sleep(10)
            else:
                time.sleep(10) #Check SQL connection every 10 sec
        except Exception as e:
            print('['+timenow+']' + " >> Failed to connect to Database\nretry in 10secs")
            starting = True
            connectedSQL = False
            time.sleep(10)

def readSQL():
    mycursor.execute("SELECT * FROM battery_status")
    myresult = mycursor.fetchall()
    for x in myresult:
        print(x)
 
def writeSQL(title, value, idNum):
    sql1 = "UPDATE battery_status SET "+title+" = %s WHERE id = %s"
    val1 = (value, idNum)
    mycursor.execute(sql1, val1)
    mydb.commit()
    # readSQL()

def startServer():
    global sock, socks
    connected = False
    while not connected:
        try:
            sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)  
            sock.bind((serverHost, serverPort))
            socks = [sock]
            connected = True
        except socket.error:
            print('['+timenow+']' + " >> Failed to establish Server\nretry in 10secs")
            time.sleep(10)
    sock.listen(5)
    print('['+timenow+']'+' >> Server Started')

def waitClient():
    clients = {}
    lastread = {}
    while True:
        closed = []
        readable,_,_ = select.select(socks,[],[], 10)
        for s in readable:
            if s is sock:
                lastread[s] = now
                connection,address = sock.accept()
                client = str(address[0])
                if client not in clients:
                    clients[client] = 0
                    print('['+timenow+']' + " Accepting connection from >> "+client)
                else:
                    threading.Thread(target=getClient, args=(connection,str(address))).start()
                    for i in clients:
                        timeout = 5
                        if i != client:
                            clients[i] += 1
                        else:
                            clients[i] = 0
                        if clients[i] == timeout:
                            print('['+timenow+']' + " Client disconnected >> " + i)
                            closed.append(i)
        for s in lastread:
            timeout = 20
            if s not in readable and now - lastread[s] > timeout:
                print("No Client Connected")
                statusListener("disconnected")
                closed.append(s)
                del clients[client]
        for s in closed:
            try:
                del lastread[s]
            except (ValueError, KeyError) as e:
                try:
                    del clients[s]
                except (ValueError, KeyError) as e:
                    pass

def getClient(connection,address):
    try:  
        buf = connection.recv(1024)
        # print(buf)
        data = json.loads(str(buf,'utf-8'))
        idNum = data[getId]
        writeSQL(getBattPercent, data[getBattPercent], idNum)
        writeSQL(getBattCharging, data[getBattCharging], idNum)
        statusListener(idNum)
        print('[' + timenow + '] ' + "Client" + address + " >>")
        print(data)
    except Exception as e:
        # print(e)
        connection.close()
    connection.close()

def statusListener(idNum):
    timeout = 20
    if connectedSQL:
        for key in status:
            if idNum == key:
                status[key][0] = 'online'
                status[key][1] = now
            if status[key][1] != 0.0 and now - status[key][1] > timeout or idNum == "disconnected":
                status[key][0] = 'offline'
                status[key][1] = 0.0
            writeSQL('status', status[key][0], key)

threading.Thread(target=connectSQL).start()
threading.Thread(target=Time).start()
startServer()
waitClient()