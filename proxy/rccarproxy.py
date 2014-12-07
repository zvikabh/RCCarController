#!/usr/bin/python

import Queue
import socket
import threading
import time

RECEIVER_TCP_PORT = 5005
CONTROLLER_TCP_PORT = 5006

BUFFER_SIZE = 8


def main():
  queue = Queue.Queue()

  controller_thread = threading.Thread(target=ControllerThread, args=(queue, CONTROLLER_TCP_PORT))
  receiver_thread = threading.Thread(target=ReceiverThread, args=(queue, RECEIVER_TCP_PORT))

  controller_thread.start()
  receiver_thread.start()

  controller_thread.join()
  receiver_thread.join()


def ConnectSocket(s, name):
  conn, addr = s.accept()
  print '%s connected to %s' % (name, addr)
  return conn

def ControllerThread(queue, port):
  s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
  s.bind(('', port))
  s.listen(1)

  conn = ConnectSocket(s, 'Controller')

  while True:
    data = conn.recv(BUFFER_SIZE)
    if not data:
      print 'Controller disconnected - attempting to reconnect'
      conn.close()
      conn = ConnectSocket(s, 'Controller')
      continue
    print 'From controller: ', ''.join(['%02X ' % ord(b) for b in data])
    queue.put(data)

  conn.close()


def ReceiverThread(queue, port):
  s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
  s.bind(('', port))
  s.listen(1)

  conn = ConnectSocket(s, 'Receiver')

  while True:
    data = queue.get()
    try:
      conn.sendall(data)
    except:
      print 'Receiver disconnected - attempting to reconnect'
      conn.close()
      conn = ConnectSocket(s, 'Receiver')
      continue
    print 'To receiver: ', ''.join(['%02X ' % ord(b) for b in data])

  conn.close()


if __name__ == '__main__':
  main()
