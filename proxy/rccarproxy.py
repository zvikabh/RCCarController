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


def ControllerThread(queue, port):
  s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
  s.bind(('', port))
  s.listen(1)

  conn, addr = s.accept()
  print 'Controller connected to address:', addr

  while True:
    data = conn.recv(BUFFER_SIZE)
    print 'From controller: ', ''.join(['%02X ' % ord(b) for b in data])
    queue.put(data)
    if not data: break

  conn.close()


def ReceiverThread(queue, port):
  s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
  s.bind(('', port))
  s.listen(1)

  conn, addr = s.accept()
  print 'Receiver connected to address: ', addr

  while True:
    data = queue.get()
    if not data: break
    conn.send(data)
    print 'To receiver: ', ''.join(['%02X ' % ord(b) for b in data])

  conn.close()


if __name__ == '__main__':
  main()
