#!/usr/bin/env python

import socket
import thread
import time

def main():
	TCP_IP = '10.0.0.6'
	CONTROLLER_TCP_PORT = 5006

	MESSAGE_FORWARD = '\x7f\x7f\x80\x80\xf0\x00\xf0\x00'
	MESSAGE_BACK =    '\x7f\x7f\x80\x80\x0f\xff\x0f\xff'
	
	receiver_socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
	receiver_socket.connect((TCP_IP, CONTROLLER_TCP_PORT))
	while True:
		receiver_socket.send(MESSAGE_FORWARD)
		time.sleep(1)
		receiver_socket.send(MESSAGE_BACK)
		time.sleep(1)
	
main()
