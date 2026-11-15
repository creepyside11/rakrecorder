import socket
with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as server:
    server.bind(('0.0.0.0', 8766))
    while True:
        data, peer = server.recvfrom(2048)
        server.sendto(b'rakrecorder-echo:' + data, peer)
