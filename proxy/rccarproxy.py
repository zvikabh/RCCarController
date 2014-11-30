#!/usr/bin/python

import socket
import thread
import time

def main():
	TCP_IP = '10.0.0.6'
	RECEIVER_TCP_PORT = 5005
	controller_TCP_PORT = 5006

	receiver_socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
	receiver_socket.bind((TCP_IP, RECEIVER_TCP_PORT))
	receiver_socket.listen(1)

	controller_socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
	controller_socket.bind((TCP_IP, controller_TCP_PORT))
	controller_socket.listen(1)
	
	#thread.start_new_thread(receiver_controller_thread, (receiver_socket, controller_socket))
	receiver_controller_thread(receiver_socket, controller_socket)
	


def receiver_controller_thread(receiver_socket, controller_socket):
	BUFFER_SIZE = 8

	while True:
		receiver_conn, receiver_addr = receiver_socket.accept()
		print 'Receiver Connection address:', receiver_addr
		controller_conn, controller_addr = controller_socket.accept()
		print 'Controller Connection address:', controller_addr
		while True:
			data = controller_conn.recv(BUFFER_SIZE)
			if not data: 
				print "Can't read data"
				break
			print 'received data: ' + ' '.join(['%02X' % ord(b) for b in data])
			receiver_conn.send(data)  # pass this to the receiver
			print 'sent'
		print 'closing'
		receiver_conn.close()
		controller_conn.close()

def receiver_thread():
	TCP_PORT = 5005

	s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
	s.bind((TCP_IP, TCP_PORT))
	s.listen(1)

	conn, addr = s.accept()
	print 'Connection address:', addr
	while True:
		data = conn.recv(BUFFER_SIZE)
		if not data: break
		print "received data:", data
		conn.send(data)  # echo
	conn.close()

def controller_thread():
	TCP_PORT = 5006

	s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
	s.bind((TCP_IP, TCP_PORT))
	s.listen(1)

	conn, addr = s.accept()
	print 'Connection address:', addr
	while True:
		data = conn.recv(BUFFER_SIZE)
		if not data: break
		print "received data:", data
		conn.send(data)  # echo
	conn.close()

if __name__=='__main__':
	main()
#thread.start_new_thread(receiver_thread, ())
#thread.start_new_thread(controller_thread, ())
#while True:
#	time.sleep(1000)
